"""Summarize phase-7 hover-probe records of one or more runs: tooltip cost and table-state versus table-miss names."""
from pathlib import Path
import json
import re
import sys

report = Path(__file__).resolve().parents[3] / 'docs/research/legacy112/name-table-phase2-diagnosis-2026-10-16'
strip = lambda line: re.sub('§.', '', line).strip()
result = {}
for attempt in sys.argv[1:]:
    run = report / ('run-meatball-' + attempt)
    iron = json.loads((run / 'hover-iron-ingot.json').read_text(encoding='utf-8'))
    sample = json.loads((run / 'fallback-sample.json').read_text(encoding='utf-8'))
    rows = {}
    for name, row in sample.items():
        if row.get('missing'):
            rows[name] = 'not in recipe viewer'
            continue
        if 'error' in row['table'] or 'error' in row['miss']:
            rows[name] = dict(item=row['item'], tableError=row['table'].get('error'), missError=row['miss'].get('error'))
            continue
        table, miss = [strip(line) for line in row['table']['tooltip']], [strip(line) for line in row['miss']['tooltip']]
        # Secondary names are inserted right after the title; compare the leading rows up to the first shared later row.
        rows[name] = dict(item=row['item'], metadata=row['metadata'], covered=row.get('covered'),
                          tableMedianMs=row['table']['median'], missMedianMs=row['miss']['median'],
                          title=table[0] if table else None, onlyTable=[l for l in table[1:4] if l not in miss],
                          onlyMiss=[l for l in miss[1:4] if l not in table], same=table == miss,
                          tableHead=table[:3], missHead=miss[:3])
    result[attempt] = dict(ironIngot=dict(median=iron['median'], p95=iron['p95'], max=iron['max'], tooltip=iron['tooltip']), classes=rows)
print(json.dumps(result, ensure_ascii=False, indent=1))
