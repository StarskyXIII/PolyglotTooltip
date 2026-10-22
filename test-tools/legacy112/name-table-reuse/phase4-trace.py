"""Phase-4 startup timeline: NameStartupTrace events and HEI search-tree console lines relative to world readiness.

Usage: phase4-trace.py <run folder> [<run folder> ...]; prints JSON per run.
"""
from pathlib import Path
import datetime
import json
import re
import sys

LINE = re.compile(r'^\[(\d\d):(\d\d):(\d\d)\] \[([^\]]+)\] \[([^\]]+)\]: (.*)$')
HEI = re.compile(r'Building \[(\w+)\] search tree( took ([\d.]+) (ms|s))?')


def load(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def run(folder):
    folder = Path(folder)
    summary = load(folder / 'summary.json')
    observations = load(folder / 'observations.json')
    ready = datetime.datetime.fromisoformat(summary['worldReadyWall'].replace('Z', '+00:00'))
    ready_ms = ready.timestamp() * 1000
    trace = observations[-1]['table'].get('trace', {})
    events = [dict(sinceWorldReadyS=round((event['at'] - ready_ms) / 1000, 2), event=event['event'],
                   detail=event.get('detail')) for event in trace.get('events', [])]
    # Console stamps are local wall time at one-second resolution; the local offset is the machine's.
    local = ready.astimezone()
    hei = []
    for line in (folder / 'console.log').read_text(encoding='utf-8', errors='replace').splitlines():
        match = LINE.match(line)
        if not match or 'Had Enough Items' not in match.group(5) and 'Enchantments Control' not in match.group(5):
            continue
        stamp = local.replace(hour=int(match.group(1)), minute=int(match.group(2)), second=int(match.group(3)), microsecond=0)
        since = (stamp - local).total_seconds()
        if since < -5 or since > 240:
            continue
        message = match.group(6)
        if 'search tree' in message and 'Asynchronously' not in message or 'localization' in message:
            hei.append(dict(sinceWorldReadyS=round(since, 1), thread=match.group(4), message=message))
    states, previous = [], None
    for value in observations:
        key = (value['table']['state'], value.get('inputStatus'), value.get('appliedState'))
        if key != previous:
            states.append(dict(sinceWorldReadyS=round((datetime.datetime.fromisoformat(value['at'].replace('Z', '+00:00')).timestamp() * 1000 - ready_ms) / 1000, 2),
                               table=key[0], inputs=key[1], applied=key[2], aliasCursor=value.get('aliasCursor')))
            previous = key
    gaps = []
    for before, after in zip(observations, observations[1:]):
        gap = after['monotonicMs'] - before['monotonicMs']
        if gap > 2000:
            start = datetime.datetime.fromisoformat(before['at'].replace('Z', '+00:00')).timestamp() * 1000 - ready_ms
            gaps.append(dict(fromS=round(start / 1000, 2), gapS=round(gap / 1000, 2)))
    return dict(run=folder.name, mode=summary['mode'], worldReadyWall=summary['worldReadyWall'],
                tableReadyS=summary['tableReady']['ms'] / 1000, aliasReadyS=summary['aliasReady']['ms'] / 1000,
                counters=trace.get('counters', {}), events=events, console=hei, states=states, pollGaps=gaps)


if __name__ == '__main__':
    print(json.dumps([run(folder) for folder in sys.argv[1:]], indent=1, ensure_ascii=False))
