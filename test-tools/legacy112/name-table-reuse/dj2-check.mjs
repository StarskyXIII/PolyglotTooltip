// Name check on the user's Divine Journey 2 instance (POLYGLOT_INSTANCE="Divine Journey 2"; plain Forge 14.23.5.2860 on
// LaunchWrapper and Java 8, MixinBooter 10.7, zh_cn with an en_us secondary): the first non-Cleanroom pack in the
// regression loop. Join a throwaway world, wait for the table and the alias index, then put vanilla stone and iron, a
// diamond pickaxe in three tag shapes, and representative Draconic Evolution, Thermal, Tinkers, Mekanism and Botania
// items into the main hand and record which secondary rows the product shows; then the shared enchantment cases.
// Same flow and summary as e2ee-check.mjs, plus the name contracts' observer and definition-gate lines in the summary.
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {Bridge} from '../../name-audit/runner/bridge.mjs';
import {read, save, sameOwned, pause} from '../name-table/runtime-common.mjs';
import {checkEnchantments, enchantmentSummary} from './enchantment-cases.mjs';
import {playLaunchWarnings} from './play-launch.mjs';

const [run] = process.argv.slice(2);
const config = await read(path.join(run, 'config.json')), owner = await read(path.join(run, 'process.json'));
let bridge, owned = false;
const call = (method, params = {}, timeout = 240000) => bridge.call(method, params, timeout);
const record = (name, value) => save(path.join(run, name + '.json'), value);
const HELD = [
  {id: 'stone-control', give: 'minecraft:stone 1 0'},
  {id: 'iron-ingot', give: 'minecraft:iron_ingot 1 0'},
  {id: 'diamond-pickaxe-tagless', give: 'minecraft:diamond_pickaxe 1 0'},
  {id: 'diamond-pickaxe-empty-tag', give: 'minecraft:diamond_pickaxe 1 0 {}'},
  {id: 'diamond-pickaxe-unbreakable', give: 'minecraft:diamond_pickaxe 1 0 {Unbreakable:1b}'},
  {id: 'wyvern-pick-plain', give: 'draconicevolution:wyvern_pick 1 0 {}'},
  {id: 'wyvern-pick-energy', give: 'draconicevolution:wyvern_pick 1 0 {Energy:100000}'},
  {id: 'thermal-copper-ingot', give: 'thermalfoundation:material 1 128'},
  {id: 'tconstruct-cobalt-ingot', give: 'tconstruct:ingots 1 0'},
  {id: 'mekanism-osmium-ingot', give: 'mekanism:ingot 1 1'},
  {id: 'botania-manasteel', give: 'botania:manaresource 1 0'},
];
const SYNTHETIC = [
  {id: 'synthetic-stone', item: 'minecraft:stone'},
  {id: 'synthetic-diamond-pickaxe', item: 'minecraft:diamond_pickaxe'},
  {id: 'synthetic-wyvern-pick', item: 'draconicevolution:wyvern_pick'},
];
// Uncompressed NBT of a bare stack: root compound with id, Count and Damage, as a recipe viewer would hold it.
function bareStack(item, count = 1, damage = 0) {
  const str = text => { const bytes = Buffer.from(text, 'utf8'); const length = Buffer.alloc(2); length.writeUInt16BE(bytes.length); return Buffer.concat([length, bytes]); };
  const short = value => { const bytes = Buffer.alloc(2); bytes.writeInt16BE(value); return bytes; };
  return Buffer.concat([
    Buffer.from([10]), str(''),
    Buffer.from([8]), str('id'), str(item),
    Buffer.from([1]), str('Count'), Buffer.from([count]),
    Buffer.from([2]), str('Damage'), short(damage),
    Buffer.from([0]),
  ]).toString('base64');
}
const summarize = (row, observe, query) => {
  const observed = observe?.rows?.[0];
  row.tooltip = observed?.tooltip ?? null;
  row.created = observed?.created?.map(entry => entry.text) ?? null;
  row.outcomes = observed?.annotationOutcomes ?? null;
  row.productObservation = observed ? ('productNameObservation' in observed) : null;
  row.productObservationDetail = observed?.productNameObservation ?? null;
  row.captureFailed = observed?.captureFailed ?? null;
  row.phases = observed?.phases ?? null;
  const queried = query?.rows?.[0];
  row.tableHits = queried?.tooltipTableHits ?? null;
  row.repeatTableHits = queried?.repeatTooltipTableHits ?? null;
  row.nativeCaptured = queried?.nativeCaptured ?? null;
  row.languages = queried?.languages ?? null;
  row.searchNames = queried?.searchNames ?? null;
  row.queryError = queried?.error ?? null;
  // The tooltip text itself proves a row: the plain production bridge has no audit transformer, so `created`
  // stays empty there and only the lines show whether the product appended the resolved names.
  row.queryTooltip = queried?.tooltip ?? null;
  row.repeatTooltip = queried?.repeatTooltip ?? null;
  const resolved = (queried?.languages?.values ?? []).filter(value => value.status === 'translated').map(value => value.name);
  const plain = line => String(line).replace(/§./g, '').trim();
  // Without the audit transformer the query carries no resolved languages; the product's own secondary-row
  // style then identifies the appended rows: the lines right after the title whose every run is colour + italic
  // (yellow for a plain title, the title's own colours otherwise, e.g. "§a§o主世界§e§o Matter§r"), closed by a reset.
  // DJ2's config sets tooltipSecondaryNameFormat=&7: colour only, no italic (the other packs use colour + italic).
  const styled = lines => {
    const rows = [];
    for (const line of (lines ?? []).slice(1)) {
      if (!/^(§[0-9a-f](§o)?[^§]*)+§r$/.test(String(line))) break;
      rows.push(plain(line));
    }
    return rows;
  };
  const holds = lines => resolved.length > 0 ? resolved.every(name => (lines ?? []).some(line => plain(line) === name)) : styled(lines).length > 0;
  row.secondaryRowsFirst = styled(row.queryTooltip);
  row.secondaryRowsRepeat = styled(row.repeatTooltip);
  row.namesInFirstTooltip = holds(row.queryTooltip);
  row.namesInRepeatTooltip = holds(row.repeatTooltip);
  row.namesInObservedTooltip = holds(row.tooltip);
};
const flatten = (value, prefix = '', out = {}) => {
  if (value && typeof value === 'object' && !Array.isArray(value)) for (const [key, inner] of Object.entries(value)) flatten(inner, prefix ? prefix + '.' + key : key, out);
  else out[prefix] = Array.isArray(value) ? JSON.stringify(value).slice(0, 300) : value;
  return out;
};

// DJ2_RESTART_CHECK=1: after the cases, recreate the two things that once refused every name in play. A quest description
// is edited on disk while the game runs (the live maps keep the loaded text), a runtime writer changes another one in
// the live client dictionary, and a late reload listener makes the injections stale on the same native snapshot, so the
// preparation restarts without a resource reload. Expected: the restart reads the files captured at the last reload (the
// disk edit stays invisible), withholds exactly the live-written key, and names keep coming from the table.
const QUEST = 'resources/betterquesting/lang/zh_tw.lang', EDITED = 'dj2.quest.db.1427.desc', LIVE = 'dj2.quest.db.1653.desc';
async function restartCheck() {
  const file = path.join(config.fixture, QUEST), log = path.join(config.fixture, 'logs', 'latest.log');
  const original = await fs.readFile(file);
  const backup = path.join(run, 'quest-zh_tw.lang.bak');
  await fs.writeFile(backup, original, {flag: 'wx'});
  const result = {edited: EDITED, live: LIVE};
  const offset = (await fs.readFile(log)).length;
  try {
    const lines = original.toString('utf8').split('\n');
    const index = lines.findIndex(line => line.startsWith(EDITED + '='));
    assert.ok(index >= 0, 'quest key not in ' + QUEST);
    lines[index] = lines[index].replace(/\r?$/, ' [pt-disk-edit]' + (lines[index].endsWith('\r') ? '\r' : ''));
    await fs.writeFile(file, lines.join('\n'));
    result.write = await call('lifecycle.languageRestart', {action: 'write-live', key: LIVE, value: 'pt-live-writer'});
    result.listener = await call('lifecycle.languageRestart', {action: 'append-listener'});
    const deadline = Date.now() + 180000;
    let text = '';
    while (Date.now() < deadline) {
      await pause(2000);
      text = (await fs.readFile(log)).subarray(offset).toString('utf8');
      if (/Secondary language preparation completed|Secondary languages are unavailable/.test(text)) break;
    }
    const polyglot = text.split('\n').filter(line => line.includes('[polyglottooltip]')).map(line => line.slice(0, 700));
    result.lines = polyglot;
    result.restarted = polyglot.some(line => line.includes('restarts (stale:injections)'));
    result.reusedFiles = polyglot.some(line => line.includes('reads the language files captured'));
    const except = polyglot.find(line => /matches the live .* except \d+ keys/.test(line));
    result.withheldLine = except ?? null;
    result.withheldOnlyLive = !!except && except.includes(LIVE) && !except.includes(EDITED) && /except 1 keys/.test(except);
    result.unavailable = polyglot.some(line => line.includes('Secondary languages are unavailable'));
    result.completed = polyglot.some(line => line.includes('Secondary language preparation completed'));
    await pause(5000);
    result.published = await call('lifecycle.languageRestart', {action: 'published-withheld', key: LIVE});
    result.publishedWithholds = result.published.primary === true && result.published.secondaryTables > 0
      && result.published.secondaryWithholding === result.published.secondaryTables;
    const state = await call('probe.nameReuse');
    result.tableState = state.table?.state ?? null;
    result.nativeUnavailable = state.nativeIndependentUnavailable ?? null;
    await call('world.command', {command: '/replaceitem entity @p slot.hotbar.0 minecraft:stone 1 0'});
    await call('wait.ticks', {ticks: 10});
    const held = await call('audit.captureHeld');
    const query = await call('probe.nameReuse', {action: 'query', samples: [{sampleId: 'restart-stone', item: held.item, metadata: held.metadata,
      damage: held.metadata, count: 1, stack: held.stack, serialized: held.serialized}]});
    result.stoneTooltip = query.rows?.[0]?.tooltip ?? null;
    result.stoneNamed = (result.stoneTooltip ?? []).slice(1).some(line => /^(§[0-9a-f](§o)?[^§]*)+§r$/.test(String(line)));
    result.pass = result.restarted && result.reusedFiles && result.withheldOnlyLive && !result.unavailable && result.completed
      && result.publishedWithholds && result.stoneNamed;
  } finally {
    await fs.writeFile(file, original);
    const restored = await fs.readFile(file);
    result.restored = Buffer.compare(restored, original) === 0;
    if (result.restored) await fs.unlink(backup);
  }
  return result;
}
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
  const contracts = await call('audit.contracts', {}, 120000).catch(error => ({error: String(error)}));
  await record('contracts', contracts);
  // The plain-Forge gates this pack exercises: the observer must end the pipeline and no definition may have failed.
  const gate = {};
  for (const [key, value] of Object.entries(flatten(contracts)))
    if (/observer|definition|delegated@|skipped@|observerTail/.test(key)) gate[key] = value;
  await record('reuse-state', await call('probe.nameReuse'));
  await call('world.command', {command: '/gamemode creative @p'}).catch(() => {});
  const results = [];
  for (const entry of HELD) {
    const row = {id: entry.id, give: entry.give};
    try {
      row.replaced = await call('world.command', {command: '/replaceitem entity @p slot.hotbar.0 ' + entry.give});
      await call('wait.ticks', {ticks: 10});
      const held = await call('audit.captureHeld');
      row.item = held.item; row.metadata = held.metadata; row.heldStack = held.sourceAfter;
      row.ready = (await call('probe.nameReuse')).table.ready;
      const observe = await call('audit.observe', {samples: [{id: entry.id, serialized: held.serialized}], phases: true});
      const query = await call('probe.nameReuse', {action: 'query', samples: [{
        sampleId: entry.id, item: held.item, metadata: held.metadata, damage: held.metadata, count: 1, stack: held.stack, serialized: held.serialized,
      }]}).catch(error => ({rows: [{error: String(error).slice(0, 400)}]}));
      summarize(row, observe, query);
      row.full = {observe, query};
    } catch (error) { row.error = String(error).slice(0, 500); }
    results.push(row);
  }
  for (const entry of SYNTHETIC) {
    const row = {id: entry.id, item: entry.item, synthetic: true};
    try {
      const serialized = bareStack(entry.item);
      row.ready = (await call('probe.nameReuse')).table.ready;
      const observe = await call('audit.observe', {samples: [{id: entry.id, serialized}], phases: true});
      summarize(row, observe, null);
      row.full = {observe};
    } catch (error) { row.error = String(error).slice(0, 500); }
    results.push(row);
  }
  const enchantments = await checkEnchantments(call);
  const enchantmentState = (await call('lifecycle.snapshot').catch(error => ({enchantment: {error: String(error)}}))).enchantment ?? null;
  await record('enchantments-full', {state: enchantmentState, cases: enchantments.cases});
  await record('reuse-state-after', await call('probe.nameReuse'));
  const restart = process.env.DJ2_RESTART_CHECK === '1' ? await restartCheck().catch(error => ({pass: false, error: String(error)})) : null;
  if (restart) await record('restart', restart);
  // An enchantment row without its secondary name fails the whole check: that loss used to surface only in play.
  // So does a jar without a clean no-bridge play launch on this instance (play-check.py): the bridge changes ItemStack.
  const play = await playLaunchWarnings(run, config);
  // On plain Forge the whole feature hangs on the observer ending every pipeline run and on no definition failure;
  // a jar that falls at either gate fails the check by name instead of showing only as an empty table.
  const pipeline = gate['effectiveNameMethods.observer@pipeline'];
  const failed = [...(enchantments.pass ? [] : ['enchantment-rows']), ...(play.failed ? [play.failed] : []),
    ...(restart && !restart.pass ? ['restart-check'] : []), ...(restart && !restart.restored ? ['quest-file-not-restored'] : []),
    ...(typeof pipeline === 'string' && pipeline.endsWith('-final') ? [] : ['observer-' + (pipeline ?? 'absent')]),
    ...(gate['effectiveNameMethods.observer@definition'] ? ['definition-failure'] : []),
    ...(tableState === 'deferred' || tableState === null ? ['table-' + tableState] : [])];
  await record('summary', {worldName: config.worldName, candidate: config.candidate, tableState, aliasReady, gate,
    restart: restart && {pass: restart.pass, restarted: restart.restarted, reusedFiles: restart.reusedFiles, withheldOnlyLive: restart.withheldOnlyLive, publishedWithholds: restart.publishedWithholds, completed: restart.completed, unavailable: restart.unavailable, stoneNamed: restart.stoneNamed, restored: restart.restored},
    failed, playLaunchWarnings: play, enchantmentState, enchantments: enchantmentSummary(enchantments),
    cases: results.map(({full, phases, productObservationDetail, heldStack, ...rest}) => rest)});
  await record('cases-full', results);
  if (failed.length) process.exitCode = 2;
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
