"""One owned Meatball build launch driven by phase2-runtime.mjs (rejection breakdown and long-frame attribution)."""
from pathlib import Path
import json
import subprocess
import sys
import time

root = Path(__file__).resolve().parents[3]
tools = root / 'test-tools/legacy112/name-table-reuse'
report = root / 'docs/research/legacy112/name-table-phase2-diagnosis-2026-10-16'
attempt, world = sys.argv[1:]
run = report / ('run-meatball-' + attempt)
assert not run.exists()
launcher = subprocess.Popen([sys.executable, str(tools / 'phase2-launch.py'), 'Meatball', attempt, world], cwd=root,
                            creationflags=subprocess.CREATE_NO_WINDOW)
deadline = time.monotonic() + 60
while not (run / 'process.json').is_file():
    if launcher.poll() is not None or time.monotonic() >= deadline:
        raise RuntimeError('Owned fixture launch failed; inspect its saved records')
    time.sleep(0.25)
controller = subprocess.run(['node', str(tools / 'phase2-runtime.mjs'), 'Meatball', attempt], cwd=root,
                            creationflags=subprocess.CREATE_NO_WINDOW)
exit_code = launcher.wait(timeout=1800)
restoration = json.loads((run / 'restoration.json').read_text(encoding='utf-8'))
print(json.dumps(dict(run=run.name, controller=controller.returncode, client=exit_code,
                      restored=all(file['restored'] for file in restoration['files']))), flush=True)
sys.exit(1 if controller.returncode or exit_code else 0)
