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
world_b = None
for mode, attempt in [('build', build_attempt), ('reuse', reuse_attempt)]:
    run = report / ('run-' + pack.lower() + '-' + attempt)
    assert not run.exists()
    command = [sys.executable, str(tools / 'core-runtime.py'), pack, attempt]
    if world_b:
        command.extend(['zh_tw', world_b])
    else:
        command.extend(['zh_tw', 'pt112-reuse-' + pack.lower() + '-' + build_attempt])
    launcher = subprocess.Popen(command, cwd=root, creationflags=subprocess.CREATE_NO_WINDOW)
    deadline = time.monotonic() + 60
    while not (run / 'process.json').is_file():
        if launcher.poll() is not None or time.monotonic() >= deadline:
            raise RuntimeError('Owned fixture launch failed; inspect its saved records')
        time.sleep(0.25)
    controller = subprocess.run(['node', str(tools / 'core-runtime.mjs'), pack, attempt, mode, 'shared-scope'],
                                cwd=root, creationflags=subprocess.CREATE_NO_WINDOW)
    exit_code = launcher.wait(timeout=600)
    if controller.returncode or exit_code:
        raise RuntimeError('Shared fixture validation failed: ' + run.name)
    subprocess.run([sys.executable, str(tools / 'persisted-table.py'), run.name], cwd=root, check=True,
                   creationflags=subprocess.CREATE_NO_WINDOW)
    availability = read(run / 'shared-autosave-result.json') if (run / 'shared-autosave-result.json').is_file() else None
    records.append(dict(run=run.name, complete=read(run / 'complete.json'), persisted=read(run / 'persisted-table.json'),
                        launch=read(run / 'launch.json'), config=read(run / 'config.json'), availability=availability))
    if mode == 'build':
        transition = read(run / 'shared-transition.json')
        assert transition['passed'] and transition['newWorld']
        world_b = transition['worldB']
    else:
        assert records[-1]['config']['worldName'] == world_b
for key in ['generation', 'sha256', 'modifiedNs']:
    assert records[0]['persisted'][key] == records[1]['persisted'][key], 'Stored table changed on restart: ' + key
for key in ['productSha256', 'bridgeSha256']:
    assert records[0]['launch'][key] == records[1]['launch'][key], 'Candidate changed between launches'
availability_passed = pack != 'Meatball' or all(value['availability'] and value['availability']['passed'] for value in records)
result = dict(pack=pack, passed=bool(availability_passed), restartPassed=True,
              availability=[value['availability'] for value in records],
              runs=[value['run'] for value in records], worldB=world_b,
              transition=transition, table=records[1]['persisted'])
with (report / ('shared-pair-' + pack.lower() + '-' + build_attempt + '.json')).open('x', encoding='utf-8') as stream:
    json.dump(result, stream, indent=2)
print(json.dumps(result), flush=True)
sys.exit(0 if result['passed'] else 1)
