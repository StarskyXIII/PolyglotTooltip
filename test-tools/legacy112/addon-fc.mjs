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
  const version = observations.runtime.mods.find(mod => mod.id === 'ae2fc').version;
  const modern = !version.startsWith('2.6.');
  const gas = observations.runtime.mods.some(mod => mod.id === 'mekeng');
  if (observations.runtime.language !== 'en_us') await call('language.set', {language: 'en_us'});
  await call('resources.select', {packs: []});
  if (product) await configure([]);
  if (!(await call('status')).world) await call('world.create', {name: 'pt112-addon-fc'});
  for (let index = 0; index < 90 && !(await call('status')).player; index++) await call('wait.ticks', {ticks: 20});
  assert.equal((await call('status')).player, true);
  await call('screen.close');
  observations.native = await call('addon.fc');
  check('native contextual tooltip owners populated', Object.keys(observations.native).length > 0, true);
  check('native contextual pipeline preserves input NBT', Object.values(observations.native).flat().every(row => row.unchanged), true);
  for (const row of observations.native.packaged || []) {
    if (!row.contentName) continue;
    check(`PackagedAuto ${row.key} native handler replaces packet title`, plain(row.lines[0]), row.contentName);
    check(`PackagedAuto ${row.key} native handler owns two lines`, row.lines.length, 2);
    check(`PackagedAuto ${row.key} native amount`, plain(row.lines[1]), row.expectedAmountLine);
  }
  await call('language.set', {language: 'zh_cn'});
  observations.target = await call('addon.fc');
  await call('language.set', {language: 'en_us'});
  if (product) {
    await configure(['zh_cn']);
    observations.secondary = await call('addon.fc');
    for (const [owner, rows] of Object.entries(observations.native)) {
      for (const native of rows) {
        const current = observations.secondary[owner].find(row => row.key === native.key);
        const target = observations.target[owner].find(row => row.key === native.key);
        const alias = plain(native.lines[0]) !== plain(target.lines[0]) && native.key !== 'custom' ? plain(target.lines[0]) : null;
        check(`${owner} ${native.key} native title`, current.lines[0], native.lines[0]);
        check(`${owner} ${native.key} input NBT unchanged`, current.unchanged, true);
        if (alias) {
          check(`${owner} ${native.key} contextual alias exactly once`, current.lines.filter(line => plain(line) === alias).length, 1);
          check(`${owner} ${native.key} native body retained`, current.lines.filter(line => plain(line) !== alias), native.lines);
        } else check(`${owner} ${native.key} no extra aliases`, current.lines, native.lines);
      }
    }
    await configure([]);
    check('disabled contextual event pipeline returns native output', await call('addon.fc'), observations.native);
    if (baselinePath) check('contextual native output equals separate baseline', observations.native, JSON.parse(await fs.readFile(baselinePath, 'utf8')).observations.native);
  }
  if (modern) {
    const contents = ['water', 'lava', ...(gas ? ['hydrogen', 'oxygen'] : [])];
    observations.renders = [];
    for (const content of contents) {
      let alias;
      if (product) {
        await call('language.set', {language: 'zh_cn'});
        alias = plain((await call('addon.fc', {render: content})).nativeName);
        await call('language.set', {language: 'en_us'});
      }
      for (const amount of [12345, 0, 2147484000]) {
        for (const storage of amount === 12345 ? [true, false] : [true]) {
          if (product) await configure([]);
          const params = {render: content, amount, storage};
          const native = await call('addon.fc', params), hit = await call('addon.fc', params);
          check(`${content} ${amount} ${storage} native cache hit`, hit.previousMatches, true);
          check(`${content} ${amount} ${storage} native list object reused`, hit.reusedNativeList, true);
          check(`${content} ${amount} ${storage} native repeat`, hit.lines, native.lines);
          check(`${content} ${amount} ${storage} original AE amount`, hit.amount, amount);
          if (!product) { observations.renders.push({params, native, hit}); continue; }
          await configure(['zh_cn']);
          const translated = await call('addon.fc', params), translatedHit = await call('addon.fc', params);
          let primary;
          if (amount === 12345 && storage) {
            await call('language.set', {language: 'zh_cn'}); await call('wait.ticks', {ticks: 5});
            primary = await call('addon.fc', params);
            check(`${content} current primary invalidates native cache`, plain(primary.lines[0]), alias);
            await call('language.set', {language: 'en_us'}); await call('wait.ticks', {ticks: 5});
          }
          check(`${content} ${amount} ${storage} secondary uses native fresh path`, translated.reusedNativeList, false);
          check(`${content} ${amount} ${storage} secondary at fresh draw`, translated.lines.filter(line => plain(line) === alias).length, 1);
          check(`${content} ${amount} ${storage} same secondary at cache hit`, translatedHit.lines, translated.lines);
          check(`${content} ${amount} ${storage} translated native list object reused`, translatedHit.reusedNativeList, true);
          check(`${content} ${amount} ${storage} cached native list remains undecorated`, translatedHit.cachedLines, native.cachedLines);
          check(`${content} ${amount} ${storage} native amount and body retained`, translated.lines.filter(line => plain(line) !== alias), native.lines);
          await configure([]);
          const disabled = await call('addon.fc', params);
          check(`${content} ${amount} ${storage} disabled draw restored`, disabled.lines, native.lines);
          observations.renders.push({params, native, hit, translated, translatedHit, primary, disabled});
        }
      }
    }
    if (product && baselinePath) {
      const baseline = JSON.parse(await fs.readFile(baselinePath, 'utf8')).observations;
      check('all native renderer output matches separate baseline', observations.renders.map(row => ({params: row.params, lines: row.native.lines})), baseline.renders.map(row => ({params: row.params, lines: row.native.lines})));
    }
  }
} catch (error) { checks.push({name: 'runner', pass: false, error: error.stack}); }
finally { bridge.close(); await fs.mkdir(path.dirname(output), {recursive: true}); await fs.writeFile(output, JSON.stringify({launch, checks, observations}, null, 2)); }
const failures = checks.filter(check => !check.pass);
console.log(JSON.stringify({passed: checks.length - failures.length, failed: failures.length, failures, output}, null, 2));
if (failures.length) process.exitCode = 1;
