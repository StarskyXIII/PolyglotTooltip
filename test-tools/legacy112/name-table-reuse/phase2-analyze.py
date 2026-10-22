"""Summarise phase-2 Meatball runs against background-full-meatball-results.json (read only)."""
from pathlib import Path
import json
import sys

root = Path(__file__).resolve().parents[3]
report = root / 'docs/research/legacy112/name-table-phase2-diagnosis-2026-10-16'
baseline_file = root / 'docs/research/legacy112/name-table-reuse-2026-10-14/background-full-meatball-results.json'
read = lambda file: json.loads(file.read_text(encoding='utf-8-sig'))


def window(performance):
    raw = performance['rawFrameIntervalsMs']
    return dict(elapsedMs=performance['elapsedMs'], frameIntervalMs=performance['frameIntervalMs'],
                averageFps=len(raw) / performance['elapsedMs'] * 1000,
                over50ms=sum(1 for value in raw if value > 50), over100ms=sum(1 for value in raw if value > 100),
                expired=performance['expired'], aliasReadyAtEnd=performance.get('aliasReady'))


def run(name):
    folder = report / name
    summary, launch = read(folder / 'summary.json'), read(folder / 'launch.json')
    inventory = folder / 'background-inventory.json'
    return dict(run=name, mode=summary['mode'], worldName=summary['worldName'], origin=summary['origin'],
                worldLoadMs=summary['worldLoadMs'], collectionStartAfterWorldReadyMs=summary['collectionStartAfterWorldReadyMs'],
                tableReady=summary['tableReady'], aliasReady=summary['aliasReady'], searchReady=summary['searchReady'],
                lastCollection=summary['lastCollection'],
                table={key: summary['table'].get(key) for key in ['builds', 'loads', 'switches', 'hits', 'primaryMismatches', 'tableGeneration', 'entries', 'completeCatalog', 'protocol']},
                persisted=summary['persisted'],
                inventoryOpenedAfterWorldReadyMs=read(inventory)['sinceWorldReadyMs'] if inventory.is_file() else None,
                background=window(read(folder / 'background-performance.json')),
                idle=window(read(folder / 'idle-performance.json')),
                productSha256=launch['productSha256'], bridgeSha256=launch['bridgeSha256'])


baseline = read(baseline_file)
runs = [run(name) for name in sys.argv[2:]]
result = dict(baselineFile=str(baseline_file.relative_to(root)), baseline=dict(
    elapsedMs=baseline['table']['lastCollection']['elapsedMs'], nativeCalls=baseline['table']['lastCollection']['nativeCalls'],
    tableReadyMs=baseline['tableReadiness']['elapsedMsFromCollectionStart'],
    searchReadyMs=baseline['searchReadiness']['elapsedMsFromCollectionStart'],
    background=baseline['background'], idle=baseline['baseline']), runs=runs)
(report / sys.argv[1]).write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
for value in runs:
    print(value['run'], value['lastCollection'].get('elapsedMs'), value['tableReady']['ms'], value['aliasReady']['ms'],
          round(value['background']['averageFps'], 1), value['background']['over50ms'], value['background']['over100ms'],
          round(value['idle']['averageFps'], 1), value['idle']['over50ms'], value['idle']['over100ms'])
