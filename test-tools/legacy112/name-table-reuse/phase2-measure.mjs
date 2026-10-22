// Phase-2 measurement controller (phase1-runtime.mjs with the phase-2 report folder): one owned Meatball launch, build or reuse, with the
// same observation windows as run-meatball-core-background-full-4.
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {Bridge} from '../../name-audit/runner/bridge.mjs';
import {root, read, save, hashFile, sameOwned, pause} from '../name-table/runtime-common.mjs';
import {checkEnchantments, enchantmentSummary, MEATBALL_ENCHANTED} from './enchantment-cases.mjs';

const [pack, attempt, mode] = process.argv.slice(2);
assert.equal(pack, 'Meatball');
assert.match(attempt, /^phase2-[a-z0-9-]+$/);
assert.ok(['build', 'reuse', 'warmup'].includes(mode));
const report = path.join(root, 'docs/research/legacy112/name-table-phase2-diagnosis-2026-10-16');
const run = path.join(report, `run-${pack.toLowerCase()}-${attempt}`);
const config = await read(path.join(run, 'config.json')), owner = await read(path.join(run, 'process.json'));
// Baseline windows: background 37,824 ms from world readiness, idle 22,450 ms after search readiness.
const BACKGROUND_MS = 37824, IDLE_MS = 22450, INVENTORY_AT_MS = 9500, POLL_MS = 500;
// collection-window: restart the background window when collection is first seen, so it covers the
// build and alias preparation even when input checks delay collection well past world readiness.
const collectionWindow = process.argv.includes('collection-window');
const COLLECTION_INVENTORY_MS = 2800;
let bridge, owned = false;
const call = (method, params = {}, timeout = 240000) => bridge.call(method, params, timeout);
const record = (name, value) => save(path.join(run, name + '.json'), value);
const observations = [];
const performanceState = async () => call('probe.performance', {action: 'state'});
const observe = async label => {
  const input = await call('probe.nameReuse'), performance = await performanceState(), status = await call('status');
  const value = {at: new Date().toISOString(), monotonicMs: performance_now(), label, table: input.table,
    aliasReady: performance.aliasReady ?? false, aliasCursor: performance.aliasCursor, aliasSize: performance.aliasSize,
    aliasIndexed: performance.aliasIndexed, aliasFallback: performance.aliasFallback, aliasMissing: performance.aliasMissing,
    screen: status.screen, appliedState: input.appliedState, reasons: input.reasons, inputStatus: input.status};
  observations.push(value);
  await fs.writeFile(path.join(run, 'progress.json'), JSON.stringify(value, null, 2));
  return value;
};
const started = process.hrtime.bigint();
function performance_now() { return Number(process.hrtime.bigint() - started) / 1e6; }
const persisted = async table => {
  const scopes = path.join(config.fixture, 'polyglottooltip/name-tables/contexts');
  for (const scope of await fs.readdir(scopes)) {
    const file = path.join(scopes, scope, `generation-${table.tableGeneration}.pnt`);
    const stat = await fs.stat(file, {bigint: true}).catch(() => null);
    if (stat) return {path: file, scope, generation: table.tableGeneration, sha256: await hashFile(file),
      modifiedNs: stat.mtimeNs.toString(), bytes: stat.size.toString()};
  }
  return null;
};
try {
  const startupDeadline = Date.now() + 900000;
  while (!bridge && Date.now() < startupDeadline) {
    try { bridge = await Bridge.connect(config.port); } catch { await pause(1500); }
  }
  assert.ok(bridge, 'Bridge startup deadline');
  let state;
  while (Date.now() < startupDeadline) {
    try { state = await call('status'); } catch { await pause(1500); continue; }
    sameOwned(state, config, owner); owned = true;
    if (['net.minecraft.client.gui.GuiMainMenu', 'lumien.custommainmenu.gui.GuiCustom'].includes(state.screen) && !state.world) break;
    await pause(1000);
  }
  assert.ok(state && !state.world, 'Main menu deadline');
  const menu = state;
  await record('menu', state);
  await record('menu-inputs', await call('probe.nameReuse'));
  await record('pack-setup', await call('pack.setup'));
  if (mode === 'warmup') {
    // Let first-start config writes settle, then show what the input monitor saw; never keep a table.
    const samples = [];
    for (let index = 0; index < 120; index++) {
      const input = await call('probe.nameReuse');
      samples.push({at: new Date().toISOString(), status: input.status, appliedState: input.appliedState, reasons: input.reasons,
        generatedDuringStartup: input.generatedDuringStartup, watch: input.watch, table: input.table});
      if (input.appliedState !== 'STARTING' && index >= 10) break;
      await pause(1000);
    }
    await record('warmup-menu', samples);
    await call('world.create', {name: config.worldName});
    const worldSamples = [], until = Date.now() + 240000;
    while (Date.now() < until) {
      const status = await call('status'), input = await call('probe.nameReuse');
      worldSamples.push({at: new Date().toISOString(), world: status.world, player: status.player, status: input.status,
        appliedState: input.appliedState, reasons: input.reasons, table: input.table});
      if (input.table.busy) await call('probe.nameReuse', {action: 'cancel'});
      if (status.world && status.player && worldSamples.filter(value => value.world).length >= 30) break;
      await pause(1000);
    }
    await record('warmup-world', worldSamples);
    await record('complete', {mode, final: await call('probe.nameReuse'), state: await call('status')});
    throw Object.assign(new Error('warmup-finished'), {warmup: true});
  }
  const worldRequestedAt = performance_now();
  await call('world.create', {name: config.worldName});
  const worldDeadline = Date.now() + 360000;
  while (Date.now() < worldDeadline) {
    state = await call('status');
    if (state.world && state.player) break;
    await pause(250);
  }
  assert.ok(state.world && state.player, 'World readiness deadline');
  if (state.screen && !state.screen.endsWith('.NamePreparationScreen')) await call('screen.close');
  const worldReadyAt = performance_now(), worldReadyWall = new Date().toISOString();
  await record('background-monitor-start', await call('probe.performance', {action: 'monitor'}));
  let inventory = null, background = null, tableReady = null, aliasReady = null, searchReady = null;
  let collectionStart = null, previousState = '', windowStart = worldReadyAt, windowRestarted = null;
  const deadline = Date.now() + 1800000;
  while (Date.now() < deadline) {
    const value = await observe(background ? 'after-background' : 'background');
    const collecting = value.table.busy || value.table.currentCollection || value.table.ready;
    if (collectionWindow && !windowRestarted && collecting) {
      windowRestarted = await call('probe.performance', {action: 'monitor'});
      windowStart = performance_now();
      await record('background-monitor-restart', {sinceWorldReadyMs: windowStart - worldReadyAt, observedState: value.table.state, monitor: windowRestarted});
    }
    const since = value.monotonicMs - worldReadyAt, sinceWindow = value.monotonicMs - windowStart;
    const current = value.table.currentCollection;
    if (current && typeof current.elapsedMs === 'number') {
      const estimate = value.monotonicMs - current.elapsedMs;
      collectionStart = collectionStart == null ? estimate : Math.max(collectionStart, estimate);
    }
    if (value.table.state !== previousState) { previousState = value.table.state; console.log(JSON.stringify({since, state: value.table.state})); }
    const inventoryDue = collectionWindow ? windowRestarted && sinceWindow >= COLLECTION_INVENTORY_MS
      : since >= INVENTORY_AT_MS && (mode === 'reuse' || collecting);
    if (!inventory && inventoryDue) {
      inventory = {at: new Date().toISOString(), sinceWorldReadyMs: since, open: await call('screen.inventory'),
        search: await call('jei.search', {query: 'Iron Ingot'})};
      await record('background-inventory', inventory);
    }
    if (!tableReady && value.table.ready && !value.table.busy) tableReady = value;
    if (!aliasReady && value.aliasReady && value.table.ready) aliasReady = value;
    if (!background && (collectionWindow ? windowRestarted && sinceWindow >= BACKGROUND_MS : since >= BACKGROUND_MS)) {
      background = await call('probe.performance', {action: 'report', raw: true});
      await record('background-performance', background);
    }
    if (tableReady && aliasReady && background) {
      const search = await call('jei.observe');
      if (search.rows.some(row => row.id === 'minecraft:iron_ingot') && !search.rows.some(row => row.id === 'minecraft:stone')) {
        searchReady = {at: new Date().toISOString(), monotonicMs: performance_now(), query: search.query, count: search.count};
        await record('search-iron-ingot', search);
        break;
      }
    }
    if (value.appliedState === 'RESTART_REQUIRED' || value.table.state === 'deferred') throw Error('Preparation deferred: ' + JSON.stringify(value.table));
    await pause(POLL_MS);
  }
  await record('observations', observations);
  assert.ok(tableReady && aliasReady && searchReady, 'Table, alias index and English search readiness deadline');
  // Idle window after search readiness, inventory and query unchanged.
  await call('probe.performance', {action: 'monitor'});
  const idleStart = performance_now();
  await pause(IDLE_MS);
  const idle = await call('probe.performance', {action: 'report', raw: true});
  await record('idle-performance', idle);
  // Which recipe-viewer item keys the bound table contains in this session (read only).
  await record('jei-key-audit', await call('probe.nameReuse', {action: 'jeiKeyAudit'}).catch(error => ({error: String(error)})));
  // Enchantment rows (enchantment-cases.mjs): recorded in the summary; a row without its secondary name is reported, not fatal.
  const enchantments = await checkEnchantments(call, MEATBALL_ENCHANTED).catch(error => ({pass: false, cases: [], error: String(error)}));
  const enchantmentState = (await call('lifecycle.snapshot').catch(error => ({enchantment: {error: String(error)}}))).enchantment ?? null;
  await record('enchantments-full', {state: enchantmentState, cases: enchantments.cases, error: enchantments.error});
  // hover-probe: tooltip cost of a vanilla item, then table-state versus table-miss tooltips for the classes in
  // phase7-hover-classes.json (the miss variant carries one extra NBT byte and is named by the fallback resolver).
  if (process.argv.includes('hover-probe')) {
    const classes = JSON.parse(await fs.readFile(path.join(root, 'test-tools/legacy112/name-table-reuse/phase7-hover-classes.json'), 'utf8'));
    await record('hover-iron-ingot', await call('probe.performance', {action: 'hover', item: 'minecraft:iron_ingot', repetitions: 64}));
    await record('fallback-sample', await call('probe.performance', {action: 'fallbackSample', classes, repetitions: 16}, 600000));
  }
  const final = await call('probe.nameReuse'), finalPerformance = await performanceState();
  if (mode === 'build') assert.ok(final.table.builds >= 1, 'Production table built');
  else {
    assert.equal(final.table.builds, 0, 'No acquisition on reuse');
    assert.ok(final.table.loads >= 1, 'Durable generation loaded');
  }
  const origin = mode === 'build' && collectionStart != null ? collectionStart : worldReadyAt;
  const relative = value => value == null ? null : Math.round((value - origin) * 10) / 10;
  await record('summary', {
    mode, worldName: config.worldName, worldReadyWall, worldLoadMs: worldReadyAt - worldRequestedAt,
    origin: mode === 'build' && collectionStart != null ? 'collection-start (estimated from currentCollection.elapsedMs)' : 'world-ready',
    collectionStartAfterWorldReadyMs: collectionStart == null ? null : collectionStart - worldReadyAt,
    pollIntervalMs: POLL_MS,
    tableReady: {at: tableReady.at, ms: relative(tableReady.monotonicMs), state: tableReady.table.state},
    aliasReady: {at: aliasReady.at, ms: relative(aliasReady.monotonicMs), afterTableReadyMs: aliasReady.monotonicMs - tableReady.monotonicMs,
      indexed: aliasReady.aliasIndexed, fallback: aliasReady.aliasFallback, missing: aliasReady.aliasMissing, size: aliasReady.aliasSize},
    searchReady: {...searchReady, ms: relative(searchReady.monotonicMs)},
    idleStartAfterSearchReadyMs: idleStart - searchReady.monotonicMs,
    lastCollection: final.table.lastCollection,
    table: final.table, finalPerformance, persisted: await persisted(final.table),
    menuSelectiveResourceReload: menu.selectiveResourceReload,
    enchantmentState, enchantments: {...enchantmentSummary(enchantments), error: enchantments.error}
  });
  await call('jei.search', {query: ''});
  await call('screen.close');
  await record('complete', {mode, final: await call('probe.nameReuse'), state: await call('status')});
} catch (error) {
  if (error.warmup) { /* normal end of a warm-up launch */ } else {
  await record('failure', {error: String(error), stack: error.stack}).catch(() => {});
  await fs.writeFile(path.join(run, 'observations-partial.json'), JSON.stringify(observations, null, 2)).catch(() => {});
  process.exitCode = 1;
  }
} finally {
  if (bridge && owned && !await fs.stat(path.join(run, 'exit.json')).catch(() => null)) {
    try {
      sameOwned(await call('status'), config, owner);
      const input = await call('probe.nameReuse');
      if (input.table.busy) {
        await call('probe.nameReuse', {action: 'cancel'});
        const deadline = Date.now() + 300000;
        while (Date.now() < deadline && (await call('probe.nameReuse')).table.busy) await pause(1000);
      }
      await record('before-quit', {state: await call('status'), table: (await call('probe.nameReuse')).table});
      await call('quit');
    } catch (error) { await record('quit-result', {error: String(error)}).catch(() => {}); }
  }
  if (bridge) bridge.close();
}
