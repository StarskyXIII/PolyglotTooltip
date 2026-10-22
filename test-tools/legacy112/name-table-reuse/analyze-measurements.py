"""Summarize the pre-implementation measurements without making cache-admission claims."""
import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
REPORT = ROOT / 'docs/research/legacy112/name-table-reuse-2026-10-14'


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def summarize(pack, attempt):
    run = REPORT / f'run-{pack.lower()}-{attempt}'
    complete = read(run / 'complete.json')
    phases = {}
    input_hashes = {}
    rows_by_phase = {}
    for phase in ['primary-first', 'en-us', 'zh-cn', 'primary-repeat']:
        source = run / f'{phase}.json'
        data = read(source)
        rows = data['rows']
        rows_by_phase[phase] = {row['sampleId']: row for row in rows}
        assert len(rows_by_phase[phase]) == len(rows)
        input_hashes[source.name] = hashlib.sha256(source.read_bytes()).hexdigest()
        phases[phase] = dict(rows=len(rows), elapsedMs=data['elapsedMs'],
                             empty=sum(not row.get('name') for row in rows),
                             errors=sum(bool(row.get('error')) for row in rows),
                             inputMismatch=sum(row.get('inputMatches') is False for row in rows),
                             stateChanged=sum(row.get('stateChanged') is True for row in rows),
                             contextChanged=sum(row.get('contextChanged') is True for row in rows),
                             nativeNameCalls=sum(row.get('directNativeNameCalls', 0) for row in rows))
    first, repeat = rows_by_phase['primary-first'], rows_by_phase['primary-repeat']
    assert first.keys() == repeat.keys()
    changed = [dict(sampleId=key, item=first[key].get('item'), before=first[key].get('name'),
                    after=repeat[key].get('name')) for key in first if first[key].get('name') != repeat[key].get('name')]
    switches = []
    for label in ['switch-en-us', 'switch-zh-cn', 'switch-primary']:
        source = run / f'{label}.json'
        data = read(source)
        input_hashes[source.name] = hashlib.sha256(source.read_bytes()).hexdigest()
        switches.append({key: data.get(key) for key in ['from', 'to', 'callerElapsedMs', 'readyElapsedMs', 'complete']})
    restoration = REPORT / f'measurement-{pack.lower()}-{attempt}' / 'restoration.json'
    return dict(pack=pack, attempt=attempt, complete=complete, phases=phases,
                primaryRoundtripChanged=changed, switches=switches,
                fixtureRestoration=read(restoration) if restoration.exists() else None,
                sha256=input_hashes)


result = dict(purpose='Pre-implementation selective-reload measurements',
              limits=['Conditioned acquisition; not cold-start reference data.',
                      'v1 serialized-state sampling; not v2 callback-free eligibility or production cache coverage.',
                      'readyElapsedMs includes existing fallback/library/index readiness, not just native reload.',
                      'No paired full-reload control in this measurement; old prototype timings are descriptive only.'],
              packs=[summarize('E2E', 'selective-measure2'), summarize('Meatball', 'selective-measure1')])
output = REPORT / 'selective-measurement-summary.json'
with output.open('x', encoding='utf-8') as stream:
    json.dump(result, stream, ensure_ascii=False, indent=2)
print(json.dumps(result, ensure_ascii=False, indent=2))
