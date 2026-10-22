from pathlib import Path
import subprocess
import shutil
import sys
import time

root = Path(__file__).resolve().parents[3]
pack, attempt = sys.argv[1:]
assert pack in ('E2E', 'Meatball')
run = root / 'docs/research/legacy112/name-table-reuse-2026-10-14' / ('run-' + pack.lower() + '-' + attempt)
assert not run.exists()
launcher = subprocess.Popen([sys.executable, str(root / 'test-tools/legacy112/name-table-reuse/core-runtime.py'),
    pack, attempt, 'zh_tw', 'pt112-scope-' + pack.lower() + '-' + attempt], cwd=root, creationflags=subprocess.CREATE_NO_WINDOW)
deadline = time.monotonic() + 60
while not (run / 'process.json').is_file():
    if launcher.poll() is not None or time.monotonic() >= deadline:
        raise RuntimeError('Owned fixture launch failed; inspect saved evidence')
    time.sleep(.25)
for filename in ['core-runtime.mjs', 'scoped-runtime.mjs', 'run-scoped.py']:
    shutil.copy2(root / 'test-tools/legacy112/name-table-reuse' / filename, run / ('controller-' + filename))
controller = subprocess.run(['node', str(root / 'test-tools/legacy112/name-table-reuse/core-runtime.mjs'),
    pack, attempt, 'build', 'scoped-validation'], cwd=root, creationflags=subprocess.CREATE_NO_WINDOW)
exit_code = launcher.wait(timeout=600)
sys.exit(0 if not controller.returncode and not exit_code else 1)
