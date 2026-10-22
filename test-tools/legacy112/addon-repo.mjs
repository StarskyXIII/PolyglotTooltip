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
const ids = (value, owner) => value[owner].rows.map(row => row.id).sort();
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
  const characters = observations.runtime.mods.some(mod => mod.id === 'jecharacters');
  if (observations.runtime.language !== 'en_us') await call('language.set', {language: 'en_us'});
  await call('resources.select', {packs: []});
  if (product) await configure([]);
  observations.native = await call('addon.repo.setup');
  check('native repositories populated', Object.keys(observations.native).length > 0, true);
  if (observations.native.the?.optimized) {
    observations.nativeTooltipDisabled = await call('addon.repo.search', {query: 'pt112-native-absent'});
    check('ThE optimized native disabled-tooltip path accepts all', ids(observations.nativeTooltipDisabled, 'the'), ids(observations.native, 'the'));
    await call('addon.repo.search', {query: '', tooltips: true});
  }
  await call('language.set', {language: 'zh_cn'});
  observations.target = await call('addon.repo.search', {query: ''});
  await call('language.set', {language: 'en_us'});
  observations.nativeQueries = {};
  for (const owner of Object.keys(observations.native)) {
    const id = owner === 'gas' ? 'hydrogen' : 'minecraft:diamond';
    const original = observations.native[owner].rows.find(row => row.id === id);
    const translated = observations.target[owner].rows.find(row => row.id === id);
    assert.ok(original && translated);
    assert.notEqual(original.name, translated.name);
    const nativeResults = observations.nativeQueries[owner] = {};
    for (const query of [original.name.toLowerCase(), translated.name, 'pt112-absent', `[${original.name}`, `@${translated.name}`, `^${original.name}$`, `.*${original.name}.*`, '(?-i:^Diamond$)', '(?-i:^DIAMOND$)']) {
      nativeResults[query] = ids(await call('addon.repo.search', {query}), owner);
    }
    check(`${owner} native name`, nativeResults[original.name.toLowerCase()], [id]);
    check(`${owner} target absent natively`, nativeResults[translated.name], []);
    check(`${owner} absent query`, nativeResults['pt112-absent'], []);
    check(`${owner} target never matches mod domain`, nativeResults[`@${translated.name}`], []);
    if (owner === 'the') {
      check('ThE independent native name predicate', (await call('addon.repo.nameLeaf', {query: original.name})).rows, [id]);
      check('ThE native name predicate has no alternate title', (await call('addon.repo.nameLeaf', {query: translated.name})).rows, []);
    }
    if (!product) continue;
    await call('addon.repo.search', {query: translated.name});
    await configure(['zh_cn']);
    const enabled = await call('addon.repo.observe');
    check(`${owner} unchanged query refresh finds alias`, ids(enabled, owner), [id]);
    check(`${owner} refresh preserves query`, enabled[owner].query, translated.name);
    if (owner === 'the') check('ThE alias enters actual native name predicate', (await call('addon.repo.nameLeaf', {query: translated.name})).rows, [id]);
    await configure([]);
    check(`${owner} unchanged query removes alias`, ids(await call('addon.repo.observe'), owner), []);
    if (owner === 'the') check('ThE name predicate removes disabled alias', (await call('addon.repo.nameLeaf', {query: translated.name})).rows, []);
    await configure(['zh_cn']);
    for (const [query, expected] of Object.entries(nativeResults)) {
      check(`${owner} native grammar preserved ${query}`, ids(await call('addon.repo.search', {query}), owner), query === translated.name ? [id] : expected);
    }
    const pattern = characters ? `.*${translated.name}.*` : `^${translated.name}$`;
    check(`${owner} alias uses host pattern evaluator`, ids(await call('addon.repo.search', {query: pattern}), owner), [id]);
    if (owner === 'the') check('ThE actual name predicate uses host pattern evaluator', (await call('addon.repo.nameLeaf', {query: pattern})).rows, [id]);
    check(`${owner} names never joined`, ids(await call('addon.repo.search', {query: original.name + translated.name}), owner), []);
    check(`${owner} aliases preserve craftable visibility`, ids(await call('addon.repo.search', {query: translated.name, viewMode: 'CRAFTABLE'}), owner), []);
    await call('addon.repo.search', {query: translated.name, viewMode: 'ALL'});
    await call('resources.reload'); await call('wait.ticks', {ticks: 5});
    check(`${owner} resource generation retains alias`, ids(await call('addon.repo.observe'), owner), [id]);
    await configure([]);
    await call('language.set', {language: 'zh_cn'}); await call('wait.ticks', {ticks: 5});
    check(`${owner} changed primary refreshes native view`, ids(await call('addon.repo.observe'), owner), [id]);
    await call('language.set', {language: 'en_us'}); await call('wait.ticks', {ticks: 5});
    check(`${owner} old primary removed after generation`, ids(await call('addon.repo.observe'), owner), []);
    if (owner === 'the') {
      await call('addon.repo.search', {query: 'diamond', searchMode: 'JEI_MANUAL_SEARCH'});
      check('ThE ordinary native search synchronizes recipe query', (await call('jei.observe')).query, 'diamond');
      await call('jei.search', {query: 'pt112-independent-recipe'});
      await configure(['zh_cn']);
      check('ThE own refresh preserves independent recipe query', (await call('jei.observe')).query, 'pt112-independent-recipe');
      await call('addon.repo.search', {query: 'apple'});
      check('ThE ordinary synchronization still works after own refresh', (await call('jei.observe')).query, 'apple');
      await call('addon.repo.search', {searchMode: 'MANUAL_SEARCH'});
      await configure([]);
    }
  }
  if (product && baselinePath) {
    const baseline = JSON.parse(await fs.readFile(baselinePath, 'utf8')).observations;
    check('disabled repositories equal separate native baseline', observations.native, baseline.native);
    check('disabled query grammar equals separate native baseline', observations.nativeQueries, baseline.nativeQueries);
  }
} catch (error) { checks.push({name: 'runner', pass: false, error: error.stack}); }
finally { bridge.close(); await fs.mkdir(path.dirname(output), {recursive: true}); await fs.writeFile(output, JSON.stringify({launch, checks, observations}, null, 2)); }
const failures = checks.filter(check => !check.pass);
console.log(JSON.stringify({passed: checks.length - failures.length, failed: failures.length, failures, output}, null, 2));
if (failures.length) process.exitCode = 1;
