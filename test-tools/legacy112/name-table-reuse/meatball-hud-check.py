"""One owned Meatball launch with The One Probe title capture: the fixture's bridge gets hudAudit through
polyglot-devbridge.properties, nova-hud-check.mjs joins a throwaway world, aims at stone, a furnace and water and
records the probe's drawn rows. Usage: meatball-hud-check.py <attempt-suffix> <world>."""
from pathlib import Path
import json
import subprocess
import sys
import time

sys.stdout.reconfigure(encoding='utf-8')
root = Path(__file__).resolve().parents[3]
tools = root / 'test-tools/legacy112/name-table-reuse'
report = root / 'docs/research/legacy112/name-table-phase2-diagnosis-2026-10-16'
suffix, world = sys.argv[1:]
attempt = 'phase2-' + suffix
read = lambda file: json.loads(file.read_text(encoding='utf-8-sig'))
run = report / ('run-meatball-' + attempt)
assert not run.exists()
game = Path(r'C:/Users/StarskyXIII/AppData/Roaming/PrismLauncher/instances/PT-NamePlan-product-zh-1/minecraft')
settings = game / 'polyglot-devbridge.properties'
assert not settings.exists()
settings.write_text('clientdevbridge.hudAudit=true\n', encoding='utf-8')
try:
    launcher = subprocess.Popen([sys.executable, str(tools / 'phase2-launch.py'), 'Meatball', attempt, world], cwd=root,
                                creationflags=subprocess.CREATE_NO_WINDOW)
    deadline = time.monotonic() + 60
    while not (run / 'process.json').is_file():
        if launcher.poll() is not None or time.monotonic() >= deadline:
            raise RuntimeError('Owned fixture launch failed; inspect its saved records')
        time.sleep(0.25)
    controller = subprocess.run(['node', str(tools / 'nova-hud-check.mjs'), str(run)], cwd=root, creationflags=subprocess.CREATE_NO_WINDOW)
    exit_code = launcher.wait(timeout=1500)
finally:
    if settings.exists(): settings.unlink()
restoration = read(run / 'restoration.json')
assert all(file['restored'] for file in restoration['files']), 'Fixture settings not restored'
summary = read(run / 'summary.json') if (run / 'summary.json').is_file() else None
print(json.dumps(dict(run=run.name, controller=controller.returncode, client=exit_code, summary=summary), ensure_ascii=False, indent=1))
sys.exit(controller.returncode or exit_code or 0)
