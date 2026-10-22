import path from 'node:path';
import assert from 'node:assert/strict';
import {Bridge} from '../../name-audit/runner/bridge.mjs';
import {root, read, save, sameOwned, pause} from '../name-table/runtime-common.mjs';

const [pack, attempt, method, parameterText = '{}', record = 'diagnostic-ready'] = process.argv.slice(2);
assert.ok(['E2E', 'Meatball'].includes(pack));
assert.match(attempt, /^core-[a-z0-9-]+$/);
assert.match(record, /^[a-z0-9-]+$/);
const run = path.join(root, 'docs/research/legacy112/name-table-reuse-2026-10-14', `run-${pack.toLowerCase()}-${attempt}`);
const config = await read(path.join(run, 'config.json')), owner = await read(path.join(run, 'process.json'));
let bridge;
const call = (name, parameters = {}) => bridge.call(name, parameters, 240000);
try {
  const deadline = Date.now() + 900000;
  while (!bridge && Date.now() < deadline) {
    try { bridge = await Bridge.connect(config.port); } catch { await pause(1500); }
  }
  assert.ok(bridge, 'Owned diagnostic bridge startup deadline');
  let state = await call('status'); sameOwned(state, config, owner);
  if (method === 'boot') {
    while (Date.now() < deadline && !['net.minecraft.client.gui.GuiMainMenu', 'lumien.custommainmenu.gui.GuiCustom'].includes(state.screen)) {
      await pause(1000); state = await call('status'); sameOwned(state, config, owner);
    }
    assert.ok(!state.world && ['net.minecraft.client.gui.GuiMainMenu', 'lumien.custommainmenu.gui.GuiCustom'].includes(state.screen));
    await save(path.join(run, 'diagnostic-menu.json'), state);
    await call('pack.setup');
    await call('world.create', {name: config.worldName});
    const worldDeadline = Date.now() + 360000;
    do { await pause(1000); state = await call('status'); } while ((!state.world || !state.player) && Date.now() < worldDeadline);
    assert.ok(state.world && state.player, 'Diagnostic world ready');
    if (state.screen && !state.screen.endsWith('.NamePreparationScreen')) await call('screen.close');
    const values = [], readyDeadline = Date.now() + 360000;
    let input;
    do {
      input = await call('probe.nameReuse');
      values.push({at: new Date().toISOString(), status: input.status, table: input.table});
      if (input.table.ready || input.table.busy || input.table.state === 'deferred') break;
      await pause(1000);
    } while (Date.now() < readyDeadline);
    await save(path.join(run, 'diagnostic-startup-observations.json'), values);
    await save(path.join(run, record + '.json'), {at: new Date().toISOString(), state: await call('status'), input});
    console.log(JSON.stringify({stage: 'diagnostic-ready', table: input.table}));
  } else {
    const parameters = JSON.parse(parameterText);
    if (method === 'quit') {
      let input = await call('probe.nameReuse');
      if (input.table.busy) {
        await call('probe.nameReuse', {action: 'cancel'});
        const restoreDeadline = Date.now() + 300000;
        do { await pause(1000); input = await call('probe.nameReuse'); } while (input.table.busy && Date.now() < restoreDeadline);
      }
      assert.equal(input.table.busy, false, 'Preparation restored before diagnostic exit');
      await save(path.join(run, record + '-before.json'), {state: await call('status'), input});
    }
    const result = await call(method, parameters);
    await save(path.join(run, record + '.json'), result);
    console.log(JSON.stringify(result));
  }
} finally { if (bridge) bridge.close(); }
