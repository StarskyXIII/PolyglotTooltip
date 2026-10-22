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
const configure = async languages => { await call('config.set', {languages}); await call('wait.ticks', {ticks: 5}); };
const ui = (params = {}) => call('addon.ui', params);
const caret = value => ({query: value.query, cursor: value.cursor, selection: value.selection, focused: value.focused});
try {
  observations.runtime = await call('status');
  assert.equal(path.resolve(observations.runtime.gameDirectory).toLowerCase(), fixture.toLowerCase());
  assert.equal(observations.runtime.pid, String(launch.pid));
  const loaded = id => observations.runtime.mods.some(mod => mod.id === id), product = loaded('polyglottooltip');
  const modes = [...(loaded('extracells') ? ['ec'] : []), ...(loaded('mekeng') ? ['gas', 'gasConfig'] : []), ...(loaded('thaumicenergistics') ? ['the'] : [])];
  assert.ok(modes.length);
  if (observations.runtime.language !== 'en_us') await call('language.set', {language: 'en_us'});
  await call('resources.select', {packs: []});
  if (product) await configure([]);
  if (!(await call('status')).world) await call('world.create', {name: 'pt112-addon-world'});
  for (let index = 0; index < 90 && !(await call('status')).player; index++) await call('wait.ticks', {ticks: 20});
  assert.equal((await call('status')).player, true);
  observations.modes = {};
  for (const mode of modes) {
    await call('screen.close'); await call('addon.world', {mode}); await call('wait.ticks', {ticks: 60});
    const opened = await call('addon.world', {mode, open: true}); await call('wait.ticks', {ticks: 20});
    check(`${mode} native part opens GUI`, opened.opened, true);
    if (mode !== 'gasConfig') check(`${mode} network powered`, opened.powered, true);
    const data = observations.modes[mode] = {};
    data.native = await ui({query: '', focused: true});
    check(`${mode} actual network supplied rows`, data.native.rows.length > 0, true);
    if (mode === 'the' && data.native.optimized) {
      const nativeDisabled = await ui({query: 'pt112-native-absent', tooltips: false});
      check('ThE optimized native GUI disabled-tooltip path accepts all', nativeDisabled.rows, data.native.rows);
      await ui({query: '', tooltips: true});
    }
    await call('language.set', {language: 'zh_cn'}); data.target = await ui();
    await call('language.set', {language: 'en_us'});
    const source = data.native.nativeName.toLowerCase(), alias = data.target.nativeName;
    assert.notEqual(source, alias.toLowerCase());
    data.nativeQuery = await ui({query: source});
    check(`${mode} native name finds stored content`, data.nativeQuery.rows.length > 0, true);
    data.nativeAlias = await ui({query: alias, cursor: 1, selection: Math.min(2, alias.length), focused: true});
    check(`${mode} native alias query absent`, data.nativeAlias.rows, []);
    if (mode === 'ec') await ui({select: 'water'});
    if (mode === 'gas' || mode === 'ec') {
      await ui({query: source}); data.nativeHover = await call('addon.hover');
      check(`${mode} positive native hover captured`, data.nativeHover.lines.length > 0, true);
      if (mode === 'ec') {
        data.nativeRequest = await call('addon.hover', {request: true});
        check('EC positive request capture', data.nativeRequest.handled && data.nativeRequest.lines.length >= 2, true);
        check('EC selector zero amount does not draw', (await call('addon.hover', {zero: true})).lines, []);
        check('EC selector outside does not draw', (await call('addon.hover', {outside: true})).lines, []);
        check('EC request outside does not draw', (await call('addon.hover', {request: true, outside: true})).lines, []);
      }
    }
    if (!product) continue;
    const before = await ui({query: alias, cursor: 1, selection: Math.min(2, alias.length), focused: true});
    await configure(['zh_cn']); data.enabled = await ui();
    check(`${mode} unchanged-query native view refresh`, data.enabled.rows, data.nativeQuery.rows);
    check(`${mode} query caret highlight focus retained`, caret(data.enabled), caret(before));
    if (mode === 'ec') {
      check('EC selected fluid retained', data.enabled.selected, 'water');
      check('EC alias cannot bypass native storage type', (await ui({type: 'GAS'})).rows, []);
      await ui({type: 'FLUID'});
      const request = await call('addon.hover', {request: true});
      check('EC request Craftable label remains first', request.lines[0], data.nativeRequest.lines[0]);
      check('EC request alias follows fluid title', plain(request.lines[2]), alias);
      check('EC request native body retained', request.lines.filter(line => plain(line) !== alias), data.nativeRequest.lines);
    }
    if (mode === 'ec' || mode === 'gas') {
      data.enabledHover = await call('addon.hover');
      check(`${mode} native draw adds one alias`, data.enabledHover.lines.filter(line => plain(line) === alias).length, 1);
      check(`${mode} native draw body amount retained`, data.enabledHover.lines.filter(line => plain(line) !== alias), data.nativeHover.lines);
      if (mode === 'gas') {
        const name = `addon-gas-${Date.now()}.png`;
        data.rendered = await call('screen.render', {name, x: data.enabledHover.x, y: data.enabledHover.y});
        check('gas terminal rendered frame includes alias', data.rendered.lines.filter(line => plain(line) === alias).length, 1);
        await fs.copyFile(data.rendered.path, path.join(path.dirname(output), 'addon-gas.png'));
      }
    }
    if (mode === 'gasConfig') {
      for (const [query, expected] of [[`${alias} -${alias}`, false], [`${alias} !${alias}`, false], [`-${alias}`, false], [`!${alias}`, false], [`${alias} -pt112absent`, true]]) check(`gas interface token ${query}`, (await ui({query})).rows.length > 0, expected);
      await ui({query: alias});
    }
    await configure([]); const disabled = await ui();
    check(`${mode} disabling removes alias without new packets`, disabled.rows, []);
    check(`${mode} disabling keeps query`, disabled.query, alias);
    await call('language.set', {language: 'zh_cn'}); await call('wait.ticks', {ticks: 5});
    check(`${mode} primary-language refresh`, (await ui()).rows.length > 0, true);
    await call('language.set', {language: 'en_us'}); await call('wait.ticks', {ticks: 5});
    check(`${mode} previous primary removed`, (await ui()).rows, []);
    if (baselinePath) {
      const baseline = JSON.parse(await fs.readFile(baselinePath, 'utf8')).observations.modes[mode];
      check(`${mode} initial network results equal native baseline`, data.nativeQuery.rows, baseline.nativeQuery.rows);
      if (data.nativeHover) check(`${mode} initial tooltip equals native baseline`, data.nativeHover.lines, baseline.nativeHover.lines);
    }
  }
} catch (error) { checks.push({name: 'runner', pass: false, error: error.stack}); }
finally { bridge.close(); await fs.mkdir(path.dirname(output), {recursive: true}); await fs.writeFile(output, JSON.stringify({launch, checks, observations}, null, 2)); }
const failures = checks.filter(check => !check.pass);
console.log(JSON.stringify({passed: checks.length - failures.length, failed: failures.length, failures, output}, null, 2));
if (failures.length) process.exitCode = 1;
