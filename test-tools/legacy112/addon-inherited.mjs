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
const ui = (params = {}) => call('addon.inherited.ui', params);
const ids = value => value.rows.map(row => row.id).sort();
const caret = value => ({query: value.query, cursor: value.cursor, selection: value.selection, focused: value.focused});
const search = async text => {
  const previous = await ui({focused: true});
  await call('input.key', {key: 207});
  if (previous.query.length) await call('input.key', {key: 14, repeat: previous.query.length});
  await call('input.type', {text}); await call('wait.ticks', {ticks: 3}); return ui();
};
try {
  observations.runtime = await call('status');
  assert.equal(path.resolve(observations.runtime.gameDirectory).toLowerCase(), fixture.toLowerCase());
  assert.equal(observations.runtime.pid, String(launch.pid));
  const loaded = id => observations.runtime.mods.some(mod => mod.id === id), product = loaded('polyglottooltip');
  const modes = [...(loaded('wct') ? ['wct'] : []), ...(loaded('ae2exttable') ? ['ext'] : []), ...(loaded('ae2fc') ? ['fc', 'fcExpanded', 'fcWireless'] : [])];
  assert.ok(modes.length);
  if (observations.runtime.language !== 'en_us') await call('language.set', {language: 'en_us'});
  await call('resources.select', {packs: []}); if (product) await configure([]);
  if (!(await call('status')).world) await call('world.create', {name: 'pt112-inherited'});
  for (let index = 0; index < 90 && !(await call('status')).player; index++) await call('wait.ticks', {ticks: 20});
  assert.equal((await call('status')).player, true); observations.modes = {};
  for (const mode of modes) {
    const open = async () => {
      await call('screen.close');
      const value = mode === 'wct' ? await call('addon.inherited.open') : await call('addon.inherited.world', {mode, action: 'open'});
      await call('wait.ticks', {ticks: 20}); return value;
    };
    await call('screen.close'); const data = observations.modes[mode] = {};
    data.prepared = await call('addon.inherited.world', {mode}); await call('wait.ticks', {ticks: 60});
    if (mode === 'wct' || mode === 'fcWireless') {
      data.linked = await call('addon.inherited.world', {mode, action: 'link'});
      for (const key of ['powered', 'accessActive', 'sameGrid', 'registered', 'charged']) check(`${mode} native wireless ${key}`, data.linked[key], true);
      check(`${mode} native station owner`, data.linked.owner, data.linked.playerId);
      await call('wait.ticks', {ticks: 20});
    }
    data.opened = await open(); data.native = await ui();
    check(`${mode} uses native item repository`, data.native.repoClass, 'appeng.client.me.ItemRepo');
    check(`${mode} real network exceeds screen capacity`, data.native.rows.length > data.native.capacity, true);
    data.diamond = await search('diamond');
    check(`${mode} native input finds diamond variants`, ids(data.diamond), ['minecraft:diamond', 'minecraft:diamond_block', 'minecraft:diamond_ore']);
    check(`${mode} native stored quantity`, data.diamond.rows.map(row => row.count), [64, 64, 64]);
    const pack = 'pt112-inherited', resource = path.join(fixture, 'resourcepacks', pack);
    await fs.mkdir(path.join(resource, 'assets', 'minecraft', 'lang'), {recursive: true});
    await fs.writeFile(path.join(resource, 'pack.mcmeta'), JSON.stringify({pack: {pack_format: 3, description: 'Native terminal fixture'}}));
    await fs.writeFile(path.join(resource, 'assets', 'minecraft', 'lang', 'zh_cn.lang'), Object.entries(data.prepared.names).map(([key, value]) => `${key}=${value}`).join('\n'));
    await call('resources.select', {packs: [pack]});
    await call('language.set', {language: 'zh_cn'}); await open(); data.primary = await search('ptwide');
    check(`${mode} native resource names exceed screen capacity`, data.primary.rows.length > data.primary.capacity, true);
    await call('language.set', {language: 'en_us'}); await open(); data.absent = await search('ptwide');
    check(`${mode} native alternate language absent`, data.absent.rows, []);
    if (product) {
      const input = await ui({cursor: 2, selection: 4, focused: true});
      const expanded = await call('screen.render', {name: `inherited-${mode}-enabled-${Date.now()}.png`, x: 1, y: 1, inherited: true, configuration: {languages: ['zh_cn']}});
      data.expanded = expanded;
      check(`${mode} first draw expands native alias view`, ids(expanded.after), ids(data.primary));
      check(`${mode} first draw restores positive scrollbar range`, expanded.after.scroll.maxScroll > 0, true);
      check(`${mode} first draw keeps text caret selection`, caret(expanded.after), caret(input));
      data.scrolled = await ui({scrollToEnd: true});
      check(`${mode} native scrollbar reaches nonzero end`, data.scrolled.scroll.currentScroll > 0 && data.scrolled.scroll.currentScroll === data.scrolled.scroll.maxScroll, true);
      const shrunk = await call('screen.render', {name: `inherited-${mode}-disabled-${Date.now()}.png`, x: 1, y: 1, inherited: true, configuration: {languages: []}});
      data.shrunk = shrunk;
      check(`${mode} generation changes repository before draw`, shrunk.changed.rows, []);
      check(`${mode} no intervening tick updated old scrollbar`, shrunk.changed.scroll, shrunk.before.scroll);
      check(`${mode} first draw clears alias view`, shrunk.after.rows, []);
      check(`${mode} first draw clamps native scrollbar`, shrunk.after.scroll, {currentScroll: 0, minScroll: 0, maxScroll: 0});
      check(`${mode} shrinking keeps text caret selection`, caret(shrunk.after), caret(data.scrolled));
      await configure(['zh_cn']); data.restored = await ui();
      check(`${mode} repeated refresh restores entries`, ids(data.restored), ids(data.primary));
      await fs.copyFile(expanded.path, path.join(path.dirname(output), `inherited-${mode}.png`));
      await configure([]);
      if (baselinePath) {
        const baseline = JSON.parse(await fs.readFile(baselinePath, 'utf8')).observations.modes[mode];
        check(`${mode} initial results match native baseline`, data.diamond.rows, baseline.diamond.rows);
        check(`${mode} native resource results match baseline`, ids(data.primary), ids(baseline.primary));
      }
    }
    await call('resources.select', {packs: []});
  }
} catch (error) { checks.push({name: 'runner', pass: false, error: error.stack}); }
finally { bridge.close(); await fs.mkdir(path.dirname(output), {recursive: true}); await fs.writeFile(output, JSON.stringify({launch, checks, observations}, null, 2)); }
const failures = checks.filter(check => !check.pass);
console.log(JSON.stringify({passed: checks.length - failures.length, failed: failures.length, failures, output}, null, 2));
if (failures.length) process.exitCode = 1;
