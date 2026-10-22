"""Sidecar phase snapshots for the already-running first measurement controller."""
from pathlib import Path
import hashlib
import importlib.util
import json
import shutil
import sys
import time

ROOT = Path(__file__).resolve().parents[3]
REPORT = ROOT / 'docs/research/legacy112/name-table-reuse-2026-10-14'
pack = sys.argv[1]
assert pack == 'E2E'
run = REPORT / 'run-e2e-selective-measure1'
evidence = REPORT / 'measurement-e2e'
config = json.loads((run / 'config.json').read_text(encoding='utf-8'))
game = Path(config['fixture'])

def sha(file):
    return hashlib.sha256(file.read_bytes()).hexdigest()

pending = {'en-us': 'switch-en-us', 'zh-cn': 'switch-zh-cn'}
deadline = time.monotonic() + 1800
while pending and time.monotonic() < deadline:
    if (run / 'failure.json').exists() or (run / 'exit.json').exists():
        break
    for phase, name in list(pending.items()):
        marker = run / (name + '.json')
        if not marker.exists():
            continue
        value = json.loads(marker.read_text(encoding='utf-8'))
        if not value.get('complete'):
            raise RuntimeError('Language phase did not become ready')
        target = evidence / ('config-during-' + phase)
        target.mkdir()
        rows = []
        started = time.time()
        for source in sorted((game / 'config').rglob('*')):
            assert not source.is_symlink()
            if not source.is_file():
                continue
            relative = source.relative_to(game / 'config')
            destination = target / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            before = sha(source)
            shutil.copy2(source, destination)
            copied, after = sha(destination), sha(source)
            rows.append(dict(path=relative.as_posix(), sha256=copied, bytes=destination.stat().st_size,
                             stableDuringCopy=before == copied == after, inputBefore=before, inputAfter=after))
        (evidence / (target.name + '-manifest.json')).write_text(json.dumps(rows, indent=2), encoding='utf-8')
        (evidence / (target.name + '-timing.json')).write_text(json.dumps(dict(started=started, finished=time.time(),
                    method='sidecar after language readiness; concurrent with name-only capture', phase=phase), indent=2), encoding='utf-8')
        print(json.dumps(dict(phase=phase, files=len(rows), unstable=sum(not r['stableDuringCopy'] for r in rows))), flush=True)
        del pending[phase]
    time.sleep(0.5)
if pending:
    raise RuntimeError('Phases were not reached: ' + str(pending))
