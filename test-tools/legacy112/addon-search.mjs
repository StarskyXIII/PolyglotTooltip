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
const check = (name, actual, expected) => {
  try { assert.deepEqual(actual, expected); checks.push({name, pass: true}); }
  catch { checks.push({name, pass: false, actual, expected}); }
};
const configure = async languages => { await call('config.set', {languages}); await call('wait.ticks', {ticks: 5}); };
const query = (owner, value, extra = {}) => call('addon.search.query', {owner, query: value, ...extra});
const state = () => call('addon.search.observe');
const caret = value => ({query: value.query, cursor: value.cursor, selection: value.selection, focused: value.focused});
try {
  observations.runtime = await call('status');
  assert.equal(path.resolve(observations.runtime.gameDirectory).toLowerCase(), fixture.toLowerCase());
  assert.equal(observations.runtime.pid, String(launch.pid));
  const product = observations.runtime.mods.some(mod => mod.id === 'polyglottooltip');
  if (observations.runtime.language !== 'en_us') await call('language.set', {language: 'en_us'});
  await call('resources.select', {packs: []});
  if (product) await configure([]);
  if (!(await call('status')).world) await call('world.create', {name: 'pt112-addon-search'});
  for (let index = 0; index < 90 && !(await call('status')).player; index++) await call('wait.ticks', {ticks: 20});
  assert.equal((await call('status')).player, true);
  await call('screen.close');
  observations.native = await call('addon.search.setup');
  check('native search owners populated', Object.keys(observations.native).some(key => key !== 'nativeNames'), true);
  await call('language.set', {language: 'zh_cn'});
  observations.target = await state();
  await call('language.set', {language: 'en_us'});
  const native = observations.native.nativeNames, target = observations.target.nativeNames;
  observations.nativeQueries = {};
  if (observations.native.lazy) {
    const cached = observations.nativeQueries.lazy = {};
    for (const term of [native.diamond.toLowerCase(), native.apple.toLowerCase(), native.iron.toLowerCase(), target.diamond, 'encoded pattern', native.diamond.toLowerCase() + target.diamond, native.diamond.toLowerCase() + native.apple.toLowerCase(), 'pt112-absent']) {
      cached[term] = (await query('lazy', term)).lazy.map(slot => slot.matches);
    }
    check('Lazy actual two-output pattern native output', cached[native.diamond.toLowerCase()], [true, true]);
    check('Lazy pattern input not searchable', cached[native.iron.toLowerCase()], [false, false]);
    check('Lazy target absent from native cache', cached[target.diamond], [false, false]);
    if (product) {
      await query('lazy', target.diamond); await configure(['zh_cn']);
      check('Lazy cached false invalidates on both pages', (await state()).lazy.map(slot => slot.matches), [true, true]);
      check('Lazy retains query on hidden page', (await state()).lazy.map(slot => slot.query), [target.diamond, target.diamond]);
      await configure([]);
      check('Lazy cached true invalidates on both pages', (await state()).lazy.map(slot => slot.matches), [false, false]);
      await configure(['zh_cn']);
      for (const [term, expected] of Object.entries(cached)) check(`Lazy native output grammar ${term}`, (await query('lazy', term)).lazy.map(slot => slot.matches), term === target.diamond ? [true, true] : expected);
      await configure([]); await query('lazy', native.diamond.toLowerCase());
      await call('language.set', {language: 'zh_cn'}); await call('wait.ticks', {ticks: 5});
      check('Lazy old primary output cache removed', (await state()).lazy.map(slot => slot.matches), [false, false]);
      check('Lazy new primary output available', (await query('lazy', target.diamond)).lazy.map(slot => slot.matches), [true, true]);
      await call('language.set', {language: 'en_us'});
    }
  }
  if (observations.native.nee) {
    const cached = observations.nativeQueries.nee = {};
    for (const term of [native.diamond.toLowerCase(), native.diamond.toUpperCase(), target.diamond, '@minecraft', `@${target.diamond}`, 'pt112-absent', native.diamond.toLowerCase() + target.diamond]) cached[term] = (await query('nee', term)).nee.rows;
    check('NEE native diamond count', cached[native.diamond.toLowerCase()].length, 20);
    check('NEE native raw uppercase query remains unmatched', cached[native.diamond.toUpperCase()], []);
    check('NEE target absent natively', cached[target.diamond], []);
    if (product) {
      const before = (await query('nee', target.diamond, {cursor: 1, selection: 2})).nee;
      await configure(['zh_cn']); const refreshed = (await state()).nee;
      check('NEE unchanged query rebuild finds translated items', refreshed.rows, cached[native.diamond.toLowerCase()]);
      check('NEE text caret selection focus preserved', caret(refreshed), caret(before));
      for (const [term, expected] of Object.entries(cached)) check(`NEE native grammar ${term}`, (await query('nee', term)).nee.rows, term === target.diamond ? cached[native.diamond.toLowerCase()] : expected);
      const selected = (await query('nee', '', {select: 0, offset: 2})).nee;
      await call('resources.reload'); await call('wait.ticks', {ticks: 5});
      const retained = (await state()).nee;
      check('NEE offscreen logical selection retained', retained.selected, selected.selected);
      check('NEE valid offset retained', retained.offset, selected.offset);
      check('NEE viewport rebuilt from filtered rows', retained.visible, retained.rows.slice(retained.offset * 4, retained.offset * 4 + 16));
      check('NEE refresh emits no apply callback', retained.applications, 0);
      const visible = (await query('nee', target.diamond, {select: 4, offset: 1})).nee;
      await call('resources.reload'); await call('wait.ticks', {ticks: 5});
      check('NEE visible logical selection retained', (await state()).nee.selected, visible.selected);
      await configure([]); const removed = (await state()).nee;
      check('NEE removed translation clears selected item', removed.selected, '');
      check('NEE empty results clamp offset', removed.offset, 0);
      check('NEE unchanged query removes aliases', removed.rows, []);
    }
  }
  if (observations.native.p2p) {
    const types = observations.native.p2p.types;
    const type = types.find(row => /item/i.test(row.currentNative)); assert.ok(type);
    const targetType = observations.target.p2p.types.find(row => row.index === type.index); assert.ok(targetType);
    assert.notEqual(type.currentNative, targetType.currentNative);
    const source = type.currentNative.toLowerCase(), alias = targetType.currentNative.toLowerCase();
    const cached = observations.nativeQueries.p2p = {};
    for (const term of [`@type=${source}`, `@type=${alias}`, `@type=${source} @in`, `@type=${source} @out`, `@type=${source} @b`, `@type=${source} @u`, `@type=absent;${source}`, `@type=${source} input`, `@type=${source} absent`, `@unknown=${alias}`, alias, `"input literal"`]) cached[term] = (await query('p2p', term, {select: -1})).p2p.rows;
    check('P2P native type selects paired rows', cached[`@type=${source}`].length, 2);
    check('P2P alias absent natively', cached[`@type=${alias}`], []);
    if (product) {
      const before = (await query('p2p', `@type=${alias}`, {cursor: 2, selection: 4})).p2p;
      await configure(['zh_cn']); const refreshed = (await state()).p2p;
      check('P2P native parser accepts alias on unchanged query', refreshed.rows, cached[`@type=${source}`]);
      check('P2P caret focus selection retained', caret(refreshed), caret(before));
      check('P2P alias OR stays within TYPE', (await query('p2p', `@type=absent;${alias} @in`)).p2p.rows, cached[`@type=${source} @in`]);
      check('P2P alias plus NAME stays AND', (await query('p2p', `@type=${alias} absent`)).p2p.rows, []);
      check('P2P TYPE aliases do not enter custom label domain', (await query('p2p', alias)).p2p.rows, []);
      for (const [term, expected] of Object.entries(cached)) check(`P2P native grammar ${term}`, (await query('p2p', term)).p2p.rows, term === `@type=${alias}` ? cached[`@type=${source}`] : expected);
      const selected = (await query('p2p', '@type=pt112-absent', {select: 0})).p2p;
      check('P2P native selected row bypass', selected.rows.length, 1);
      await configure([]);
      check('P2P generation retains selected row bypass', (await state()).p2p.rows, selected.rows);
      check('P2P generation retains selected location', (await state()).p2p.selected, selected.selected);
      await query('p2p', `@type=${alias}`, {select: -1});
      await call('language.set', {language: 'zh_cn'}); await call('wait.ticks', {ticks: 5});
      check('P2P recomputes previously cached primary type', (await state()).p2p.rows, cached[`@type=${source}`]);
      await call('language.set', {language: 'en_us'}); await call('wait.ticks', {ticks: 5});
      check('P2P previous primary type removed', (await state()).p2p.rows, []);
      await configure(['zh_cn']);
      for (const sourceType of types.filter(value => /^(Item|Fluid|Redstone)$/i.test(value.currentNative))) {
        const translatedType = observations.target.p2p.types.find(value => value.index === sourceType.index);
        const rows = (await query('p2p', `@type=${translatedType.currentNative.toLowerCase()}`, {select: -1})).p2p.rows;
        check(`P2P native ${sourceType.currentNative} composed type alias`, rows.length === 2 && rows.every(row => row.type === sourceType.index), true);
      }
      await configure([]);
    }
    const pack = 'pt112-p2p-composition', resource = path.join(fixture, 'resourcepacks', pack);
    await fs.mkdir(path.join(resource, 'assets', 'appliedenergistics2', 'lang'), {recursive: true});
    await fs.writeFile(path.join(resource, 'pack.mcmeta'), JSON.stringify({pack: {pack_format: 3, description: 'Native P2P composition fixture'}}));
    await fs.writeFile(path.join(resource, 'assets', 'appliedenergistics2', 'lang', 'zh_cn.lang'), 'item.appliedenergistics2.multi_part.p2p_tunnel.name=FixtureRoot - FixtureMiddle\ngui.appliedenergistics2.ItemTunnel=FixtureType - FixtureTail\n');
    const allRows = (await query('p2p', '', {select: -1})).p2p.rows;
    await call('resources.select', {packs: [pack]}); await call('language.set', {language: 'zh_cn'});
    observations.composedPrimary = await state();
    check('P2P native complete titles select second component', observations.composedPrimary.p2p.types.map(type => type.currentNative), types.map(() => 'FixtureMiddle'));
    const nativeComposite = (await query('p2p', '@type=fixturemiddle', {select: -1})).p2p;
    check('P2P resource change follows native cached type or refreshed product', nativeComposite.rows, product ? allRows : []);
    check('P2P native split excludes later extra component', (await query('p2p', '@type=fixturetype')).p2p.rows, []);
    await call('language.set', {language: 'en_us'});
    check('P2P composition resource absent in native primary', (await query('p2p', '@type=fixturemiddle')).p2p.rows, []);
    if (product) {
      await configure(['zh_cn']);
      check('P2P aliases split the entire composed target title', (await state()).p2p.rows, allRows);
      check('P2P aliases exclude later target type component', (await query('p2p', '@type=fixturetype')).p2p.rows, []);
      await configure([]);
    }
    await call('resources.select', {packs: []});
  }
  if (product && baselinePath) check('disabled native query outcomes equal separate baseline', observations.nativeQueries, JSON.parse(await fs.readFile(baselinePath, 'utf8')).observations.nativeQueries);
} catch (error) { checks.push({name: 'runner', pass: false, error: error.stack}); }
finally { bridge.close(); await fs.mkdir(path.dirname(output), {recursive: true}); await fs.writeFile(output, JSON.stringify({launch, checks, observations}, null, 2)); }
const failures = checks.filter(check => !check.pass);
console.log(JSON.stringify({passed: checks.length - failures.length, failed: failures.length, failures, output}, null, 2));
if (failures.length) process.exitCode = 1;
