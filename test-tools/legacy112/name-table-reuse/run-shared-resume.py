"""Verify restart independently after a recorded post-transition failure."""
from pathlib import Path
import hashlib
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
build = report / ('run-' + pack.lower() + '-' + build_attempt)
run = report / ('run-' + pack.lower() + '-' + reuse_attempt)
assert not run.exists()
transition = read(build / 'shared-transition.json')
failure = read(build / 'failure.json')
restored = read(build / 'restoration.json')
assert transition['passed'] and transition['newWorld']
assert failure['error'] == 'AssertionError [ERR_ASSERTION]: Ready search survives a world-save interval'
assert restored['optionsRestored'] and restored['clientExitCode'] == 0
before = transition['tableAfter']
table = Path(before['path'])
assert hashlib.sha256(table.read_bytes()).hexdigest() == before['sha256']
assert str(table.stat().st_mtime_ns) == before['modifiedNs']
command = [sys.executable, str(tools / 'core-runtime.py'), pack, reuse_attempt,
           'zh_tw', transition['worldB']]
launcher = subprocess.Popen(command, cwd=root, creationflags=subprocess.CREATE_NO_WINDOW)
deadline = time.monotonic() + 60
while not (run / 'process.json').is_file():
    if launcher.poll() is not None or time.monotonic() >= deadline:
        raise RuntimeError('Owned restart launch failed; inspect saved records')
    time.sleep(0.25)
controller = subprocess.run(['node', str(tools / 'core-runtime.mjs'), pack, reuse_attempt,
                             'reuse', 'shared-scope'], cwd=root, creationflags=subprocess.CREATE_NO_WINDOW)
exit_code = launcher.wait(timeout=600)
if controller.returncode or exit_code:
    raise RuntimeError('Independent restart validation failed: ' + run.name)
subprocess.run([sys.executable, str(tools / 'persisted-table.py'), run.name], cwd=root,
               check=True, creationflags=subprocess.CREATE_NO_WINDOW)
after = read(run / 'persisted-table.json')
for key in ['generation', 'sha256', 'modifiedNs']:
    assert str(before[key]) == str(after[key]), 'Stored table changed: ' + key
for key in ['productSha256', 'bridgeSha256']:
    assert read(build / 'launch.json')[key] == read(run / 'launch.json')[key]
complete = read(run / 'complete.json')
assert complete['final']['scope'] == transition['after']['scope']
assert complete['final']['identity'] == transition['after']['identity']
result = dict(pack=pack, restartPassed=True, overallAcceptancePassed=False,
              priorFailure=build.name + '/failure.json', runs=[build.name, run.name],
              worldB=transition['worldB'], table=after,
              availability=read(run / 'shared-autosave-result.json'))
destination = report / ('shared-restart-' + pack.lower() + '-' + build_attempt + '.json')
with destination.open('x', encoding='utf-8') as stream:
    json.dump(result, stream, indent=2)
print(json.dumps(result), flush=True)
