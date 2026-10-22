"""Phase-6 alias drain timeline: table ready, table preparation adopted, missing drained, alias ready.

Usage: phase6-drain.py <run folder> [<run folder> ...]; prints JSON per run. Times are seconds after the
first background observation (the measurement origin of summary.json).
"""
from pathlib import Path
import json
import re
import sys

DRAIN = re.compile(r'Polyglot alias missing drain: (.*)$')


def load(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def run(folder):
    folder = Path(folder)
    rows = load(folder / 'observations.json')
    origin = rows[0]['monotonicMs']
    at = lambda row: round((row['monotonicMs'] - origin) / 1000, 1)
    table_ready = adopted = drained = alias_ready = None
    for row in rows:
        size, cursor, missing = row.get('aliasSize'), row.get('aliasCursor'), row.get('aliasMissing')
        if table_ready is None and (row.get('table') or {}).get('ready'):
            table_ready = at(row)
        if adopted is None and missing is not None and missing >= 0 and cursor:
            adopted = dict(at=at(row), cursor=cursor, missing=missing)
        if drained is None and size and cursor == size:
            drained = at(row)
        if alias_ready is None and row.get('aliasReady'):
            alias_ready = at(row)
    drains = []
    console = folder / 'console.log'
    if console.is_file():
        for line in console.read_text(encoding='utf-8', errors='replace').splitlines():
            match = DRAIN.search(line)
            if match:
                drains.append(match.group(1))
    summary = load(folder / 'summary.json')
    return dict(run=folder.name, tableReady=table_ready, adopted=adopted, cursorAtSize=drained, aliasReady=alias_ready,
                summaryAliasReady=summary.get('aliasReady'), drainLog=drains)


if __name__ == '__main__':
    print(json.dumps([run(folder) for folder in sys.argv[1:]], indent=2, ensure_ascii=False))
