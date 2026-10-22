// Mid-session name loss on the Nova instance: join a throwaway world, wait for the table, then for twelve minutes
// poll the runtime state every fifteen seconds (table state, binding scope, applied inputs, startup-trace events)
// and observe the tooltip of a bare stone and a bare Wyvern pick, opening and closing the inventory between polls
// as a player would. Stops early once secondary rows that were present stay absent for three polls.
import path from 'node:path';
import assert from 'node:assert/strict';
import {Bridge} from '../../name-audit/runner/bridge.mjs';
import {read, save, sameOwned, pause} from '../name-table/runtime-common.mjs';

const [run] = process.argv.slice(2);
const config = await read(path.join(run, 'config.json')), owner = await read(path.join(run, 'process.json'));
let bridge, owned = false;
const call = (method, params = {}, timeout = 240000) => bridge.call(method, params, timeout);
const record = (name, value) => save(path.join(run, name + '.json'), value);
const MINUTES = Number(process.env.NOVA_IDLE_MINUTES ?? 12);
function bareStack(item, count = 1, damage = 0) {
  const str = text => { const bytes = Buffer.from(text, 'utf8'); const length = Buffer.alloc(2); length.writeUInt16BE(bytes.length); return Buffer.concat([length, bytes]); };
  const short = value => { const bytes = Buffer.alloc(2); bytes.writeInt16BE(value); return bytes; };
  return Buffer.concat([Buffer.from([10]), str(''), Buffer.from([8]), str('id'), str(item), Buffer.from([1]), str('Count'), Buffer.from([count]),
    Buffer.from([2]), str('Damage'), short(damage), Buffer.from([0])]).toString('base64');
}
const SAMPLES = [{id: 'stone', serialized: bareStack('minecraft:stone')}, {id: 'wyvern-pick', serialized: bareStack('draconicevolution:wyvern_pick')}];
const styled = lines => (lines ?? []).slice(1).filter(line => /^§e§o.*§r$/.test(String(line))).map(line => String(line).replace(/§./g, ''));
const timeline = [];
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
  const joinedAt = Date.now();
  const until = Date.now() + 300000;
  let tableState = null;
  while (Date.now() < until) {
    const input = await call('probe.nameReuse');
    if (input.table.ready && !input.table.busy) { tableState = input.table.state; break; }
    if (input.table.state === 'deferred' || input.appliedState === 'RESTART_REQUIRED') { tableState = 'deferred'; break; }
    await pause(1000);
  }
  let seenRows = false, absentPolls = 0, poll = 0, lost = null;
  const end = Date.now() + MINUTES * 60000;
  while (Date.now() < end) {
    poll++;
    const row = {poll, at: new Date().toISOString(), sinceJoinS: Math.round((Date.now() - joinedAt) / 1000)};
    try {
      if (poll % 2 === 0) { await call('screen.inventory').catch(() => {}); await call('wait.ticks', {ticks: 20}); }
      const s = await call('probe.nameReuse');
      row.state = s.table.state; row.ready = s.table.ready; row.busy = s.table.busy; row.scope = Boolean(s.scope);
      row.appliedState = s.appliedState; row.status = s.status; row.failure = s.table.failure; row.languageFailure = s.table.languageFailure;
      row.generation = s.table.generation; row.hits = s.table.hits;
      const events = s.table.trace?.events ?? [];
      row.traceEvents = events.length; row.lastEvents = events.slice(-4);
      row.counters = s.table.trace?.counters ?? null;
      const observed = await call('audit.observe', {samples: SAMPLES});
      row.rows = Object.fromEntries((observed.rows ?? []).map(entry => [entry.id, styled(entry.tooltip)]));
      row.screen = (await call('status')).screen ?? null;
      if (poll % 2 === 0) await call('screen.close').catch(() => {});
      const present = Object.values(row.rows).some(list => list.length > 0);
      if (present) { seenRows = true; absentPolls = 0; } else if (seenRows) absentPolls++;
      timeline.push(row);
      console.log(JSON.stringify({poll, sinceJoinS: row.sinceJoinS, state: row.state, scope: row.scope, rows: row.rows, traceEvents: row.traceEvents}));
      if (absentPolls >= 3) { lost = row; break; }
    } catch (error) { row.error = String(error).slice(0, 300); timeline.push(row); }
    await pause(15000);
  }
  if (lost) {
    await record('lost-state', await call('probe.nameReuse'));
    await record('lost-lifecycle', await call('lifecycle.snapshot').catch(error => ({error: String(error)})));
  }
  await record('timeline', timeline);
  await record('summary', {candidate: config.candidate, tableState, minutes: MINUTES, polls: poll, seenRows, lost: lost && {poll: lost.poll, sinceJoinS: lost.sinceJoinS, lastEvents: lost.lastEvents}});
  if (lost) process.exitCode = 2;
} catch (error) {
  await record('failure', {error: String(error), stack: error.stack}).catch(() => {});
  await record('timeline-partial', timeline).catch(() => {});
  process.exitCode = 1;
} finally {
  if (bridge && owned) {
    try { await record('before-quit', {state: await call('status')}); await call('quit'); }
    catch (error) { await record('quit-result', {error: String(error)}).catch(() => {}); }
  }
  if (bridge) bridge.close();
}
