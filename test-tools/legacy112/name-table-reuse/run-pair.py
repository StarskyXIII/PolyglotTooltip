from pathlib import Path
import json
import subprocess
import sys
import time

root = Path(__file__).resolve().parents[3]
tools = root / 'test-tools/legacy112/name-table-reuse'
report = root / 'docs/research/legacy112/name-table-reuse-2026-10-14'
pack, build_attempt, reuse_attempt = sys.argv[1:]
assert pack in ('E2E', 'Meatball')
read = lambda file: json.loads(file.read_text(encoding='utf-8-sig'))
records = []
for mode, attempt in [('build', build_attempt), ('reuse', reuse_attempt)]:
    run = report / ('run-' + pack.lower() + '-' + attempt)
    assert not run.exists()
    launcher = subprocess.Popen([sys.executable, str(tools / 'core-runtime.py'), pack, attempt], cwd=root,
                                creationflags=subprocess.CREATE_NO_WINDOW)
    deadline = time.monotonic() + 60
    while not (run / 'process.json').is_file():
        if launcher.poll() is not None or time.monotonic() >= deadline:
            raise RuntimeError('Owned fixture launch failed; inspect its saved records')
        time.sleep(0.25)
    controller = subprocess.run(['node', str(tools / 'core-runtime.mjs'), pack, attempt, mode], cwd=root,
                                creationflags=subprocess.CREATE_NO_WINDOW)
    exit_code = launcher.wait(timeout=600)
    if controller.returncode or exit_code:
        raise RuntimeError('Fixture validation failed: ' + run.name)
    subprocess.run([sys.executable, str(tools / 'persisted-table.py'), run.name], cwd=root, check=True,
                   creationflags=subprocess.CREATE_NO_WINDOW)
    records.append(dict(run=run.name, complete=read(run / 'complete.json'), persisted=read(run / 'persisted-table.json'),
                        launch=read(run / 'launch.json')))
for key in ['generation', 'sha256', 'modifiedNs']:
    assert records[0]['persisted'][key] == records[1]['persisted'][key], 'Stored table changed on reuse: ' + key
for key in ['productSha256', 'bridgeSha256']:
    assert records[0]['launch'][key] == records[1]['launch'][key], 'Candidate changed between launches'
result = dict(pack=pack, passed=True, runs=[value['run'] for value in records], table=records[1]['persisted'])
with (report / ('pair-' + pack.lower() + '-' + build_attempt + '.json')).open('x', encoding='utf-8') as stream:
    json.dump(result, stream, indent=2)
print(json.dumps(result), flush=True)
