import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {Bridge} from '../../name-audit/runner/bridge.mjs';
import {root, read, save, sameOwned, pause} from '../name-table/runtime-common.mjs';

const pack = process.argv[2], attempt = process.argv[3];
assert.ok(['E2E', 'Meatball'].includes(pack));
assert.match(attempt, /^startup-inputs-[0-9]+$/);
const run = path.join(root, 'docs/research/legacy112/name-table-reuse-2026-10-14', `run-${pack.toLowerCase()}-${attempt}`);
const config = await read(path.join(run, 'config.json')), owner = await read(path.join(run, 'process.json'));
let bridge, owned = false;
const call = (method, params = {}, timeout = 30000) => bridge.call(method, params, timeout);
const record = (name, data) => save(path.join(run, name + '.json'), data);
try {
  const deadline = Date.now() + 900000;
  while (!bridge && Date.now() < deadline) {
    try { bridge = await Bridge.connect(config.port); }
    catch { await pause(1500); }
  }
  assert.ok(bridge, 'Bridge startup deadline');
  let state;
  while (Date.now() < deadline) {
    try { state = await call('status'); }
    catch { await pause(1500); continue; }
    sameOwned(state, config, owner); owned = true;
    if (['net.minecraft.client.gui.GuiMainMenu', 'lumien.custommainmenu.gui.GuiCustom'].includes(state.screen) && !state.world) break;
    await pause(1000);
  }
  assert.ok(state && !state.world, 'Main menu deadline');
  await record('menu', state);
  await record('runtime', await call('pack.runtime'));
  const observations = [], scanDeadline = Date.now() + 120000;
  while (Date.now() < scanDeadline) {
    const inputs = await call('probe.nameReuse');
    observations.push({at: new Date().toISOString(), inputs});
    if (inputs.current || inputs.appliedState === 'RESTART_REQUIRED' || !inputs.monitorPresent) break;
    await pause(1000);
  }
  await record('input-observations', observations);
  await record('complete', {at: new Date().toISOString(), finalInputs: observations.at(-1)});
  await record('raw-controls', await call('probe.nameReuse', {action: 'rawControls'}));
  const final = observations.at(-1).inputs;
  console.log(JSON.stringify({status: final.status, current: final.current, nativeReady: final.nativeIndependentReady,
    nativeUnavailable: final.nativeIndependentUnavailable, reasons: final.reasons, scanFailure: final.scanFailure}));
} catch (error) {
  await record('failure', {error: String(error), stack: error.stack}); process.exitCode = 1;
} finally {
  if (bridge && owned && !await fs.stat(path.join(run, 'exit.json')).catch(() => null)) {
    try {
      sameOwned(await call('status'), config, owner);
      await call('quit');
    } catch (error) { await record('quit-result', {error: String(error)}); }
  }
  if (bridge) bridge.close();
}
