// Name-table diagnosis on the Nova instance: wait through the menu, create a throwaway world, wait for the table
// and the alias index, record the build's rejection breakdown, then quit.
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {Bridge} from '../../name-audit/runner/bridge.mjs';
import {read, save, sameOwned, pause} from '../name-table/runtime-common.mjs';

const [run] = process.argv.slice(2);
const config = await read(path.join(run, 'config.json')), owner = await read(path.join(run, 'process.json'));
let bridge, owned = false;
const call = (method, params = {}, timeout = 240000) => bridge.call(method, params, timeout);
const record = (name, value) => save(path.join(run, name + '.json'), value);
const started = process.hrtime.bigint();
const now = () => Number(process.hrtime.bigint() - started) / 1e6;
const observations = [];
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
    if (state.screen && !state.world && /GuiMainMenu|GuiCustom|MainMenu/.test(state.screen)) break;
    await pause(1000);
  }
  assert.ok(state && !state.world, 'Main menu deadline: ' + JSON.stringify(state && state.screen));
  await record('menu', state);
  const menuSamples = [], menuUntil = Date.now() + 45000;
  while (Date.now() < menuUntil) {
    const input = await call('probe.nameReuse');
    menuSamples.push({at: new Date().toISOString(), status: input.status, appliedState: input.appliedState, reasons: input.reasons, table: input.table});
    if (input.table.state === 'deferred') break;
    await pause(3000);
  }
  await record('menu-samples', menuSamples);
  await record('pack-setup', await call('pack.setup').catch(error => ({error: String(error)})));
  await call('world.create', {name: config.worldName});
  const worldDeadline = Date.now() + 480000;
  while (Date.now() < worldDeadline) {
    state = await call('status');
    if (state.world && state.player) break;
    await pause(250);
  }
  assert.ok(state.world && state.player, 'World readiness deadline');
  if (state.screen && !state.screen.endsWith('.NamePreparationScreen')) await call('screen.close').catch(() => {});
  const worldReadyAt = now();
  let tableReady = null, aliasReady = null, deferred = null, previous = '';
  const deadline = Date.now() + 1200000;
  while (Date.now() < deadline) {
    const input = await call('probe.nameReuse'), performance = await call('probe.performance', {action: 'state'});
    const value = {at: new Date().toISOString(), monotonicMs: now(), state: input.table.state, ready: input.table.ready, busy: input.table.busy,
      aliasReady: performance.aliasReady ?? false, aliasIndexed: performance.aliasIndexed, aliasMissing: performance.aliasMissing, appliedState: input.appliedState};
    observations.push(value);
    if (value.state !== previous) { previous = value.state; console.log(JSON.stringify({since: value.monotonicMs - worldReadyAt, state: value.state})); }
    if (!tableReady && input.table.ready && !input.table.busy) tableReady = {...value, table: input.table};
    if (!aliasReady && value.aliasReady && input.table.ready) aliasReady = value;
    if (tableReady && aliasReady) break;
    if (input.appliedState === 'RESTART_REQUIRED' || input.table.state === 'deferred') { deferred = input.table; break; }
    await pause(1000);
  }
  await record('observations', observations);
  const final = await call('probe.nameReuse');
  const collection = final.table?.lastCollection ?? null;
  await record('final-table', final.table);
  await record('summary', {
    worldName: config.worldName, candidate: config.candidate, deferred,
    tableReady: tableReady && {ms: Math.round(tableReady.monotonicMs - worldReadyAt)},
    aliasReady: aliasReady && {ms: Math.round(aliasReady.monotonicMs - worldReadyAt), indexed: aliasReady.aliasIndexed, missing: aliasReady.aliasMissing},
    entries: final.table?.entries ?? null, builds: final.table?.builds, loads: final.table?.loads,
    collection: collection && {enumerated: collection.enumerated, eligible: collection.eligible, admitted: collection.admitted, elapsedMs: collection.elapsedMs,
      rejected: collection.rejected, rejectedRawState: collection.rejectedRawState, largestUnits: collection.largestUnits, sources: collection.sources},
  });
  if (deferred) process.exitCode = 2;
  // Which recipe-viewer stacks the table does not hold, by mod and class; focused classes list each
  // stack beside the stored rows of the same id and damage and the names a tooltip resolves for it.
  // Opt-in (NOVA_JEI_AUDIT=1): the audit makes one native call per focused stack and can take minutes.
  if (tableReady && process.env.NOVA_JEI_AUDIT === '1') await record('jei-key-audit', await call('probe.nameReuse', {action: 'jeiKeyAudit', focus: config.auditFocus ?? [
    'ic2.core.item.block.ItemBlockTileEntity', 'ic2.core.item.block.ItemCable', 'ic2.core.item.block.ItemFluidPipe',
    'com.github.glodblock.eae.common.item.InfinityCell', 'cofh.thermalexpansion.block.machine.ItemBlockMachine',
    'vazkii.botania.common.item.lens.ItemLens', 'appeng.items.misc.ItemCrystalSeed',
  ]}, 600000).catch(error => ({error: String(error)})));
  // Same opt-in: replay the alias index for focused classes to see why elements reach the main-thread drain.
  if (aliasReady && process.env.NOVA_JEI_AUDIT === '1') await record('alias-audit', await call('probe.nameReuse', {action: 'aliasAudit',
    focus: ['ic2.core.item.block.ItemBlockTileEntity', 'ic2.core.item.block.ItemCable', 'ic2.core.item.block.ItemFluidPipe',
      'com.github.glodblock.eae.common.item.InfinityCell']}, 600000).catch(error => ({error: String(error)})));
} catch (error) {
  await record('failure', {error: String(error), stack: error.stack}).catch(() => {});
  await fs.writeFile(path.join(run, 'observations-partial.json'), JSON.stringify(observations, null, 2)).catch(() => {});
  process.exitCode = 1;
} finally {
  if (bridge && owned) {
    try {
      const input = await call('probe.nameReuse');
      if (input.table.busy) {
        await call('probe.nameReuse', {action: 'cancel'});
        const until = Date.now() + 300000;
        while (Date.now() < until && (await call('probe.nameReuse')).table.busy) await pause(1000);
      }
      await record('before-quit', {state: await call('status')});
      await call('quit');
    } catch (error) { await record('quit-result', {error: String(error)}).catch(() => {}); }
  }
  if (bridge) bridge.close();
}
