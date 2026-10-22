"""Build launch then reuse launch of the owned Meatball fixture with one candidate."""
from pathlib import Path
import json
import subprocess
import sys
import time

root = Path(__file__).resolve().parents[3]
tools = root / 'test-tools/legacy112/name-table-reuse'
report = root / 'docs/research/legacy112/name-table-phase2-diagnosis-2026-10-16'
build_attempt, reuse_attempt, world, *options = sys.argv[1:]
read = lambda file: json.loads(file.read_text(encoding='utf-8-sig'))
records = []
for mode, attempt in [('build', build_attempt), ('reuse', reuse_attempt)]:
    run = report / ('run-meatball-' + attempt)
    assert not run.exists()
    launcher = subprocess.Popen([sys.executable, str(tools / 'phase2-launch.py'), 'Meatball', attempt, world], cwd=root,
                                creationflags=subprocess.CREATE_NO_WINDOW)
    deadline = time.monotonic() + 60
    while not (run / 'process.json').is_file():
        if launcher.poll() is not None or time.monotonic() >= deadline:
            raise RuntimeError('Owned fixture launch failed; inspect its saved records')
        time.sleep(0.25)
    controller = subprocess.run(['node', str(tools / 'phase2-measure.mjs'), 'Meatball', attempt, mode, *options], cwd=root,
                                creationflags=subprocess.CREATE_NO_WINDOW)
    exit_code = launcher.wait(timeout=900)
    restoration = read(run / 'restoration.json')
    assert all(file['restored'] for file in restoration['files']), 'Fixture settings not restored'
    if controller.returncode or exit_code:
        raise RuntimeError('Fixture measurement failed: ' + run.name)
    records.append(dict(run=run.name, summary=read(run / 'summary.json'), launch=read(run / 'launch.json')))
same = {key: records[0]['summary']['persisted'][key] == records[1]['summary']['persisted'][key] for key in ['generation', 'sha256', 'modifiedNs']}
for key in ['productSha256', 'bridgeSha256']:
    assert records[0]['launch'][key] == records[1]['launch'][key], 'Candidate changed between launches'
result = dict(runs=[value['run'] for value in records], sameStoredTable=same, table=records[1]['summary']['persisted'])
(report / ('pair-' + build_attempt + '.json')).write_text(json.dumps(result, indent=2), encoding='utf-8')
print(json.dumps(result), flush=True)
