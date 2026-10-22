import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {Bridge} from '../name-audit/runner/bridge.mjs';

const [port, fixturePath, outputPath] = process.argv.slice(2);
const fixture = path.resolve(fixturePath), output = path.resolve(outputPath);
const launch = JSON.parse(await fs.readFile(path.join(fixture, 'launch.json'), 'utf8'));
assert.equal((await fs.readFile(path.join(fixture, '.polyglot-fixture'), 'utf8')).replace(/\r\n/g, '\n'), 'legacy112\n');
assert.equal(launch.port, Number(port));
const checks = [], observations = {};
const bridge = await Bridge.connect(Number(port));
const call = (method, params = {}) => bridge.call(method, params, 125000);
const check = (name, actual, expected) => {
  try { assert.deepEqual(actual, expected); checks.push({name, pass: true}); }
  catch { checks.push({name, pass: false, actual, expected}); }
};
const configure = async settings => { await call('config.set', settings); await call('wait.ticks', {ticks: 5}); };
const rows = result => result.rows.filter(row => row.id !== undefined);
const search = async (query, field, clear = true) => {
  if (clear) for (const name of Object.keys((await call('ae.interface')).queries)) {
    await call('ae.interface', {focus: name});
    await call('input.key', {key: 14, repeat: 100});
  }
  await call('ae.interface', {focus: field});
  await call('input.type', {text: query});
  await call('wait.ticks', {ticks: 5});
  return call('ae.interface');
};
try {
  observations.runtime = await call('status');
  assert.equal(path.resolve(observations.runtime.gameDirectory).toLowerCase(), fixture.toLowerCase());
  assert.equal(observations.runtime.pid, String(launch.pid));
  const product = observations.runtime.mods.some(mod => mod.id === 'polyglottooltip');
  assert.equal(product, Object.keys(launch.artifacts).some(name => name.startsWith('polyglottooltip-')));
  const rv6 = observations.runtime.mods.find(mod => mod.id === 'appliedenergistics2').version.startsWith('rv6');
  if (observations.runtime.language !== 'en_us') await call('language.set', {language: 'en_us'});
  await call('resources.select', {packs: []});
  if (product) await configure({languages: ['zh_tw'], alwaysShow: false, enableChineseScriptMatching: true});
  if (!(await call('status')).world) await call('world.create', {name: 'pt112-ae-interface'});
  for (let index = 0; index < 90 && !(await call('status')).player; index++) await call('wait.ticks', {ticks: 20});
  assert.equal((await call('status')).player, true);
  for (const mode of rv6 ? ['interface'] : ['interface', 'itemConfig', 'fluidConfig']) {
    const record = observations[mode] = {};
    await call('screen.close');
    await call('ae.world', {mode});
    await call('wait.ticks', {ticks: 60});
    record.open = await call('ae.world', {mode, open: true});
    check(`${mode} native part opens GUI`, record.open.opened, true);
    await call('wait.ticks', {ticks: 30});
    record.initial = await call('ae.interface');
    check(`${mode} network sends native interface`, record.initial.available, 1);
    const fields = Object.keys(record.initial.queries);
    const outputField = fields.includes('searchFieldOutputs') ? 'searchFieldOutputs' : fields.includes('searchFieldInputs') ? 'searchFieldInputs' : 'searchField';
    const nameField = fields.includes('searchFieldNames') ? 'searchFieldNames' : outputField;
    const native = mode === 'fluidConfig' ? 'water' : 'diamond';
    const alias = mode === 'fluidConfig' ? '水' : '鑽石';
    record.native = await search(native, outputField);
    check(`${mode} native configured ingredient`, rows(record.native).length, 1);
    record.alias = await search(alias, outputField);
    check(`${mode} translated configured ingredient`, rows(record.alias).length, product ? 1 : 0);
    if (product) {
      await configure({languages: []});
      record.disabled = await call('ae.interface');
      check(`${mode} cached search removes disabled aliases`, rows(record.disabled).length, 0);
      check(`${mode} cached refresh preserves query`, record.disabled.queries, record.alias.queries);
      await configure({languages: ['zh_tw']});
      check(`${mode} cached refresh restores aliases`, rows(await call('ae.interface')).length, 1);
      if (mode === 'interface' && fields.includes('searchFieldInputs')) {
        await search('iron', 'searchFieldInputs');
        record.nativeCombination = await search('polyglot_missing_112', 'searchFieldOutputs', false);
        record.input = await search('鐵錠', 'searchFieldInputs');
        check('pattern input aliases', rows(record.input).length, 1);
        record.both = await search('鑽石', 'searchFieldOutputs', false);
        check('pattern input and output aliases combine', rows(record.both).length, 1);
        record.absent = await search('不存在', 'searchFieldOutputs', false);
        check('separate pattern fields preserve native combination semantics', rows(record.absent).length, rows(record.nativeCombination).length);
      }
    }
    record.machine = await search('furnace', nameField);
    check(`${mode} native machine name`, rows(record.machine).length, 1);
    record.machineAlias = await search('熔爐', nameField);
    check(`${mode} default machine name follows transmitted source`, rows(record.machineAlias).length,
      product && rows(record.initial)[0]?.raw === 'tile.furnace' ? 1 : 0);
    if (product) {
      await call('screen.close');
      await call('ae.world', {mode, rename: 'tile.furnace'});
      await call('ae.world', {mode, open: true});
      await call('wait.ticks', {ticks: 30});
      record.keyNative = await search('furnace', nameField);
      check(`${mode} native translatable machine key`, rows(record.keyNative).length, 1);
      record.keyAlias = await search('熔爐', nameField);
      check(`${mode} source-key machine alias`, rows(record.keyAlias).length, 1);
      check(`${mode} native grouping label unchanged`, record.keyAlias.rows.map(row => row.label), record.keyNative.rows.map(row => row.label));
      await call('screen.close');
      await call('ae.world', {mode, rename: 'Manual label 42'});
      await call('ae.world', {mode, open: true});
      await call('wait.ticks', {ticks: 30});
      record.custom = await search('manual label 42', nameField);
      check(`${mode} custom machine name remains searchable`, rows(record.custom).length, 1);
      record.customAlias = await search('熔爐', nameField);
      check(`${mode} custom machine name is not translated`, rows(record.customAlias).length, 0);
    }
  }
} catch (error) { checks.push({name: 'runner', pass: false, error: error.stack}); }
finally {
  bridge.close();
  await fs.mkdir(path.dirname(output), {recursive: true});
  await fs.writeFile(output, JSON.stringify({launch, checks, observations}, null, 2));
}
const failures = checks.filter(check => !check.pass);
console.log(JSON.stringify({passed: checks.length - failures.length, failed: failures.length, failures, output}, null, 2));
if (failures.length) process.exitCode = 1;
