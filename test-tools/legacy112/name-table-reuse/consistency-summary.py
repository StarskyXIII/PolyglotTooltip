"""Collect the consistency-probe runs into one Markdown table: per pack the measured stacks, the share the vanilla
standard naming path reproduces (= what a key-based secondary dictionary can name), how the rest splits between items
that override getItemStackDisplayName and names changed by something else, secondary-dictionary hits among the
consistent stacks, and the item classes with the most inconsistent stacks (the layer-2 priority list).

Usage: consistency-summary.py [attempt]   (default: the latest run per pack)
Writes docs/research/legacy112/consistency-probe-2026-10-22/summary[-attempt].md and prints it.
"""
from pathlib import Path
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')
REPORT = Path(__file__).resolve().parents[3] / 'docs/research/legacy112/consistency-probe-2026-10-22'
ORDER = ['nova', 'extended', 'dj2', 'expert', 'meatball']


def pct(part, whole):
    return '—' if not whole else '%.1f%%' % (100.0 * part / whole)


def load(attempt):
    runs = {}
    for folder in sorted(REPORT.glob('run-*-*')):
        summary = folder / 'summary.json'
        if not summary.is_file(): continue
        pack, _, run_attempt = folder.name[4:].rpartition('-')
        if attempt and run_attempt != attempt: continue
        runs[pack] = (folder.name, json.loads(summary.read_text(encoding='utf-8')))
    return runs


def main():
    attempt = sys.argv[1] if len(sys.argv) > 1 else None
    runs = load(attempt)
    lines = ['# Consistency probe: coverage gap per pack' + (' (attempt %s)' % attempt if attempt else ''), '',
             'Measured = stacks − custom-named − errors. Consistent = the vanilla standard path (`getUnlocalizedName(stack)` → translate → `.name` → translate → trim) '
             'equals `getDisplayName()` without style codes, i.e. a key-based secondary dictionary names the stack. Override = the item class overrides '
             '`getItemStackDisplayName`; external = it does not, the name is changed elsewhere. Hit = the key exists in the pack\'s own lang files for that language '
             '(en_us fallback included); redundant = the secondary text equals the primary name.', '']
    lines.append('| pack | phase | source | stacks | custom | measured | consistent | untranslated | override | external | composite | keyless | errors | secondary hits (hit / miss / redundant) | fluids consistent |')
    lines.append('|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|')
    for pack in ORDER + sorted(set(runs) - set(ORDER)):
        if pack not in runs: continue
        name, s = runs[pack]
        hits = ', '.join('%s %d / %d / %d' % (code, v['hit'], v['miss'], v['redundant']) for code, v in (s.get('languageHits') or {}).items())
        fluids = s.get('fluids') or {}
        lines.append('| %s | %s | %s | %d | %d | %d | %d (%s) | %d | %d (%s) | %d (%s) | %d | %d | %d | %s | %s |' % (
            pack, s.get('phaseUsed'), s.get('source'), s['stacks'], s['custom'], s['measured'], s['consistent'], pct(s['consistent'], s['measured']),
            s['consistentUntranslated'], s['inconsistentOverride'], pct(s['inconsistentOverride'], s['measured']), s['inconsistentExternal'],
            pct(s['inconsistentExternal'], s['measured']), s.get('inconsistentContains', 0), s.get('inconsistentKeyless', 0), s['errors'], hits or '—',
            ('%d / %d' % (fluids.get('consistent', 0), fluids.get('total', 0))) if fluids else '—'))
    lines.append('')
    for pack in ORDER + sorted(set(runs) - set(ORDER)):
        if pack not in runs: continue
        name, s = runs[pack]
        lines.append('## %s (%s, %s, game language %s, secondary %s)' % (pack, s.get('instance'), name, s.get('currentLanguage'), ', '.join(s.get('languages') or [])))
        lines.append('')
        if s.get('sourceError'): lines.append('- source error: `%s`' % s['sourceError'])
        if s.get('errorKinds'): lines.append('- errors: ' + ', '.join('%s ×%d' % kv for kv in s['errorKinds'].items()))
        dictionaries = s.get('dictionaries') or {}
        for code, d in dictionaries.items():
            lines.append('- dictionary %s: %d domains, %d files (%d own), %d entries (%d own keys), %d failures' % (code, d['domains'], d['files'], d['ownFiles'], d['entries'], d['ownKeys'], d['failures']))
        lines.append('- standard path mean: %.1f µs per stack; probe elapsed menu %s ms, world %s ms' % (s.get('standardPathMeanMicros') or 0, s['elapsedMs'].get('menu'), s['elapsedMs'].get('world')))
        if s.get('menuVersusWorld'):
            m = s['menuVersusWorld']
            lines.append('- menu vs world: consistent %d → %d, errors %d → %d' % (m['menuConsistent'], m['worldConsistent'], m['menuErrors'], m['worldErrors']))
        lines.append('')
        lines.append('| item class | mod | stacks | inconsistent | override | external | composite | keyless | errors | sample (key → expected ≠ actual) |')
        lines.append('|---|---|---|---|---|---|---|---|---|---|')
        for entry in (s.get('topInconsistentClasses') or [])[:20]:
            sample = entry.get('sample') or {}
            text = ('`%s` → `%s` ≠ `%s`' % (sample.get('key'), sample.get('expected'), sample.get('actual'))) if sample else ''
            lines.append('| `%s` | %s | %d | %d | %d | %d | %d | %d | %d | %s |' % (entry['name'], entry['mod'], entry['stacks'], entry['inconsistent'], entry['override'], entry['external'],
                                                                           entry.get('composite', 0), entry.get('keyless', 0), entry['errors'], text.replace('|', '\\|')))
        lines.append('')
        lines.append('| mod | stacks | inconsistent | override | external | errors |')
        lines.append('|---|---|---|---|---|---|')
        for entry in (s.get('topInconsistentMods') or [])[:15]:
            lines.append('| %s | %d | %d | %d | %d | %d |' % (entry['mod'], entry['stacks'], entry['inconsistent'], entry['override'], entry['external'], entry['errors']))
        lines.append('')
    text = '\n'.join(lines) + '\n'
    target = REPORT / ('summary%s.md' % ('-' + attempt if attempt else ''))
    target.write_text(text, encoding='utf-8')
    print(text)
    print('written', target)


if __name__ == '__main__':
    main()
