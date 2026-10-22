import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {Bridge} from '../name-audit/runner/bridge.mjs';

const [port, fixturePath, outputPath, baselinePath] = process.argv.slice(2);
const fixture = path.resolve(fixturePath), output = path.resolve(outputPath);
const launch = JSON.parse(await fs.readFile(path.join(fixture, 'launch.json'), 'utf8'));
assert.equal((await fs.readFile(path.join(fixture, '.polyglot-fixture'), 'utf8')).replace(/\r\n/g, '\n'), 'legacy112\n');
assert.equal(launch.port, Number(port));
const bridge = await Bridge.connect(Number(port)), checks = [], observations = {};
const call = (method, params = {}) => bridge.call(method, params, 125000);
const plain = text => text.replace(/§./g, '').trim();
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
  assert.equal(product, Object.keys(launch.artifacts).some(name => name.startsWith('polyglottooltip-')));
  if (observations.runtime.language !== 'en_us') await call('language.set', {language: 'en_us'});
  await call('resources.select', {packs: []});
  if (product) await configure(['zh_cn']);
  observations.english = await call('addon.names', {reset: true});
  if (observations.runtime.mods.some(mod => mod.id === 'extracells') && !observations.runtime.mods.some(mod => mod.id === 'mekanism')) {
    check('ExtraCells native gas integration absent', observations.english.extraGasEnabled, false);
    check('ExtraCells omits unregistered gas items', observations.english.items.some(row => row.key.startsWith('ec-gas-')), false);
  }
  check('native addon fixture populated', observations.english.items.length > 0, true);
  check('native count and NBT unchanged', observations.english.items.every(row => row.unchanged), true);
  await call('language.set', {language: 'zh_cn'});
  await call('wait.ticks', {ticks: 5});
  observations.chinese = await call('addon.names');
  check('target-language native fixtures unchanged', observations.chinese.items.every(row => row.unchanged), true);
  for (const row of observations.english.items) {
    check(`${row.key} keeps its native title`, plain(row.tooltip[0]), plain(row.nativeName));
    if (!product) continue;
    const target = observations.chinese.items.find(other => other.key === row.key);
    assert.ok(target);
    const expected = plain(row.nativeName) === plain(target.nativeName) ? [] : [plain(target.nativeName)];
    check(`${row.key} matches actual native target-language title`, row.aliases.map(plain), expected);
    check(`${row.key} secondary title rendered exactly once`, row.aliases.every(alias => row.tooltip.filter(line => plain(line) === plain(alias)).length === 1), true);
    if (row.key.includes('-custom') || row.key.startsWith('the-aspect-')) check(`${row.key} literal title remains native`, row.aliases, []);
  }
  await call('language.set', {language: 'en_us'});
  observations.mutated = await call('addon.names', {mutate: 'lava'});
  for (const key of ['ec-fluid-water', 'fc-fluid-packet-water']) {
    const row = observations.mutated.items.find(row => row.key === key);
    if (!row) continue;
    const equivalent = observations.english.items.find(other => other.key === key.replace(/water$/, 'lava'));
    check(`${key} same stack content mutation updates native title`, row.nativeName, equivalent.nativeName);
    if (product) check(`${key} same stack content mutation updates aliases`, row.aliases, equivalent.aliases);
  }
  await call('addon.names', {mutate: 'water'});
  if (product) {
    await configure([]);
    observations.disabled = await call('addon.names');
    check('disabled aliases removed', observations.disabled.items.every(row => row.aliases.length === 0), true);
    for (const row of observations.english.items) {
      const native = observations.disabled.items.find(other => other.key === row.key);
      const aliases = new Set(row.aliases.map(plain));
      check(`${row.key} native body and amounts retained`, row.tooltip.filter((line, index) => index === 0 || !aliases.has(plain(line))), native.tooltip);
    }
    if (baselinePath) {
      const baseline = JSON.parse(await fs.readFile(baselinePath, 'utf8'));
      check('disabled output equals same-profile native baseline', observations.disabled.items.map(({aliases, ...row}) => row), baseline.observations.english.items);
    }
    await configure(['zh_cn']);
  }
} catch (error) { checks.push({name: 'runner', pass: false, error: error.stack}); }
finally { bridge.close(); await fs.mkdir(path.dirname(output), {recursive: true}); await fs.writeFile(output, JSON.stringify({launch, checks, observations}, null, 2)); }
const failures = checks.filter(check => !check.pass);
console.log(JSON.stringify({passed: checks.length - failures.length, failed: failures.length, failures, output}, null, 2));
if (failures.length) process.exitCode = 1;
