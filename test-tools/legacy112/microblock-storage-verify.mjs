import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {Bridge} from '../name-audit/runner/bridge.mjs';
import {readExport} from './name-export-report.mjs';
import {loadFamilyProfile} from './microblock-family-profile.mjs';

const run = path.resolve(process.argv[2]);
const scope = process.argv[4] ?? 'storage';
const profiles = {
  storage: {states: 204, materials: 17, pattern: /^thermalfoundation:storage(?:_alloy)?\[type=[A-Z]+\]$/,
    tooltips: ['thermalfoundation:storage[type=COPPER]', 'thermalfoundation:storage_alloy[type=ENDERIUM]'],
    mutation: 'thermalfoundation:storage[type=TIN]'},
  'glass-rockwool': {states: 396, materials: 33, pattern: /^thermalfoundation:(?:glass(?:_alloy)?|rockwool)\[type=[A-Z_]+\]$/,
    tooltips: ['thermalfoundation:glass[type=COPPER]', 'thermalfoundation:glass_alloy[type=ENDERIUM]', 'thermalfoundation:rockwool[type=LIGHT_BLUE]'],
    mutation: 'thermalfoundation:glass[type=TIN]'},
};
const cases = JSON.parse(await fs.readFile(path.resolve(process.argv[3]), 'utf8'));
const profile = scope === 'family' ? await loadFamilyProfile(path.resolve(process.argv[5]), cases) : profiles[scope];
assert.ok(profile, 'Select one bounded, reviewed material group');
assert.equal(cases.length, profile.states);
assert.equal(new Set(cases.map(value => value.material)).size, profile.materials);
assert.ok(cases.every(value => profile.pattern.test(value.material)
  && value.baselineStatus === 'unresolved' && Object.keys(value.expected).length === 2));
const previous = scope === 'family' ? profile.previous
  : scope === 'glass-rockwool' ? JSON.parse(await fs.readFile(path.resolve(process.argv[5]), 'utf8')) : [];
if (scope === 'glass-rockwool') {
  assert.equal(previous.length, 8);
  assert.ok(previous.every(value => profiles.storage.tooltips.includes(value.material) && [1, 258, 516, 769].includes(value.metadata)));
}
const config = JSON.parse(await fs.readFile(path.join(run, 'config.json'), 'utf8'));
const owner = JSON.parse(await fs.readFile(path.join(run, 'process.json'), 'utf8'));
const save = (name, value) => fs.writeFile(path.join(run, name + '.json'), JSON.stringify(value, null, 2), {flag: 'wx'});
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const plain = value => value.replace(/§./g, '');
const sample = value => ({item: value.item, metadata: value.metadata, nbt: '{mat:' + JSON.stringify(value.material) + '}'});
const auditSample = value => ({id: value.sampleId, serialized: value.serialized});
let bridge, stage = 'launch';
const call = (method, params = {}) => bridge.call(method, params, 120000);
const progress = async details => {
  const result = {at: new Date().toISOString(), stage, ...details};
  await fs.writeFile(path.join(run, 'progress.json'), JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
};
const ready = async () => {
  for (let count = 0; count < 1200; count++) {
    const state = await call('probe.performance', {action: 'state'});
    if (state.ready && !state.libraryPending) return state;
    await pause(250);
  }
  throw new Error('Language readiness deadline');
};
const exported = async (label, samples) => {
  await call('probe.export', {action: 'start', samples, mode: 'batch'});
  for (let count = 0; count < 3000; count++) {
    const state = await call('probe.export', {action: 'status'});
    if (state.closed) {
      assert.equal(state.state, 'COMPLETE');
      assert.ok(path.resolve(state.directory).toLowerCase().startsWith(path.resolve(config.fixture, 'exports/polyglot-names').toLowerCase() + path.sep));
      const directory = path.join(run, label);
      await fs.cp(state.directory, directory, {recursive: true, force: false, errorOnExist: true});
      const report = await readExport(directory);
      assert.ok(report.integrityValid, report.errors.join('; '));
      await save(label + '-status', state);
      const rows = (await fs.readFile(path.join(directory, 'names.jsonl'), 'utf8')).trim().split('\n').map(JSON.parse);
      assert.equal(rows.length, samples.length);
      assert.ok(rows.every(row => row.stateChanged === false && !row.error));
      return rows;
    }
    await pause(25);
  }
  throw new Error('Export deadline: ' + label);
};
const comparisons = [], previousComparisons = [], failures = [];
const compareRows = (rows, batch, output) => {
  for (let index = 0; index < rows.length; index++) {
    const row = rows[index], expected = batch[index];
    assert.equal(row.sampleId, expected.sampleId); assert.equal(row.nativeName, expected.primary);
    assert.equal(row.serialized, expected.serialized); assert.deepEqual(row.stack, expected.stack);
    for (const [language, name] of Object.entries(expected.expected)) {
      const actual = row.languages.find(value => value.language === language);
      const comparison = {sampleId: row.sampleId, material: expected.material, metadata: expected.metadata,
        language, expected: name, actual: actual?.name, status: actual?.status, reason: actual?.reason};
      output.push(comparison);
      if (actual?.status !== 'translated' || actual?.name !== name) failures.push(comparison);
    }
  }
};
try {
  let menu = false;
  for (let count = 0; count < 600; count++) {
    try {
      bridge ??= await Bridge.connect(config.port);
      const state = await bridge.call('status', {}, 15000);
      assert.equal(String(state.pid), String(owner.pid)); assert.equal(state.fixtureNonce, config.nonce);
      assert.equal(path.resolve(state.gameDirectory).toLowerCase(), path.resolve(config.fixture).toLowerCase());
      if (['net.minecraft.client.gui.GuiMainMenu', 'lumien.custommainmenu.gui.GuiCustom'].includes(state.screen)) {
        await save('menu', state); menu = true; break;
      }
    } catch (error) { bridge?.close(); bridge = null; }
    await pause(2000);
  }
  assert.ok(menu, 'Menu deadline');
  await call('pack.setup'); await call('world.create', {name: config.worldName});
  await call('wait.ticks', {ticks: 60}); await call('screen.close');
  assert.equal((await call('status')).language, 'zh_tw');
  await call('config.set', {languages: ['en_us', 'zh_cn']}); await ready();
  await save('initial-runtime', await call('pack.runtime'));

  if (scope === 'family') {
    stage = 'material-dependencies';
    const materials = [...new Set(cases.map(value => value.material))], diagnostics = [];
    for (let offset = 0; offset < materials.length; offset += 32) {
      const batch = materials.slice(offset, offset + 32);
      const result = await call('probe.export', {action: 'microblockMaterials', materials: batch});
      assert.deepEqual(result.rows.map(value => value.material), batch); diagnostics.push(...result.rows);
    }
    await save('material-diagnostics', diagnostics);
    assert.ok(diagnostics.every(value => value.stage === 'capture-ready'),
      JSON.stringify(diagnostics.filter(value => value.stage !== 'capture-ready').slice(0, 3)));
  }

  stage = 'fixed-materials';
  for (let offset = 0; offset < cases.length; offset += 16) {
    const batch = cases.slice(offset, offset + 16);
    const rows = await exported('fixed-' + String(offset / 16).padStart(2, '0'), batch.map(sample));
    compareRows(rows, batch, comparisons);
    await progress({states: offset + batch.length, failures: failures.length});
  }
  await save('comparisons', comparisons);
  await save('contracts', await call('audit.contracts'));
  assert.equal(failures.length, 0, JSON.stringify(failures.slice(0, 3)));

  if (previous.length) {
    stage = 'previous-materials';
    for (let offset = 0; offset < previous.length; offset += 16) {
      const batch = previous.slice(offset, offset + 16);
      compareRows(await exported('previous-materials-' + offset, batch.map(sample)), batch, previousComparisons);
    }
    await save('previous-comparisons', previousComparisons);
    assert.equal(failures.length, 0);
  }

  stage = 'controls';
  const micro = (material, metadata = 1, extra = {}) => ({item: 'microblockcbe:microblock', metadata, nbt: '{mat:' + JSON.stringify(material) + '}', ...extra});
  const controls = [
    micro('minecraft:stone[variant=stone]'), micro('minecraft:stone[variant=granite]', 260),
    micro('redstonearsenal:storage[type=ELECTRUM_FLUX]'), micro('redstonearsenal:storage[type=CRYSTAL_FLUX]'),
    micro('thermalfoundation:storage[type=COPPER]', 1, {name: '100% Custom Microblock'}),
    {item: 'thermalfoundation:ore'}, {item: 'minecraft:stone'},
    ...(profile.negativeMaterials ?? []).map(value => micro(value)),
  ];
  const rows = await exported('controls', controls);
  for (const index of [0, 1, 5, 6]) assert.ok(rows[index].languages.every(value => value.status === 'translated'));
  for (const index of [2, 3]) assert.ok(rows[index].languages.every(value => value.status === 'unresolved'));
  for (let index = 7; index < controls.length; index++) assert.ok(rows[index].languages.every(value => value.status === 'unresolved'));
  assert.ok(rows[4].languages.every(value => value.status === 'literal' && value.name === '100% Custom Microblock'));

  stage = 'tooltips';
  const selected = cases.filter(value => [1, 258, 516, 769].includes(value.metadata)
    && profile.tooltips.includes(value.material));
  assert.equal(selected.length, profile.tooltips.length * 4);
  let tooltipChecks = 0;
  for (const mode of ['NORMAL', 'ADVANCED']) {
    const result = await call('audit.observe', {samples: selected.map(auditSample), tooltipFlag: mode, repetitions: 2});
    await save('tooltips-' + mode.toLowerCase(), result);
    assert.equal(result.rows.length, selected.length * 2);
    assert.equal(result.tooltipFlag, mode);
    for (const row of result.rows) {
      const expected = selected.find(value => value.sampleId === row.id);
      assert.ok(!row.error && !row.captureFailed);
      assert.deepEqual(row.before, row.after);
      assert.equal(row.nameObservation.nativeName, expected.primary);
      const created = row.created.filter(value => value.rootList && value.tooltipFrames === 1).map(value => plain(value.text)).join('\n');
      for (const value of Object.values(expected.expected)) assert.ok(created.includes(plain(value)), JSON.stringify(row));
      tooltipChecks++;
    }
  }
  stage = 'guards';
  const guards = await call('audit.guardChecks', {sample: auditSample(selected[0]), tooltipFlag: 'NORMAL',
    mutationKey: 'mat', mutationValue: profile.mutation});
  await save('guards', guards);
  assert.ok(guards.positive.length > guards.base.length);
  for (const key of ['positiveCurrent', 'beforeReplacementCurrent', 'afterReplacementCurrent',
    'beforeReplacementAfterCurrent', 'afterReplacementAfterCurrent',
    'nbtBeforeCurrent', 'generationBeforeCurrent', 'freshCurrent']) assert.equal(guards[key], true, key);
  for (const key of ['nbtChangedCurrent', 'nbtRestoredCurrent', 'generationChangedCurrent'])
    assert.equal(guards[key], false, key);
  assert.deepEqual(guards.nbtChanged, guards.base);
  assert.deepEqual(guards.generationChanged, guards.base);
  assert.deepEqual(guards.beforeEventReplacement, guards.replacementBase);
  assert.deepEqual(guards.afterEventReplacement, guards.replacementBase);
  assert.ok(guards.nativeWritePreserved && guards.newGenerationPreserved);
  // ConsumedNameReceipt.current permanently invalidates a stale snapshot; restoring data must not revive it.
  assert.equal(guards.restoredCurrent, false);
  const fresh = await call('audit.observe', {samples: [auditSample(selected[0])], tooltipFlag: 'NORMAL', repetitions: 2});
  await save('fresh-after-guards', fresh);
  assert.equal(fresh.rows.length, 2);
  for (const row of fresh.rows) {
    assert.ok(!row.error && !row.captureFailed && row.productNameObservation.current);
    assert.deepEqual(row.before, row.after);
    for (const expected of Object.values(selected[0].expected)) assert.ok(row.created.some(value => plain(value.text).includes(plain(expected))));
  }
  if (profile.woodGuards) {
    const checks = [];
    for (const material of profile.tooltips) {
      const value = selected.find(sample => sample.material === material);
      const result = await call('probe.export', {action: 'microblockWoodGuards', sample: auditSample(value)});
      assert.ok(result.stackUnchanged);
      assert.deepEqual(result.checks.map(check => check.field), ['fireproof', 'blockNumber']);
      assert.ok(result.checks.every(check => !check.changedCurrent && !check.restoredCurrent && check.freshCurrent && check.fieldRestored));
      checks.push({material, ...result});
    }
    await save('wood-dependency-guards', checks);
  }
  await save('final-runtime', await call('pack.runtime'));
  await save('verification', {passed: true, scope, states: cases.length, materialCount: profile.materials, languageComparisons: comparisons.length,
    previousStates: previous.length, previousLanguageComparisons: previousComparisons.length,
    tooltipChecks, freshTooltipChecks: fresh.rows.length, controls: controls.length, guards: 'passed', fullPackInventory: false,
    baseline: 'Frozen confirmed gaps and independent native names from the prior inventory; all input identities matched.'});
  stage = 'complete'; await progress({passed: true}); await call('quit');
} catch (error) {
  await save('verification-failure', {stage, message: error.message, stack: error.stack, failures});
  console.error(error.stack); process.exitCode = 1;
} finally { bridge?.close(); }
