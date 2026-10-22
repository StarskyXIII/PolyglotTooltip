import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {Bridge} from '../../name-audit/runner/bridge.mjs';
import {root, read, save, hashFile, sameOwned, pause} from '../name-table/runtime-common.mjs';

const report = path.join(root, 'docs/research/legacy112/name-table-reuse-2026-10-14');
const pack = process.argv[2];
const attempt = process.argv[3] ?? 'selective-measure2';
assert.ok(['E2E', 'Meatball'].includes(pack));
assert.match(attempt, /^selective-measure[0-9]+$/);
const run = path.join(report, `run-${pack.toLowerCase()}-${attempt}`);
const evidence = path.join(report, `measurement-${pack.toLowerCase()}-${attempt}`);
const config = await read(path.join(run, 'config.json')), owner = await read(path.join(run, 'process.json'));
const selectionPath = path.join(root, 'docs/research/legacy112/name-table-prototype-2026-10-14/corpus-final', pack, 'runtime-selection.json');
const seal = await read(path.join(path.dirname(selectionPath), 'runtime-selection-seal.json'));
assert.equal(await hashFile(selectionPath), seal.sha256);
const samples = (await read(selectionPath)).acquisition;
await fs.copyFile(selectionPath, path.join(run, 'runtime-selection.json'), fs.constants.COPYFILE_EXCL);
await save(path.join(run, 'runtime-selection-seal.json'), seal);
const controllerSources = [];
await fs.mkdir(path.join(run, 'controller-sources'));
for (const name of ['measure.mjs', 'measure.py']) {
  const copy = path.join(run, 'controller-sources', name);
  await fs.copyFile(path.join(root, 'test-tools/legacy112/name-table-reuse', name), copy, fs.constants.COPYFILE_EXCL);
  controllerSources.push({name, sha256: await hashFile(copy)});
}
await recordSourceSeal();
async function recordSourceSeal() { await save(path.join(run, 'controller-source-seal.json'), controllerSources); }
let bridge, owned = false, stage = 'startup', initialLanguage;
const record = (label, value) => save(path.join(run, label + '.json'), value);
const call = (method, params = {}, timeout = 360000) => bridge.call(method, params, timeout);
async function progress(value = {}) {
  const state = {at: new Date().toISOString(), stage, ...value};
  await fs.writeFile(path.join(run, 'progress.json'), JSON.stringify(state, null, 2));
  console.log(JSON.stringify(state));
}
async function snapshot(label) {
  const target = path.join(evidence, label);
  await fs.mkdir(target);
  const rows = [];
  async function visit(source, relative = '') {
    for (const entry of await fs.readdir(source, {withFileTypes: true})) {
      assert.ok(!entry.isSymbolicLink(), 'Symlink in fixture config');
      const next = path.join(relative, entry.name), input = path.join(source, entry.name), output = path.join(target, next);
      if (entry.isDirectory()) { await fs.mkdir(output); await visit(input, next); }
      else if (entry.isFile()) {
        const before = await hashFile(input);
        await fs.copyFile(input, output, fs.constants.COPYFILE_EXCL);
        const copied = await hashFile(output), after = await hashFile(input);
        rows.push({path: next.replaceAll('\\', '/'), sha256: copied, bytes: (await fs.stat(output)).size,
          stableDuringCopy: before === copied && copied === after, inputBefore: before, inputAfter: after});
      }
    }
  }
  await visit(path.join(config.fixture, 'config'));
  await save(path.join(evidence, label + '-manifest.json'), rows.sort((a, b) => a.path.localeCompare(b.path)));
}
async function ready() {
  const deadline = Date.now() + 300000;
  while (Date.now() < deadline) {
    const state = await call('probe.performance', {action: 'state'});
    if (state.ready && !state.libraryPending) return state;
    await pause(300);
  }
  throw Error('Language readiness deadline');
}
async function language(code, label) {
  stage = label;
  const before = await call('status'), started = Date.now();
  const result = {from: before.language, to: code, before, changed: before.language !== code};
  try {
    if (result.changed) result.returned = await call('language.set', {language: code});
    result.callerElapsedMs = Date.now() - started;
    result.ready = await ready(); result.readyElapsedMs = Date.now() - started;
    result.after = await call('status');
    assert.equal(result.after.language, code);
    result.complete = true;
  } catch (error) { result.error = String(error); throw error; }
  finally { await record(label, result); await progress({from: result.from, to: result.to, complete: result.complete,
    callerElapsedMs: result.callerElapsedMs, readyElapsedMs: result.readyElapsedMs, error: result.error}); }
}
async function capture(label) {
  stage = label; await ready();
  const folder = path.join(run, label + '-batches'); await fs.mkdir(folder);
  const rows = [], contexts = [], started = Date.now();
  for (let offset = 0; offset < samples.length; offset += 16) {
    const selected = samples.slice(offset, offset + 16);
    const result = await call('probe.nameTable', {action: 'capture', samples: selected});
    await save(path.join(folder, String(offset).padStart(6, '0') + '.json'), {offset, selectedIds: selected.map(x => x.sampleId), response: result});
    assert.equal(result.contextChanged, false, 'Context changed during acquisition');
    assert.deepEqual(result.rows.map(x => x.sampleId), selected.map(x => x.sampleId));
    rows.push(...result.rows);
    const {rows: ignored, ...context} = result; contexts.push(context);
    if (offset % 256 === 0) await progress({captured: rows.length, total: samples.length});
  }
  await record(label, {elapsedMs: Date.now() - started, rows, contexts});
}
try {
  const deadline = Date.now() + 900000;
  while (!bridge && Date.now() < deadline) {
    try { bridge = await Bridge.connect(config.port); }
    catch { await pause(1500); }
  }
  assert.ok(bridge, 'Client bridge startup timeout');
  let menu;
  while (Date.now() < deadline) {
    try { menu = await call('status', {}, 15000); }
    catch { await pause(1500); continue; }
    sameOwned(menu, config, owner); owned = true;
    if (['net.minecraft.client.gui.GuiMainMenu', 'lumien.custommainmenu.gui.GuiCustom'].includes(menu.screen)
        && !menu.world && menu.language === 'zh_tw') break;
    await pause(1000);
  }
  assert.equal(menu.selectiveResourceReload, true, 'Actual selective flag must be enabled');
  assert.equal(menu.world, false);
  initialLanguage = menu.language;
  await record('menu', menu); await record('menu-runtime', await call('pack.runtime'));
  await record('menu-adapter', await call('probe.performance', {action: 'state'}));
  await snapshot('config-at-menu');
  stage = 'world'; await progress();
  await record('pack-setup', await call('pack.setup'));
  await call('world.create', {name: config.worldName});
  const worldDeadline = Date.now() + 360000;
  let state;
  while (Date.now() < worldDeadline) {
    state = await call('status');
    if (state.world && state.player) break;
    await pause(500);
  }
  assert.ok(state.world && state.player, 'World and player not ready');
  await call('wait.ticks', {ticks: 60}); await call('screen.close');
  const beforeReady = await call('probe.performance', {action: 'state'});
  await record('world-adapter-before-conditioning', beforeReady);
  await record('world-native-language-before-conditioning', await call('lifecycle.translations'));
  if (!beforeReady.ready) {
    const start = Date.now(), returned = await call('language.set', {language: initialLanguage});
    await record('conditioning-current-language-reload', {reason: 'Existing adapter not ready after world entry',
      elapsedMs: Date.now() - start, sameLanguage: true, returned});
  }
  await ready(); await record('world', state); await record('world-runtime', await call('pack.runtime'));
  await record('world-native-language-ready', await call('lifecycle.snapshot'));
  await snapshot('config-at-world');
  await capture('primary-first');
  await language('en_us', 'switch-en-us'); await snapshot('config-after-en-us-switch'); await capture('en-us');
  await language('zh_cn', 'switch-zh-cn'); await snapshot('config-after-zh-cn-switch'); await capture('zh-cn');
  await language('zh_tw', 'switch-primary'); await capture('primary-repeat');
  await snapshot('config-after-cycle');
  await record('final-runtime', await call('pack.runtime'));
  await record('complete', {at: new Date().toISOString(), samples: samples.length});
  stage = 'complete'; await progress();
} catch (error) {
  await record('failure', {stage, error: String(error), stack: error.stack}); process.exitCode = 1;
} finally {
  if (bridge && owned && !await fs.stat(path.join(run, 'exit.json')).catch(() => null)) {
    try {
      const last = await call('status', {}, 20000); sameOwned(last, config, owner);
      if (initialLanguage && last.language !== initialLanguage) await language(initialLanguage, 'finally-restore-language');
      await snapshot('config-before-quit');
    } catch (error) { await record('restore-language-failure', {error: String(error)}); process.exitCode = 1; }
    try { await call('quit', {}, 20000); } catch (error) { await record('quit-result', {error: String(error)}); }
    bridge.close();
  }
}
