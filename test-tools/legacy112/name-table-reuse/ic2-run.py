"""One owned E2E launch with the IC2 language-injection check, then the language lines of the fixture log."""
from pathlib import Path
import json
import subprocess
import sys
import time

sys.stdout.reconfigure(encoding='utf-8')
root = Path(__file__).resolve().parents[3]
tools = root / 'test-tools/legacy112/name-table-reuse'
report = root / 'docs/research/legacy112/name-table-ic2-injection-2026-10-17'
report.mkdir(exist_ok=True)
attempt, world = sys.argv[1:]
read = lambda file: json.loads(file.read_text(encoding='utf-8-sig'))
run = report / ('run-e2e-' + attempt)
assert not run.exists()
launcher = subprocess.Popen([sys.executable, str(tools / 'ic2-launch.py'), 'E2E', attempt, world], cwd=root,
                            creationflags=subprocess.CREATE_NO_WINDOW)
deadline = time.monotonic() + 60
while not (run / 'process.json').is_file():
    if launcher.poll() is not None or time.monotonic() >= deadline:
        raise RuntimeError('Owned fixture launch failed; inspect its saved records')
    time.sleep(0.25)
controller = subprocess.run(['node', str(tools / 'ic2-check.mjs'), 'E2E', attempt], cwd=root, creationflags=subprocess.CREATE_NO_WINDOW)
exit_code = launcher.wait(timeout=1800)
restoration = read(run / 'restoration.json')
assert all(file['restored'] for file in restoration['files']), 'Fixture settings not restored'
game = Path(read(run / 'config.json')['fixture'])
lines = [line for line in (game / 'logs/latest.log').read_text(encoding='utf-8', errors='replace').splitlines()
         if '[polyglottooltip]' in line and ('language' in line.lower() or 'Secondary' in line or 'deferred' in line)]
(run / 'language-log.json').write_text(json.dumps(lines, ensure_ascii=False, indent=2), encoding='utf-8')
summary = read(run / 'summary.json') if (run / 'summary.json').is_file() else None
print(json.dumps(dict(run=run.name, controller=controller.returncode, client=exit_code,
                      summary=None if summary is None else dict(deferred=summary['deferred'], tableReady=summary['tableReady'],
                                                                 aliasReady=summary['aliasReady'], searches=summary['searches']),
                      languageLines=[line[:400] for line in lines]), ensure_ascii=False, indent=1), flush=True)
sys.exit(controller.returncode or exit_code or 0)
