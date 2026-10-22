"""Phase-3 comparison: the epoch build/reuse pair against the phase-2 final candidate runs (read only).

Usage: phase3-summarize.py <output.json> <run-name>...   (runs live in the phase-2 report folder)
"""
from pathlib import Path
import json
import sys

root = Path(__file__).resolve().parents[3]
report = root / 'docs/research/legacy112/name-table-phase2-diagnosis-2026-10-16'
read = lambda file: json.loads(file.read_text(encoding='utf-8-sig'))


def window(performance):
    raw = performance['rawFrameIntervalsMs']
    return dict(elapsedMs=round(performance['elapsedMs']), averageFps=round(len(raw) / performance['elapsedMs'] * 1000, 1),
                p95=performance['frameIntervalMs']['p95'], p99=performance['frameIntervalMs']['p99'],
                max=performance['frameIntervalMs']['max'],
                over50ms=sum(1 for value in raw if value > 50), over100ms=sum(1 for value in raw if value > 100))


def run(name):
    folder = report / name
    summary, launch = read(folder / 'summary.json'), read(folder / 'launch.json')
    audit_file = folder / 'jei-key-audit.json'
    audit = read(audit_file) if audit_file.is_file() else {}
    complete = read(folder / 'complete.json') if (folder / 'complete.json').is_file() else {}
    final_table = (complete.get('final') or {}).get('table') or summary['table']
    collection = summary['lastCollection'] or {}
    return dict(
        run=name, mode=summary['mode'], worldName=summary['worldName'], origin=summary['origin'],
        productSha256=launch['productSha256'][:8], bridgeSha256=launch['bridgeSha256'][:8],
        worldLoadMs=round(summary['worldLoadMs']),
        collectionStartAfterWorldReadyMs=summary['collectionStartAfterWorldReadyMs'] and round(summary['collectionStartAfterWorldReadyMs']),
        tableReadyMs=summary['tableReady']['ms'], aliasReadyMs=summary['aliasReady']['ms'],
        aliasAfterTableReadyMs=round(summary['aliasReady']['afterTableReadyMs']),
        indexed=summary['aliasReady']['indexed'], fallback=summary['aliasReady']['fallback'], missing=summary['aliasReady']['missing'],
        elements=summary['aliasReady']['size'],
        tableBytes=int(summary['persisted']['bytes']), entries=summary['table']['entries'], protocol=summary['table']['protocol'],
        generation=summary['table']['tableGeneration'], builds=summary['table']['builds'], loads=summary['table']['loads'],
        hits=summary['table']['hits'], primaryMismatches=summary['table']['primaryMismatches'],
        dictionaryPasses=final_table.get('dictionaryPasses'),
        collection=dict(elapsedMs=collection.get('elapsedMs'), phaseMs=collection.get('phaseMs'), eligible=collection.get('eligible'),
                        admitted=collection.get('admitted'), nativeCalls=collection.get('nativeCalls'),
                        rejected=collection.get('rejected')) if collection else None,
        jeiKeyAudit=dict(ingredients=audit.get('ingredients'), hits=audit.get('hits'), misses=audit.get('misses'),
                         failures=audit.get('failures'), missClasses={key: value for key, value in (audit.get('classesHitMissFailed') or {}).items()
                                                                      if value[1] > 0}) if audit else None,
        background=window(read(folder / 'background-performance.json')), idle=window(read(folder / 'idle-performance.json')))


runs = [run(name) for name in sys.argv[2:]]
(report / sys.argv[1]).write_text(json.dumps(dict(runs=runs), ensure_ascii=False, indent=2), encoding='utf-8')
for value in runs:
    print(value['run'], value['mode'], 'table', value['tableReadyMs'], 'alias', value['aliasReadyMs'], 'after', value['aliasAfterTableReadyMs'],
          'idx/miss', value['indexed'], value['missing'], 'bytes', value['tableBytes'], 'entries', value['entries'],
          'audit', value['jeiKeyAudit'] and (value['jeiKeyAudit']['hits'], value['jeiKeyAudit']['misses']),
          'bg', value['background']['averageFps'], value['background']['over50ms'], value['background']['over100ms'],
          'idle', value['idle']['averageFps'], value['idle']['over50ms'], value['idle']['over100ms'])
