"""Diagnose the name-table build on the user's Nova Engineering instance with the research bridge attached.

Installs the candidate jar and the bridge jar, enables the bridge through polyglot-devbridge.properties in the game
directory (PrismLauncher rewrites instance.cfg at launch, so JVM arguments cannot be edited from outside), starts
the instance through PrismLauncher, lets nova-table-check.mjs create a throwaway world and read the table status,
then removes the properties file and the bridge jar and deletes the world.

Usage: nova-table-check.py <jar> <attempt> [controller.mjs] [bridge-classifier]
"""
from pathlib import Path
import datetime
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
import uuid

sys.stdout.reconfigure(encoding='utf-8')
INSTANCE = os.environ.get('POLYGLOT_INSTANCE', 'Nova Engineering - World')  # any PrismLauncher instance name
ROOT = Path(__file__).resolve().parents[3]
INSTANCE_DIR = Path(r'C:/Users/StarskyXIII/AppData/Roaming/PrismLauncher/instances') / INSTANCE
GAME = INSTANCE_DIR / 'minecraft'
PRISM = Path(r'C:/Users/StarskyXIII/AppData/Local/Programs/PrismLauncher/prismlauncher.exe')
REPORT = ROOT / 'docs/research/legacy112/name-table-ic2-injection-2026-10-17'
PORT = 19594
jar = Path(sys.argv[1]).resolve()
attempt = sys.argv[2]
CONTROLLER = sys.argv[3] if len(sys.argv) > 3 else 'nova-table-check.mjs'
BRIDGE = ROOT / ('test-tools/legacy112/bridge/build/libs/polyglot-devbridge-1.12.2-1.0.0-' + (sys.argv[4] if len(sys.argv) > 4 else 'name-audit-production') + '.jar')
assert re.fullmatch(r'[a-z0-9-]{1,40}', attempt)
world = 'pt112-nova-' + attempt

def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for part in iter(lambda: stream.read(1 << 20), b''):
            digest.update(part)
    return digest.hexdigest()

def nova_processes():
    query = ("@(Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javaw.exe'\" | Where-Object { $_.CommandLine -and "
             "$_.CommandLine -like '*" + INSTANCE + "*' } | Select-Object -ExpandProperty ProcessId) | ConvertTo-Json -Compress")
    text = subprocess.check_output(['powershell', '-NoProfile', '-Command', query], text=True).strip()
    if not text: return []
    value = json.loads(text)
    return value if isinstance(value, list) else [value]

def crash_reports():
    folder = GAME / 'crash-reports'
    return set(os.listdir(folder)) if folder.is_dir() else set()

def write(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')

assert PRISM.is_file() and GAME.is_dir() and jar.is_file() and BRIDGE.is_file()
assert not nova_processes(), 'Nova is already running'
target = GAME / 'mods' / 'polyglottooltip-1.12.2-forge-2.0.jar'
assert len(list((GAME / 'mods').glob('polyglottooltip-*.jar'))) == 1 and target.is_file()
assert not list((GAME / 'mods').glob('polyglot-devbridge-*.jar'))
settings = GAME / 'polyglot-devbridge.properties'
assert not settings.exists()
run = REPORT / ('run-nova-' + attempt)
run.mkdir(parents=True)
bridge_target = GAME / 'mods' / BRIDGE.name
nonce = str(uuid.uuid4())
started = time.time()
code = None
crash = None
crashes_before = crash_reports()
try:
    before = sha(target)
    if before != sha(jar):
        shutil.copy2(target, run / ('polyglottooltip-before-' + before[:8] + '.jar'))
        shutil.copy2(jar, target)
    shutil.copy2(BRIDGE, bridge_target)
    settings.write_text('clientdevbridge.enabled=true\nclientdevbridge.nameTablePrototype=true\nclientdevbridge.hudAudit=true\nclientdevbridge.nameAudit=true\nclientdevbridge.port=' + str(PORT)
                        + '\nclientdevbridge.timeoutSeconds=120\nclientdevbridge.lifecycleTimeoutSeconds=900\nclientdevbridge.fixtureNonce=' + nonce + '\n',
                        encoding='utf-8')
    # Extra properties for one run (NOVA_EXTRA_PROPERTIES, ';'-separated), e.g. the product's observeDump diagnostic.
    extra = os.environ.get('NOVA_EXTRA_PROPERTIES', '').strip()
    if extra: settings.write_text(settings.read_text(encoding='utf-8') + extra.replace(';', '\n') + '\n', encoding='utf-8')
    write(run / 'config.json', dict(pack='Nova', fixture=str(GAME), port=PORT, nonce=nonce, worldName=world, attempt=attempt,
                                    purpose='User-requested name-table diagnosis on the Nova Engineering instance', candidate=sha(jar), bridge=sha(BRIDGE)))
    subprocess.Popen([str(PRISM), '--launch', INSTANCE], creationflags=subprocess.CREATE_NO_WINDOW, stdin=subprocess.DEVNULL,
                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    pids = []
    deadline = time.time() + 120
    while not pids and time.time() < deadline:
        time.sleep(2)
        pids = nova_processes()
    assert pids, 'Nova did not start'
    write(run / 'process.json', dict(pid=pids[0], nonce=nonce, gameDirectory=str(GAME)))
    controller = subprocess.Popen(['node', str(ROOT / 'test-tools/legacy112/name-table-reuse' / CONTROLLER), str(run)], cwd=ROOT,
                                  creationflags=subprocess.CREATE_NO_WINDOW)
    # Watch the game while the controller waits on the bridge: a new crash report or an early exit ends the run now.
    seen = False
    launched_at = time.time()
    while controller.poll() is None:
        time.sleep(3)
        fresh = sorted(crash_reports() - crashes_before)
        if fresh:
            report = GAME / 'crash-reports' / fresh[-1]
            crash = dict(report=str(report), head=report.read_text(encoding='utf-8', errors='replace').splitlines()[:40])
            write(run / 'crash.json', crash)
            controller.terminate()
            break
        running = bool(nova_processes())
        seen = seen or running
        # The launcher needs a few seconds to spawn the game; only a game seen once, or absent for 90 s, counts as exited.
        # Once the controller has recorded before-quit.json the game is leaving on request, not crashing.
        if not running and (seen or time.time() - launched_at > 90) and not (run / 'before-quit.json').is_file():
            crash = dict(report=None, head=['Nova exited before the controller finished'])
            write(run / 'crash.json', crash)
            controller.terminate()
            break
    try: code = controller.wait(timeout=60)
    except subprocess.TimeoutExpired: controller.kill(); code = controller.wait()
    for _ in range(60):
        if not nova_processes(): break
        time.sleep(3)
    for pid in nova_processes():
        subprocess.run(['taskkill', '/PID', str(pid)], creationflags=subprocess.CREATE_NO_WINDOW, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for _ in range(40):
        if not nova_processes(): break
        time.sleep(3)
finally:
    if settings.exists(): settings.unlink()
    if bridge_target.is_file(): bridge_target.unlink()
    # The build's rejection and settled-state lines are only in the game log, which the next launch replaces.
    if not nova_processes() and (GAME / 'logs' / 'latest.log').is_file():
        shutil.copy2(GAME / 'logs' / 'latest.log', run / 'latest.log')
    save = GAME / 'saves' / world
    removed_world = False
    if save.is_dir() and not nova_processes():
        shutil.rmtree(save, ignore_errors=True)
        removed_world = not save.exists()
    write(run / 'restoration.json', dict(at=datetime.datetime.now(datetime.timezone.utc).isoformat(), settingsRemoved=not settings.exists(),
                                         bridgeRemoved=not bridge_target.exists(), worldRemoved=removed_world, worldLeft=save.exists(),
                                         candidateLeftInstalled=sha(target) == sha(jar), controllerExit=code))
summary = run / 'summary.json'
print(json.dumps(dict(run=run.name, elapsedS=round(time.time() - started), controller=code, crash=crash,
                      restoration=json.loads((run / 'restoration.json').read_text(encoding='utf-8')),
                      summary=json.loads(summary.read_text(encoding='utf-8')) if summary.is_file() else None), ensure_ascii=False, indent=1))
sys.exit(3 if crash else (code or 0))
