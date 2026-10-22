import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {Bridge} from '../name-audit/runner/bridge.mjs';
import {readMaterials, groupMaterials} from './microblock-families.mjs';

const [runArg, inventory, ...exclusions] = process.argv.slice(2);
assert.ok(runArg && inventory, 'run-directory inventory [verified-case-files...]');
const run = path.resolve(runArg), excluded = new Set();
for (const file of exclusions) for (const row of JSON.parse(await fs.readFile(file, 'utf8'))) excluded.add(row.sampleId);
const materials = await readMaterials(inventory, excluded);
const config = JSON.parse(await fs.readFile(path.join(run, 'config.json'), 'utf8'));
const owner = JSON.parse(await fs.readFile(path.join(run, 'process.json'), 'utf8'));
const save = (name, value) => fs.writeFile(path.join(run, name + '.json'), JSON.stringify(value, null, 2), {flag: 'wx'});
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
let bridge;
const call = (method, params = {}) => bridge.call(method, params, 120000);
try {
  let menu = false;
  for (let count = 0; count < 600; count++) {
    try {
      bridge ??= await Bridge.connect(config.port);
      const state = await bridge.call('status', {}, 15000);
      assert.equal(String(state.pid), String(owner.pid)); assert.equal(state.fixtureNonce, config.nonce);
      assert.equal(path.resolve(state.gameDirectory).toLowerCase(), path.resolve(config.fixture).toLowerCase());
      if (['net.minecraft.client.gui.GuiMainMenu', 'lumien.custommainmenu.gui.GuiCustom'].includes(state.screen)) {
        await save('menu', state); menu = true; break;
      }
    } catch (error) { bridge?.close(); bridge = null; }
    await pause(2000);
  }
  assert.ok(menu, 'Menu deadline');
  await call('pack.setup'); await call('world.create', {name: config.worldName});
  await call('wait.ticks', {ticks: 60}); await call('screen.close');
  await call('config.set', {languages: ['en_us', 'zh_cn']});
  let ready = false;
  for (let count = 0; count < 1200; count++) {
    const state = await call('probe.performance', {action: 'state'});
    if (state.ready && !state.libraryPending) { ready = true; break; }
    await pause(250);
  }
  assert.ok(ready, 'Language readiness deadline');
  const diagnostics = [];
  for (let offset = 0; offset < materials.length; offset += 32) {
    const batch = materials.slice(offset, offset + 32).map(value => value.material);
    const result = await call('probe.export', {action: 'microblockMaterials', materials: batch});
    assert.deepEqual(result.rows.map(row => row.material), batch);
    diagnostics.push(...result.rows);
  }
  await save('diagnostics', diagnostics);
  const report = groupMaterials(materials, diagnostics); await save('families', report);
  console.log(JSON.stringify({materials: report.materialCount, states: report.states, groups: report.groups.length,
    errors: diagnostics.filter(row => row.stage === 'diagnostic-error').length}));
  await call('quit');
} catch (error) {
  await save('diagnostic-failure', {error: String(error), stack: error.stack}); throw error;
} finally { bridge?.close(); }
