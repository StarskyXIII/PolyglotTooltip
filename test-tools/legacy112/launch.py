import argparse
import csv
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import socket
import zipfile
import re
from urllib.request import urlopen
from concurrent.futures import ThreadPoolExecutor


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def allowed(library):
    rules = library.get('rules', [])
    value = not rules
    for rule in rules:
        if rule.get('os', {}).get('name', 'windows') == 'windows':
            value = rule['action'] == 'allow'
    return value


def library_path(root, name, classifier=None):
    parts = name.split(':')
    group, artifact, version = parts[:3]
    classifier = classifier or (parts[3] if len(parts) > 3 else None)
    suffix = '-' + classifier if classifier else ''
    return root / group.replace('.', '/') / artifact / version / f'{artifact}-{version}{suffix}.jar'


def resolve_library(prism, cache, library, download, classifier=None):
    path = library_path(prism / 'libraries', library['name'], classifier)
    expected = download.get('sha1')
    if path.is_file() and (not expected or hashlib.sha1(path.read_bytes()).hexdigest() == expected):
        return path
    path = library_path(cache, library['name'], classifier)
    if not path.is_file() or expected and hashlib.sha1(path.read_bytes()).hexdigest() != expected:
        with urlopen(download['url'], timeout=60) as response:
            data = response.read()
        if expected and hashlib.sha1(data).hexdigest() != expected:
            raise RuntimeError('Library checksum mismatch: ' + library['name'])
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    return path


def running(pid):
    if os.name == 'nt':
        result = subprocess.run(['tasklist', '/FI', f'PID eq {pid}', '/FO', 'CSV', '/NH'],
                                capture_output=True, text=True, creationflags=subprocess.CREATE_NO_WINDOW)
        if result.returncode != 0:
            raise RuntimeError('Could not inspect existing fixture process')
        return any(len(row) > 1 and row[1] == str(pid) for row in csv.reader(result.stdout.splitlines()))
    return Path('/proc', str(pid)).exists()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--prism', type=Path, required=True)
    parser.add_argument('--java', type=Path, required=True)
    parser.add_argument('--mixinbooter', type=Path)
    parser.add_argument('--cleanroom', type=Path)
    parser.add_argument('--mod', type=Path, action='append', default=[])
    parser.add_argument('--name', default='')
    parser.add_argument('--jvm-arg', action='append', default=[])
    parser.add_argument('--forge', choices=['2860', '2864'], default='2864')
    parser.add_argument('--port', type=int, default=19122)
    parser.add_argument('--baseline', action='store_true')
    parser.add_argument('--product', type=Path)
    parser.add_argument('--bridge-mode', choices=['instrumented', 'passive'], default='instrumented')
    parser.add_argument('--selective-languages', choices=['true', 'false'])
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    profile = 'cleanroom-0.6.13' if args.cleanroom else 'forge' + args.forge
    if args.name:
        if not re.fullmatch('[a-zA-Z0-9-]{1,64}', args.name):
            raise ValueError('Invalid fixture name')
        profile += '-' + args.name
    fixture = root / 'legacy/1.12.2/build/fixtures' / (profile + ('-baseline' if args.baseline else ''))
    fixture.mkdir(parents=True, exist_ok=True)
    ownership = fixture / '.polyglot-fixture'
    if not ownership.exists() and any(fixture.iterdir()):
        raise RuntimeError('Refusing existing unowned fixture directory')
    if ownership.exists() and ownership.read_text(encoding='utf-8') != 'legacy112\n':
        raise RuntimeError('Invalid fixture ownership marker')
    record_path = fixture / 'launch.json'
    if record_path.exists() and running(json.loads(record_path.read_text(encoding='utf-8'))['pid']):
        raise RuntimeError('Fixture process is still running')
    with socket.socket() as probe:
        probe.settimeout(0.25)
        if probe.connect_ex(('127.0.0.1', args.port)) == 0:
            raise RuntimeError('Fixture bridge port is occupied')
    ownership.write_text('legacy112\n', encoding='utf-8', newline='\n')
    for directory in ['mods', 'natives', 'config', 'resourcepacks']:
        (fixture / directory).mkdir(exist_ok=True)
    if args.selective_languages is not None:
        forge_config = fixture / 'config/forge.cfg'
        if forge_config.exists():
            content = forge_config.read_text(encoding='utf-8')
            content, count = re.subn(r'(?m)^(\s*B:selectiveResourceReloadEnabled=)(true|false)\s*$',
                                     lambda match: match[1] + args.selective_languages, content)
            if count != 1:
                raise RuntimeError('Missing or ambiguous selective reload option')
        else:
            content = 'client {\n    B:selectiveResourceReloadEnabled=' + args.selective_languages + '\n}\n'
        forge_config.write_text(content, encoding='utf-8')
    product = args.product or root / 'legacy/1.12.2/build/libs/polyglottooltip-1.12.2-forge-2.0.jar'
    if args.product and args.baseline:
        raise ValueError('A product override cannot be used in the product-free baseline')
    bridge_classifier = 'passive-production' if args.bridge_mode == 'passive' else 'production'
    bridge = root / ('test-tools/legacy112/bridge/build/libs/polyglot-devbridge-1.12.2-1.0.0-' + bridge_classifier + '.jar')
    if not args.cleanroom and args.mixinbooter is None:
        raise ValueError('Forge requires --mixinbooter')
    artifacts = ([] if args.cleanroom else [args.mixinbooter]) + [bridge] + ([] if args.baseline else [product]) + args.mod
    desired = {artifact.name for artifact in artifacts}
    if len(desired) != len(artifacts):
        raise ValueError('Duplicate artifact filenames')
    embedded = {}
    for artifact in artifacts:
        with zipfile.ZipFile(artifact) as archive:
            for member in archive.namelist():
                if member.endswith('.jar'):
                    embedded[Path(member).name] = hashlib.sha256(archive.read(member)).hexdigest()
    unexpected = []
    for item in (fixture / 'mods').rglob('*'):
        if item.is_dir():
            continue
        relative = item.relative_to(fixture / 'mods')
        if len(relative.parts) == 1 and item.name in desired:
            continue
        if len(relative.parts) == 2 and relative.parts[0] == '1.12.2' and embedded.get(item.name) == digest(item):
            continue
        unexpected.append(str(relative))
    if unexpected:
        raise RuntimeError('Unexpected fixture mods; use a distinct --name: ' + ', '.join(sorted(unexpected)))
    for artifact in artifacts:
        shutil.copy2(artifact, fixture / 'mods' / artifact.name)
    options = fixture / 'options.txt'
    if not options.exists():
        options.write_text('lang:en_us\nforceUnicodeFont:false\nguiScale:2\nrenderDistance:3\nmaxFps:60\npauseOnLostFocus:false\n', encoding='utf-8')
    classpath = []
    native_jars = []
    components = []
    if args.cleanroom:
        with zipfile.ZipFile(args.cleanroom) as archive:
            for component in ['net.minecraft', 'org.lwjgl3', 'net.minecraftforge']:
                components.append(json.loads(archive.read('patches/' + component + '.json')))
    else:
        for component, version in [('net.minecraft', '1.12.2'), ('org.lwjgl', '2.9.4-nightly-20150209'), ('net.minecraftforge', '14.23.5.' + args.forge)]:
            components.append(json.loads((args.prism / 'meta' / component / (version + '.json')).read_text()))
    jobs = {}
    native_jobs = []
    for metadata in components:
        libraries = metadata['libraries'] + ([metadata['mainJar']] if 'mainJar' in metadata else [])
        for library in libraries:
            if not allowed(library):
                continue
            downloads = library.get('downloads', {})
            if 'artifact' in downloads and not library.get('natives'):
                jobs[library['name']] = (library, downloads['artifact'], None)
            classifier = library.get('natives', {}).get('windows')
            if classifier:
                key = library['name'] + ':' + classifier
                jobs[key] = (library, downloads['classifiers'][classifier], classifier)
                native_jobs.append(key)
    cache = root / 'legacy/1.12.2/build/runtime-libraries'
    with ThreadPoolExecutor(max_workers=6) as pool:
        resolved = dict(zip(jobs, pool.map(lambda job: resolve_library(args.prism, cache, *job), jobs.values())))
    for key, library in resolved.items():
        if key in native_jobs:
            native_jars.append(library)
        else:
            classpath.append(library)
    for path in native_jars:
        with zipfile.ZipFile(path) as archive:
            for member in archive.namelist():
                if member.lower().endswith('.dll'):
                    (fixture / 'natives' / Path(member).name).write_bytes(archive.read(member))
    command = [str(args.java), '-Xms512m', '-Xmx3g', '-Dfile.encoding=UTF-8'] + args.jvm_arg + [
               '-Djava.library.path=' + str(fixture / 'natives'), '-Dclientdevbridge.enabled=true',
               '-Dclientdevbridge.port=' + str(args.port), '-cp', os.pathsep.join(map(str, classpath)),
               components[-1]['mainClass'], '--tweakClass', 'net.minecraftforge.fml.common.launcher.FMLTweaker',
               '--username', 'PTLegacyFixture', '--version', '1.12.2', '--gameDir', str(fixture),
               '--assetsDir', str(args.prism / 'assets'), '--assetIndex', '1.12',
               '--uuid', '00000000000040008000000000112200', '--accessToken', '0',
               '--userType', 'legacy', '--versionType', 'release', '--width', '1280', '--height', '800']
    with (fixture / 'launch.log').open('w', encoding='utf-8') as log:
        process = subprocess.Popen(command, cwd=fixture, stdout=log, stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    record = {'pid': process.pid, 'port': args.port, 'fixture': str(fixture), 'profile': profile,
              'java': str(args.java), 'artifacts': {str(a.relative_to(fixture / 'mods')): digest(a)
                                                   for a in (fixture / 'mods').rglob('*') if a.is_file()},
              'jvmArguments': ['-Xms512m', '-Xmx3g', '-Dfile.encoding=UTF-8'] + args.jvm_arg,
              'embeddedArtifacts': embedded,
              'selectiveResourceReload': args.selective_languages,
              'libraries': {str(a): digest(a) for a in classpath}}
    (fixture / 'launch.json').write_text(json.dumps(record, indent=2), encoding='utf-8')
    print(json.dumps({key: value for key, value in record.items() if key != 'libraries'}, indent=2))


if __name__ == '__main__':
    main()
