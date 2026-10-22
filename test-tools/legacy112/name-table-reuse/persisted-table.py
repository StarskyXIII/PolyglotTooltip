from pathlib import Path
import datetime
import hashlib
import json
import sys

root = Path(__file__).resolve().parents[3]
report = root / 'docs/research/legacy112/name-table-reuse-2026-10-14'
run = report / sys.argv[1]
assert run.parent == report and run.is_dir()
read = lambda file: json.loads(file.read_text(encoding='utf-8-sig'))
config = read(run / 'config.json')
completed = read(run / 'complete.json')
restoration = read(run / 'restoration.json')
assert restoration['clientExitCode'] == 0 and restoration['optionsRestored']
generation = completed['final']['table']['tableGeneration']
folder = Path(config['fixture']) / 'polyglottooltip/name-tables/contexts'
matches = list(folder.glob('*/generation-' + generation + '.pnt'))
assert len(matches) == 1
file = matches[0]
result = dict(at=datetime.datetime.now(datetime.timezone.utc).isoformat(), generation=generation,
              path=str(file), bytes=file.stat().st_size, modifiedNs=file.stat().st_mtime_ns,
              sha256=hashlib.sha256(file.read_bytes()).hexdigest())
with (run / 'persisted-table.json').open('x', encoding='utf-8') as stream:
    json.dump(result, stream, indent=2)
print(json.dumps(result))
