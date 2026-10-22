import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {Bridge} from '../name-audit/runner/bridge.mjs';
import {readExport} from './name-export-report.mjs';

const run = path.resolve(process.argv[2]);
const config = JSON.parse(await fs.readFile(path.join(run, 'config.json'), 'utf8'));
const owner = JSON.parse(await fs.readFile(path.join(run, 'process.json'), 'utf8'));
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const save = (name, value) => fs.writeFile(path.join(run, name + '.json'), JSON.stringify(value, null, 2), {flag: 'wx'});
const samples = [
  {item: 'minecraft:stone'}, {item: 'minecraft:stone', count: 2},
  {item: 'minecraft:stone', name: 'Survey marker'},
  {item: 'minecraft:potion', nbt: '{Potion:"minecraft:healing"}'},
  {item: 'minecraft:potion', nbt: '{Potion:"minecraft:swiftness"}'},
  {item: 'appliedenergistics2:part', metadata: 0},
  {item: 'appliedenergistics2:facade', nbt: '{item:"minecraft:stone",damage:1}'},
  {item: 'libvulpes:productdust', metadata: 3},
  {item: 'extendedcrafting:singularity', metadata: 1},
  {item: 'tconstruct:pickaxe', materials: ['iron', 'iron', 'iron']},
  {item: 'appliedenergistics2:facade'},
  {item: 'minecraft:enchanted_book', nbt: '{StoredEnchantments:[{id:16s,lvl:3s}]}'},
];
let bridge;
const results = {};
const call = (method, params = {}) => bridge.call(method, params, 120000);
const ready = async () => {
  for (let i = 0; i < 600; i++) {
    const state = await call('probe.performance', {action: 'state'});
    if (state.ready && !state.libraryPending) return state;
    await pause(250);
  }
  throw new Error('Language readiness deadline');
};
const closed = async label => {
  for (let i = 0; i < 600; i++) {
    const state = await call('probe.export', {action: 'status'});
    if (state.closed) {
      const destination = path.join(run, label);
      assert.equal(path.resolve(state.directory).toLowerCase().startsWith(path.resolve(config.fixture, 'exports/polyglot-names').toLowerCase() + path.sep), true);
      await fs.cp(state.directory, destination, {recursive: true, errorOnExist: true, force: false});
      const report = await readExport(destination);
      await save(label + '-report', report); await save(label + '-status', state);
      assert.equal(report.integrityValid, true, JSON.stringify(report.errors));
      results[label] = {state: state.state, processed: state.processed, duplicates: state.duplicates, counts: report.counts, selectedSourceComplete: report.selectedSourceComplete};
      return {state, report, rows: (await fs.readFile(path.join(destination, state.state === 'COMPLETE' ? 'names.jsonl' : 'names.partial.jsonl'), 'utf8')).trim().split('\n').filter(Boolean).map(JSON.parse)};
    }
    await pause(100);
  }
  throw new Error('Export completion deadline');
};
try {
  let menu = false;
  for (let i = 0; i < 450; i++) {
    try {
      bridge ??= await Bridge.connect(config.port);
      const state = await bridge.call('status', {}, 15000);
      assert.equal(String(state.pid), String(owner.pid)); assert.equal(state.fixtureNonce, config.nonce);
      assert.equal(path.resolve(state.gameDirectory).toLowerCase(), path.resolve(config.fixture).toLowerCase());
      if (['net.minecraft.client.gui.GuiMainMenu', 'lumien.custommainmenu.gui.GuiCustom'].includes(state.screen)) { await save('menu', state); menu = true; break; }
    } catch (error) { bridge?.close(); bridge = null; }
    await pause(2000);
  }
  assert.equal(menu, true, 'Menu deadline');
  await call('pack.setup'); await call('world.create', {name: 'pt112-e2e-compatibility-final'});
  await call('wait.ticks', {ticks: 60}); await call('screen.close');
  // Independent native baselines are collected before starting an export and use no secondary-name resolver.
  const baseline = {};
  for (const language of ['en_us', 'zh_cn', 'zh_tw']) {
    await call('language.set', {language}); await call('config.set', {languages: ['en_us', 'zh_cn']}); await ready();
    baseline[language] = (await call('probe.export', {action: 'native', samples})).rows;
  }
  await save('native-baselines', baseline); await save('samples', samples);
  await call('probe.export', {action: 'start', samples: [...samples, samples[0]], paused: true});
  await call('wait.ticks', {ticks: 8});
  const paused = await call('probe.export', {action: 'status'});
  assert.equal(paused.state, 'PAUSED'); assert.equal(paused.processed, 0);
  await call('probe.export', {action: 'resume'});
  const main = await closed('samples');
  assert.equal(main.state.state, 'COMPLETE'); assert.equal(main.rows.length, samples.length); assert.equal(main.state.duplicates, 1);
  assert.notEqual(main.rows[0].sampleId, main.rows[1].sampleId); assert.notEqual(main.rows[3].sampleId, main.rows[4].sampleId);
  const comparisons = [];
  for (const [index, row] of main.rows.entries()) {
    assert.equal(row.nativeName, baseline.zh_tw[index].name);
    for (const value of row.languages) {
      assert.ok(['translated', 'same-as-native', 'literal'].includes(value.status), `${row.item} ${value.language}: ${value.status} ${value.reason}`);
      assert.equal(value.name, baseline[value.language][index].name, `${row.item} ${value.language}`);
      comparisons.push({index, item: row.item, language: value.language, match: true});
    }
  }
  await save('native-comparisons', comparisons);
  for (const source of ['defaults', 'creative', 'jei']) {
    const start = await call('probe.export', {action: 'catalog', source, item: 'minecraft:stone'});
    assert.equal(start.commandResult, 1);
    const output = await closed(source); assert.equal(output.state.state, 'COMPLETE'); assert.equal(output.report.selectedSourceComplete, true);
    assert.ok(output.rows.length >= 1); assert.ok(output.rows.every(row => row.item === 'minecraft:stone'));
  }
  await call('probe.export', {action: 'start', samples, limit: 2});
  const limited = await closed('limited'); assert.equal(limited.state.state, 'LIMIT_REACHED'); assert.equal(limited.rows.length, 2);
  await call('probe.export', {action: 'start', samples, paused: true});
  await call('probe.export', {action: 'cancel'});
  const cancelled = await closed('cancelled'); assert.equal(cancelled.state.state, 'CANCELLED'); assert.equal(cancelled.rows.length, 0);
  await call('probe.export', {action: 'start', samples, paused: true});
  await call('config.set', {languages: ['zh_tw', 'en_us']});
  const invalid = await closed('invalidated'); assert.equal(invalid.state.state, 'INVALIDATED'); assert.equal(invalid.rows.length, 0);
  const state = await ready(); await save('primary-in-targets', state);
  await call('probe.export', {action: 'start', samples: [samples[0]]});
  const hidden = await closed('primary-in-targets'); assert.equal(hidden.state.state, 'COMPLETE');
  assert.equal(hidden.rows[0].languages.length, 2);
  assert.equal(hidden.rows[0].languages.find(value => value.language === 'en_us').name, baseline.en_us[0].name);
  await call('probe.export', {action: 'start', samples: [{item: 'minecraft:stone', invalidNbt: 'cycle'}, samples[0]]});
  const invalidNbt = await closed('invalid-nbt');
  assert.equal(invalidNbt.state.state, 'COMPLETE'); assert.equal(invalidNbt.rows.length, 2);
  assert.equal(invalidNbt.report.selectedSourceComplete, false);
  assert.equal(invalidNbt.report.mods.minecraft.sourceErrors, 1);
  assert.equal(invalidNbt.rows[0].item, 'minecraft:stone');
  const errorTsv = (await fs.readFile(path.join(run, 'invalid-nbt/names.tsv'), 'utf8')).split('\n')[1].split('\t');
  assert.equal(errorTsv.length, 12); assert.equal(errorTsv[2], 'minecraft');
  assert.equal(errorTsv[10], 'source-error'); assert.match(errorTsv[11], /Cyclic/);
  await save('runtime', await call('pack.runtime'));
  await save('verification', {passed: true, distinctSamples: samples.length, comparisons: comparisons.filter(value => value.match).length, unresolved: comparisons.filter(value => !value.match), results});
  console.log(JSON.stringify({passed: true, results}));
} catch (error) {
  await save('verification-failure', {message: error.message, stack: error.stack, results});
  console.error(error.stack); process.exitCode = 1;
} finally { bridge?.close(); }
