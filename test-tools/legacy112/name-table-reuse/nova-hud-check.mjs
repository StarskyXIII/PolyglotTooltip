// The One Probe title check on the Nova instance: join a throwaway world, let the bridge place and aim at a block,
// capture the probe's drawn text and record whether a secondary-language row follows the native title.
// The name-audit bridge installs the HUD hooks only with clientdevbridge.nameAudit=true and does not trace the
// probe packet, so the capture predicate names the origin but not the packet.
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
const plain = value => String(value).replace(/§./g, '').trim();
const topology = tree => tree ? [tree.class, ...(tree.children || []).map(topology)] : null;
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
  await record('menu-inputs', await call('probe.nameReuse'));
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
  // Names come through the table once it is published and through the fallback before that; wait for the table
  // so the capture shows the steady state.
  let tableState = null;
  const until = Date.now() + 300000;
  while (Date.now() < until) {
    const input = await call('probe.nameReuse');
    if (input.table.ready && !input.table.busy) { tableState = input.table.state; break; }
    if (input.table.state === 'deferred' || input.appliedState === 'RESTART_REQUIRED') { tableState = 'deferred'; break; }
    await pause(1000);
  }
  const cases = [];
  let prepared = false;
  for (const target of ['stone', 'furnace', 'water']) {
    const row = {target};
    try {
      const aim = await call('hud.world', {prepare: !prepared, target});
      prepared = true;
      row.state = aim.origin.state;
      await call('wait.ticks', {ticks: 30});
      const origin = {x: aim.origin.x, y: aim.origin.y, z: aim.origin.z, dimension: aim.origin.dimension};
      const capture = await call('hud.capture', {kind: 'top', name: `nova-top-${target}-${config.attempt}.png`, origin}, 120000);
      row.complete = capture.matched?.complete ?? null;
      row.texts = (capture.matched?.text ?? []).map(entry => plain(entry.text));
      row.rows = (capture.matched?.text ?? []).map(entry => ({x: entry.x, y: entry.y, text: plain(entry.text)}));
      row.tree = topology(capture.matched?.tree);
      row.width = capture.matched?.width; row.height = capture.matched?.height;
      row.screenshot = capture.path;
    } catch (error) { row.error = String(error); }
    try { row.observe = await call('hud.observe'); } catch (error) { row.observeError = String(error); }
    cases.push(row);
  }
  await record('summary', {worldName: config.worldName, candidate: config.candidate, tableState, cases: cases.map(({observe, ...rest}) => rest)});
  await record('observe', cases.map(row => row.observe ?? row.observeError));
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
