import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {Bridge} from '../name-audit/runner/bridge.mjs';

const [port, fixturePath, outputPath, baselinePath] = process.argv.slice(2);
const fixture = path.resolve(fixturePath);
const output = path.resolve(outputPath);
assert.equal((await fs.readFile(path.join(fixture, '.polyglot-fixture'), 'utf8')).replace(/\r\n/g, '\n'), 'legacy112\n');
const launch = JSON.parse(await fs.readFile(path.join(fixture, 'launch.json'), 'utf8'));
assert.equal(launch.port, Number(port));
assert.equal(path.resolve(launch.fixture).toLowerCase(), fixture.toLowerCase());
const checks = [];
const observations = {};
const bridge = await Bridge.connect(Number(port));
const plain = value => value.replace(/§[0-9a-fk-or]/gi, '').trim();
const check = (name, actual, expected) => {
  try { assert.deepEqual(actual, expected); checks.push({name, pass: true}); }
  catch (error) { checks.push({name, pass: false, actual, expected}); }
};
const call = (method, params = {}) => bridge.call(method, params, 125000);
const configure = async value => { await call('config.set', value); await call('wait.ticks', {ticks: 3}); };
const probe = async (key, params) => observations[key] = await call('probe.core', params);
const baseline = baselinePath ? JSON.parse(await fs.readFile(baselinePath, 'utf8')) : null;
try {
  observations.runtime = await call('status');
  assert.equal(path.resolve(observations.runtime.gameDirectory).toLowerCase(), fixture.toLowerCase());
  assert.equal(observations.runtime.pid, String(launch.pid));
  const product = observations.runtime.mods.some(mod => mod.id === 'polyglottooltip');
  assert.equal(product, Object.keys(launch.artifacts).some(name => name.startsWith('polyglottooltip-')));
  await call('language.set', {language: 'en_us'});
  await call('resources.select', {packs: []});
  if (product) await configure({languages: ['zh_tw', 'zh_cn'], alwaysShow: false, enableChineseScriptMatching: true, tooltipSecondaryNameFormat: '&7'});
  const cases = {
    diamond: {item: 'minecraft:diamond'},
    redWool: {item: 'minecraft:wool', metadata: 14},
    potion: {item: 'minecraft:potion', nbt: '{Potion:"minecraft:healing"}'},
    splash: {item: 'minecraft:splash_potion', nbt: '{Potion:"minecraft:swiftness"}'},
    lingering: {item: 'minecraft:lingering_potion', nbt: '{Potion:"minecraft:strength"}'},
    arrow: {item: 'minecraft:tipped_arrow', nbt: '{Potion:"minecraft:poison"}'},
    banner: {item: 'minecraft:banner', metadata: 1},
    egg: {item: 'minecraft:spawn_egg', nbt: '{EntityTag:{id:"minecraft:pig"}}'},
    enchanted: {item: 'minecraft:diamond_sword', nbt: '{ench:[{id:16s,lvl:5s}]}'},
    custom: {item: 'minecraft:diamond_sword', nbt: '{display:{Name:"Sharpness V"},ench:[{id:16s,lvl:5s}]}'}
  };
  for (const [key, params] of Object.entries(cases)) {
    const observed = await probe(key, params);
    if (!product) {
      check(`${key} baseline native title`, plain(observed.tooltip[0]), observed.nativeName);
      continue;
    }
    if (key === 'custom') {
      check('literal custom title preserved', plain(observed.tooltip[0]), 'Sharpness V');
      check('custom title has no aliases', observed.aliases, []);
    } else check(`${key} has translated aliases`, observed.aliases.length > 0, true);
    if (baseline) {
      check(`${key} native title unchanged`, observed.tooltip[0], baseline.observations[key].tooltip[0]);
      const original = baseline.observations[key].tooltip.slice(1).map(plain);
      const result = observed.tooltip.slice(1).map(plain).filter(line => !observed.aliases.includes(line));
      check(`${key} body preserved`, result.map(line => line.split(' ｜ ')[0]), original);
    }
  }
  if (product) {
    observations.contracts = await call('probe.contracts');
    for (const [name, result] of Object.entries(observations.contracts)) check(name, result.pass, true);
    check('enchantment translated', observations.enchanted.tooltip.some(line => line.includes('Sharpness V') && line.includes('｜')), true);
    await configure({languages: []});
    const disabled = await probe('disabled', cases.diamond);
    const nativeTooltip = baseline?.observations.diamond.tooltip ?? ['Diamond§r'];
    check('empty language list disables names', disabled.aliases, []);
    check('empty language list restores native tooltip', disabled.tooltip, nativeTooltip);
    await configure({languages: ['en_us'], alwaysShow: false});
    const sameLanguage = await probe('dedup', cases.diamond);
    check('same-language deduplication', sameLanguage.tooltip, nativeTooltip);
    check('same-language processing disabled', sameLanguage.secondaryProcessing, false);
    await configure({alwaysShow: true});
    const always = await probe('always', cases.diamond);
    check('alwaysShow enables processing', always.secondaryProcessing, true);
    check('alwaysShow preserves title deduplication', always.tooltip, nativeTooltip);
    check('alwaysShow preserves alias deduplication', always.aliases, []);
    await configure({languages: ['en_us', 'zh_tw', 'zh_tw'], alwaysShow: false, tooltipSecondaryNameFormat: '&6&o'});
    await call('language.set', {language: 'zh_cn'});
    const switched = await probe('primaryLanguageSwitch', cases.diamond);
    check('native language switch', switched.nativeName, '钻石');
    check('ordered unique aliases after language switch', switched.aliases, ['Diamond', '鑽石']);
    check('configured formatting', switched.tooltip[1], '§6§oDiamond§r');
    await call('language.set', {language: 'en_us'});
    await configure({languages: ['zh_tw'], tooltipSecondaryNameFormat: '&7'});
    check('simplified search matches traditional alias', (await probe('scriptEnabled', {...cases.diamond, search: '钻石'})).searchMatch, true);
    await configure({enableChineseScriptMatching: false});
    check('disabled script matching removes simplified candidate', (await probe('scriptDisabled', {...cases.diamond, search: '钻石'})).searchMatch, false);
    check('disabled script matching preserves native candidate', (await probe('nativeSearch', {...cases.diamond, search: 'diamond'})).searchMatch, true);
    await configure({enableChineseScriptMatching: true});
    const packs = [
      ['pt112-v2', 2, 'en_US', 'zh_TW', 'PT Base A', 'PT Traditional A'],
      ['pt112-v3', 3, 'en_us', 'zh_tw', 'PT Base B', 'PT Traditional B'],
      ['pt112-escapes', 3, 'en_us', 'zh_tw', 'PT Base C', '#PARSE_ESCAPES\nitem.diamond.name=PT\\u947D\\u77F3']
    ];
    for (const [name, format, primaryCode, secondaryCode, primary, secondary] of packs) {
      const packRoot = path.join(fixture, 'resourcepacks', name);
      const lang = path.join(packRoot, 'assets/minecraft/lang');
      await fs.mkdir(lang, {recursive: true});
      await fs.writeFile(path.join(packRoot, 'pack.mcmeta'), JSON.stringify({pack: {pack_format: format, description: name}}));
      await fs.writeFile(path.join(lang, `${primaryCode}.lang`), `item.diamond.name=${primary}\n`);
      await fs.writeFile(path.join(lang, `${secondaryCode}.lang`), secondary.startsWith('#') ? `${secondary}\n` : `item.diamond.name=${secondary}\n`);
    }
    await call('resources.select', {packs: ['pt112-v2']});
    const legacyPack = await probe('legacyPack', cases.diamond);
    check('format2 uppercase primary locale', legacyPack.nativeName, 'PT Base A');
    check('format2 uppercase secondary locale', legacyPack.aliases, ['PT Traditional A']);
    await call('resources.select', {packs: ['pt112-v2', 'pt112-v3']});
    const orderedPack = await probe('resourcePriority', cases.diamond);
    check('resource order native priority', orderedPack.nativeName, 'PT Base B');
    check('resource order secondary priority', orderedPack.aliases, ['PT Traditional B']);
    await call('resources.select', {packs: ['pt112-escapes']});
    check('Forge escape parsing', (await probe('escapedPack', cases.diamond)).aliases, ['PT鑽石']);
    await call('resources.select', {packs: []});
    const restored = await probe('resourcesRemoved', cases.diamond);
    check('resource removal restores native', restored.nativeName, 'Diamond');
    check('resource removal restores secondary', restored.aliases, ['鑽石']);
    check('generation advances', restored.generation > observations.diamond.generation, true);
  }
} catch (error) {
  checks.push({name: 'runner', pass: false, error: error.stack});
} finally {
  bridge.close();
  await fs.mkdir(path.dirname(output), {recursive: true});
  await fs.writeFile(output, JSON.stringify({checks, observations}, null, 2));
}
const failures = checks.filter(check => !check.pass);
console.log(JSON.stringify({passed: checks.length - failures.length, failed: failures.length, failures, output}, null, 2));
if (failures.length) process.exitCode = 1;
