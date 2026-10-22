// Phase-2 diagnostic controller: one owned Meatball build launch. Records the raw-state rejection
// breakdown and attributes long frames: bridge calls that run on the client thread stall during a
// long frame, so a stalled call triggers client-thread stack samples (jstack) alongside the
// name table, JEI and input status observed immediately before and after the stall.
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {execFile} from 'node:child_process';
import {Bridge} from '../../name-audit/runner/bridge.mjs';
import {root, read, save, hashFile, sameOwned, pause} from '../name-table/runtime-common.mjs';

const [pack, attempt] = process.argv.slice(2);
assert.equal(pack, 'Meatball');
assert.match(attempt, /^phase2-[a-z0-9-]+$/);
const report = path.join(root, 'docs/research/legacy112/name-table-phase2-diagnosis-2026-10-16');
const run = path.join(report, `run-${pack.toLowerCase()}-${attempt}`);
const config = await read(path.join(run, 'config.json')), owner = await read(path.join(run, 'process.json'));
const JSTACK = 'C:/Program Files/Zulu/zulu-25/bin/jstack.exe';
const POLL_MS = 500, STALL_MS = 1500, SAMPLE_EVERY_MS = 1000, SAMPLES_PER_STALL = 12, FRAME_SEGMENT_MS = 50000;
const AFTER_ALIAS_MS = 60000, DEADLINE_MS = 900000;
let bridge, owned = false;
const started = process.hrtime.bigint();
const now = () => Number(process.hrtime.bigint() - started) / 1e6;
const record = (name, value) => save(path.join(run, name + '.json'), value);

// Pending bridge calls, so a stall can be sampled while the client thread is blocked.
const pending = new Map();
let nextCall = 0;
const call = async (method, params = {}, timeout = 240000) => {
  const id = nextCall++;
  pending.set(id, {method, since: now(), wall: new Date().toISOString()});
  try { return await bridge.call(method, params, timeout); } finally { pending.delete(id); }
};

const stalls = [];
let sampling = false, stall = null;
const clientThread = text => {
  const start = text.indexOf('"Client thread"');
  if (start < 0) return null;
  const end = text.indexOf('\n\n', start);
  return text.slice(start, end < 0 ? undefined : end).split(/\r?\n/).slice(0, 80);
};
const jstack = () => new Promise(resolve => execFile(JSTACK, [String(owner.pid)], {maxBuffer: 64 * 1024 * 1024, timeout: 15000, windowsHide: true},
  (error, stdout) => resolve(error ? {error: String(error)} : {stack: clientThread(stdout)})));
const watchdog = setInterval(async () => {
  if (sampling) return;
  const oldest = [...pending.values()].reduce((value, entry) => value && value.since <= entry.since ? value : entry, null);
  const pendingMs = oldest ? now() - oldest.since : 0;
  if (!oldest || pendingMs < STALL_MS) { stall = null; return; }
  if (!stall || stall.call !== oldest) {
    stall = {call: oldest, samples: [], lastSampleAt: -Infinity};
    stalls.push({callStartedAt: oldest.wall, method: oldest.method, samples: stall.samples});
  }
  if (stall.samples.length >= SAMPLES_PER_STALL || now() - stall.lastSampleAt < SAMPLE_EVERY_MS) return;
  sampling = true;
  try {
    const sampleAt = new Date().toISOString(), pendingAt = pendingMs;
    stall.lastSampleAt = now();
    stall.samples.push({at: sampleAt, pendingMs: Math.round(pendingAt), ...(await jstack())});
  } finally { sampling = false; }
}, 250);

const observations = [];
const observe = async label => {
  const requested = now(), requestedAt = new Date().toISOString();
  const input = await call('probe.nameReuse'), performance = await call('probe.performance', {action: 'state'}), status = await call('status');
  const value = {at: requestedAt, completedAt: new Date().toISOString(), monotonicMs: requested, latencyMs: Math.round(now() - requested), label,
    tableState: input.table.state, tableBusy: input.table.busy, tableReady: input.table.ready, builds: input.table.builds, loads: input.table.loads,
    collection: input.table.currentCollection ? {elapsedMs: input.table.currentCollection.elapsedMs, phase: input.table.currentCollection.phaseMs,
      enumerated: input.table.currentCollection.enumerated} : null,
    aliasReady: performance.aliasReady ?? false, aliasCursor: performance.aliasCursor, aliasSize: performance.aliasSize,
    aliasIndexed: performance.aliasIndexed, aliasFallback: performance.aliasFallback, aliasMissing: performance.aliasMissing,
    jeiElementSearch: performance.elementSearch, jeiSearchTree: performance.searchTree,
    screen: status.screen, world: !!status.world, appliedState: input.appliedState, reasons: input.reasons, inputStatus: input.status};
  observations.push(value);
  return value;
};

// Frame intervals in consecutive monitor segments (the probe caps one monitor at 60 s).
const frames = [];
let segmentStarted = 0;
const restartFrames = async () => { await call('probe.performance', {action: 'monitor'}); segmentStarted = now(); };
const closeFrames = async () => {
  const value = await call('probe.performance', {action: 'report', raw: true});
  frames.push({startedAt: value.startedAt, elapsedMs: value.elapsedMs, frameIntervalMs: value.frameIntervalMs, rawFrameIntervalsMs: value.rawFrameIntervalsMs});
};

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
  await record('menu', state);
  await record('menu-inputs', await call('probe.nameReuse'));
  const probe = await jstack();
  await record('jstack-probe', {ok: !!probe.stack, error: probe.error ?? null});
  await call('world.create', {name: config.worldName});
  const worldDeadline = Date.now() + 360000;
  while (Date.now() < worldDeadline) {
    state = await call('status');
    if (state.world && state.player) break;
    await pause(250);
  }
  assert.ok(state.world && state.player, 'World readiness deadline');
  if (state.screen && !state.screen.endsWith('.NamePreparationScreen')) await call('screen.close');
  const worldReadyAt = now(), worldReadyWall = new Date().toISOString();
  await restartFrames();
  let aliasReady = null, catalogue = null;
  const deadline = Date.now() + DEADLINE_MS;
  while (Date.now() < deadline) {
    const value = await observe(aliasReady ? 'after-alias' : 'preparing');
    if (now() - segmentStarted >= FRAME_SEGMENT_MS) { await closeFrames(); await restartFrames(); }
    if (!catalogue && value.collection && value.tableState !== 'enumerating' && value.tableState !== 'preparing') {
      const table = (await call('probe.nameReuse')).table;
      if (table.currentCollection?.rejectedRawState) { catalogue = {at: new Date().toISOString(), currentCollection: table.currentCollection}; await record('catalogue', catalogue); }
    }
    if (!aliasReady && value.aliasReady && value.tableReady && !value.tableBusy) aliasReady = value;
    if (aliasReady && value.monotonicMs - aliasReady.monotonicMs >= AFTER_ALIAS_MS) break;
    if (value.appliedState === 'RESTART_REQUIRED' || value.tableState === 'deferred') throw Error('Preparation deferred: ' + JSON.stringify(value));
    await pause(POLL_MS);
  }
  await closeFrames();
  const final = await call('probe.nameReuse');
  await record('summary', {
    worldName: config.worldName, worldReadyWall, worldReadyMonotonicMs: worldReadyAt,
    aliasReady: aliasReady && {at: aliasReady.at, sinceWorldReadyMs: aliasReady.monotonicMs - worldReadyAt, indexed: aliasReady.aliasIndexed,
      fallback: aliasReady.aliasFallback, missing: aliasReady.aliasMissing, size: aliasReady.aliasSize},
    builds: final.table.builds, loads: final.table.loads, lastCollection: final.table.lastCollection,
    table: final.table, persisted: final.table.tableGeneration ? await persisted(final.table) : null
  });
  await call('screen.close');
  await record('complete', {final: await call('probe.nameReuse'), state: await call('status')});
} catch (error) {
  await record('failure', {error: String(error), stack: error.stack}).catch(() => {});
  process.exitCode = 1;
} finally {
  clearInterval(watchdog);
  await record('observations', observations).catch(() => {});
  await record('frames', frames).catch(() => {});
  await record('stalls', stalls).catch(() => {});
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
