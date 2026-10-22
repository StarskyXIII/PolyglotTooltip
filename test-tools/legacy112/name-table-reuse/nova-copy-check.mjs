// Copy-key check on the Nova instance: join a throwaway world, wait for the table and the alias index, compare the
// raw keys of ItemStack.copy() and of an NBT round trip with every JEI stack (probe.nameCopy audit), then put
// catalogue-state stacks with capability providers (IC2 cable and pipe, Botania lens) and vanilla controls into the
// main hand and render the tooltip of the synced stack, its copies and its round trip (probe.nameCopy held).
// Run with the production bridge and clientdevbridge.nameTrace=false so the fallback observation path stays admitted.
import path from 'node:path';
import assert from 'node:assert/strict';
import {Bridge} from '../../name-audit/runner/bridge.mjs';
import {read, save, sameOwned, pause} from '../name-table/runtime-common.mjs';

const [run] = process.argv.slice(2);
const config = await read(path.join(run, 'config.json')), owner = await read(path.join(run, 'process.json'));
let bridge, owned = false;
const call = (method, params = {}, timeout = 240000) => bridge.call(method, params, timeout);
const record = (name, value) => save(path.join(run, name + '.json'), value);
// NBT as the JEI catalogue holds these states (jei-key-audit focus of run-nova-diag-6).
const HELD = [
  {id: 'stone-control', give: 'minecraft:stone 1 0'},
  {id: 'diamond-pickaxe-tagless', give: 'minecraft:diamond_pickaxe 1 0'},
  {id: 'ic2-cable-copper', give: 'ic2:cable 1 0 {type:0b,insulation:0b}'},
  {id: 'ic2-cable-copper-insulated', give: 'ic2:cable 1 0 {type:0b,insulation:1b}'},
  {id: 'ic2-cable-glass', give: 'ic2:cable 1 1 {type:1b,insulation:0b}'},
  {id: 'ic2-pipe-small', give: 'ic2:pipe 1 0 {type:0b,size:0b}'},
  {id: 'botania-lens-plain', give: 'botania:lens 1 0 {}'},
  {id: 'botania-lens-speed', give: 'botania:lens 1 1 {}'},
  {id: 'botania-lens-tagless', give: 'botania:lens 1 0'},
  // The de-4 case: string/int tags, a state no catalogue holds.
  {id: 'ic2-cable-de4-tags', give: 'ic2:cable 1 0 {type:"copper",insulation:0}'},
];
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
  await pause(30000);
  await call('world.create', {name: config.worldName});
  const worldDeadline = Date.now() + 480000;
  while (Date.now() < worldDeadline) {
    state = await call('status');
    if (state.world && state.player) break;
    await pause(250);
  }
  assert.ok(state.world && state.player, 'World readiness deadline');
  if (state.screen && !state.screen.endsWith('.NamePreparationScreen')) await call('screen.close').catch(() => {});
  let tableState = null, aliasReady = false;
  const until = Date.now() + 300000;
  while (Date.now() < until) {
    const input = await call('probe.nameReuse');
    if (input.table.state === 'deferred' || input.appliedState === 'RESTART_REQUIRED') { tableState = 'deferred'; break; }
    if (input.table.ready && !input.table.busy) {
      tableState = input.table.state;
      const performance = await call('probe.performance', {action: 'state'}).catch(() => ({}));
      if (performance.aliasReady) { aliasReady = true; break; }
    }
    await pause(1000);
  }
  const reuse = await call('probe.nameReuse');
  await record('reuse-state', reuse);
  const audit = await call('probe.nameCopy', {action: 'audit'}).catch(error => ({error: String(error)}));
  await record('copy-audit', audit);
  await call('world.command', {command: '/gamemode creative @p'}).catch(() => {});
  const results = [];
  for (const entry of HELD) {
    const row = {id: entry.id, give: entry.give};
    try {
      row.replaced = await call('world.command', {command: '/replaceitem entity @p slot.hotbar.0 ' + entry.give});
      await call('wait.ticks', {ticks: 10});
      row.held = await call('probe.nameCopy', {action: 'held', slot: 0});
    } catch (error) { row.error = String(error).slice(0, 500); }
    results.push(row);
  }
  await record('held', results);
  const brief = results.map(row => ({id: row.id, error: row.error, nbt: row.held?.nbt,
    variants: Object.fromEntries(Object.entries(row.held?.variants ?? {}).map(([name, value]) => [name, {
      capNbt: value.capNbtBefore, firstHits: value.firstTableHits, first: value.firstSecondaryRows,
      repeatHits: value.repeatTableHits, repeat: value.repeatSecondaryRows, keyInTable: value.keyAfterInTable,
    }]))}));
  const {samples, ...auditBrief} = audit;
  await record('summary', {worldName: config.worldName, candidate: config.candidate, tableState, aliasReady,
    table: reuse.table?.lastCollection ? {rows: reuse.table.lastCollection.admitted, settled: reuse.table.lastCollection.settled} : null,
    audit: auditBrief, held: brief});
} catch (error) {
  await record('failure', {error: String(error), stack: error.stack}).catch(() => {});
  process.exitCode = 1;
} finally {
  if (bridge && owned) {
    try {
      const input = await call('probe.nameReuse');
      if (input.table.busy) {
        await call('probe.nameReuse', {action: 'cancel'});
        const deadline = Date.now() + 300000;
        while (Date.now() < deadline && (await call('probe.nameReuse')).table.busy) await pause(1000);
      }
      await record('before-quit', {state: await call('status')});
      await call('quit');
    } catch (error) { await record('quit-result', {error: String(error)}).catch(() => {}); }
  }
  if (bridge) bridge.close();
}
