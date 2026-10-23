// Controller for core-check.py: one scenario against a minimal-core candidate on a bridged PT-Matrix clone.
// Scenario ae2: in a throwaway pt112-core-* world, search a client ItemRepo filled through ae.setup and the terminal of
// an ME chest built through ae.world, with secondary-language queries; read the stone and iron ingot tooltips and run
// recipe-viewer searches. Scenario rs (a launch.py fixture, not a clone): switch the game to zh_tw at the main menu, build
// a creative RS network through rs.world and search the item grid with secondary-language queries. Write every
// observation and a summary, then quit.
import path from 'node:path';
import assert from 'node:assert/strict';
import {Bridge} from '../../name-audit/runner/bridge.mjs';
import {read, save, sameOwned, pause} from '../name-table/runtime-common.mjs';

const [run] = process.argv.slice(2);
const config = await read(path.join(run, 'config.json')), owner = await read(path.join(run, 'process.json'));
let bridge = null;
const call = (method, params = {}, timeout = 240000) => bridge.call(method, params, timeout);
const record = (name, value) => save(path.join(run, name + '.json'), value);
const integrationOn = !config.overrides.includes(`integrations.${config.scenario}=false`);
const ids = rows => [...new Set(rows.map(row => row.id))];

const REPO_ITEMS = ['stone', 'cobblestone', 'iron_ingot', 'iron_ore', 'gold_ingot', 'diamond', 'dirt'].map(item => ({item: 'minecraft:' + item}));
const REPO_QUERIES = [
  {query: 'stone', expect: ['minecraft:stone', 'minecraft:cobblestone'], secondary: true},
  {query: 'iron', expect: ['minecraft:iron_ingot', 'minecraft:iron_ore'], secondary: true},
  {query: 'iron ingot', expect: ['minecraft:iron_ingot'], secondary: true},
  {query: 'stone -cobble', expect: ['minecraft:stone'], secondary: true},
  {query: '石头', expect: ['minecraft:stone'], secondary: false},
  {query: '铁锭', expect: ['minecraft:iron_ingot'], secondary: false},
];
const TERMINAL_QUERIES = [
  {query: 'stone', expect: ['minecraft:stone', 'minecraft:cobblestone'], secondary: true},
  {query: 'iron', expect: ['minecraft:iron_ore', 'minecraft:iron_block'], secondary: true},
  {query: '石头', expect: ['minecraft:stone'], secondary: false},
];

function judge(entry, found) {
  const shouldHit = integrationOn || !entry.secondary;
  const hit = entry.expect.every(id => found.includes(id));
  const none = entry.expect.every(id => !found.includes(id));
  return {query: entry.query, found, expect: entry.expect, shouldHit, pass: shouldHit ? hit : none};
}

async function waitFor(test, deadlineMs, label) {
  const deadline = Date.now() + deadlineMs;
  let state = null;
  while (Date.now() < deadline) {
    if (!bridge) {
      try { bridge = await Bridge.connect(config.port); } catch { await pause(2000); continue; }
    }
    try { state = await call('status', {}, 15000); } catch { await pause(2000); continue; }
    sameOwned(state, config, owner);
    if (test(state)) return state;
    await pause(1000);
  }
  throw new Error(label + ' deadline: ' + JSON.stringify(state && state.screen));
}

async function terminalSearch(query) {
  await call('ae.ui', {focused: true});
  await call('input.key', {key: 14, repeat: 64});
  await call('input.type', {text: query});
  await call('wait.ticks', {ticks: 5});
  return call('ae.ui', {});
}

async function ae2() {
  const result = {repo: [], terminal: [], tooltips: {}, recipeViewer: []};
  const setup = await call('ae.setup', {items: REPO_ITEMS});
  await record('ae-setup', setup);
  for (const entry of REPO_QUERIES) {
    const observed = await call('ae.search', {query: entry.query});
    result.repo.push(judge(entry, ids(observed.items)));
  }
  await call('ae.search', {query: ''});
  await record('repo-results', result.repo);
  // Load and generate the chunk first: a block set in a chunk that is generated later is overwritten.
  await call('world.command', {command: '/tp @p 0 5 3'});
  await call('wait.ticks', {ticks: 60});
  await record('ae-world', await call('ae.world', {}));
  await call('wait.ticks', {ticks: 20});
  await record('ae-world-open', await call('ae.world', {open: true}));
  await call('wait.ticks', {ticks: 20});
  const opened = await call('ae.ui', {});
  await record('terminal-open', opened);
  for (const entry of TERMINAL_QUERIES) {
    const observed = await terminalSearch(entry.query);
    await record('terminal-' + entry.query.replace(/[^a-z0-9]+/gi, '_') + '-' + result.terminal.length, observed);
    result.terminal.push({...judge(entry, ids(observed.rows)), screen: observed.class, typed: observed.query});
  }
  await record('terminal-results', result.terminal);
  for (const item of ['minecraft:stone', 'minecraft:iron_ingot']) {
    const observed = await call('pack.item', {item});
    result.tooltips[item] = {name: observed.nativeName, tooltip: observed.tooltip};
  }
  const tooltipStone = result.tooltips['minecraft:stone'].tooltip.join('\n');
  const tooltipIron = result.tooltips['minecraft:iron_ingot'].tooltip.join('\n');
  result.tooltipPass = /Stone/.test(tooltipStone) && /Iron Ingot/.test(tooltipIron);
  await call('screen.close');
  for (const entry of [{query: 'stone', expect: ['minecraft:stone']}, {query: 'iron ingot', expect: ['minecraft:iron_ingot']}]) {
    const observed = await call('jei.search', {query: entry.query});
    const found = ids(observed.rows.filter(row => row.id));
    result.recipeViewer.push({query: entry.query, count: observed.count, pass: entry.expect.every(id => found.includes(id))});
  }
  await call('jei.search', {query: ''});
  result.pass = [...result.repo, ...result.terminal, ...result.recipeViewer].every(entry => entry.pass) && result.tooltipPass;
  return result;
}

const GRID_QUERIES = [
  {query: 'diamond', expect: ['minecraft:diamond'], secondary: true},
  {query: 'emerald', expect: ['minecraft:emerald'], secondary: true},
  {query: '钻石', expect: ['minecraft:diamond'], secondary: true},
  {query: '鑽石', expect: ['minecraft:diamond'], secondary: false},
];

async function rs() {
  const result = {grid: [], tooltips: {}};
  await record('rs-setup', await call('rs.world', {operation: 'setup'}));
  await call('wait.ticks', {ticks: 20});
  await record('rs-insert', await call('rs.world', {operation: 'insert'}));
  await call('wait.ticks', {ticks: 10});
  await record('rs-open', await call('rs.world', {operation: 'open', kind: 'item'}));
  await call('wait.ticks', {ticks: 20});
  await record('grid-open', await call('rs.ui', {}));
  for (const entry of GRID_QUERIES) {
    await call('rs.ui', {query: entry.query});
    await call('wait.ticks', {ticks: 5});
    const observed = await call('rs.ui', {});
    await record('grid-' + result.grid.length, observed);
    // The fixture also stores a diamond renamed "Literal fixture diamond 314"; custom names match natively, so leave them out.
    const byKey = new Map(observed.retained.filter(row => !row.literal).map(row => [row.key, row.id]));
    const found = [...new Set(observed.visible.map(key => byKey.get(key)).filter(Boolean))];
    result.grid.push({...judge(entry, found), typed: observed.query});
  }
  const observed = await call('pack.item', {item: 'minecraft:diamond'});
  result.tooltips['minecraft:diamond'] = {name: observed.nativeName, tooltip: observed.tooltip};
  result.tooltipPass = /Diamond/.test(observed.tooltip.join(' '));
  await call('screen.close');
  result.pass = result.grid.every(entry => entry.pass) && result.tooltipPass;
  return result;
}

try {
  const menu = await waitFor(state => state.screen && !state.world && /GuiMainMenu|GuiCustom|MainMenu/.test(state.screen),
    (config.menuTimeoutS ?? 2400) * 1000, 'Main menu');
  await record('status-menu', menu);
  await pause(config.menuSettleMs ?? 20000);
  if (config.language) {
    await record('language-set', await call('language.set', {language: config.language}, 600000));
    await pause(5000);
  }
  await call('world.create', {name: config.worldName});
  const world = await waitFor(state => state.world && state.player, (config.worldTimeoutS ?? 1200) * 1000, 'World readiness');
  await record('status-world', world);
  if (world.screen) await call('screen.close').catch(() => {});
  await pause(config.worldSettleMs ?? 15000);
  assert.ok(['ae2', 'rs'].includes(config.scenario));
  const result = config.scenario === 'ae2' ? await ae2() : await rs();
  await record('summary', {scenario: config.scenario, instance: config.instance, language: world.language,
    overrides: config.overrides, integrationOn, ...result});
} finally {
  if (bridge) {
    await record('before-quit', {at: new Date().toISOString()}).catch(() => {});
    await call('quit', {}, 10000).catch(() => {});
    bridge.close();
  }
}
