import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {Bridge} from '../name-audit/runner/bridge.mjs';

const [port, fixturePath, outputPath, baselinePath] = process.argv.slice(2);
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
const plain = text => text.replace(/§./g, '').trim();
const configure = async settings => { await call('config.set', settings); await call('wait.ticks', {ticks: 5}); };
try {
  observations.runtime = await call('status');
  assert.equal(path.resolve(observations.runtime.gameDirectory).toLowerCase(), fixture.toLowerCase());
  assert.equal(observations.runtime.pid, String(launch.pid));
  const product = observations.runtime.mods.some(mod => mod.id === 'polyglottooltip');
  assert.equal(product, Object.keys(launch.artifacts).some(name => name.startsWith('polyglottooltip-')));
  if (observations.runtime.language !== 'en_us') await call('language.set', {language: 'en_us'});
  await call('resources.select', {packs: []});
  if (product) await configure({languages: ['zh_tw'], alwaysShow: false});
  observations.english = await call('mek.names');
  check('native factory recipe matrix populated', [9, 24].includes(observations.english.recipes), true);
  check('gas registry populated', observations.english.gases.length > 10, true);
  check('gas amount unchanged by renderer', observations.english.gases.every(row => row.amount === 12345), true);
  if (product) {
    await call('language.set', {language: 'zh_tw'});
    await call('wait.ticks', {ticks: 5});
    observations.nativeTarget = await call('mek.names');
    for (const group of ['items', 'gases']) {
      const nativeTarget = new Map(observations.nativeTarget[group].map(row => [row.key, row]));
      for (const row of observations.english[group]) {
        const target = nativeTarget.get(row.key);
        check(`${group} ${row.key} alias follows actual native target language`, row.aliases.map(plain), plain(row.nativeName) === plain(target.nativeName) ? [] : [plain(target.nativeName)]);
        check(`${group} ${row.key} native title unchanged`, plain(row.tooltip[0]), plain(row.nativeName));
        check(`${group} ${row.key} alias appears once in tooltip`, row.aliases.every(alias => row.tooltip.filter(line => plain(line) === plain(alias)).length === 1), true);
      }
    }
    check('custom tank name remains literal', observations.english.items.find(row => row.key === 'CUSTOM_TANK').aliases, []);
    await call('language.set', {language: 'en_us'});
    const gas = observations.english.gases.find(row => row.key === 'gas.hydrogen' && row.aliases.length);
    assert.ok(gas, 'Expected translated hydrogen fixture');
    observations.gasSearch = await call('jei.search', {query: plain(gas.aliases[0])});
    check('gas alias indexed by native recipe filter', observations.gasSearch.rows.some(row => row.type === 'mekanism.api.gas.GasStack' && plain(row.name) === plain(gas.nativeName)), true);
    observations.gasModSearch = await call('jei.search', {query: '@' + plain(gas.aliases[0])});
    check('gas alias excluded from mod field', observations.gasModSearch.rows.some(row => row.type === 'mekanism.api.gas.GasStack' && plain(row.name) === plain(gas.nativeName)), false);
    await call('jei.search', {query: plain(gas.aliases[0])});
    await configure({languages: []});
    observations.gasDisabledSearch = await call('jei.observe');
    check('gas alias removed from index on disable', observations.gasDisabledSearch.rows.some(row => row.type === 'mekanism.api.gas.GasStack' && plain(row.name) === plain(gas.nativeName)), false);
    observations.disabled = await call('mek.names');
    check('disabled item aliases empty', observations.disabled.items.every(row => row.aliases.length === 0), true);
    check('disabled gas aliases empty', observations.disabled.gases.every(row => row.aliases.length === 0), true);
    for (const group of ['items', 'gases']) {
      const disabled = new Map(observations.disabled[group].map(row => [row.key, row]));
      for (const row of observations.english[group]) {
        const secondary = new Set(row.aliases.map(plain));
        const original = row.tooltip.filter((line, index) => index === 0 || !secondary.has(plain(line)));
        check(`${group} ${row.key} body and numeric lines preserved`, original, disabled.get(row.key).tooltip);
      }
    }
    if (baselinePath) {
      const baseline = JSON.parse(await fs.readFile(baselinePath, 'utf8'));
      for (const group of ['items', 'gases']) {
        check(`same-profile ${group} baseline native output`, observations.disabled[group].map(({aliases, ...row}) => row), baseline.observations.english[group]);
      }
    }
    await configure({languages: ['zh_tw']});
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
