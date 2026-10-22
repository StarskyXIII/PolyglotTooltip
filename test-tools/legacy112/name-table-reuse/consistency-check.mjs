// Coverage-gap controller for the minimal-core design (docs/refactoring/2026-10-22-legacy112-minimal-core-design.md §8).
// Driven by consistency-run.py: the product jar is hidden, only the passive devbridge is attached. Wait for the main
// menu, run probe.consistency once there (every JEI/HEI ingredient against the vanilla standard naming path and the
// pack's own lang files for the configured secondary languages), then create a throwaway world and run it again
// (names that depend on a client world settle there), write the two reports and a summary, and quit on request.
import path from 'node:path';
import assert from 'node:assert/strict';
import {Bridge} from '../../name-audit/runner/bridge.mjs';
import {read, save, sameOwned, pause} from '../name-table/runtime-common.mjs';

const [run] = process.argv.slice(2);
const config = await read(path.join(run, 'config.json')), owner = await read(path.join(run, 'process.json'));
let bridge = null;
const call = (method, params = {}, timeout = 240000) => bridge.call(method, params, timeout);
const record = (name, value) => save(path.join(run, name + '.json'), value);

async function probe(phase) {
  const started = Date.now();
  const result = await call('probe.consistency',
    {languages: config.languages, samples: config.samples ?? 5, source: config.source ?? 'auto', fluids: true}, 1800000);
  result.phase = phase;
  result.controllerMs = Date.now() - started;
  await record(phase + '-consistency', result);
  return result;
}

function summarize(menu, world) {
  const final = world ?? menu;
  const totals = final.totals;
  const measured = totals.stacks - totals.custom - totals.errors;
  return {
    pack: config.pack,
    instance: config.instance,
    currentLanguage: final.currentLanguage,
    languages: config.languages,
    source: final.source,
    sourceError: final.sourceError ?? null,
    productLoaded: final.productLoaded,
    phaseUsed: final.phase,
    stacks: totals.stacks,
    custom: totals.custom,
    measured,
    consistent: totals.consistent,
    consistentShare: totals.consistentShare,
    consistentUntranslated: totals.consistentUntranslated,
    inconsistent: totals.inconsistent,
    inconsistentOverride: totals.inconsistentOverride,
    inconsistentExternal: totals.inconsistentExternal,
    inconsistentContains: totals.inconsistentContains,
    inconsistentKeyless: totals.inconsistentKeyless,
    errors: totals.errors,
    errorKinds: final.errors,
    languageHits: totals.languages,
    dictionaries: final.dictionaries,
    standardPathMeanMicros: final.standardPathMeanMicros,
    classes: final.classes,
    fluids: final.fluids ? {total: final.fluids.total, consistent: final.fluids.consistent, inconsistent: final.fluids.inconsistent, errors: final.fluids.errors, languages: final.fluids.languages} : null,
    topInconsistentClasses: final.byClass.slice(0, 25).filter(entry => entry.inconsistent > 0 || entry.errors > 0)
      .map(entry => ({name: entry.name, mod: entry.mod, stacks: entry.stacks, inconsistent: entry.inconsistent, override: entry.inconsistentOverride, external: entry.inconsistentExternal, composite: entry.inconsistentContains, keyless: entry.inconsistentKeyless, errors: entry.errors, sample: entry.samples[0] ?? null})),
    topInconsistentMods: final.byMod.slice(0, 25).filter(entry => entry.inconsistent > 0 || entry.errors > 0)
      .map(entry => ({mod: entry.mod, stacks: entry.stacks, inconsistent: entry.inconsistent, override: entry.inconsistentOverride, external: entry.inconsistentExternal, errors: entry.errors})),
    menuVersusWorld: world ? {menuConsistent: menu.totals.consistent, worldConsistent: world.totals.consistent, menuErrors: menu.totals.errors, worldErrors: world.totals.errors} : null,
    elapsedMs: {menu: menu.elapsedMs, world: world?.elapsedMs ?? null},
  };
}

try {
  const deadline = Date.now() + (config.menuTimeoutS ?? 1200) * 1000;
  let state = null;
  while (Date.now() < deadline) {
    if (!bridge) {
      try { bridge = await Bridge.connect(config.port); } catch { await pause(1500); continue; }
    }
    try { state = await call('status', {}, 10000); } catch { await pause(1500); continue; }
    sameOwned(state, config, owner);
    if (state.screen && !state.world && /GuiMainMenu|GuiCustom|MainMenu/.test(state.screen)) break;
    await pause(1000);
  }
  assert.ok(state && !state.world, 'Main menu deadline: ' + JSON.stringify(state && state.screen));
  await record('status-menu', state);
  // Recipe viewers finish their ingredient lists shortly after load complete; give them a moment before the first pass.
  await pause(config.menuSettleMs ?? 20000);
  const menu = await probe('menu');
  let world = null;
  if (config.world !== false) {
    await call('world.create', {name: config.worldName});
    const worldDeadline = Date.now() + 480000;
    while (Date.now() < worldDeadline) {
      state = await call('status');
      if (state.world && state.player) break;
      await pause(250);
    }
    assert.ok(state.world && state.player, 'World readiness deadline');
    await record('status-world', state);
    if (state.screen) await call('screen.close').catch(() => {});
    await pause(config.worldSettleMs ?? 10000);
    world = await probe('world');
  }
  await record('summary', summarize(menu, world));
} finally {
  if (bridge) {
    await record('before-quit', {at: new Date().toISOString()}).catch(() => {});
    await call('quit', {}, 10000).catch(() => {});
    bridge.close();
  }
}
