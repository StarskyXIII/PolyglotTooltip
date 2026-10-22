// Language-injection check on the owned E2E fixture: wait through the menu-time language preparation, join a world,
// wait for the table and the alias index, then search IC2's drill by its secondary-language names.
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {Bridge} from '../../name-audit/runner/bridge.mjs';
import {root, read, save, sameOwned, pause} from '../name-table/runtime-common.mjs';

const [pack, attempt] = process.argv.slice(2);
assert.equal(pack, 'E2E');
assert.match(attempt, /^ic2-[a-z0-9-]+$/);
const report = path.join(root, 'docs/research/legacy112/name-table-ic2-injection-2026-10-17');
const run = path.join(report, `run-${pack.toLowerCase()}-${attempt}`);
const config = await read(path.join(run, 'config.json')), owner = await read(path.join(run, 'process.json'));
// IC2 2.8.164 lang_ic2: drill = Mining Drill / 采矿钻头 / 採礦電鑽. The fixture plays zh_tw with en_us and zh_cn secondary.
const QUERIES = [
  {label: 'en_us secondary', query: 'Mining Drill'},
  {label: 'zh_cn secondary', query: '采矿钻头'},
  {label: 'zh_tw native', query: '採礦電鑽'},
  {label: 'vanilla control', query: 'Iron Ingot'},
];
let bridge, owned = false;
const call = (method, params = {}, timeout = 240000) => bridge.call(method, params, timeout);
const record = (name, value) => save(path.join(run, name + '.json'), value);
const started = process.hrtime.bigint();
const now = () => Number(process.hrtime.bigint() - started) / 1e6;
const observations = [];
const observe = async label => {
  const input = await call('probe.nameReuse'), performance = await call('probe.performance', {action: 'state'}), status = await call('status');
  const value = {at: new Date().toISOString(), monotonicMs: now(), label, table: input.table, aliasReady: performance.aliasReady ?? false,
    aliasIndexed: performance.aliasIndexed, aliasFallback: performance.aliasFallback, aliasMissing: performance.aliasMissing,
    screen: status.screen, appliedState: input.appliedState, reasons: input.reasons, inputStatus: input.status};
  observations.push(value);
  return value;
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
  await record('menu', state);
  // The language preparation runs at the menu; sample the inputs while it does.
  const menuSamples = [], menuUntil = Date.now() + 60000;
  while (Date.now() < menuUntil) {
    const input = await call('probe.nameReuse');
    menuSamples.push({at: new Date().toISOString(), monotonicMs: now(), status: input.status, appliedState: input.appliedState,
      reasons: input.reasons, table: input.table, nativeLanguage: input.nativeLanguage});
    if (input.table.state === 'deferred') break;
    await pause(2000);
  }
  await record('menu-samples', menuSamples);
  await record('pack-setup', await call('pack.setup').catch(error => ({error: String(error)})));
  await call('world.create', {name: config.worldName});
  const worldDeadline = Date.now() + 360000;
  while (Date.now() < worldDeadline) {
    state = await call('status');
    if (state.world && state.player) break;
    await pause(250);
  }
  assert.ok(state.world && state.player, 'World readiness deadline');
  if (state.screen && !state.screen.endsWith('.NamePreparationScreen')) await call('screen.close');
  const worldReadyAt = now();
  let tableReady = null, aliasReady = null, deferred = null, previous = '';
  const deadline = Date.now() + 1200000;
  while (Date.now() < deadline) {
    const value = await observe('world');
    if (value.table.state !== previous) { previous = value.table.state; console.log(JSON.stringify({since: value.monotonicMs - worldReadyAt, state: value.table.state})); }
    if (!tableReady && value.table.ready && !value.table.busy) tableReady = value;
    if (!aliasReady && value.aliasReady && value.table.ready) aliasReady = value;
    if (tableReady && aliasReady) break;
    if (value.appliedState === 'RESTART_REQUIRED' || value.table.state === 'deferred') { deferred = value; break; }
    await pause(500);
  }
  await record('observations', observations);
  const searches = [];
  if (!deferred) {
    await call('screen.inventory');
    for (const probe of QUERIES) {
      await call('jei.search', {query: probe.query});
      await pause(2500);
      const observed = await call('jei.observe');
      const ids = (observed.rows ?? []).map(row => row.id);
      searches.push({...probe, count: observed.count, observedQuery: observed.query, ic2: ids.filter(id => String(id).startsWith('ic2:')).slice(0, 12),
        first: ids.slice(0, 8), rows: (observed.rows ?? []).slice(0, 12)});
    }
    await call('jei.search', {query: ''});
    await call('screen.close');
  }
  await record('searches', searches);
  const final = await call('probe.nameReuse');
  await record('summary', {
    worldName: config.worldName, deferred: deferred ? deferred.table : null,
    tableReady: tableReady && {ms: tableReady.monotonicMs - worldReadyAt, state: tableReady.table.state},
    aliasReady: aliasReady && {ms: aliasReady.monotonicMs - worldReadyAt, afterTableReadyMs: tableReady ? aliasReady.monotonicMs - tableReady.monotonicMs : null,
      indexed: aliasReady.aliasIndexed, fallback: aliasReady.aliasFallback, missing: aliasReady.aliasMissing},
    searches: searches.map(({label, query, count, ic2}) => ({label, query, count, ic2})),
    table: final.table, trace: final.table?.trace ?? null,
  });
  if (deferred) { process.exitCode = 2; }
} catch (error) {
  await record('failure', {error: String(error), stack: error.stack}).catch(() => {});
  await fs.writeFile(path.join(run, 'observations-partial.json'), JSON.stringify(observations, null, 2)).catch(() => {});
  process.exitCode = 1;
} finally {
  if (bridge && owned && !await fs.stat(path.join(run, 'exit.json')).catch(() => null)) {
    try {
      sameOwned(await call('status'), config, owner);
      const input = await call('probe.nameReuse');
      if (input.table.busy) {
        await call('probe.nameReuse', {action: 'cancel'});
        const until = Date.now() + 300000;
        while (Date.now() < until && (await call('probe.nameReuse')).table.busy) await pause(1000);
      }
      await record('before-quit', {state: await call('status'), table: (await call('probe.nameReuse')).table});
      await call('quit');
    } catch (error) { await record('quit-result', {error: String(error)}).catch(() => {}); }
  }
  if (bridge) bridge.close();
}
