import test from 'node:test';
import assert from 'node:assert/strict';
import {hash} from './name-audit-core.mjs';
import {environmentKey, observationKey, comparisonKey, assessNativeReuse, selectContractCases} from './name-audit-baseline.mjs';

function fixture() {
  const files = new Map(), evidence = [];
  const add = (path, value, role) => {
    const bytes = Buffer.from(JSON.stringify(value)); files.set(path, bytes);
    const entry = {path, sha256: hash(bytes), ...(role ? {role} : {})};
    if (role) evidence.push(entry);
    return entry;
  };
  const environment = Object.fromEntries(['game', 'loader', 'java', 'dependencies', 'mods', 'embeddedJars',
    'transformConfigurationAndOrder', 'effectiveMethods', 'observer', 'scripts', 'configuration',
    'generatedInputs', 'registry', 'world', 'resourceProvidersInOrder', 'language', 'selectiveReload',
    'fontSettings'].map(field => [field, add(field, {field, complete: true, entries: ['fixed']}, 'environment.' + field).sha256]));
  const source = add('source', {closedMethods: ['display', 'lookup']}, 'source');
  const state = add('state', {tick: 0, randomUsed: false}, 'state');
  const generation = add('generation', {resource: 1, language: 1}, 'generation');
  const specimen = {typedStack: {type: 'compound', value: {Count: {type: 'byte', value: 1}}},
    tooltipFlag: 'NORMAL', modifiers: {shift: false, control: false}, callOrdinal: 1,
    history: [{kind: 'fresh-object', process: 'already-indexed'}], generation: generation.sha256,
    ui: {screen: 'inventory'}, query: {text: ''}, determinism: {kind: 'source-closed',
      sourceSha256: source.sha256, externalStateSha256: state.sha256}};
  const row = {before: specimen.typedStack, after: specimen.typedStack, tooltipFlag: 'NORMAL',
    modifiers: specimen.modifiers, captureFailed: false, tooltip: ['Stone'], created: [],
    nameObservation: {nativeName: 'Stone', initialTitle: 'Stone'}};
  const session = add('session', {pid: '123', config: {nonce: 'test-nonce', product: false}, identity: {product: false}}, 'session');
  const artifact = {complete: true, observationSchema: 'names-v3', sessionSha256: session.sha256,
    rows: [{case: specimen, observation: row}]};
  const descriptor = {...add('rows', artifact), rows: 1};
  const candidate = {schema: 1, complete: true, independentProcess: true, productPresent: false,
    provenance: {pid: '123', nonce: 'test-nonce', sessionSha256: session.sha256},
    observationSchema: 'names-v3', environment, evidence, files: [descriptor], rows: 1};
  const request = {observationSchema: 'names-v3', environment: structuredClone(environment),
    cases: [structuredClone(specimen)], requiredFields: ['nameObservation.nativeName', 'nameObservation.initialTitle']};
  return {candidate, request, files, specimen, artifact, read: async path => files.get(path)};
}

test('reuse reads exact independent observations and every fingerprint witness', async () => {
  const f = fixture();
  assert.equal((await assessNativeReuse(f.candidate, f.request, f.read)).reusable, true);
  f.candidate.evidence = f.candidate.evidence.filter(entry => entry.role !== 'environment.java');
  assert.equal((await assessNativeReuse(f.candidate, f.request, f.read)).reason, 'environment-evidence-missing');
});

test('each native environment dimension invalidates reuse', async () => {
  for (const field of Object.keys(fixture().request.environment)) {
    const f = fixture(); f.request.environment[field] = hash('changed-' + field);
    assert.equal((await assessNativeReuse(f.candidate, f.request, f.read)).reason, 'environment-changed', field);
    delete f.request.environment[field];
    assert.equal((await assessNativeReuse(f.candidate, f.request, f.read)).reusable, false, field);
  }
});

test('typed values, ordered history, call, flags and UI are distinct cases', async () => {
  const mutations = [s => s.typedStack.value.Count.type = 'int', s => s.tooltipFlag = 'ADVANCED',
    s => s.modifiers.shift = true, s => s.callOrdinal = 2, s => s.history.push({kind: 'same-object'}),
    s => s.generation = hash('later'), s => s.ui.screen = 'recipe', s => s.query.text = 'stone'];
  for (const mutate of mutations) {
    const f = fixture(); const previous = observationKey(f.request.cases[0]); mutate(f.request.cases[0]);
    assert.notEqual(observationKey(f.request.cases[0]), previous);
    assert.equal((await assessNativeReuse(f.candidate, f.request, f.read)).reason, 'case-coverage-missing');
  }
});

test('unknown time or RNG and missing case history never reuse', async () => {
  for (const determinism of [undefined, {kind: 'unknown'}, {kind: 'seeded', seed: 10}]) {
    const f = fixture(); f.request.cases[0].determinism = determinism;
    assert.equal((await assessNativeReuse(f.candidate, f.request, f.read)).reusable, false);
  }
  const f = fixture(); f.request.cases[0].history = [];
  assert.equal((await assessNativeReuse(f.candidate, f.request, f.read)).reason, 'case-history');
});

test('non-JSON values cannot collide with ordinary case state', () => {
  for (const value of [NaN, Infinity, undefined, new Date(), new Map(), () => 1, Array(1), Object.assign([], {extra: 1}),
    Object.defineProperty({}, 'hidden', {value: 1})]) {
    const f = fixture(); f.specimen.ui.value = value;
    assert.throws(() => observationKey(f.specimen), /non-json/);
  }
  const f = fixture(); f.specimen.ui.loop = f.specimen;
  assert.throws(() => observationKey(f.specimen), /non-json/);
  let called = false;
  const accessor = fixture();
  Object.defineProperty(accessor.specimen.ui, 'read', {enumerable: true, get() { called = true; return 1; }});
  assert.throws(() => observationKey(accessor.specimen), /non-json/);
  assert.equal(called, false);
  const environment = fixture().request.environment;
  Object.defineProperty(environment, 'game', {enumerable: true, get() { called = true; return hash('game'); }});
  assert.throws(() => environmentKey(environment), /non-json/);
  assert.equal(called, false);
});

test('corruption, stale schema, absent fields, skipped and incomplete receipts fail closed', async () => {
  const mutations = [f => f.files.set('rows', Buffer.from('{}')), f => f.request.observationSchema = 'names-v4',
    f => f.request.requiredFields.push('new.capture'), f => f.candidate.complete = false,
    f => f.candidate.rows = 2, f => f.candidate.productPresent = true,
    f => f.candidate.independentProcess = false, f => f.candidate.provenance.pid = '999'];
  for (const mutate of mutations) {
    const f = fixture(); mutate(f);
    assert.equal((await assessNativeReuse(f.candidate, f.request, f.read)).reusable, false);
  }
  for (const mutate of [a => a.complete = false, a => a.rows[0].observation.error = 'failed',
    a => a.rows[0].observation.created.push('alias'), a => a.rows[0].observation.captureFailed = true,
    a => a.rows[0].observation.observationAttempted = false,
    a => a.rows.push(structuredClone(a.rows[0]))]) {
    const f = fixture(); mutate(f.artifact);
    const bytes = Buffer.from(JSON.stringify(f.artifact)); f.files.set('rows', bytes);
    f.candidate.files[0].sha256 = hash(bytes); f.candidate.files[0].rows = f.artifact.rows.length;
    assert.equal((await assessNativeReuse(f.candidate, f.request, f.read)).reusable, false);
  }
});

test('product and comparator edits change comparisons without invalidating native keys', () => {
  const f = fixture(), nativeKey = environmentKey(f.request.environment);
  const input = {nativeEnvironment: f.request.environment, cases: f.request.cases, productArtifact: hash('product'),
    productConfiguration: hash('config'), productTransforms: hash('transforms'), comparator: hash('comparator')};
  for (const key of ['productArtifact', 'productConfiguration', 'productTransforms', 'comparator']) {
    assert.notEqual(comparisonKey({...input, [key]: hash('next')}), comparisonKey(input));
    assert.equal(environmentKey(f.request.environment), nativeKey);
  }
});

test('contract selection preserves each ordered branch and reports uncovered requirements', () => {
  const {specimen} = fixture();
  const cases = [
    {id: 'first-normal', specimen, contracts: ['first', 'style']},
    {id: 'same-object-normal', specimen: {...specimen, callOrdinal: 2}, contracts: ['warm']},
    {id: 'reversed-lens', specimen, contracts: ['composition-reversed']},
    {id: 'advanced', specimen: {...specimen, tooltipFlag: 'ADVANCED'}, contracts: ['style']}
  ];
  const selection = selectContractCases(cases, ['first', 'warm', 'style', 'composition-reversed']);
  assert.equal(selection.complete, true); assert.deepEqual(selection.selected, cases);
  assert.deepEqual(selectContractCases(cases, ['style', 'reload']).missing, ['reload']);
  assert.throws(() => selectContractCases([...cases, cases[0]], ['style']), /duplicate-case-id/);
});
