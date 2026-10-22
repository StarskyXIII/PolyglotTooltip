import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {Bridge} from '../name-audit/runner/bridge.mjs';
import {readExport} from './name-export-report.mjs';

const run = path.resolve(process.argv[2]);
const sourceRun = path.resolve(process.argv[3]);
const config = JSON.parse(await fs.readFile(path.join(run, 'config.json'), 'utf8'));
const owner = JSON.parse(await fs.readFile(path.join(run, 'process.json'), 'utf8'));
const sourceIndex = JSON.parse(await fs.readFile(path.join(sourceRun, 'catalog-index.json'), 'utf8'));
const sourceDirectory = sourceIndex.chunks.find(part => part.label === 'catalog-003').state.directory;
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const save = (name, value) => fs.writeFile(path.join(run, name + '.json'), JSON.stringify(value, null, 2), {flag: 'wx'});
const results = {}, comparisons = [];
let bridge, stage = 'launch';
const call = (method, params = {}) => bridge.call(method, params, 120000);
const progress = async value => {
  const entry = {at: new Date().toISOString(), stage, ...value};
  await fs.writeFile(path.join(run, 'progress.json'), JSON.stringify(entry, null, 2));
  console.log(JSON.stringify(entry));
};
const ready = async () => {
  for (let i = 0; i < 1200; i++) {
    const state = await call('probe.performance', {action: 'state'});
    if (state.ready && !state.libraryPending) return state;
    await pause(250);
  }
  throw new Error('Language readiness deadline');
};
const collect = async label => {
  stage = label;
  for (let i = 0; i < 3000; i++) {
    const state = await call('probe.export', {action: 'status'});
    if (state.closed) {
      assert.ok(path.resolve(state.directory).toLowerCase().startsWith(path.resolve(config.fixture, 'exports/polyglot-names').toLowerCase() + path.sep));
      const directory = path.join(run, label);
      await fs.cp(state.directory, directory, {recursive: true, force: false, errorOnExist: true});
      const report = await readExport(directory);
      assert.ok(report.integrityValid, report.errors.join('; '));
      const prefix = state.state === 'COMPLETE' ? 'names' : 'names.partial';
      const lines = await fs.readFile(path.join(directory, prefix + '.jsonl'), 'utf8');
      const rows = lines.trim().split('\n').filter(Boolean).map(JSON.parse);
      const manifest = JSON.parse(await fs.readFile(path.join(directory, 'manifest.json'), 'utf8'));
      assert.equal(manifest.mode, state.mode);
      assert.equal(manifest.maxSourceStepsPerTick, state.mode === 'batch' ? 512 : 8);
      assert.equal(manifest.softBudgetMicros, state.mode === 'batch' ? 20000 : 4000);
      const tsv = await fs.readFile(path.join(directory, prefix + '.tsv'), 'utf8');
      await save(label + '-status', state); await save(label + '-report', report);
      results[label] = {mode: state.mode, state: state.state, rows: rows.length, duplicates: state.duplicates,
        sourceExhausted: state.sourceExhausted, elapsedMillis: state.elapsedMillis};
      await progress(results[label]);
      return {state, rows, tsv, manifest};
    }
    await pause(10);
  }
  throw new Error('Focused export deadline: ' + label);
};
const comparable = rows => rows.map(({elapsedMicros, ...row}) => row);
const equal = (left, right, label) => {
  assert.deepEqual(comparable(left.rows), comparable(right.rows), label + ' JSON rows');
  assert.equal(left.tsv, right.tsv, label + ' TSV');
  for (const key of ['state', 'processed', 'written', 'duplicates', 'sourceExhausted'])
    assert.equal(left.state[key], right.state[key], label + ' ' + key);
  for (const key of ['primaryLanguage', 'languages', 'generation', 'nativeNameEpoch', 'resourceGeneration', 'resourcePacks'])
    assert.deepEqual(left.manifest[key], right.manifest[key], label + ' ' + key);
  comparisons.push({label, rows: left.rows.length, exactExceptElapsedMicros: true, exactTsv: true});
};
const sampleControl = [
  {item: 'minecraft:stone'}, {item: 'minecraft:stone', count: 2},
  {item: 'minecraft:stone', name: 'Batch verification'},
  {item: 'minecraft:potion', nbt: '{Potion:"minecraft:healing"}'},
  {item: 'appliedenergistics2:facade', nbt: '{item:"minecraft:stone",damage:1}'},
  {item: 'minecraft:enchanted_book', nbt: '{StoredEnchantments:[{id:16s,lvl:3s}]}'},
];
try {
  let menu = false;
  for (let i = 0; i < 600; i++) {
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

  const control = [];
  for (const mode of ['interactive', 'batch']) {
    await call('probe.export', {action: 'start', samples: sampleControl, mode});
    control.push(await collect('control-' + mode));
  }
  equal(control[0], control[1], 'six-diverse-stacks');
  for (const [index, params] of [
    {source: 'defaults'}, {source: 'creative', mode: 'batch'}, {source: 'jei', mode: 'batch', omitLimit: true}, {source: 'creative'},
  ].entries()) {
    const start = await call('probe.export', {action: 'catalog', item: 'minecraft:stone', ...params});
    assert.equal(start.commandResult, 1);
    const output = await collect('command-' + index);
    assert.equal(output.state.state, 'COMPLETE'); assert.equal(output.state.mode, params.mode ?? 'interactive');
  }
  // Alternate order to expose cache/warmup bias. Limits deliberately remain partial, not complete.
  const catalog = [];
  for (const [index, mode] of ['interactive', 'batch', 'batch', 'interactive'].entries()) {
    await call('probe.export', {action: 'catalog', source: 'creative', item: 'microblockcbe:microblock', limit: 64, mode});
    const output = await collect('catalog-' + index + '-' + mode);
    assert.equal(output.state.state, 'LIMIT_REACHED'); assert.equal(output.rows.length, 64);
    if (catalog.length) equal(catalog[0], output, 'catalog-' + index);
    catalog.push(output);
  }
  await save('prepared-samples', await call('probe.exportBatch', {action: 'prepare', directory: sourceDirectory,
    item: 'microblockcbe:microblock', count: 128}));
  const replay = [];
  for (const [index, mode] of ['interactive', 'batch', 'batch', 'interactive'].entries()) {
    await call('probe.exportBatch', {action: 'start', repeats: 16, mode});
    const output = await collect('duplicates-' + index + '-' + mode);
    assert.equal(output.state.state, 'COMPLETE'); assert.equal(output.rows.length, 128);
    assert.equal(output.state.duplicates, 1920);
    if (replay.length) equal(replay[0], output, 'duplicates-' + index);
    replay.push(output);
  }
  await call('probe.exportBatch', {action: 'start', repeats: 1, mode: 'batch', paused: true});
  await call('wait.ticks', {ticks: 5});
  const paused = await call('probe.export', {action: 'status'});
  assert.equal(paused.state, 'PAUSED'); assert.equal(paused.processed, 0);
  await call('probe.export', {action: 'resume'});
  const resumed = await collect('batch-resumed');
  assert.deepEqual(comparable(resumed.rows), comparable(replay[0].rows)); assert.equal(resumed.state.state, 'COMPLETE');
  await call('probe.exportBatch', {action: 'start', mode: 'batch', paused: true});
  await call('probe.export', {action: 'cancel'});
  const cancelled = await collect('batch-cancelled');
  assert.equal(cancelled.state.state, 'CANCELLED'); assert.equal(cancelled.rows.length, 0);
  await call('probe.exportBatch', {action: 'start', mode: 'batch', paused: true});
  await call('config.set', {languages: ['zh_cn', 'en_us']});
  const invalidated = await collect('batch-invalidated');
  assert.equal(invalidated.state.state, 'INVALIDATED'); assert.equal(invalidated.rows.length, 0);
  await call('config.set', {languages: ['en_us', 'zh_cn']}); await ready();
  const average = (values, mode) => values.filter(value => value.state.mode === mode)
    .reduce((sum, value) => sum + value.state.elapsedMillis, 0) / 2;
  const performance = Object.fromEntries([['catalog64', catalog], ['duplicates128x16', replay]].map(([key, values]) => {
    const interactiveMillis = average(values, 'interactive'), batchMillis = average(values, 'batch');
    assert.ok(batchMillis < interactiveMillis, key + ' did not improve');
    return [key, {interactiveMillis, batchMillis, speedup: interactiveMillis / batchMillis, order: 'interactive,batch,batch,interactive'}];
  }));
  await save('final-runtime', await call('pack.runtime'));
  await save('verification', {passed: true, nameOnly: true, fullPackInventory: false, comparisons, results, performance,
    limits: 'One exact catalog ID, 64 accepted rows; one bounded snapshot of 128 states replayed 16 times. Six diverse controls. Runtime results exclude elapsedMicros only; TSV matches byte-for-byte.'});
  stage = 'complete'; await progress({passed: true, performance}); await call('quit');
} catch (error) {
  await save('verification-failure', {stage, message: error.message, stack: error.stack, results});
  console.error(error.stack); process.exitCode = 1;
} finally { bridge?.close(); }
