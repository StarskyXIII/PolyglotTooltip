import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {Bridge} from '../../name-audit/runner/bridge.mjs';
import {root, read, save, hashFile, sameOwned, pause} from '../name-table/runtime-common.mjs';
import {validateScoped} from './scoped-runtime.mjs';

const [pack, attempt, mode = 'build'] = process.argv.slice(2);
const extraConsumers = process.argv[5] === 'extra-consumers';
const sharedScope = process.argv.includes('shared-scope');
const scopedValidation = process.argv.includes('scoped-validation');
assert.ok(['E2E', 'Meatball'].includes(pack));
assert.match(attempt, /^core-[a-z0-9-]+$/);
assert.ok(['build', 'reuse', 'cancel', 'auto'].includes(mode));
const run = path.join(root, 'docs/research/legacy112/name-table-reuse-2026-10-14', `run-${pack.toLowerCase()}-${attempt}`);
const config = await read(path.join(run, 'config.json')), owner = await read(path.join(run, 'process.json'));
let bridge, owned = false, finished = false;
const call = (method, params = {}, timeout = 240000) => bridge.call(method, params, timeout);
const record = (name, value) => save(path.join(run, name + '.json'), value);
const observations = [];
const compact = value => ({at: new Date().toISOString(), status: value.status, current: value.current,
  identity: value.identity, scope: value.scope, runtimeContext: value.runtimeContext,
  nativeReady: value.nativeIndependentReady, nativeUnavailable: value.nativeIndependentUnavailable,
  appliedState: value.appliedState, reasons: value.reasons, scanFailure: value.scanFailure,
  watch: value.watch, scanElapsedMs: value.scanElapsedMs, scanIncremental: value.scanIncremental, bytesRead: value.bytesRead,
  table: value.table, preference: value.languagePreference});
const persisted = async value => {
  const file = path.join(config.fixture, 'polyglottooltip/name-tables/contexts', value.scope,
    `generation-${value.table.tableGeneration}.pnt`);
  const stat = await fs.stat(file, {bigint: true});
  return {path: file, generation: value.table.tableGeneration, sha256: await hashFile(file),
    modifiedNs: stat.mtimeNs.toString(), bytes: stat.size.toString()};
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
  const menuState = state;
  await record('menu', state); await record('runtime', await call('pack.runtime'));
  await record('menu-inputs', await call('probe.nameReuse'));
  await record('raw-catalog-controls', await call('probe.nameReuse', {action: 'rawCatalogControls'}));
  await record('pack-setup', await call('pack.setup'));
  if (scopedValidation) await record('scope-armed', await call('probe.nameReuse', {action: 'scopeArm'}));
  await call('world.create', {name: config.worldName});
  const worldDeadline = Date.now() + 360000;
  while (Date.now() < worldDeadline) {
    state = await call('status');
    if (state.world && state.player) break;
    await pause(500);
  }
  assert.ok(state.world && state.player, 'World readiness deadline');
  if (state.screen && !state.screen.endsWith('.NamePreparationScreen')) await call('screen.close');
  const buildDeadline = Date.now() + 1800000;
  let previous = '', cancelled = false, final;
  while (Date.now() < buildDeadline) {
    const raw = await call('probe.nameReuse'), value = compact(raw);
    observations.push(value); final = value;
    await fs.writeFile(path.join(run, 'progress.json'), JSON.stringify(value, null, 2));
    if (value.table.state !== previous) { previous = value.table.state; console.log(JSON.stringify(value)); }
    if (mode === 'cancel' && value.table.busy && !cancelled) {
      await call('probe.nameReuse', {action: 'cancel'}); cancelled = true;
    }
    if (value.table.ready && !value.table.busy || cancelled && value.table.state === 'deferred') break;
    if (value.appliedState === 'RESTART_REQUIRED' || value.table.state === 'deferred') throw Error('Preparation deferred: ' + JSON.stringify(value));
    await pause(1000);
  }
  await record('preparation-observations', observations);
  assert.ok(final && (mode === 'cancel' ? final.table.state === 'deferred' : final.table.ready), 'Name table readiness deadline');
  state = await call('status');
  assert.equal(state.language, config.initialLanguage, 'Original language restored');
  assert.equal(state.selectiveResourceReload, menuState.selectiveResourceReload, 'Original selective reload preference restored');
  assert.equal(state.forceUnicode, menuState.forceUnicode, 'Original font preference preserved');
  assert.equal(final.preference.active, false, 'No temporary language preference remains');
  if (mode === 'reuse' || mode === 'auto' && final.table.builds === 0) {
    assert.equal(final.table.builds, 0, 'No acquisition on reuse');
    assert.equal(final.table.switches, 0, 'No automatic language switches on reuse');
    assert.ok(final.table.loads >= 1, 'Durable generation loaded');
  }
  if (mode === 'build') assert.ok(final.table.builds >= 1, 'Production table built');
  if (sharedScope && mode === 'build') {
    const before = compact(await call('probe.nameReuse')), tableBefore = await persisted(before);
    const held = await call('probe.nameReuse', {action: 'contextHold'});
    assert.ok(held.heldBindingCurrent && held.heldObservationCurrent && held.sameBinding);
    assert.equal(held.freshEnglishName, 'Iron Ingot', 'World A uses a native table row');
    await record('shared-context-held', {before, tableBefore, held});
    await call('world.leave');
    const menuDeadline = Date.now() + 180000;
    while (Date.now() < menuDeadline && (await call('status')).world) await pause(500);
    assert.equal((await call('status')).world, false, 'World A exits normally');
    const retired = await call('probe.nameReuse', {action: 'contextCheck'});
    assert.equal(retired.heldBindingCurrent, false);
    assert.equal(retired.heldObservationCurrent, false);
    assert.equal(retired.heldMayRebind, false);
    const worldB = config.worldName + '-shared-b';
    const newWorld = !await fs.stat(path.join(config.fixture, 'saves', worldB)).catch(() => null);
    await record('shared-world-b-intent', {worldA: config.worldName, worldB, newWorld, retired});
    await call('world.create', {name: worldB});
    const contextDeadline = Date.now() + 360000, transitions = [];
    let after;
    while (Date.now() < contextDeadline) {
      const currentState = await call('status');
      if (currentState.world && currentState.player) {
        if (currentState.screen && !currentState.screen.endsWith('.NamePreparationScreen')) await call('screen.close');
        after = compact(await call('probe.nameReuse')); transitions.push(after);
        await fs.writeFile(path.join(run, 'shared-progress.json'), JSON.stringify(after, null, 2));
        assert.equal(after.table.builds, before.table.builds, 'No collection for new world');
        assert.equal(after.table.switches, before.table.switches, 'No language switches for new world');
        assert.equal(after.table.busy, false, 'New world never starts acquisition');
        if (after.table.ready) break;
        assert.notEqual(after.appliedState, 'RESTART_REQUIRED');
      }
      await pause(1000);
    }
    await record('shared-transition-observations', transitions);
    assert.ok(after?.table.ready, 'World B loads the shared table');
    assert.equal(after.identity, before.identity); assert.equal(after.scope, before.scope);
    assert.equal(after.table.tableGeneration, before.table.tableGeneration);
    assert.ok(after.table.loads > before.table.loads, 'New runtime context loads the existing disk table');
    const rebound = await call('probe.nameReuse', {action: 'contextCheck'});
    assert.equal(rebound.heldBindingCurrent, false); assert.equal(rebound.heldObservationCurrent, false);
    assert.equal(rebound.heldMayRebind, false); assert.equal(rebound.sameRuntimeContext, false);
    assert.equal(rebound.sameBinding, false); assert.equal(rebound.heldScope, rebound.currentScope);
    assert.equal(rebound.freshObservationCurrent, true); assert.equal(rebound.freshEnglishName, 'Iron Ingot');
    const tableAfter = await persisted(after); assert.deepEqual(tableAfter, tableBefore);
    await record('shared-transition', {passed: true, worldA: config.worldName, worldB, newWorld,
      before, after, tableBefore, tableAfter, retired, rebound});
  }
  if (mode !== 'cancel') {
    const selectionFile = path.join(root, 'docs/research/legacy112/name-table-prototype-2026-10-14/corpus-final', pack, 'runtime-selection.json');
    const selection = await read(selectionFile), seal = await read(path.join(path.dirname(selectionFile), 'runtime-selection-seal.json'));
    assert.equal(await hashFile(selectionFile), seal.sha256);
    const selected = selection.selected ?? selection.acquisition;
    const samples = [], mods = new Set(), ids = new Set();
    for (const sample of selected) if (/minecraft:(stone|iron_ingot|diamond|iron_sword)$|cyclicmagic:.*guide|draconicevolution:mob_soul/.test(sample.item)) {
      if (!ids.has(sample.sampleId)) { samples.push(sample); ids.add(sample.sampleId); }
    }
    for (const sample of selected) if (!mods.has(sample.mod) && !ids.has(sample.sampleId)) {
      mods.add(sample.mod); ids.add(sample.sampleId); samples.push(sample); if (samples.length >= 128) break;
    }
    await record('samples', samples);
    const rows = [];
    for (let index = 0; index < samples.length; index += 16) {
      const result = await call('probe.nameReuse', {action: 'query', samples: samples.slice(index, index + 16)});
      await record('queries-' + String(index).padStart(4, '0'), result); rows.push(...result.rows);
    }
    const tableValues = rows.flatMap(row => row.languages?.values ?? []).filter(value => value.source === 'table');
    assert.ok(rows.every(row => !row.error), 'Sample queries have no execution errors');
    assert.ok(rows.every(row => row.nativeName === row.repeatNativeName), 'Direct query primary agrees with repeated native observation');
    assert.ok(rows.some(row => row.tooltipTableHits > 0 || row.repeatTooltipTableHits > 0), 'Actual tooltip table HIT');
    assert.ok(tableValues.length > 0, 'Actual coordinator table HIT');
    await record('query-summary', {samples: samples.length, tooltipHits: rows.filter(row => row.tooltipTableHits > 0 || row.repeatTooltipTableHits > 0).length,
      firstTooltipHits: rows.filter(row => row.tooltipTableHits > 0).length,
      repeatTooltipHits: rows.filter(row => row.repeatTooltipTableHits > 0).length,
      tableValues: tableValues.length, errors: rows.filter(row => row.error),
      sources: rows.flatMap(row => row.languages?.values ?? []).reduce((out, value) => {out[value.source] = (out[value.source] ?? 0) + 1; return out;}, {})});
    const query = 'iron ingot', queryDeadline = Date.now() + 600000;
    let search = await call('jei.search', {query});
    const searchObservations = [];
    while (Date.now() < queryDeadline) {
      const performance = await call('probe.performance', {action: 'state'});
      const input = compact(await call('probe.nameReuse'));
      search = await call('jei.observe');
      const value = {at: new Date().toISOString(), performance, table: input.table,
        query: search.query, count: search.count, iron: search.rows.some(row => row.id === 'minecraft:iron_ingot'),
        filtered: search.query === query && !search.rows.some(row => row.id === 'minecraft:stone')};
      searchObservations.push(value);
      await fs.writeFile(path.join(run, 'search-progress.json'), JSON.stringify(value, null, 2));
      if (value.iron && value.filtered && performance.aliasReady && input.table.ready) break;
      await pause(1000);
    }
    await record('search-observations', searchObservations);
    await record('search-iron-ingot', search);
    assert.ok(searchObservations.at(-1).iron && searchObservations.at(-1).filtered && searchObservations.at(-1).performance.aliasReady
      && searchObservations.at(-1).table.ready, 'Ready native recipe search finds English item alias');
    if (scopedValidation) await validateScoped({call, record, pause, run, config, attempt});
    if (sharedScope && pack === 'Meatball') {
      const autosave = [], deadline = Date.now() + 65000;
      while (Date.now() < deadline) {
        const input = await call('probe.nameReuse'), performance = await call('probe.performance', {action: 'state'});
        const visible = await call('jei.observe');
        autosave.push({at: new Date().toISOString(), revision: input.revision, ...compact(input), performance,
          query: visible.query, iron: visible.rows.some(row => row.id === 'minecraft:iron_ingot')});
        await pause(2000);
      }
      await record('shared-autosave-observations', autosave);
      // Keep this acceptance result separate so a known availability failure does
      // not prevent the remaining restart, formatting and restoration evidence.
      await record('shared-autosave-result', {
        passed: autosave.length >= 2 && autosave.at(-1).revision > autosave[0].revision
          && autosave.every(value => value.table.ready && value.performance.aliasReady && value.iron),
        criterion: 'Ready table and English search remain available at every observed sample across input changes',
        samples: autosave.length,
        unavailableSamples: autosave.filter(value => !value.table.ready || !value.performance.aliasReady || !value.iron).length
      });
    }
    const formatted = rows.map((row, index) => ({row, sample: samples[index]}))
      .filter(value => value.row.nativeName?.includes('§') && value.row.languages?.values.some(language => language.source === 'table'));
    if (formatted.length) {
      const attempts = [], deadline = Date.now() + 180000;
      let after;
      while (Date.now() < deadline) {
        const before = compact(await call('probe.nameReuse'));
        if (!before.table.ready) { await pause(1000); continue; }
        const result = await call('probe.nameReuse', {action: 'query', samples: formatted.map(value => value.sample)});
        const end = compact(await call('probe.nameReuse'));
        attempts.push({before, result, after: end});
        if (end.table.ready && before.table.generation === end.table.generation) { after = result; break; }
        await pause(1000);
      }
      await record('formatted-search-attempts', attempts);
      assert.ok(after, 'Formatted-name check runs within one available table binding');
      await record('formatted-after-search', after);
      for (let index = 0; index < formatted.length; index++) {
        assert.equal(after.rows[index].nativeName, formatted[index].row.nativeName, 'Formatted raw primary remains unchanged after index preparation');
        for (const language of formatted[index].row.languages.values.filter(value => value.source === 'table'))
          assert.ok(after.rows[index].languages.values.some(value => value.language === language.language
            && value.source === 'table' && value.name === language.name), 'Recipe display projection does not quarantine a valid raw name');
      }
    }
    await call('jei.search', {query: ''});
    if (state.mods.some(mod => mod.id === 'emi')) {
      await call('screen.inventory');
      let emi = await call('emi', {action: 'query', text: query});
      const emiDeadline = Date.now() + 180000;
      const matchesIron = value => value.text === query
        && value.matches?.some(key => key.startsWith('minecraft:iron_ingot:'))
        && !value.matches.some(key => key.startsWith('minecraft:stone:'));
      while (!matchesIron(emi) && Date.now() < emiDeadline) {
        await pause(1000); emi = await call('emi', {action: 'state'});
      }
      await record('emi-search-iron-ingot', emi);
      assert.ok(matchesIron(emi), 'EMI native search widget finds English alias and filters out stone');
      await call('emi', {action: 'query', text: ''});
      await call('screen.close');
    }
    const consumersBefore = compact(await call('probe.nameReuse'));
    if (extraConsumers && state.mods.some(mod => mod.id === 'appliedenergistics2')) {
      await record('ae-setup', await call('ae.setup', {items: [{item: 'minecraft:iron_ingot'}, {item: 'minecraft:stone'}]}));
      const ae = await call('ae.search', {query});
      await record('ae-search-iron-ingot', ae);
      assert.ok(ae.items.some(row => row.id === 'minecraft:iron_ingot'), 'AE2 native repository finds English alias');
      assert.ok(!ae.items.some(row => row.id === 'minecraft:stone'), 'AE2 repository retains query filtering');
      await call('ae.search', {query: ''});
    }
    if (extraConsumers && state.mods.some(mod => mod.id === 'integratedterminals')) {
      const it = await call('it.predicate', {item: 'minecraft:iron_ingot', query});
      await record('it-search-iron-ingot', it);
      assert.equal(it.result, true, 'Integrated Terminals native predicate finds English alias');
    }
    const consumersAfter = compact(await call('probe.nameReuse'));
    await record('search-consumers', {extraConsumers, before: consumersBefore, after: consumersAfter,
      counterScope: 'Global table counters include background recipe-viewer work; not an assertion about individual search rows'});
    const settleDeadline = Date.now() + 180000;
    while (!(await call('probe.nameReuse')).table.ready && Date.now() < settleDeadline) await pause(1000);
    assert.ok((await call('probe.nameReuse')).table.ready, 'Table remains available after native search');
  }
  await record('complete', {mode: mode === 'auto' ? (final.table.builds ? 'build' : 'reuse') : mode,
    requestedMode: mode, final: compact(await call('probe.nameReuse')), state: await call('status')});
  finished = true;
} catch (error) {
  await record('failure', {error: String(error), stack: error.stack, observations}); process.exitCode = 1;
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
      await record('before-quit', {finished, state: await call('status'), input: compact(await call('probe.nameReuse'))});
      await call('quit');
    } catch (error) { await record('quit-result', {error: String(error)}); }
  }
  if (bridge) bridge.close();
}
