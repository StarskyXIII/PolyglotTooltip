import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {Bridge} from '../name-audit/runner/bridge.mjs';

const [port, fixturePath, outputPath, baselinePath] = process.argv.slice(2);
const fixture = path.resolve(fixturePath), output = path.resolve(outputPath);
const launch = JSON.parse(await fs.readFile(path.join(fixture, 'launch.json'), 'utf8'));
assert.equal(launch.port, Number(port));
const checks = [], observations = {};
const bridge = await Bridge.connect(Number(port));
const call = (method, params = {}) => bridge.call(method, params, 125000);
const plain = value => value.replace(/§./g, '');
const check = (name, actual, expected) => {
  try { assert.deepEqual(actual, expected); checks.push({name, pass: true}); }
  catch { checks.push({name, pass: false, actual, expected}); }
};
const configure = async languages => { await call('config.set', {languages}); await call('wait.ticks', {ticks: 5}); };
try {
  observations.runtime = await call('status');
  assert.equal(path.resolve(observations.runtime.gameDirectory).toLowerCase(), fixture.toLowerCase());
  assert.equal(observations.runtime.pid, String(launch.pid));
  const product = observations.runtime.mods.some(mod => mod.id === 'polyglottooltip');
  if (observations.runtime.language !== 'en_us') await call('language.set', {language: 'en_us'});
  await call('resources.select', {packs: []});
  if (product) await configure([]);
  observations.nativeWidgets = await call('ae.fluid.widgets');
  if (!(await call('status')).world) await call('world.create', {name: 'pt112-ae-fluid'});
  for (let index = 0; index < 90 && !(await call('status')).player; index++) await call('wait.ticks', {ticks: 20});
  assert.equal((await call('status')).player, true);
  await call('screen.close');
  await call('ae.world', {fluid: true});
  await call('wait.ticks', {ticks: 40});
  await call('ae.world', {open: true});
  await call('wait.ticks', {ticks: 20});
  await call('ae.ui', {focused: true});
  await call('input.key', {key: 14, repeat: 100});
  await call('input.type', {text: 'water'});
  await call('wait.ticks', {ticks: 3});
  observations.nativeHover = await call('ae.fluid.hover');
  check('native hover amount', observations.nativeHover.amount, 16000);
  check('native hover title', plain(observations.nativeHover.lines[0]), 'Water');
  check('native hover keeps bucket quantity', observations.nativeHover.lines.some(line => plain(line) === '16 B'), true);
  if (product) {
    await configure(['zh_tw']);
    observations.secondaryWidgets = await call('ae.fluid.widgets');
    observations.secondaryHover = await call('ae.fluid.hover');
    for (const [fluid, alias] of [['water', '水'], ['lava', '熔岩']]) {
      for (const widget of ['tank', 'slot']) {
        const native = observations.nativeWidgets[fluid][widget].split('\n');
        const updated = observations.secondaryWidgets[fluid][widget].split('\n');
        check(`${fluid} ${widget} native title`, updated[0], native[0]);
        check(`${fluid} ${widget} exactly one alias`, updated.map(plain).filter(line => line === alias).length, 1);
        check(`${fluid} ${widget} original body`, updated.filter(line => plain(line) !== alias), native);
      }
    }
    check('empty widgets unchanged', observations.secondaryWidgets.empty, observations.nativeWidgets.empty);
    check('zero tank unchanged', observations.secondaryWidgets.zero.tank, observations.nativeWidgets.zero.tank);
    check('zero slot native title', observations.secondaryWidgets.zero.slot.split('\n')[0], observations.nativeWidgets.zero.slot);
    check('zero slot alias', observations.secondaryWidgets.zero.slot.split('\n').map(plain).filter(line => line === '水').length, 1);
    check('native terminal alias', observations.secondaryHover.lines.map(plain).filter(line => line === '水').length, 1);
    check('native terminal body preserved', observations.secondaryHover.lines.filter(line => plain(line) !== '水'), observations.nativeHover.lines);
    if (observations.nativeWidgets.jei) {
      check('recipe fluid renderer alias', observations.secondaryWidgets.jei.map(plain).filter(line => line === '水').length, 1);
      check('recipe fluid renderer native body preserved', observations.secondaryWidgets.jei.filter(line => plain(line) !== '水'), observations.nativeWidgets.jei);
    }
    await configure([]);
    check('widget settings remove aliases', await call('ae.fluid.widgets'), observations.nativeWidgets);
    check('terminal settings remove aliases', await call('ae.fluid.hover'), observations.nativeHover);
    if (baselinePath) {
      const baseline = JSON.parse(await fs.readFile(baselinePath, 'utf8')).observations;
      check('widgets match separate baseline', observations.nativeWidgets, baseline.nativeWidgets);
      check('terminal lines match separate baseline', observations.nativeHover.lines, baseline.nativeHover.lines);
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
