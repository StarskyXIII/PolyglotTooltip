"""Freeze historical native inputs and stratified fresh samples. Does not start a game."""
from pathlib import Path
import collections
import hashlib
import importlib.util
import json
import sys

REPO = Path(__file__).resolve().parents[3]
LANGUAGES = ['en_us', 'zh_cn']
SEED = 'native-name-table-prototype-v1-20261014'
RUNS = {
    'E2E': REPO / 'docs/research/legacy112/name-correctness-2026-10-09/e2e-name-inventory1',
    'Meatball': REPO / 'docs/research/legacy112/feedback-2026-10-13/meatball-name-inventory2',
}
FAMILIES = {'microblockcbe': 128, 'tconstruct': 64, 'forestry': 32, 'extratrees': 32,
            'appliedenergistics2': 32, 'enderio': 32, 'gregtech': 32, 'ic2': 32}

def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))

def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()

def dump(value):
    return json.dumps(value, ensure_ascii=False, separators=(',', ':'))

def write(path, value):
    with path.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)
        stream.write('\n')

def rows(directory):
    summary = read(directory / 'summary.json')
    assert summary['state'] == 'COMPLETE' and summary['sourceExhausted']
    assert not (directory / 'failure.json').exists()
    count = 0
    with (directory / 'names.jsonl').open(encoding='utf-8') as stream:
        for line in stream:
            assert line.endswith('\n')
            row = json.loads(line)
            assert row['sequence'] == count, (directory, count)
            count += 1
            yield row
    assert count == summary['written'] == summary['processed']

def reason(row, primary, target):
    if primary.get('error') or target.get('error'):
        return 'native-callback-error'
    if not primary.get('inputMatches') or not target.get('inputMatches'):
        return 'native-input-roundtrip-mismatch'
    if row.get('stateChanged') or primary.get('stateChanged') or target.get('stateChanged'):
        return 'observed-stack-mutation'
    if row.get('nativeName') != primary.get('name'):
        return 'primary-observation-drift'
    if not primary.get('name', '').strip() or not target.get('name', '').strip():
        return 'empty-native-name'
    return None

def candidate(buckets, key, limit, rank, row):
    values = buckets.setdefault(key, [])
    if len(values) < limit or rank < values[-1][0]:
        values.append((rank, row))
        values.sort(key=lambda entry: entry[0])
        del values[limit:]

def prepare(output):
    assert not output.exists(), 'Use a new output directory'
    output.mkdir(parents=True)
    existing_manifest = read(REPO / 'docs/research/legacy112/feedback-2026-10-13/name-inventory-final1/evidence-manifest.json')
    expected = {str(Path(row['path']).resolve()).casefold(): row['sha256'] for row in existing_manifest['files']}
    classification_path = REPO / 'docs/research/legacy112/feedback-2026-10-13/summarize-name-inventory.py'
    spec = importlib.util.spec_from_file_location('historical_classifier', classification_path)
    classifier = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(classifier)
    overall = {'schema': 'native-name-table-prototype-corpus-v1', 'seed': SEED,
        'scope': 'Historical finite native observations; replay measures addressability, not held-out accuracy or linguistic quality.',
        'selection': {'perNamespace': {'general': 8, 'nbt': 2, 'historicalGap': 2, 'nativeRisk': 2},
                      'familySupplement': FAMILIES, 'order': 'SHA256(seed:pack:sampleId), ascending; union all buckets; final sampleId sort',
                      'tooltip': 'First rank per up to 12 named namespaces plus first risk in those namespaces; deterministic before fresh capture'},
        'packs': {}, 'mods': []}
    for pack, run in RUNS.items():
        directory = output / pack
        directory.mkdir()
        files = []
        for sub in ['catalog', 'native-zh_tw', 'native-en_us', 'native-zh_cn']:
            for name in ['names.jsonl', 'manifest.json', 'summary.json']:
                path = run / sub / name
                digest = sha(path)
                prior = expected.get(str(path.resolve()).casefold())
                assert prior is not None and digest == prior, ('Historical evidence changed', path)
                files.append({'path': str(path), 'sha256': digest, 'bytes': path.stat().st_size})
        for name in ['registry.json', 'initial-runtime.json', 'launch.json', 'inventory-complete.json', 'catalog-status.json']:
            path = run / name
            files.append({'path': str(path), 'sha256': sha(path), 'bytes': path.stat().st_size})
        environment = hashlib.sha256(('historical-only:' + dump(files)).encode()).hexdigest()
        write(directory / 'source-manifest.json', {'historicalOnly': True, 'environment': environment, 'files': files})
        registry = read(run / 'registry.json')
        modules = {value['id']: value for value in registry['mods']}
        item_counts = collections.Counter(value['item'].split(':')[0] for value in registry['items']
                                          if value['item'] != 'minecraft:air' and not value['item'].startswith('polyglotdevbridge:'))
        native = {language: iter(rows(run / ('native-' + language))) for language in ['zh_tw', *LANGUAGES]}
        totals = collections.Counter()
        per_mod = {}
        buckets, examples, lossy, exact_seen = {}, [], {}, set()
        controls = {}
        selected_candidates = {}
        source_counts = collections.Counter()
        output_file = directory / 'observations.jsonl'
        with output_file.open('x', encoding='utf-8', newline='\n') as stream:
            stream.write(dump({'environment': environment, 'primaryLanguage': 'zh_tw', 'languages': LANGUAGES, 'historicalOnly': True}) + '\n')
            for row in rows(run / 'catalog'):
                totals['catalogRows'] += 1
                if row['kind'] != 'item':
                    totals['sourceErrors'] += 1
                    continue
                observations = {code: next(iterator) for code, iterator in native.items()}
                assert all(value['sampleId'] == row['sampleId'] and value['item'] == row['item'] for value in observations.values())
                if row['mod'] == 'polyglotdevbridge':
                    totals['excludedFixtureStates'] += 1
                    continue
                assert row['sampleId'] not in exact_seen
                exact_seen.add(row['sampleId'])
                mod = row['mod']
                stats = per_mod.setdefault(mod, collections.Counter())
                totals['states'] += 1
                stats['states'] += 1
                source_counts[row['source']] += 1
                state = {key: row[key] for key in ['item', 'metadata', 'damage', 'count', 'stack']}
                primary = observations['zh_tw'].get('name')
                targets = {}
                is_risk = False
                has_gap = False
                for language in LANGUAGES:
                    target = observations[language]
                    rejection = reason(row, observations['zh_tw'], target)
                    value = {'name': target.get('name')}
                    if rejection:
                        value['rejection'] = rejection
                        totals['rejectedPairs'] += 1
                        totals['reject:' + rejection] += 1
                        stats['rejectedPairs'] += 1
                        stats['reject:' + rejection] += 1
                        is_risk = True
                    else:
                        totals['admittedPairs'] += 1
                        stats['admittedPairs'] += 1
                        equality = 'sameAsPrimaryPairs' if primary == target['name'] else 'differentFromPrimaryPairs'
                        totals[equality] += 1
                        stats[equality] += 1
                        is_risk |= equality == 'sameAsPrimaryPairs'
                    existing = next(value for value in row['languages'] if value['language'] == language)
                    category, _ = classifier.classify(row, existing, observations['zh_tw'], target)
                    totals['historical:' + category] += 1
                    stats['historical:' + category] += 1
                    if category == 'confirmed':
                        has_gap = True
                        if rejection is None:
                            totals['historicalConfirmedAdmitted'] += 1
                            stats['historicalConfirmedAdmitted'] += 1
                    targets[language] = value
                    if (rejection or primary == target.get('name')) and len(examples) < 24:
                        examples.append({'item': row['item'], 'sampleId': row['sampleId'], 'language': language,
                                         'primary': primary, 'target': target.get('name'), 'reason': rejection or 'same-as-primary'})
                stream.write(dump({'state': state, 'primary': primary, 'targets': targets}) + '\n')
                material = row['stack'].get('value', {}).get('tag', {}).get('value', {}).get('mat', {}).get('value')
                if mod == 'microblockcbe':
                    group = lossy.setdefault(str(row['metadata']), {'states': 0, 'names': set()})
                    group['states'] += 1
                    if observations['en_us'].get('name') is not None:
                        group['names'].add(observations['en_us']['name'])
                sample = {key: row[key] for key in ['sampleId', 'item', 'mod', 'metadata', 'damage', 'count', 'stack', 'serialized', 'source']}
                sample.update(historicalPrimary=primary, historicalTargets=targets, historicalGap=has_gap, nativeRisk=is_risk)
                if material is not None:
                    sample['material'] = material
                if row['item'] == 'minecraft:stone' and row['metadata'] == 0 and row['damage'] == 0 and row['count'] == 1:
                    controls.setdefault('stone', sample)
                rank = hashlib.sha256((SEED + ':' + pack + ':' + row['sampleId']).encode()).hexdigest()
                candidate(buckets, (mod, 'general'), 8, rank, sample)
                if 'tag' in row['stack']['value'] or 'ForgeCaps' in row['stack']['value']:
                    candidate(buckets, (mod, 'nbt'), 2, rank, sample)
                if has_gap:
                    candidate(buckets, (mod, 'historicalGap'), 2, rank, sample)
                if is_risk:
                    candidate(buckets, (mod, 'nativeRisk'), 2, rank, sample)
                if mod in FAMILIES:
                    candidate(buckets, (mod, 'family'), FAMILIES[mod], rank, sample)
            for code, iterator in native.items():
                assert next(iterator, None) is None, ('Extra native rows', code)
        selected = {}
        for (mod, stratum), values in sorted(buckets.items()):
            for rank, sample in values:
                key = sample['sampleId']
                if key not in selected:
                    selected[key] = dict(sample, selectionRank=rank, strata=[])
                selected[key]['strata'].append(stratum)
        samples = [selected[key] for key in sorted(selected)]
        assert {row['mod'] for row in samples} == set(per_mod)
        tooltip_mods = ['minecraft', 'microblockcbe', 'tconstruct', 'forestry', 'extratrees', 'appliedenergistics2',
                        'enderio', 'actuallyadditions', 'binniecore', 'ic2', 'gregtech', 'draconicevolution']
        tooltip = {}
        for mod in tooltip_mods:
            candidates = sorted((row for row in samples if row['mod'] == mod), key=lambda row: row['selectionRank'])
            if candidates:
                tooltip[candidates[0]['sampleId']] = candidates[0]
            risk = [row for row in candidates if row['nativeRisk']]
            if risk:
                tooltip[risk[0]['sampleId']] = risk[0]
        write(directory / 'samples.json', samples)
        assert 'stone' in controls
        write(directory / 'controls.json', controls)
        write(directory / 'tooltip-samples.json', list(tooltip.values()))
        write(directory / 'sample-seal.json', {'seed': SEED, 'samplesSha256': sha(directory / 'samples.json'),
              'tooltipSha256': sha(directory / 'tooltip-samples.json'), 'sourceManifestSha256': sha(directory / 'source-manifest.json'),
              'controlsSha256': sha(directory / 'controls.json'),
              'selectedStates': len(samples), 'selectedNamespaces': len(per_mod), 'perNamespace': dict(collections.Counter(row['mod'] for row in samples)),
              'frozenBeforeFreshCapture': True, 'rule': overall['selection']})
        runtime = read(run / 'initial-runtime.json')
        info = {'totals': dict(totals), 'loadedMods': len(modules), 'itemNamespaces': len(item_counts),
                'catalogNamespaces': len(per_mod), 'selectedStates': len(samples), 'tooltipStates': len(tooltip),
                'historicalEnvironment': environment, 'sources': dict(source_counts), 'examples': examples,
                'microblockIdMetadataCollisions': {key: {'states': value['states'], 'distinctEnglishNames': len(value['names'])} for key, value in lossy.items()},
                'java': runtime.get('java.runtime.version'), 'forge': runtime.get('forge'), 'resourcePacks': runtime.get('resourcePacks'),
                'sourceRun': str(run), 'observationsSha256': sha(output_file)}
        overall['packs'][pack] = info
        for mod in sorted(set(modules) | set(per_mod)):
            overall['mods'].append({'pack': pack, 'mod': mod, 'name': modules.get(mod, {}).get('name', mod),
                'version': modules.get(mod, {}).get('version'), 'registeredItems': item_counts[mod],
                'selectedStates': sum(row['mod'] == mod for row in samples), **dict(per_mod.get(mod, {}))})
        print(dump({'pack': pack, 'states': totals['states'], 'selected': len(samples), 'namespaces': len(per_mod), 'admittedPairs': totals['admittedPairs']}), flush=True)
    write(output / 'summary.json', overall)
    write(output / 'preparation-seal.json', {'scriptSha256': sha(Path(__file__)), 'summarySha256': sha(output / 'summary.json'),
                                          'sourceClassifierSha256': sha(classification_path)})

if __name__ == '__main__':
    prepare(Path(sys.argv[1]).resolve())
