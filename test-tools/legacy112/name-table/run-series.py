"""Run the remaining approved experiments serially; never overlap owned game clients."""
from pathlib import Path
import json
import subprocess
import sys
import time

root = Path(__file__).resolve().parents[3]
report = root / 'docs/research/legacy112/name-table-prototype-2026-10-14'
tools = root / 'test-tools/legacy112/name-table'
deadline = time.monotonic() + 900
while True:
    build = report / 'build3.log'
    text = build.read_text(encoding='utf-8-sig', errors='replace') if build.exists() else ''
    if 'BUILD SUCCESSFUL' in text:
        break
    if 'BUILD FAILED' in text or time.monotonic() > deadline:
        raise RuntimeError('Revised bridge build is not successful; no game was launched')
    time.sleep(5)

plan = [
    ('E2E', 'controls1', 'zh_tw', 'controls', None),
    ('Meatball', 'training2', 'zh_tw', 'train', None),
    ('Meatball', 'holdout1', 'en_us', 'holdout', 'run-meatball-training2'),
]
for pack, attempt, language, mode, training in plan:
    run = report / ('run-' + pack.lower() + '-' + attempt)
    assert not run.exists(), 'Never overwrite an experiment attempt'
    launcher = subprocess.Popen([sys.executable, str(tools / 'launch_fixture.py'), pack, attempt, language], cwd=root,
                                creationflags=subprocess.CREATE_NO_WINDOW)
    deadline = time.monotonic() + 120
    while not (run / 'process.json').exists():
        if launcher.poll() is not None or time.monotonic() > deadline:
            raise RuntimeError('Launcher did not create an owned process record: ' + str(run))
        time.sleep(1)
    args = ['C:/Program Files/nodejs/node.exe', str(tools / 'run.mjs'), str(run), mode]
    if training:
        args.append(str(report / training))
    with (run / 'controller.log').open('xb') as log:
        controller = subprocess.run(args, cwd=root, stdout=log, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW)
    try:
        code = launcher.wait(timeout=240)
    except subprocess.TimeoutExpired:
        raise RuntimeError('Owned client did not exit; remaining experiments were not launched')
    print(json.dumps(dict(pack=pack, attempt=attempt, controllerExit=controller.returncode, clientExit=code)), flush=True)
    if controller.returncode or code:
        raise RuntimeError('Experiment needs review; remaining experiments were not launched')
    if mode == 'controls' and (run / 'supplemental-failure.json').exists():
        raise RuntimeError('Supplemental control acquisition failed; review before continuing')
print('All scheduled prototype experiments finished.', flush=True)
