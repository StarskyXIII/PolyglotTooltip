import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {hash, equal, canonical, compareSample} from './name-audit-core.mjs';
import {readOptional, seal, validateBatch, groupFailure, validateIdentity, validateArtifactHashes} from './name-audit-pack-core.mjs';
import {validateCorpus, parentEvidence, callbackFixtures} from './name-audit-supplement-core.mjs';

const [englishPath, chinesePath, productPath, outputPath] = process.argv.slice(2);
assert.ok(outputPath, 'Usage: native-en native-zh product-zh output-directory');
const folders = [englishPath, chinesePath, productPath].map(p => path.resolve(p));
const read = async file => JSON.parse(await fs.readFile(file, 'utf8'));
const sessions = await Promise.all(folders.map(f => read(path.join(f, 'session.json'))));
const results = await Promise.all(folders.map(f => read(path.join(f, 'result.json'))));
const bindings = await Promise.all(folders.map(f => read(path.join(f, 'corpus-binding.json'))));
const observers = await Promise.all(folders.map(f => readOptional(path.join(f, 'observer.json'))));
const corpusRoot = bindings[0].root, indexBytes = await fs.readFile(path.join(corpusRoot, 'index.json'));
const index = JSON.parse(indexBytes), samples = new Map(index.samples.map(s => [s.id, s]));
await validateCorpus(corpusRoot, index);
const checks = [], counts = {}, nameCounts = {}, groups = new Map();
const check = (name, actual, expected) => checks.push({name, pass: equal(actual, expected),
  ...(!equal(actual, expected) ? {actual, expected} : {})});
check('identical frozen corpus', bindings.map(b => b.sha256), Array(3).fill(hash(indexBytes)));
check('native source session', hash(await fs.readFile(path.join(folders[0], 'session.json'))), index.sourceSessionSha256);
check('independent roles', sessions.map(s => [s.config.language, s.config.product]), [['en_us', false], ['zh_cn', false], ['zh_cn', true]]);
check('completed attempts', results.map(r => [r.completed, r.failures]), [[true, []], [true, []], [true, []]]);
check('independent client directories', new Set(sessions.map(s => path.resolve(s.config.fixture).toLowerCase())).size, 3);
check('identical controllers', sessions.map(session => session.controller), Array(3).fill(sessions[0].controller));
const callbackCoverage = await Promise.all(folders.map(async folder => {
  const derived = callbackFixtures(await read(path.join(folder, 'contracts.json')), index.parts.find(part => part.coverage === 'manual-fixtures'));
  assert.deepEqual(derived, await read(path.join(folder, 'callback-fixtures.json')));
  return derived;
}));
check('identical callback material population', callbackCoverage, Array(3).fill(callbackCoverage[0]));
for (const [lane, session] of sessions.entries()) {
  const evidence = await parentEvidence(session, index.parent);
  for (const entry of evidence.checks) checks.push({...entry, name: lane + ': ' + entry.name});
}
check('independent nonces', new Set(sessions.map(s => s.config.nonce)).size, 3);
const normalize = (value, session) => typeof value === 'string'
  ? value.replaceAll('\\', '/').replaceAll(session.config.fixture.replaceAll('\\', '/'), '${fixture}')
    .replaceAll(session.config.nonce, '${nonce}')
    .replace('-Dclientdevbridge.port=' + session.config.port, '-Dclientdevbridge.port=${port}')
    .replace('-Dcleanroom.relauncher.parent=' + session.environment['cleanroom.relauncher.parent'], '-Dcleanroom.relauncher.parent=${bootstrapPid}')
  : value;
for (let lane = 0; lane < sessions.length; lane++) {
  const session = sessions[lane], baseline = sessions[0];
  validateArtifactHashes(session.provenance);
  validateIdentity(results[lane].identity, session, observers[lane], {requireObserver: true});
  check(lane + ': result PID', results[lane].pid, session.pid);
  check(lane + ': result config', results[lane].config, session.config);
  check(lane + ': result corpus', results[lane].corpus, bindings[lane]);
  const mods = s => s.provenance.mods.filter(m => m.id !== 'polyglottooltip')
    .map(m => [m.id, m.version, m.sha256 ?? null]).sort((a, b) => canonical(a).localeCompare(canonical(b)));
  check(lane + ': loaded native mod bytes', mods(session), mods(baseline));
  check(lane + ': resource packs', session.provenance.packs, baseline.provenance.packs);
  check(lane + ': native class bytes', Object.fromEntries(Object.entries(session.provenance.origins).map(([key, value]) => [key, value.sha256])),
    Object.fromEntries(Object.entries(baseline.provenance.origins).map(([key, value]) => [key, value.sha256])));
  for (const key of ['forge', 'java.runtime.version', 'java.vendor', 'java.home']) check(lane + ': ' + key,
    session.environment[key], baseline.environment[key]);
  check(lane + ': JVM arguments', session.environment.jvmArguments.map(a => normalize(a, session)),
    baseline.environment.jvmArguments.map(a => normalize(a, baseline)));
  const contentInputs = s => Object.fromEntries(Object.entries(s.replayInputs).filter(([key]) => !key.startsWith('config/')));
  check(lane + ': scripts and resources', contentInputs(session), contentInputs(baseline));
  check(lane + ': player setup', session.setup.after, baseline.setup.after);
  const afterInputs = await read(path.join(folders[lane], 'after-inputs.json'));
  check(lane + ': stable input files', afterInputs, session.replayInputs);
  check(lane + ': product settings', results[lane].identity.settings, session.identity.settings);
  check(lane + ': client world', [results[lane].identity.jvmStartTime, results[lane].identity.worldGeneration],
    [session.identity.jvmStartTime, session.identity.worldGeneration]);
  if (lane < 2) check(lane + ': native quit requested', (await readOptional(path.join(folders[lane], 'quit.json')))?.requested, true);
}

async function rows(folder, part, method) {
  const lane = folders.indexOf(folder);
  const directory = path.join(folder, method, part.id);
  const transport = await read(path.join(directory, 'transport.json'));
  const completion = await read(path.join(directory, 'complete.json'));
  assert.equal(completion.manifestSha256, part.manifestSha256);
  const all = [...transport.skipped];
  for (let number = 0; number < transport.batches.length; number++) {
    const result = await read(path.join(directory, String(number).padStart(5, '0') + '.json'));
    validateBatch(result, transport.batches[number].map(id => ({id})));
    if (method === 'audit.observe') validateIdentity(result.identity, sessions[lane], observers[lane], {requireObserver: true});
    all.push(...result.rows);
  }
  assert.deepEqual(all.map(r => r.id).sort(), [...part.ids].sort());
  return new Map(all.map(row => [row.id, row]));
}

const output = path.resolve(outputPath);
await fs.mkdir(output, {recursive: false});
const comparedIds = [];
const coverageCounts = {};
for (const part of index.parts) {
  const bytes = await fs.readFile(path.join(corpusRoot, 'parts', part.id, 'manifest.json'));
  assert.equal(hash(bytes), part.manifestSha256);
  const corpus = JSON.parse(bytes), selected = new Set(part.ids);
  const observations = await Promise.all(folders.map(folder => rows(folder, part, 'audit.observe')));
  const diagnostics = await rows(folders[2], part, 'audit.diagnose');
  const category = coverageCounts[part.coverage] ??= {counts: {}, nameCounts: {}, uniqueIds: [],
    plannedCases: 0, witnessedCases: 0, sourceWitnesses: 0};
  category.plannedCases += part.plannedCases?.length ?? 0;
  category.witnessedCases += part.caseWitnesses?.filter(witness => witness.samples.length > 0).length ?? 0;
  category.sourceWitnesses += corpus.samples.reduce((sum, sample) => sum + sample.sources.length, 0);
  let outputRows = [];
  for (const original of corpus.samples.filter(s => selected.has(s.id))) {
    const sample = {...original, sources: samples.get(original.id).sources};
    comparedIds.push(sample.id);
    const lanes = observations.map(map => map.get(sample.id));
    const strict = compareSample(sample, ...lanes);
    const names = strict.status === 'tooltip-mutated' ? compareSample(sample, ...lanes, {allowMatchingNativeMutation: true}) : strict;
    counts[strict.status] = (counts[strict.status] ?? 0) + 1;
    nameCounts[names.status] = (nameCounts[names.status] ?? 0) + 1;
    category.uniqueIds.push(sample.id);
    category.counts[strict.status] = (category.counts[strict.status] ?? 0) + 1;
    category.nameCounts[names.status] = (category.nameCounts[names.status] ?? 0) + 1;
    outputRows.push(JSON.stringify({coverage: part.coverage, strict, names}));
    if (outputRows.length === 256) {
      await fs.appendFile(path.join(output, 'rows.ndjson'), outputRows.join('\n') + '\n');
      outputRows = [];
    }
    if (['pass', 'no-secondary-needed'].includes(names.status)) continue;
    const descriptor = groupFailure(names, diagnostics.get(sample.id)), key = canonical(descriptor);
    if (!groups.has(key)) groups.set(key, {...descriptor, count: 0, items: new Set(), examples: []});
    const group = groups.get(key);
    group.count++; group.items.add(sample.item);
    if (group.examples.length < 3) group.examples.push({id: sample.id, item: sample.item, metadata: sample.metadata,
      part: part.id, strictStatus: strict.status, result: names,
      nativeEnglish: lanes[0]?.tooltip, nativeChinese: lanes[1]?.tooltip, product: lanes[2]?.tooltip,
      diagnostics: diagnostics.get(sample.id)});
  }
  if (outputRows.length) await fs.appendFile(path.join(output, 'rows.ndjson'), outputRows.join('\n') + '\n');
}
const configurationDifferences = [...new Set(sessions.flatMap(s => Object.keys(s.replayInputs).filter(k => k.startsWith('config/'))))]
  .filter(key => !sessions.every(s => s.replayInputs[key] === sessions[0].replayInputs[key]))
  .map(key => ({path: key, sha256: sessions.map(s => s.replayInputs[key] ?? null)}));
check('exact comparison coverage', comparedIds.sort(), index.samples.map(s => s.id).sort());
check('unreviewed configuration differences', configurationDifferences, []);
const report = {schema: 3, scope: 'Selected native source recollection and separately counted authored name-boundary fixtures',
  samples: index.samples.length, catalogItems: index.catalogItems, selectedNativeItems: index.selectedNativeItems,
  collectionComplete: index.collectionComplete, coverageCounts,
  coverage: index.parts.filter(p => !p.complete || p.zeroSampleItems?.length).map(({id, complete, limits, failures, sourceErrors, zeroSampleItems}) =>
    ({id, complete, limits, failures, sourceErrors, zeroSampleItems})), checks, evidenceChecksPassed: checks.every(c => c.pass),
  counts, nameCounts, configurationDifferences,
  groups: [...groups.values()].map(g => ({...g, items: [...g.items]})).sort((a, b) => b.count - a.count),
  compatibilityAccepted: false, entityHudCovered: false, allPossibleNbtStatesCovered: false};
await seal(path.join(output, 'summary.json'), report);
console.log(JSON.stringify({samples: report.samples, collectionComplete: report.collectionComplete,
  evidenceChecksPassed: report.evidenceChecksPassed, failedChecks: checks.filter(c => !c.pass).map(c => c.name),
  counts, nameCounts, groups: report.groups.map(({namespace, reason, nameOwner, count, items}) =>
    ({namespace, reason, nameOwner, count, items: items.length}))}, null, 2));
