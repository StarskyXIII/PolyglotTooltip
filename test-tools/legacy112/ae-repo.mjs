import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {Bridge} from '../name-audit/runner/bridge.mjs';

const [port, fixturePath, outputPath] = process.argv.slice(2);
const fixture = path.resolve(fixturePath);
const output = path.resolve(outputPath);
const launch = JSON.parse(await fs.readFile(path.join(fixture, 'launch.json'), 'utf8'));
assert.equal((await fs.readFile(path.join(fixture, '.polyglot-fixture'), 'utf8')).replace(/\r\n/g, '\n'), 'legacy112\n');
assert.equal(launch.port, Number(port));
assert.equal(path.resolve(launch.fixture).toLowerCase(), fixture.toLowerCase());
const checks = [];
const observations = {};
const bridge = await Bridge.connect(Number(port));
const call = (method, params = {}) => bridge.call(method, params, 125000);
const check = (name, actual, expected) => {
  try { assert.deepEqual(actual, expected); checks.push({name, pass: true}); }
  catch { checks.push({name, pass: false, actual, expected}); }
};
const ids = result => result.items.map(item => item.id).sort();
const configure = async settings => { await call('config.set', settings); await call('wait.ticks', {ticks: 3}); };
const search = async (key, query, settings = {}) => observations[key] = await call('ae.search', {query, ...settings});
const observe = async key => observations[key] = await call('ae.observe');
try {
  observations.runtime = await call('status');
  assert.equal(path.resolve(observations.runtime.gameDirectory).toLowerCase(), fixture.toLowerCase());
  assert.equal(observations.runtime.pid, String(launch.pid));
  const product = observations.runtime.mods.some(mod => mod.id === 'polyglottooltip');
  assert.equal(product, Object.keys(launch.artifacts).some(name => name.startsWith('polyglottooltip-')));
  const regex = observations.runtime.mods.find(mod => mod.id === 'appliedenergistics2').version.startsWith('rv6');
  const characters = observations.runtime.mods.some(mod => mod.id === 'jecharacters');
  if (observations.runtime.language !== 'en_us') await call('language.set', {language: 'en_us'});
  await call('resources.select', {packs: []});
  if (product) await configure({languages: ['zh_tw'], alwaysShow: false, enableChineseScriptMatching: true});
  observations.initial = await call('ae.setup', {items: [
    {item: 'minecraft:diamond', count: 64},
    {item: 'minecraft:iron_ingot', count: 32, craftable: true},
    {item: 'minecraft:wool', metadata: 14, count: 16},
    {item: 'minecraft:emerald', count: 0, craftable: true},
    {item: 'minecraft:paper', nbt: '{display:{Name:"Manual Label"}}', count: 3}
  ]});
  check('native all items', observations.initial.items.length, 5);
  check('native fluids', observations.initial.fluids.map(fluid => fluid.id).sort(), ['lava', 'water']);
  check('native item query', ids(await search('english', 'diamond')), ['minecraft:diamond']);
  check('absent item query', ids(await search('absent', 'polyglot_missing')), []);
  check('literal custom name', ids(await search('literal', 'manual label')), ['minecraft:paper']);
  check('native mod query', (await search('mod', '@minecraft')).items.length, 5);
  check('translated item query', ids(await search('secondary', '鑽石')), product ? ['minecraft:diamond'] : []);
  check('translated fluid query', (await search('water', '水')).fluids.map(fluid => fluid.id), product ? ['water'] : []);
  check('translated mod query excludes names', ids(await search('modSecondary', '@鑽石')), []);
  await search('primeTooltips', 'polyglot_missing', {tooltips: true});
  check('translated mod query excludes tooltip additions', ids(await search('modTooltip', '@鑽石', {tooltips: true})), []);
  if (characters) {
    check('JEC phonetic item aliases', ids(await search('phoneticItem', 'zuanshi', {tooltips: false})), product ? ['minecraft:diamond'] : []);
    check('JEC phonetic fluid aliases', (await search('phoneticFluid', 'shui')).fluids.map(fluid => fluid.id), product ? ['water'] : []);
    check('JEC phonetic names stay outside mod field', ids(await search('phoneticMod', '@zuanshi')), []);
    if (regex) {
      check('JEC native item anchor remains literal', ids(await search('nativeJecAnchor', '^diamond$')), []);
      check('JEC native fluid anchor remains literal', (await search('nativeJecFluidAnchor', '^water$')).fluids.map(fluid => fluid.id), []);
      check('JEC native outer wildcard item wrapper', ids(await search('nativeJecWildcard', '.*diamond.*')), ['minecraft:diamond']);
      check('JEC native outer wildcard fluid wrapper', (await search('nativeJecFluidWildcard', '.*water.*')).fluids.map(fluid => fluid.id), ['water']);
    }
    if (product) {
      await search('phoneticBeforeDisable', 'zuanshi');
      await configure({languages: []});
      check('JEC phonetic aliases disappear on settings change', ids(await observe('phoneticDisabled')), []);
      await configure({languages: ['zh_tw']});
      check('JEC phonetic aliases return with unchanged query', ids(await observe('phoneticRestored')), ['minecraft:diamond']);
      if (!regex) check('JEC phonetic negation preserves host semantics', ids(await search('phoneticNegative', 'diamond !zuanshi')), []);
    }
  }
  if (product) {
    check('script variant query', ids(await search('simplified', '钻石', {tooltips: false})), ['minecraft:diamond']);
    await configure({enableChineseScriptMatching: false});
    check('script toggle refreshes unchanged query', ids(await observe('scriptOff')), []);
    await configure({enableChineseScriptMatching: true});
    check('script reenable refreshes unchanged query', ids(await observe('scriptOn')), ['minecraft:diamond']);
    if (regex) {
      if (characters) {
        check('JEC alias anchor follows native item grammar', ids(await search('anchored', '^鑽石$')), ids(observations.nativeJecAnchor));
        check('JEC alias anchor follows native fluid grammar', (await search('fluidAnchor', '^水$')).fluids.map(fluid => fluid.id), observations.nativeJecFluidAnchor.fluids.map(fluid => fluid.id));
        check('JEC outer wildcard item alias wrapper', ids(await search('jecWildcardAlias', '.*鑽石.*')), ['minecraft:diamond']);
        check('JEC outer wildcard fluid alias wrapper', (await search('jecFluidWildcardAlias', '.*水.*')).fluids.map(fluid => fluid.id), ['water']);
        check('JEC literal query cannot span alias boundaries', ids(await search('crossAlias', 'diamond鑽石')), []);
      } else {
        check('anchored alias regex', ids(await search('anchored', '^鑽石$')), ['minecraft:diamond']);
        check('regex cannot span alias boundaries', ids(await search('crossAlias', '^diamond.*鑽石$')), []);
        check('anchored fluid regex', (await search('fluidAnchor', '^水$')).fluids.map(fluid => fluid.id), ['water']);
      }
      check('malformed regex fallback', ids(await search('malformed', '[')), []);
    } else {
      check('positive terms combine native and translated names', ids(await search('both', 'diamond 鑽石')), ['minecraft:diamond']);
      check('negative native term excludes translated match', ids(await search('negativeNative', '鑽石 -diamond')), []);
      check('negative alias excludes native match', ids(await search('negativeAlias', 'diamond !鑽石')), []);
      check('unrelated negative term preserves match', ids(await search('negativeOther', '鑽石 -鐵')), ['minecraft:diamond']);
      check('regex stays literal in token item search', ids(await search('literalRegex', '^鑽石$')), []);
    }
    check('stored mode excludes zero-count craftable', ids(await search('stored', '綠寶石', {viewMode: 'STORED'})), []);
    const craftable = await search('craftable', '綠寶石', {viewMode: 'CRAFTABLE'});
    check('craftable alias uses nonempty definition', ids(craftable), ['minecraft:emerald']);
    check('craftable amount remains zero', craftable.items.map(item => item.count), [0]);
    check('craftable mode excludes ordinary stored item', ids(await search('notCraftable', '鑽石')), []);
    check('view cell excludes translated item', ids(await search('partition', '鑽石', {viewMode: 'ALL', partition: {item: 'minecraft:iron_ingot'}})), []);
    check('view cell removal restores translated item', ids(await search('noPartition', '鑽石', {partition: null})), ['minecraft:diamond']);
    check('stored amount preserved', observations.noPartition.items.map(item => item.count), [64]);
    await search('beforeDisable', '鑽石', {tooltips: true});
    await configure({languages: []});
    check('disable refreshes unchanged query and cached tooltip', ids(await observe('disabled')), []);
    await configure({languages: ['zh_tw']});
    check('enable refreshes unchanged query', ids(await observe('enabled')), ['minecraft:diamond']);
    await call('language.set', {language: 'zh_cn'});
    check('primary language refresh invalidates native cache', (await observe('primaryChanged')).items.map(item => item.name), ['钻石']);
    await call('language.set', {language: 'en_us'});
    const pack = path.join(fixture, 'resourcepacks', 'pt112-ae-names');
    await fs.mkdir(path.join(pack, 'assets/minecraft/lang'), {recursive: true});
    await fs.writeFile(path.join(pack, 'pack.mcmeta'), JSON.stringify({pack: {pack_format: 3, description: 'AE name fixture'}}));
    await fs.writeFile(path.join(pack, 'assets/minecraft/lang/zh_tw.lang'), 'item.diamond.name=Étoile\n');
    await search('beforePack', 'étoile', {tooltips: false});
    await call('resources.select', {packs: ['pt112-ae-names']});
    check('resource change refreshes unchanged Unicode query', ids(await observe('packChanged')), ['minecraft:diamond']);
    await call('resources.select', {packs: []});
    check('resource removal removes stale alias', ids(await observe('packRemoved')), []);
    const final = await search('restored', '');
    check('all native items and amounts restored', final.items, observations.initial.items);
    check('all native fluids and amounts restored', final.fluids, observations.initial.fluids);
  }
} catch (error) {
  checks.push({name: 'runner', pass: false, error: error.stack});
} finally {
  bridge.close();
  await fs.mkdir(path.dirname(output), {recursive: true});
  await fs.writeFile(output, JSON.stringify({launch, checks, observations}, null, 2));
}
const failures = checks.filter(check => !check.pass);
console.log(JSON.stringify({passed: checks.length - failures.length, failed: failures.length, failures, output}, null, 2));
if (failures.length) process.exitCode = 1;
