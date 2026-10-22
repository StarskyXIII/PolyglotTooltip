import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {hash, equal, canonical} from './name-audit-core.mjs';
import {validateCorpus as validateParent, mergePart as mergeNativePart, readOptional} from './name-audit-pack-core.mjs';

const read = async file => JSON.parse(await fs.readFile(file, 'utf8'));

export function callbackFixtures(contracts, part) {
  const rows = contracts.materials.filter(row => row.cotMaterial && row.callbackPresent);
  const materials = rows.map(row => row.material).sort();
  assert.equal(new Set(materials).size, materials.length, 'Duplicate callback material identifiers');
  const expected = materials.flatMap(material => ['cot-callback:' + material, 'literal:cot-callback:' + material]).sort();
  const cases = part.plannedCases.filter(name => name.startsWith('cot-callback:') || name.startsWith('literal:cot-callback:')).sort();
  assert.deepEqual(cases, expected, 'Callback material fixtures differ from runtime population');
  assert.deepEqual(part.caseWitnesses.filter(witness => cases.includes(witness.case) && witness.samples.length === 1)
    .map(witness => witness.case).sort(), expected, 'Missing callback fixture witnesses');
  return {materials: rows.map(row => ({material: row.material, materialClass: row.materialClass ?? null,
    disposition: row.disposition ?? null, callbackClass: row.callbackClass ?? null, sha256: row.sha256 ?? null,
    associationEligible: row.associationEligible ?? null, callbackStable: row.callbackStable ?? null,
    classAssociationStable: row.classAssociationStable ?? null, definitionStable: row.definitionStable ?? null}))
    .sort((a, b) => a.material.localeCompare(b.material)), plannedCases: cases.length};
}

export function boundedQuery(namespaces, items, sampleLimit, fixtureSet) {
  assert.ok(Number.isSafeInteger(sampleLimit) && sampleLimit >= 50000 && sampleLimit <= 150000);
  const query = {queryVersion: 2, namespaces: [...new Set(namespaces)].sort(), items: [...new Set(items)].sort(), sampleLimit,
    ...(fixtureSet ? {fixtureSet} : {})};
  const querySha256 = hash(JSON.stringify(query));
  return {...query, querySha256, collectionId: querySha256};
}

export async function supplementPlan(parentRoot, parentPartId) {
  const root = path.resolve(parentRoot), index = await read(path.join(root, 'index.json'));
  await validateParent(root, index);
  const catalog = await read(path.join(root, 'catalog.json')), plan = await read(path.join(root, 'plan.json'));
  const source = plan.parts.find(part => part.id === parentPartId);
  const coverage = index.parts.find(part => part.id === parentPartId);
  assert.ok(source && coverage && !coverage.complete && coverage.limits.capped, 'Parent part is not a capped native collection');
  assert.deepEqual(source.namespaces, ['microblockcbe']);
  assert.deepEqual(source.items, ['microblockcbe:microblock', 'microblockcbe:saw_diamond', 'microblockcbe:saw_iron',
    'microblockcbe:saw_stone', 'microblockcbe:stone_rod']);
  const files = Object.fromEntries(await Promise.all(['index.json', 'catalog.json', 'plan.json'].map(async name =>
    [name, hash(await fs.readFile(path.join(root, name)))])));
  const sessionFile = path.join(root, '..', 'session.json'), sessionBytes = await fs.readFile(sessionFile);
  assert.equal(hash(sessionBytes), index.sourceSessionSha256, 'Parent session binding differs');
  const parentManifest = await read(path.join(root, 'parts', source.id, 'manifest.json'));
  const sourceSignatures = parentManifest.sources.map(source => canonical([source.kind, source.owner])).sort();
  return {schema: 3, kind: 'name-audit-supplement', parent: {root, files, partId: source.id,
    sessionFile, sessionSha256: hash(sessionBytes), sourceSignatures,
    manifestSha256: coverage.manifestSha256, querySha256: source.querySha256}, catalogSha256: catalog.sha256,
    catalogItems: catalog.items.length, selectedNativeItems: source.items.length,
    items: source.items, parts: [
      {id: 'native-' + source.id, coverage: 'native-recollection', ...boundedQuery(source.namespaces, source.items, 150000)},
      {id: 'manual-name-boundaries-v1', coverage: 'manual-fixtures',
        ...boundedQuery(['minecraft'], ['minecraft:stone'], 50000, 'name-boundaries-v1')}
    ]};
}

export function mergePart(index, part, corpus, digest) {
  mergeNativePart(index, part, corpus, digest);
  const entry = index.parts[index.parts.length - 1];
  entry.coverage = part.coverage;
  if (part.coverage === 'manual-fixtures') {
    delete entry.zeroSampleItems;
    entry.plannedCases = corpus.limits.plannedCases;
    entry.caseWitnesses = corpus.sources.map(source => ({case: source.kind.slice('control:manual:'.length),
      serialized: source.serialized, samples: corpus.samples.filter(sample => sample.sources.some(witness => witness.index === source.id)).map(sample => sample.id)}));
  }
}

export async function parentEvidence(session, parent) {
  const bytes = await fs.readFile(parent.sessionFile);
  assert.equal(hash(bytes), parent.sessionSha256);
  const baseline = JSON.parse(bytes), checks = [];
  const check = (name, actual, expected) => checks.push({name, pass: equal(actual, expected), ...(!equal(actual, expected) ? {actual, expected} : {})});
  const mods = value => value.provenance.mods.filter(mod => !['polyglottooltip', 'polyglotdevbridge'].includes(mod.id))
    .map(mod => [mod.id, mod.version, mod.sha256]).sort((a, b) => canonical(a).localeCompare(canonical(b)));
  check('parent native mod bytes', mods(session), mods(baseline));
  check('parent resource packs', session.provenance.packs, baseline.provenance.packs);
  const origins = value => Object.fromEntries(Object.entries(value.provenance.origins).map(([key, origin]) => [key, origin.sha256]));
  check('parent native origins', origins(session), origins(baseline));
  for (const key of ['forge', 'java.runtime.version', 'java.vendor', 'java.home']) check('parent ' + key, session.environment[key], baseline.environment[key]);
  const argumentsOf = value => {
    const bindings = {'clientdevbridge.port': value.config.port, 'clientdevbridge.fixtureNonce': value.config.nonce,
      'cleanroom.relauncher.parent': value.environment['cleanroom.relauncher.parent']};
    for (const [key, expected] of Object.entries(bindings)) assert.deepEqual(value.environment.jvmArguments.filter(argument => argument.startsWith('-D' + key + '=')), ['-D' + key + '=' + expected]);
    return value.environment.jvmArguments.filter(argument => argument !== '-Dclientdevbridge.hudAudit=true')
      .map(argument => Object.keys(bindings).some(key => argument === '-D' + key + '=' + bindings[key]) ? argument.slice(0, argument.indexOf('=') + 1) + '${bound}' : argument);
  };
  check('parent JVM arguments', argumentsOf(session), argumentsOf(baseline));
  const content = value => Object.fromEntries(Object.entries(value.replayInputs).filter(([key]) => !key.startsWith('config/')));
  check('parent scripts and resources', content(session), content(baseline));
  const configuration = value => Object.fromEntries(Object.entries(value.replayInputs).filter(([key]) => key.startsWith('config/')));
  check('parent raw configuration', configuration(session), configuration(baseline));
  return {parentSessionSha256: parent.sessionSha256, checks,
    nativeContentMatches: checks.filter(check => check.name !== 'parent raw configuration').every(check => check.pass),
    rawEvidenceChecksPassed: checks.every(check => check.pass)};
}

export async function validateCorpus(root, index) {
  const plan = await read(path.join(root, 'plan.json'));
  assert.equal(plan.schema, 3);
  assert.ok(equal(plan, await supplementPlan(plan.parent.root, plan.parent.partId)), 'Supplement or parent binding changed');
  assert.ok(equal(await read(path.join(root, 'catalog.json')), await read(path.join(plan.parent.root, 'catalog.json'))));
  assert.equal(index.schema, 3);
  assert.ok(equal(index.parent, plan.parent));
  assert.equal(index.catalogSha256, plan.catalogSha256);
  assert.equal(index.catalogItems, plan.catalogItems);
  assert.equal(index.selectedNativeItems, plan.selectedNativeItems);
  assert.deepEqual(index.parts.map(part => part.id), plan.parts.map(part => part.id));
  const expected = {parts: [], samples: new Map()};
  for (const [number, part] of plan.parts.entries()) {
    const folder = path.join(root, 'parts', part.id);
    assert.ok(equal(await readOptional(path.join(folder, 'query.json')), part));
    const bytes = await fs.readFile(path.join(folder, 'manifest.json'));
    assert.equal(hash(bytes), index.parts[number].manifestSha256);
    const corpus = JSON.parse(bytes);
    assert.equal(corpus.limits.sampleLimit, part.sampleLimit);
    assert.deepEqual(corpus.sources.map(source => source.id), corpus.sources.map((_, i) => i));
    assert.equal(new Set(corpus.samples.map(sample => sample.id)).size, corpus.samples.length);
    for (const sample of corpus.samples) {
      assert.equal(sample.stack?.value?.id?.value, sample.item);
      assert.ok(sample.sources.length > 0);
      for (const witness of sample.sources) assert.ok(corpus.sources[witness.index]);
    }
    const complete = !corpus.limits.capped && corpus.failures.length === 0
      && corpus.sources.every(source => !source.error && source.matched === source.serialized)
      && corpus.samples.every(sample => sample.sources.every(witness => witness.sourceUnchanged));
    assert.equal(corpus.complete, complete);
    if (part.coverage === 'manual-fixtures') {
      assert.ok(corpus.sources.length > 0 && corpus.sources.every(source => source.kind.startsWith('control:manual:')));
      assert.deepEqual(corpus.sources.map(source => source.kind.slice('control:manual:'.length)), corpus.limits.plannedCases);
      for (const source of corpus.sources) {
        assert.ok(source.seedStack && /^[0-9a-f]{64}$/.test(source.seedBinarySha256));
        assert.equal(hash(Buffer.from(source.seedSerialized, 'base64')), source.seedBinarySha256);
        const witnessed = corpus.samples.filter(sample => sample.sources.some(witness => witness.index === source.id));
        if (complete) assert.equal(witnessed.length, 1);
        for (const sample of witnessed) assert.ok(equal(sample.stack, source.fixtureTargetStack));
      }
    } else {
      assert.deepEqual(corpus.sources.map(source => canonical([source.kind, source.owner])).sort(), plan.parent.sourceSignatures,
        'Native source population differs from parent');
    }
    mergePart(expected, part, corpus, hash(bytes));
  }
  assert.ok(equal(expected.parts, index.parts));
  assert.ok(equal([...expected.samples.values()], index.samples));
  assert.equal(index.collectionComplete, expected.parts.every(part => part.complete));
}

export async function coverageReport(root) {
  const index = await read(path.join(root, 'index.json'));
  await validateCorpus(root, index);
  const parent = await read(path.join(index.parent.root, 'index.json'));
  const old = new Set(parent.samples.map(sample => sample.id));
  const native = index.parts.find(part => part.id.startsWith('native-'));
  const manual = index.parts.find(part => part.id.startsWith('manual-'));
  return {schema: 1, parentIndexSha256: index.parent.files['index.json'],
    supplementIndexSha256: hash(await fs.readFile(path.join(root, 'index.json'))),
    parentCollectionComplete: parent.collectionComplete, recollectedPartComplete: native.complete,
    nativeSamples: native.ids.length, nativeOverlap: native.ids.filter(id => old.has(id)).length,
    nativeNewSamples: native.ids.filter(id => !old.has(id)).length, manualSamples: manual.ids.length,
    manualPlannedCases: manual.plannedCases.length, manualWitnessedCases: manual.caseWitnesses.filter(witness => witness.samples.length).length,
    allPossibleNbtStates: false, entityHudCovered: false};
}
