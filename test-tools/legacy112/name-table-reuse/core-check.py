"""Game check of a minimal-core candidate jar on one PT-Matrix clone.

Install the candidate jar in the clone's mods (the clone's own polyglottooltip-*.jar is renamed to *.core-hidden first),
install the passive devbridge, enable it through polyglot-devbridge.properties, optionally apply config overrides, start
the clone through the PrismLauncher helper and let core-check.mjs run one scenario in a throwaway pt112-core-* world.
Afterwards the candidate and the bridge are removed, the original jar name is restored, the properties file is deleted,
every config file this run touched is restored byte for byte and the world is deleted. Reports go to
docs/research/legacy112/<report>/run-<attempt>/.

Usage: core-check.py <pack|instance-name> <attempt> [--scenario ae2] [--jar PATH] [--report DIR]
                     [--set integrations.ae2=false] [--ae-search-tooltips NO]

Rules kept from consistency-run.py: the clone must not be running, port 19594 must be free (one bridged launch at a
time), and only PT-Matrix-* clones are touched unless an explicit instance name is given.
"""
from pathlib import Path
import argparse
import datetime
import hashlib
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import time
import uuid

sys.stdout.reconfigure(encoding='utf-8')
ROOT = Path(__file__).resolve().parents[3]
INSTANCES = Path(r'C:/Users/StarskyXIII/AppData/Roaming/PrismLauncher/instances')
PRISM = Path.home() / '.local' / 'bin' / 'prism-agent-launch.exe'
JAR = ROOT / 'legacy/1.12.2/build/libs/polyglottooltip-1.12.2-forge-2.0.jar'
BRIDGE = ROOT / 'test-tools/legacy112/bridge/build/libs/polyglot-devbridge-1.12.2-1.0.0-passive-production.jar'
CONTROLLER = ROOT / 'test-tools/legacy112/name-table-reuse/core-check.mjs'
PORT = 19594
HIDDEN = '.core-hidden'
CANDIDATE = 'polyglottooltip-core-check.jar'
AE_CONFIG = Path('config/AppliedEnergistics2/AppliedEnergistics2.cfg')
PRODUCT_CONFIG = Path('config/polyglottooltip.cfg')
PACKS = {
    'nova': 'PT-Matrix-Nova',
    'extended': 'PT-Matrix-Extended',
    'dj2': 'PT-Matrix-DJ2',
    'expert': 'PT-Matrix-Expert',
    'meatball': 'PT-Matrix-Meatball',
}


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for part in iter(lambda: stream.read(1 << 20), b''):
            digest.update(part)
    return digest.hexdigest()


def write(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')


def processes(instance):
    query = ("@(Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javaw.exe'\" | Where-Object { $_.CommandLine -and "
             "$_.CommandLine -like '*" + instance + "*' } | Select-Object -ExpandProperty ProcessId) | ConvertTo-Json -Compress")
    text = subprocess.check_output(['powershell', '-NoProfile', '-Command', query], text=True).strip()
    if not text: return []
    value = json.loads(text)
    return value if isinstance(value, list) else [value]


def port_free():
    with socket.socket() as probe:
        try:
            probe.bind(('127.0.0.1', PORT))
            return True
        except OSError:
            return False


def crash_reports(game):
    folder = game / 'crash-reports'
    return set(os.listdir(folder)) if folder.is_dir() else set()


def set_forge_option(path, category, key, kind, value):
    """Set one option of a Forge Configuration file, adding the category or the line when missing."""
    lines = path.read_text(encoding='utf-8').splitlines() if path.is_file() else ['# Configuration file', '']
    start = next((i for i, line in enumerate(lines) if re.match(r'\s*' + re.escape(category) + r'\s*\{\s*$', line)), None)
    entry = '    %s:%s=%s' % (kind, key, value)
    if start is None:
        lines += ['', category + ' {', entry, '}', '']
    else:
        end = next(i for i in range(start + 1, len(lines)) if lines[i].strip() == '}')
        found = next((i for i in range(start + 1, end) if re.match(r'\s*%s:%s=' % (kind, re.escape(key)), lines[i])), None)
        if found is None: lines.insert(end, entry)
        else: lines[found] = entry
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('\n'.join(lines) + '\n', encoding='utf-8')


def run_pack(key, instance, args):
    game = INSTANCES / instance / 'minecraft'
    assert game.is_dir(), 'No such instance: ' + instance
    assert not processes(instance), instance + ' is already running'
    assert port_free(), 'port %d is in use' % PORT
    mods = game / 'mods'
    products = sorted(path for path in mods.glob('polyglottooltip-*.jar') if path.suffix == '.jar')
    assert len(products) <= 1, 'more than one product jar in ' + str(mods)
    assert not (mods / CANDIDATE).exists(), 'a candidate jar is already installed'
    assert not list(mods.glob('polyglot-devbridge-*.jar')), 'a bridge jar is already installed'
    assert not list(mods.glob('*' + HIDDEN)), 'a hidden product jar is already present; restore it first'
    settings = game / 'polyglot-devbridge.properties'
    assert not settings.exists(), 'polyglot-devbridge.properties already exists'
    jar = Path(args.jar)
    assert jar.is_file(), 'candidate jar missing: ' + str(jar)
    assert BRIDGE.is_file(), 'bridge jar missing: ' + str(BRIDGE)
    touched = [PRODUCT_CONFIG] + ([AE_CONFIG] if args.ae_search_tooltips or args.scenario == 'ae2' else [])
    for relative in touched:
        assert not (game / (str(relative) + '.core-backup')).exists(), 'a config backup is already present: ' + str(relative)
    run = ROOT / 'docs/research/legacy112' / args.report / ('run-' + args.attempt)
    run.mkdir(parents=True)
    candidate = mods / CANDIDATE
    bridge_target = mods / BRIDGE.name
    nonce = str(uuid.uuid4())
    world = 'pt112-core-' + args.attempt
    started = time.time()
    crashes_before = crash_reports(game)
    hidden = None
    code = None
    crash = None
    backups = {}
    try:
        for relative in touched:
            original = game / relative
            if original.is_file():
                backup = game / (str(relative) + '.core-backup')
                shutil.copy2(original, backup)
                shutil.copy2(original, run / ('before-' + relative.name))
                backups[str(relative)] = dict(backup=str(backup), sha=sha(original))
            else:
                backups[str(relative)] = dict(backup=None, sha=None)
        if products:
            hidden = products[0].with_name(products[0].name + HIDDEN)
            products[0].rename(hidden)
        shutil.copy2(jar, candidate)
        shutil.copy2(BRIDGE, bridge_target)
        for override in args.set:
            name, value = override.split('=', 1)
            category, option = name.split('.', 1)
            set_forge_option(game / PRODUCT_CONFIG, category, option, 'B' if value in ('true', 'false') else 'S', value)
        tooltips = args.ae_search_tooltips or ('NO' if args.scenario == 'ae2' else None)
        if tooltips:
            set_forge_option(game / AE_CONFIG, 'client', 'SEARCH_TOOLTIPS', 'S', tooltips)
        settings.write_text('clientdevbridge.enabled=true\nclientdevbridge.port=%d\nclientdevbridge.timeoutSeconds=2400\n'
                            'clientdevbridge.lifecycleTimeoutSeconds=1200\nclientdevbridge.fixtureNonce=%s\n' % (PORT, nonce), encoding='utf-8')
        write(run / 'config.json', dict(pack=key, instance=instance, fixture=str(game), port=PORT, nonce=nonce, worldName=world,
                                        attempt=args.attempt, scenario=args.scenario, overrides=args.set, aeSearchTooltips=tooltips,
                                        candidate=str(jar), candidateSha=sha(jar), bridge=sha(BRIDGE), backups=backups,
                                        hiddenProduct=(hidden.name if hidden else None), hiddenProductSha=(sha(hidden) if hidden else None)))
        subprocess.Popen([str(PRISM), '--launch', instance], creationflags=subprocess.CREATE_NO_WINDOW, stdin=subprocess.DEVNULL,
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        pids = []
        deadline = time.time() + 180
        while not pids and time.time() < deadline:
            time.sleep(2)
            pids = processes(instance)
        assert pids, instance + ' did not start'
        write(run / 'process.json', dict(pid=pids[0], nonce=nonce, gameDirectory=str(game)))
        controller = subprocess.Popen(['node', str(CONTROLLER), str(run)], cwd=ROOT, creationflags=subprocess.CREATE_NO_WINDOW,
                                      stdout=(run / 'controller.log').open('w', encoding='utf-8'), stderr=subprocess.STDOUT)
        seen = False
        launched_at = time.time()
        while controller.poll() is None:
            time.sleep(3)
            fresh = sorted(crash_reports(game) - crashes_before)
            if fresh:
                report = game / 'crash-reports' / fresh[-1]
                crash = dict(report=str(report), head=report.read_text(encoding='utf-8', errors='replace').splitlines()[:40])
                write(run / 'crash.json', crash)
                controller.terminate()
                break
            running = bool(processes(instance))
            seen = seen or running
            if not running and (seen or time.time() - launched_at > 120) and not (run / 'before-quit.json').is_file():
                crash = dict(report=None, head=[instance + ' exited before the controller finished'])
                write(run / 'crash.json', crash)
                controller.terminate()
                break
        try: code = controller.wait(timeout=60)
        except subprocess.TimeoutExpired: controller.kill(); code = controller.wait()
        for _ in range(60):
            if not processes(instance): break
            time.sleep(3)
        for pid in processes(instance):
            subprocess.run(['taskkill', '/PID', str(pid)], creationflags=subprocess.CREATE_NO_WINDOW, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        for _ in range(40):
            if not processes(instance): break
            time.sleep(3)
    finally:
        if settings.exists(): settings.unlink()
        if bridge_target.is_file(): bridge_target.unlink()
        if candidate.is_file() and not processes(instance): candidate.unlink()
        restored = None
        if hidden is not None and hidden.is_file():
            original = hidden.with_name(hidden.name[:-len(HIDDEN)])
            if not original.exists(): hidden.rename(original)
            restored = original.is_file() and not hidden.exists()
        configs = {}
        for relative, entry in backups.items():
            target = game / relative
            if entry['backup']:
                shutil.copy2(entry['backup'], target)
                if sha(target) == entry['sha']: os.remove(entry['backup'])
                configs[relative] = sha(target) == entry['sha'] and not Path(entry['backup']).exists()
            else:
                if target.is_file(): target.unlink()
                configs[relative] = not target.exists()
        if not processes(instance) and (game / 'logs' / 'latest.log').is_file():
            shutil.copy2(game / 'logs' / 'latest.log', run / 'latest.log')
        save = game / 'saves' / world
        removed_world = False
        if save.is_dir() and not processes(instance):
            shutil.rmtree(save, ignore_errors=True)
            removed_world = not save.exists()
        write(run / 'restoration.json', dict(at=now(), settingsRemoved=not settings.exists(), bridgeRemoved=not bridge_target.exists(),
                                             candidateRemoved=not candidate.exists(), productRestored=restored, configsRestored=configs,
                                             worldRemoved=removed_world, worldLeft=save.exists(), javaLeft=processes(instance),
                                             controllerExit=code))
    log = run / 'latest.log'
    warnings = []
    if log.is_file():
        for line in log.read_text(encoding='utf-8', errors='replace').splitlines():
            if re.search(r'/(WARN|ERROR)\]', line) and re.search(r'polyglottooltip|PolyglotTooltip', line):
                warnings.append(line[:400])
    write(run / 'log-check.json', dict(productWarnings=warnings))
    summary = run / 'summary.json'
    result = dict(run=run.name, pack=key, instance=instance, elapsedS=round(time.time() - started), controller=code, crash=crash,
                  productWarnings=len(warnings), summary=json.loads(summary.read_text(encoding='utf-8')) if summary.is_file() else None)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('target', help='pack key (%s) or an instance name' % ', '.join(PACKS))
    parser.add_argument('attempt')
    parser.add_argument('--scenario', default='ae2', choices=['ae2'])
    parser.add_argument('--jar', default=str(JAR))
    parser.add_argument('--report', default='core-stage3-ae2-2026-10-23')
    parser.add_argument('--set', action='append', default=[], help='polyglottooltip.cfg override, category.option=value')
    parser.add_argument('--ae-search-tooltips', choices=['YES', 'NO'])
    args = parser.parse_args()
    assert re.fullmatch(r'[a-z0-9-]{1,40}', args.attempt), 'attempt must be [a-z0-9-]{1,40}'
    assert re.fullmatch(r'[A-Za-z0-9._-]{1,80}', args.report), 'report must be a plain directory name'
    for override in args.set:
        assert re.fullmatch(r'[a-z]+\.[a-zA-Z0-9_-]+=[^\s=]+', override), 'invalid override ' + override
    assert PRISM.is_file(), 'Prism helper missing: ' + str(PRISM)
    run_pack(re.sub(r'[^a-z0-9-]+', '-', args.target.lower()), PACKS.get(args.target, args.target), args)


if __name__ == '__main__':
    main()
