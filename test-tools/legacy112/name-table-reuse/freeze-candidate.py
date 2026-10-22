from pathlib import Path
import datetime
import hashlib
import json
import re
import shutil
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[3]
name = sys.argv[1]
assert re.fullmatch(r'candidate-(core|shared)-[0-9]+', name)
destination = root / 'docs/research/legacy112/name-table-reuse-2026-10-14' / name
destination.mkdir()
digest = lambda file: hashlib.sha256(file.read_bytes()).hexdigest()
write = lambda name, value: (destination / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
files = []
for source in ['legacy/1.12.2/build/libs/polyglottooltip-1.12.2-forge-2.0.jar',
               'test-tools/legacy112/bridge/build/libs/polyglot-devbridge-1.12.2-1.0.0-name-audit-production.jar']:
    file = root / source
    shutil.copy2(file, destination / file.name)
    files.append(dict(source=source, file=file.name, bytes=file.stat().st_size, sha256=digest(file)))
tests = {}
for role, folder in [('product', 'legacy/1.12.2/build/test-results/test'), ('bridge', 'test-tools/legacy112/bridge/build/test-results/test')]:
    output = destination / 'test-results' / role
    output.mkdir(parents=True)
    totals = dict.fromkeys(['tests', 'failures', 'errors', 'skipped'], 0)
    suites = []
    for file in sorted((root / folder).glob('TEST-*.xml')):
        suite = ET.parse(file).getroot()
        for key in totals:
            totals[key] += int(suite.get(key, '0'))
        shutil.copy2(file, output / file.name)
        suites.append(dict(suite.attrib, file=(output / file.name).relative_to(destination).as_posix(), sha256=digest(file)))
    assert suites and not totals['failures'] and not totals['errors']
    tests[role] = dict(totals=totals, suites=suites)
write('artifacts.json', dict(createdAt=datetime.datetime.now(datetime.timezone.utc).isoformat(), files=files))
write('tests.json', tests)
print(json.dumps(dict(candidate=name, files=files, tests={role: value['totals'] for role, value in tests.items()})))
