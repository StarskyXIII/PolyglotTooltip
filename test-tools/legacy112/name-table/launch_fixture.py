"""Launch exactly one stopped, previously owned fixture with the isolated research bridge."""
from pathlib import Path
import datetime
import hashlib
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import uuid

REPO = Path(__file__).resolve().parents[3]
REPORT = REPO / 'docs/research/legacy112/name-table-prototype-2026-10-14'
FIXTURES = {
    'E2E': dict(game='D:/Coding/pt-tmp/polyglot-e2e-compatibility-20261012/minecraft', port=19591,
                reference='docs/research/legacy112/name-correctness-2026-10-09/e2e-name-inventory1'),
    'Meatball': dict(game='C:/Users/StarskyXIII/AppData/Roaming/PrismLauncher/instances/PT-NamePlan-product-zh-1/minecraft', port=19592,
                    reference='docs/research/legacy112/feedback-2026-10-13/meatball-name-inventory2'),
}

def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))

def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for part in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(part)
    return digest.hexdigest()

def write(path, value):
    with path.open('x', encoding='utf-8') as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)

def main(pack, attempt, language, world_name=None):
    assert pack in FIXTURES and language in ('zh_tw', 'en_us', 'zh_cn')
    assert re.fullmatch(r'[a-z0-9-]{1,40}', attempt)
    assert world_name is None or re.fullmatch(r'pt112-[a-zA-Z0-9-]{1,64}', world_name)
    info = FIXTURES[pack]
    game = Path(info['game']).resolve()
    reference = REPO / info['reference']
    assert game.is_dir() and Path(read(reference / 'config.json')['fixture']).resolve() == game
    if pack == 'Meatball':
        marker = read(game.parent / '.polyglot-nameplan.json')
        assert Path(marker['fixture']).resolve() == game and marker['instance'] == 'PT-NamePlan-product-zh-1'
    run = (REPORT / ('run-' + pack.lower() + '-' + attempt)).resolve()
    assert run.parent == REPORT.resolve() and not run.exists()
    # Read process ownership without printing command lines or touching any other game.
    escaped = str(game).replace("'", "''")
    query = "@(Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javaw.exe'\" | Where-Object { $_.CommandLine -and $_.CommandLine.Replace('/', '\\').Contains('" + escaped.replace('/', '\\') + "') } | Select-Object -ExpandProperty ProcessId) | ConvertTo-Json -Compress"
    live = subprocess.check_output(['powershell', '-NoProfile', '-Command', query], text=True)
    assert not live.strip(), 'The exact owned fixture is already running'
    with socket.socket() as connection:
        assert connection.connect_ex(('127.0.0.1', info['port'])) != 0, 'Prototype port is already in use'
    run.mkdir()
    (run / 'before').mkdir()
    for relative in ['options.txt', 'config/polyglottooltip.cfg']:
        file = game / relative
        if file.is_file():
            shutil.copy2(file, run / 'before' / file.name)
    product = REPO / 'legacy/1.12.2/build/libs/polyglottooltip-1.12.2-forge-2.0.jar'
    bridge = REPO / 'test-tools/legacy112/bridge/build/libs/polyglot-devbridge-1.12.2-1.0.0-name-audit-production.jar'
    # Sessions sharing this checkout rebuild build/libs; a private copy keeps the candidate fixed. Same file names.
    product = Path(os.environ.get('POLYGLOT_PRODUCT_JAR', product))
    bridge = Path(os.environ.get('POLYGLOT_BRIDGE_JAR', bridge))
    assert product.name == 'polyglottooltip-1.12.2-forge-2.0.jar' and bridge.name.startswith('polyglot-devbridge-1.12.2-')
    assert len(list((game / 'mods').glob('polyglottooltip-*.jar'))) == 1
    assert len(list((game / 'mods').glob('polyglot-devbridge-*.jar'))) == 1
    for artifact in [product, bridge]:
        target = game / 'mods' / artifact.name
        assert target.is_file()
        shutil.copy2(target, run / 'before' / target.name)
        shutil.copy2(artifact, target)
        shutil.copy2(artifact, run / artifact.name)
        assert sha(target) == sha(artifact)
    options = game / 'options.txt'
    text = options.read_text(encoding='utf-8')
    assert re.search(r'(?m)^lang:.*$', text)
    options.write_text(re.sub(r'(?m)^lang:.*$', 'lang:' + language, text), encoding='utf-8')
    nonce = str(uuid.uuid4())
    config = dict(pack=pack, fixture=str(game), port=info['port'], nonce=nonce, initialLanguage=language,
                  worldName=world_name or 'pt112-name-table-20261014-' + pack.lower(), attempt=attempt,
                  purpose='User-authorized complete-name table prototype; finite stratified acquisition, held-out restart observations and shadow compatibility',
                  head=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=REPO, text=True).strip())
    write(run / 'config.json', config)
    command = read(reference / 'launch.json')['command']
    assert Path(command[0]) == Path('C:/Program Files/Zulu/zulu-25/bin/java.exe')
    command = [arg for arg in command if not arg.startswith('-Dclientdevbridge.nameTablePrototype=')]
    for index, arg in enumerate(command):
        if arg.startswith('-Dclientdevbridge.port='):
            command[index] = '-Dclientdevbridge.port=' + str(info['port'])
        elif arg.startswith('-Dclientdevbridge.fixtureNonce='):
            command[index] = '-Dclientdevbridge.fixtureNonce=' + nonce
        elif arg.startswith('-Dcleanroom.relauncher.parent='):
            command[index] = '-Dcleanroom.relauncher.parent=' + str(os.getpid())
    command.insert(1, '-Dclientdevbridge.nameTablePrototype=true')
    # Diagnostic JVM properties for one launch (';'-separated -D arguments), e.g. -Dpolyglottooltip.observeDump=<classes>.
    for extra in reversed([value for value in os.environ.get('POLYGLOT_EXTRA_JVM_ARGS', '').split(';') if value.strip()]):
        assert extra.startswith('-D'), extra
        command.insert(1, extra.strip())
    command[command.index('--gameDir') + 1] = str(game)
    assert command[command.index('--accessToken') + 1] == '0'
    classpath = command[command.index('-cp') + 1].split(';')
    assert all(Path(value).exists() for value in classpath)
    artifact_inputs = []
    for path in sorted((game / 'mods').rglob('*')):
        if path.is_file():
            artifact_inputs.append(dict(path=str(path.resolve()), sha256=sha(path), bytes=path.stat().st_size))
    write(run / 'launch.json', dict(command=command, productSha256=sha(product), bridgeSha256=sha(bridge),
          prelaunchModFiles=artifact_inputs, offlineSession=True, launchedAt=datetime.datetime.now(datetime.timezone.utc).isoformat()))
    with (run / 'console.log').open('xb') as output:
        child = subprocess.Popen(command, cwd=game, stdout=output, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW)
        write(run / 'process.json', dict(pid=child.pid, nonce=nonce, gameDirectory=str(game)))
        print(json.dumps(dict(pack=pack, run=str(run), pid=child.pid, initialLanguage=language)), flush=True)
        code = child.wait()
    write(run / 'exit.json', dict(pid=child.pid, exitCode=code, exitedAt=datetime.datetime.now(datetime.timezone.utc).isoformat()))
    return code

if __name__ == '__main__':
    sys.exit(main(*sys.argv[1:]))
