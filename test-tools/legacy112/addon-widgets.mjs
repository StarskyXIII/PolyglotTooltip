import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {Bridge} from '../name-audit/runner/bridge.mjs';

const [port, fixturePath, outputPath, baselinePath] = process.argv.slice(2);
const fixture = path.resolve(fixturePath), output = path.resolve(outputPath);
const launch = JSON.parse(await fs.readFile(path.join(fixture, 'launch.json'), 'utf8'));
assert.equal(launch.port, Number(port));
const checks = [], observations = {}, bridge = await Bridge.connect(Number(port));
const call = (method, params = {}) => bridge.call(method, params, 125000);
const plain = value => value.replace(/§./g, '').trim();
const check = (name, actual, expected) => {
  try { assert.deepEqual(actual, expected); checks.push({name, pass: true}); }
  catch { checks.push({name, pass: false, actual, expected}); }
};
const configure = async languages => { await call('config.set', {languages, alwaysShow: false}); await call('wait.ticks', {ticks: 5}); };
try {
  observations.runtime = await call('status');
  assert.equal(path.resolve(observations.runtime.gameDirectory).toLowerCase(), fixture.toLowerCase());
  assert.equal(observations.runtime.pid, String(launch.pid));
  const product = observations.runtime.mods.some(mod => mod.id === 'polyglottooltip');
  if (observations.runtime.language !== 'en_us') await call('language.set', {language: 'en_us'});
  await call('resources.select', {packs: []});
  if (product) await configure([]);
  observations.native = await call('addon.widgets');
  check('native widgets populated', observations.native.rows.length > 0, true);
  await call('language.set', {language: 'zh_cn'});
  observations.target = await call('addon.widgets');
  await call('language.set', {language: 'en_us'});
  if (product) {
    await configure(['zh_cn']);
    observations.secondary = await call('addon.widgets');
    for (const native of observations.native.rows) {
      const current = observations.secondary.rows.find(row => row.key === native.key);
      const target = observations.target.rows.find(row => row.key === native.key);
      const alias = native.nativeName && target.nativeName && plain(native.nativeName) !== plain(target.nativeName) ? plain(target.nativeName) : null;
      if (!alias) { check(`${native.key} native empty or unchanged title`, current.lines, native.lines); continue; }
      check(`${native.key} native title position`, current.lines[native.titleIndex], native.lines[native.titleIndex]);
      check(`${native.key} alias directly below title`, plain(current.lines[native.titleIndex + 1] || ''), alias);
      check(`${native.key} alias exactly once`, current.lines.filter(line => plain(line) === alias).length, 1);
      check(`${native.key} native direction amount and capacity preserved`, current.lines.filter(line => plain(line) !== alias), native.lines);
    }
    await configure([]);
    check('settings remove all widget aliases', await call('addon.widgets'), observations.native);
    if (baselinePath) {
      const baseline = JSON.parse(await fs.readFile(baselinePath, 'utf8')).observations;
      check('disabled native widgets match separate baseline', observations.native, baseline.native);
    }
  }
} catch (error) { checks.push({name: 'runner', pass: false, error: error.stack}); }
finally { bridge.close(); await fs.mkdir(path.dirname(output), {recursive: true}); await fs.writeFile(output, JSON.stringify({launch, checks, observations}, null, 2)); }
const failures = checks.filter(check => !check.pass);
console.log(JSON.stringify({passed: checks.length - failures.length, failed: failures.length, failures, output}, null, 2));
if (failures.length) process.exitCode = 1;
