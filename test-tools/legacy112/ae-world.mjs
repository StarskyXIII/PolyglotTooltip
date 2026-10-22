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
const search = async text => {
  await call('ae.ui', {focused: true});
  await call('input.key', {key: 14, repeat: 100});
  await call('input.type', {text});
  await call('wait.ticks', {ticks: 3});
  return call('ae.ui');
};
try {
  observations.runtime = await call('status');
  assert.equal(path.resolve(observations.runtime.gameDirectory).toLowerCase(), fixture.toLowerCase());
  assert.equal(observations.runtime.pid, String(launch.pid));
  const product = observations.runtime.mods.some(mod => mod.id === 'polyglottooltip');
  assert.equal(product, Object.keys(launch.artifacts).some(name => name.startsWith('polyglottooltip-')));
  if (observations.runtime.language !== 'en_us') await call('language.set', {language: 'en_us'});
  await call('resources.select', {packs: []});
  if (product) await configure({languages: ['zh_tw'], alwaysShow: false, enableChineseScriptMatching: true});
  if (!(await call('status')).world) await call('world.create', {name: 'pt112-ae-world'});
  for (let index = 0; index < 90 && !(await call('status')).player; index++) await call('wait.ticks', {ticks: 20});
  assert.equal((await call('status')).player, true);
  for (const fluid of [false, true]) {
    await call('screen.close');
    await call('ae.world', {fluid});
    await call('wait.ticks', {ticks: 40});
    observations[fluid ? 'fluidOpen' : 'itemOpen'] = await call('ae.world', {open: true});
    check(`${fluid ? 'fluid' : 'item'} chest is powered`, observations[fluid ? 'fluidOpen' : 'itemOpen'].powered, true);
    await call('wait.ticks', {ticks: 20});
    const primary = await search(fluid ? 'water' : 'diamond');
    observations[fluid ? 'fluidNative' : 'itemNative'] = primary;
    check(`${fluid ? 'fluid' : 'item'} native GUI result`, primary.rows.some(row => row.id === (fluid ? 'water' : 'minecraft:diamond')), true);
    check(`${fluid ? 'fluid' : 'item'} native amount`, primary.rows.length > 0 && primary.rows.every(row => row.count === (fluid ? 16000 : 64)), true);
    const query = fluid ? '水' : '鑽石';
    const secondary = await search(query);
    observations[fluid ? 'fluidAlias' : 'itemAlias'] = secondary;
    check(`${fluid ? 'fluid' : 'item'} secondary GUI result`, secondary.rows.map(row => row.id), product ? primary.rows.map(row => row.id) : []);
    if (product) {
      await configure({languages: []});
      const disabled = await call('ae.ui');
      observations[fluid ? 'fluidDisabled' : 'itemDisabled'] = disabled;
      check(`${fluid ? 'fluid' : 'item'} open GUI refresh removes aliases`, disabled.rows, []);
      check(`${fluid ? 'fluid' : 'item'} query remains unchanged`, disabled.query, query);
      await configure({languages: ['zh_tw']});
      const restored = await call('ae.ui');
      observations[fluid ? 'fluidRestored' : 'itemRestored'] = restored;
      check(`${fluid ? 'fluid' : 'item'} open GUI refresh restores results and input`, restored, secondary);
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
