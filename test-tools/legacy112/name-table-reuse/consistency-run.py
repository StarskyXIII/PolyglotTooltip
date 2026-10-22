"""Coverage-gap measurement for the minimal-core design: run probe.consistency on the PT-Matrix test clones.

For one clone (or all five in order): hide the product jar (renamed in place, Forge ignores files that are not .jar/.zip),
install the passive devbridge jar, enable the bridge through polyglot-devbridge.properties in the game directory, start the
clone through the PrismLauncher helper, let consistency-check.mjs measure at the main menu and in a throwaway pt112-* world,
then restore the product jar name, remove the bridge jar and the properties file and delete the world. Nothing else in the
clone is written. Reports go to docs/research/legacy112/consistency-probe-2026-10-22/run-<pack>-<attempt>/.

Usage: consistency-run.py <pack|instance-name> <attempt> [--no-world] [--samples N] [--gap SECONDS]
       consistency-run.py all <attempt> [--no-world] [--samples N] [--gap SECONDS]

Rules kept from matrix-check.py: the target clone must not be running, port 19594 must be free (one bridged launch at a
time), launches are at least --gap seconds apart, and the user's own instances are never touched (only PT-Matrix-* clones
unless an explicit instance name is given).
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
REPORT = ROOT / 'docs/research/legacy112/consistency-probe-2026-10-22'
BRIDGE = ROOT / 'test-tools/legacy112/bridge/build/libs/polyglot-devbridge-1.12.2-1.0.0-passive-production.jar'
CONTROLLER = ROOT / 'test-tools/legacy112/name-table-reuse/consistency-check.mjs'
PORT = 19594
HIDDEN = '.consistency-hidden'
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


def languages_of(game):
    """displayLanguage list from the clone's Forge config; the probe measures exactly the languages the user configured."""
    cfg = game / 'config' / 'polyglottooltip.cfg'
    if not cfg.is_file(): return ['en_us']
    lines = cfg.read_text(encoding='utf-8', errors='replace').splitlines()
    for index, line in enumerate(lines):
        if re.match(r'\s*S:displayLanguage\s*<\s*$', line):
            values = []
            for entry in lines[index + 1:]:
                if entry.strip() == '>': break
                if entry.strip(): values.append(entry.strip())
            return values or ['en_us']
        match = re.match(r'\s*S:displayLanguage=(\S+)', line)
        if match: return [match.group(1)]
    return ['en_us']


def crash_reports(game):
    folder = game / 'crash-reports'
    return set(os.listdir(folder)) if folder.is_dir() else set()


def run_pack(key, instance, attempt, args):
    game = INSTANCES / instance / 'minecraft'
    assert game.is_dir(), 'No such instance: ' + instance
    assert not processes(instance), instance + ' is already running'
    assert port_free(), 'port %d is in use' % PORT
    products = sorted(path for path in (game / 'mods').glob('polyglottooltip-*.jar') if path.suffix == '.jar')
    assert len(products) <= 1, 'more than one product jar in ' + str(game / 'mods')
    assert not list((game / 'mods').glob('polyglot-devbridge-*.jar')), 'a bridge jar is already installed'
    assert not list((game / 'mods').glob('*' + HIDDEN)), 'a hidden product jar is already present; restore it first'
    settings = game / 'polyglot-devbridge.properties'
    assert not settings.exists(), 'polyglot-devbridge.properties already exists'
    assert BRIDGE.is_file(), 'bridge jar missing: ' + str(BRIDGE)
    run = REPORT / ('run-%s-%s' % (key, attempt))
    run.mkdir(parents=True)
    bridge_target = game / 'mods' / BRIDGE.name
    nonce = str(uuid.uuid4())
    world = 'pt112-consistency-' + attempt
    started = time.time()
    crashes_before = crash_reports(game)
    hidden = None
    code = None
    crash = None
    try:
        if products:
            hidden = products[0].with_name(products[0].name + HIDDEN)
            products[0].rename(hidden)
        shutil.copy2(BRIDGE, bridge_target)
        settings.write_text('clientdevbridge.enabled=true\nclientdevbridge.port=%d\nclientdevbridge.timeoutSeconds=1800\n'
                            'clientdevbridge.lifecycleTimeoutSeconds=900\nclientdevbridge.fixtureNonce=%s\n' % (PORT, nonce), encoding='utf-8')
        languages = languages_of(game)
        write(run / 'config.json', dict(pack=key, instance=instance, fixture=str(game), port=PORT, nonce=nonce, worldName=world, attempt=attempt,
                                        languages=languages, samples=args.samples, world=not args.no_world,
                                        purpose='Coverage-gap measurement for the 1.12.2 minimal-core design (§8): vanilla standard naming path versus getDisplayName, product jar hidden',
                                        bridge=sha(BRIDGE), hiddenProduct=(hidden.name if hidden else None), hiddenProductSha=(sha(hidden) if hidden else None)))
        subprocess.Popen([str(PRISM), '--launch', instance], creationflags=subprocess.CREATE_NO_WINDOW, stdin=subprocess.DEVNULL,
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        pids = []
        deadline = time.time() + 120
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
            if not running and (seen or time.time() - launched_at > 90) and not (run / 'before-quit.json').is_file():
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
        restored = None
        if hidden is not None and hidden.is_file():
            original = hidden.with_name(hidden.name[:-len(HIDDEN)])
            if not original.exists(): hidden.rename(original)
            restored = original.is_file() and not hidden.exists()
        if not processes(instance) and (game / 'logs' / 'latest.log').is_file():
            shutil.copy2(game / 'logs' / 'latest.log', run / 'latest.log')
        save = game / 'saves' / world
        removed_world = False
        if save.is_dir() and not processes(instance):
            shutil.rmtree(save, ignore_errors=True)
            removed_world = not save.exists()
        write(run / 'restoration.json', dict(at=now(), settingsRemoved=not settings.exists(), bridgeRemoved=not bridge_target.exists(),
                                             productRestored=restored, worldRemoved=removed_world, worldLeft=save.exists(), controllerExit=code))
    summary = run / 'summary.json'
    result = dict(run=run.name, pack=key, instance=instance, elapsedS=round(time.time() - started), controller=code, crash=crash,
                  summary=json.loads(summary.read_text(encoding='utf-8')) if summary.is_file() else None)
    print(json.dumps(dict(result, summary={k: v for k, v in (result['summary'] or {}).items() if k not in ('topInconsistentClasses', 'topInconsistentMods', 'dictionaries')}),
                     ensure_ascii=False, indent=2))
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('target', help='pack key (%s), an instance name, or all' % ', '.join(PACKS))
    parser.add_argument('attempt')
    parser.add_argument('--no-world', action='store_true', help='measure at the main menu only')
    parser.add_argument('--samples', type=int, default=5)
    parser.add_argument('--gap', type=int, default=20, help='seconds between launches')
    args = parser.parse_args()
    assert re.fullmatch(r'[a-z0-9-]{1,40}', args.attempt), 'attempt must be [a-z0-9-]{1,40}'
    assert PRISM.is_file(), 'Prism helper missing: ' + str(PRISM)
    targets = list(PACKS.items()) if args.target == 'all' else [(args.target, PACKS.get(args.target, args.target))]
    results = []
    for index, (key, instance) in enumerate(targets):
        if index: time.sleep(args.gap)
        key = re.sub(r'[^a-z0-9-]+', '-', key.lower())
        try:
            results.append(run_pack(key, instance, args.attempt, args))
        except Exception as error:  # one failing clone must not stop the others
            results.append(dict(pack=key, instance=instance, error=repr(error)[:400]))
            print(json.dumps(results[-1], ensure_ascii=False))
    write(REPORT / ('matrix-%s.json' % args.attempt), dict(attempt=args.attempt, finished=now(), results=results))


if __name__ == '__main__':
    main()
