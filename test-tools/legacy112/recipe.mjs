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
const checks = [], observations = {};
const bridge = await Bridge.connect(Number(port));
const call = (method, params = {}) => bridge.call(method, params, 125000);
const check = (name, actual, expected) => {
  try { assert.deepEqual(actual, expected); checks.push({name, pass: true}); }
  catch { checks.push({name, pass: false, actual, expected}); }
};
const configure = async settings => { await call('config.set', settings); await call('wait.ticks', {ticks: 5}); };
const search = async (key, query) => observations[key] = await call('jei.search', {query});
const observe = async key => observations[key] = await call('jei.observe');
const has = (result, id) => result.rows.some(row => row.id === id);
try {
  observations.runtime = await call('status');
  assert.equal(path.resolve(observations.runtime.gameDirectory).toLowerCase(), fixture.toLowerCase());
  assert.equal(observations.runtime.pid, String(launch.pid));
  const product = observations.runtime.mods.some(mod => mod.id === 'polyglottooltip');
  assert.equal(product, Object.keys(launch.artifacts).some(name => name.startsWith('polyglottooltip-')));
  if (observations.runtime.language !== 'en_us') await call('language.set', {language: 'en_us'});
  await call('resources.select', {packs: []});
  if (product) await configure({languages: ['zh_tw'], alwaysShow: false, enableChineseScriptMatching: true});
  check('native name', has(await search('native', 'diamond'), 'minecraft:diamond'), true);
  check('absent query', (await search('absent', 'polyglot_missing_112')).count, 0);
  check('translated name', has(await search('secondary', '鑽石'), 'minecraft:diamond'), product);
  check('translated fluid', has(await search('fluid', '水'), 'water'), product);
  check('mod field does not include aliases', has(await search('modOnly', '@鑽石'), 'minecraft:diamond'), false);
  check('tooltip field does not include added title aliases', has(await search('tooltipOnly', '#鑽石'), 'minecraft:diamond'), false);
  if (observations.runtime.mods.some(mod => mod.id === 'jecharacters')) {
    check('phonetic alias through native tree', has(await search('phonetic', 'zuanshi'), 'minecraft:diamond'), product);
    check('phonetic names stay outside mod field', has(await search('phoneticMod', '@zuanshi'), 'minecraft:diamond'), false);
    if (product) {
      await search('phoneticBeforeDisable', 'zuanshi');
      await configure({languages: []});
      check('phonetic alias removed after rebuild', has(await observe('phoneticDisabled'), 'minecraft:diamond'), false);
      await configure({languages: ['zh_tw']});
      check('phonetic alias restored after rebuild', has(await observe('phoneticRestored'), 'minecraft:diamond'), true);
      check('phonetic negative token', has(await search('phoneticNegative', 'diamond -zuanshi'), 'minecraft:diamond'), false);
    }
  }
  if (product) {
    check('mod and translated name combine', has(await search('combined', '@minecraft 鑽石'), 'minecraft:diamond'), true);
    check('negative alias excludes native name', has(await search('negative', 'diamond -鑽石'), 'minecraft:diamond'), false);
    check('simplified alias', has(await search('simplified', '钻石'), 'minecraft:diamond'), true);
    const filterIdentity = observations.simplified.identity;
    await configure({enableChineseScriptMatching: false});
    check('script toggle removes stale alias', has(await observe('scriptOff'), 'minecraft:diamond'), false);
    await configure({enableChineseScriptMatching: true});
    check('script toggle restores alias', has(await observe('scriptOn'), 'minecraft:diamond'), true);
    check('refresh preserves live filter identity', observations.scriptOn.identity, filterIdentity);
    await search('beforeDisable', '鑽石');
    await configure({languages: []});
    check('disable removes stale alias with unchanged query', has(await observe('disabled'), 'minecraft:diamond'), false);
    check('query preserved', observations.disabled.query, '鑽石');
    await configure({languages: ['zh_tw']});
    check('enable restores unchanged query result', has(await observe('enabled'), 'minecraft:diamond'), true);
    observations.hidden = await call('jei.hide', {item: 'minecraft:diamond', hidden: true});
    check('hidden ingredient excluded from alias search', has(observations.hidden, 'minecraft:diamond'), false);
    await configure({languages: ['zh_tw', 'ja_jp']});
    check('hidden ingredient stays excluded after rebuild', has(await observe('hiddenRebuild'), 'minecraft:diamond'), false);
    observations.shown = await call('jei.hide', {item: 'minecraft:diamond', hidden: false});
    check('unhidden ingredient returns', has(observations.shown, 'minecraft:diamond'), true);
    await call('jei.dynamic', {item: 'polyglotdevbridge:composed_potion', add: false});
    await search('dynamicBefore', 'custom potion 42');
    check('dynamic fixture initially absent', has(observations.dynamicBefore, 'polyglotdevbridge:composed_potion'), false);
    await call('jei.dynamic', {item: 'polyglotdevbridge:composed_potion', add: true});
    check('dynamic ingredient added', has(await observe('dynamicAdded'), 'polyglotdevbridge:composed_potion'), true);
    await configure({languages: ['ja_jp', 'zh_tw']});
    check('dynamic ingredient retained after rebuild', has(await observe('dynamicRebuilt'), 'polyglotdevbridge:composed_potion'), true);
    await call('jei.dynamic', {item: 'polyglotdevbridge:composed_potion', add: false});
    await configure({languages: ['zh_tw', 'ja_jp']});
    check('removed dynamic ingredient remains absent after rebuild', has(await observe('dynamicRemoved'), 'polyglotdevbridge:composed_potion'), false);
    await configure({languages: ['zh_tw']});
    const pack = path.join(fixture, 'resourcepacks', 'pt112-recipe-names');
    await fs.mkdir(path.join(pack, 'assets/minecraft/lang'), {recursive: true});
    await fs.writeFile(path.join(pack, 'pack.mcmeta'), JSON.stringify({pack: {pack_format: 3, description: 'Recipe search fixture'}}));
    await fs.writeFile(path.join(pack, 'assets/minecraft/lang/zh_tw.lang'), 'item.diamond.name=Étoile\n');
    await search('beforePack', 'étoile');
    await call('resources.select', {packs: ['pt112-recipe-names']});
    await call('wait.ticks', {ticks: 5});
    check('resource reload replaces aliases', has(await observe('pack'), 'minecraft:diamond'), true);
    await call('resources.select', {packs: []});
    await call('wait.ticks', {ticks: 5});
    check('removed resource alias leaves index', has(await observe('removedPack'), 'minecraft:diamond'), false);
    await search('beforeLocale', '钻石');
    await call('language.set', {language: 'zh_cn'});
    await call('wait.ticks', {ticks: 5});
    check('primary language change updates native wrapper', (await observe('locale')).rows.find(row => row.id === 'minecraft:diamond')?.name, '钻石');
    await call('language.set', {language: 'en_us'});
    if (!(await call('status')).world) await call('world.create', {name: 'pt112-recipe'});
    for (let index = 0; index < 90 && !(await call('status')).player; index++) await call('wait.ticks', {ticks: 20});
    assert.equal((await call('status')).player, true);
    await call('screen.inventory');
    await call('wait.ticks', {ticks: 5});
    await search('inputQuery', 'diamond');
    observations.beforeCaret = await call('jei.ui', {cursor: 2, selection: 5, focused: true});
    await configure({languages: ['zh_tw', 'ja_jp']});
    observations.afterCaret = await call('jei.ui');
    check('generation refresh preserves caret, selection and focus', observations.afterCaret, observations.beforeCaret);
    await search('pageQuery', '@minecraft');
    observations.beforePage = await call('jei.ui', {nextPage: true});
    check('page fixture moved beyond first page', observations.beforePage.firstItemIndex > 0, true);
    await configure({languages: ['zh_tw']});
    observations.afterPage = await call('jei.ui');
    check('generation refresh preserves current result page', observations.afterPage, observations.beforePage);
    await call('screen.close');
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
