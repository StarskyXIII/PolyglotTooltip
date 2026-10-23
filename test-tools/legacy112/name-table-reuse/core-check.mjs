// Controller for core-check.py: one scenario against a minimal-core candidate on a bridged PT-Matrix clone.
// Scenario ae2: in a throwaway pt112-core-* world, search a client ItemRepo filled through ae.setup and the terminal of
// an ME chest built through ae.world, with secondary-language queries; read the stone and iron ingot tooltips and run
// recipe-viewer searches. Scenario rs (a launch.py fixture, not a clone): switch the game to zh_tw at the main menu, build
// a creative RS network through rs.world and search the item grid with secondary-language queries. Write every
// observation and a summary, then quit. Scenario export: in the same kind of world, write every ingredient's primary and
// secondary names through probe.nameExport (core-check.py moves the TSV into the report). Scenarios matrix and baseline
// (core-matrix.py) are described above matrix().
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
  // rv6 compiles the whole query as one pattern; the -term exclusion exists only in AE2-UEL.
  {query: 'stone -cobble', expect: ['minecraft:stone'], secondary: true, uelOnly: true},
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

async function ae2(state = null) {
  const rv6 = Boolean(state && state.mods.some(mod => mod.id === 'appliedenergistics2' && /^rv6/.test(mod.version)));
  const result = {rv6, skipped: rv6 ? REPO_QUERIES.filter(entry => entry.uelOnly).map(entry => entry.query) : [], repo: [], terminal: [], tooltips: {}, recipeViewer: []};
  const setup = await call('ae.setup', {items: REPO_ITEMS});
  await record('ae-setup', setup);
  for (const entry of REPO_QUERIES.filter(entry => !(rv6 && entry.uelOnly))) {
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

// Scenario matrix (core-matrix.py): layer 1 tooltips of hotbar items placed with /replaceitem, /polyglot why before and
// after every check, product coverage over every recipe-viewer ingredient, recipe-viewer searches, HUD lines on stone and
// water when TOP or Hwyla is loaded, and the ae2 scenario when AE2 is loaded. Scenario baseline: main menu only.
const MOD_ITEMS = [
  ['thermalfoundation:material', 128], ['mekanism:ingot', 1], ['botania:manaresource', 0],
  ['enderio:item_alloy_ingot', 0], ['appliedenergistics2:material', 0], ['tconstruct:ingots', 0],
];
const plain = text => String(text ?? '').replace(/§./g, '').trim();
const hasMod = (state, id) => state.mods.some(mod => mod.id === id);

function expectedNames(dictionary, primary, current) {
  return [...new Set(Object.entries(dictionary).filter(([language]) => language !== current)
    .map(([, value]) => plain(value)).filter(value => value && value !== plain(primary)))];
}

function parseWhy(observed) {
  const field = suffix => observed.messages.find(message => message.key === 'polyglottooltip.command.' + suffix);
  const arg = (message, index = 0) => message?.args?.[index];
  const integrations = field('integrations');
  const disabledText = integrations && arg(integrations)?.key !== 'polyglottooltip.command.none' ? arg(integrations).text : '';
  const languages = {};
  for (const message of observed.messages) {
    const kind = (message.key || '').replace('polyglottooltip.command.', '');
    if (['hit', 'fallback', 'missing'].includes(kind)) languages[arg(message).text] = {kind, value: arg(message, 1)?.text ?? ''};
  }
  const parsed = {
    key: arg(field('key'))?.text ?? null, primary: arg(field('primary'))?.text ?? null,
    standard: arg(field('standard'))?.text ?? null, match: (arg(field('match'))?.key || '').endsWith('.yes'),
    languages, resolver: arg(field('resolver'))?.text ?? null, disabledText,
    disabled: [...disabledText.matchAll(/(?:^|, )([a-z0-9-]+) \(/g)].map(match => match[1]),
  };
  parsed.parsable = Boolean(field('key') && field('primary') && field('match') && integrations);
  return parsed;
}

async function why(label, languages) {
  await call('world.command', {command: '/replaceitem entity @p slot.hotbar.0 minecraft:stone'});
  await call('wait.ticks', {ticks: 10});
  const observed = await call('client.command', {command: '/polyglot why', capture: true});
  const parsed = parseWhy(observed);
  parsed.pass = parsed.parsable && parsed.key === 'tile.stone.stone.name' && parsed.match && !parsed.disabledText
    && languages.every(language => parsed.languages[language] && parsed.languages[language].kind !== 'missing');
  await record('why-' + label, {observed, parsed});
  return parsed;
}

async function hudCheck(state, stone, water, current, failures) {
  const kinds = [...(hasMod(state, 'theoneprobe') ? ['top'] : []), ...(hasMod(state, 'waila') ? ['waila'] : [])];
  const result = {kinds, checks: []};
  if (!kinds.length) return result;
  const community = state.mods.some(mod => mod.id === 'theoneprobe' && /CE|community/i.test(mod.file + ' ' + mod.version));
  result.topCommunityEdition = community;
  try {
    result.settings = await call('hud.settings', {modName: true, timeout: 100});
    // Generate and populate the arena chunks first: populators such as Underground Biomes replace stone placed earlier.
    await call('world.command', {command: '/tp @p 48 5 3'});
    await call('wait.ticks', {ticks: 100});
    await call('hud.world', {prepare: true, target: 'stone'});
    await call('wait.ticks', {ticks: 25});
    for (const [target, title, dictionary] of [['stone', stone.displayName, stone.dictionary], ['water', water.standard, water.dictionary]]) {
      const aim = await call('hud.world', {target});
      await call('wait.ticks', {ticks: 25});
      for (const kind of kinds) {
        const expected = expectedNames(dictionary, title, current);
        const row = {target, kind, title, expected, aim};
        try {
          const observed = await call('hud.capture', {kind, name: `pt112-${config.pack}-${kind}-${target}-${Date.now()}.png`, text: title}, 60000);
          row.path = observed.path;
          row.text = (observed.matched?.text || []).map(entry => plain(entry.text));
          row.pass = expected.length > 0 && expected.every(name => row.text.includes(name));
        } catch (error) { row.error = String(error.message || error); row.pass = false; }
        row.limitation = !row.pass && kind === 'top' && community;
        result.checks.push(row);
      }
    }
  } catch (error) { result.error = String(error.message || error); }
  finally { result.restored = await call('hud.settings', {restore: true}).catch(error => String(error.message || error)); }
  result.pass = (!result.error || community) && result.checks.every(row => row.pass || row.limitation);
  if (result.error && community) result.limitation = result.error;
  if (!result.pass) failures.push('hud');
  return result;
}

async function matrix(world) {
  const failures = [];
  const current = world.language;
  const languages = config.languages;
  const result = {languages, currentLanguage: current};
  result.whyStart = await why('start', languages);
  if (!result.whyStart.pass) failures.push('why-start');
  const placed = [['minecraft:stone', 0, ''], ['minecraft:iron_ingot', 0, ''], ['minecraft:diamond_pickaxe', 0, ' {ench:[{id:32s,lvl:3s}]}']];
  const commands = [];
  for (const [index, [id, meta, nbt]] of placed.entries())
    commands.push({slot: index, id, meta, count: await call('world.command', {command: `/replaceitem entity @p slot.hotbar.${index} ${id} 1 ${meta}${nbt}`})});
  let slot = placed.length;
  for (const [id, meta] of MOD_ITEMS) {
    const count = await call('world.command', {command: `/replaceitem entity @p slot.hotbar.${slot} ${id} 1 ${meta}`});
    commands.push({slot, id, meta, count});
    if (count > 0) slot++;
  }
  await record('replaceitem', commands);
  await call('wait.ticks', {ticks: 20});
  result.items = [];
  for (const placedItem of commands.filter(entry => entry.count > 0)) {
    const item = await call('probe.coreItem', {slot: placedItem.slot, languages});
    const expected = expectedNames(item.dictionary, item.displayName, current);
    const lines = item.tooltip.slice(1).map(plain);
    const row = {slot: placedItem.slot, id: item.item, metadata: item.metadata, class: item.class, key: item.key,
      displayName: item.displayName, standard: item.standard, consistent: item.consistent, dictionary: item.dictionary,
      expected, tooltip: item.tooltip, vanilla: placedItem.id.startsWith('minecraft:')};
    row.representative = row.consistent && expected.length > 0;
    row.pass = row.representative && expected.every(name => lines.includes(name));
    result.items.push(row);
  }
  await record('items', result.items);
  const vanilla = result.items.filter(row => row.vanilla), modded = result.items.filter(row => !row.vanilla && row.representative);
  result.layer1 = {vanilla: vanilla.length, vanillaPass: vanilla.filter(row => row.pass).length,
    modRepresentative: modded.length, modPass: modded.filter(row => row.pass).length};
  result.layer1.pass = vanilla.length === 3 && vanilla.every(row => row.pass) && modded.length >= 1 && modded.every(row => row.pass);
  if (!result.layer1.pass) failures.push('layer1');
  await call('screen.close').catch(() => {});

  const coverage = await call('probe.coverage', {samples: 5, classes: 30}, 1800000);
  await record('coverage', coverage);
  result.coverage = {stacks: coverage.stacks, withSecondary: coverage.withSecondary, share: coverage.share,
    complete: coverage.complete, partial: coverage.partial, partialShare: coverage.partialShare, consistent: coverage.consistent, consistentShare: coverage.consistentShare, bySource: coverage.bySource,
    misses: coverage.misses, errors: coverage.errors, meanMicros: coverage.meanMicros, elapsedMs: coverage.elapsedMs};

  const stone = result.items.find(row => row.id === 'minecraft:stone'), iron = result.items.find(row => row.id === 'minecraft:iron_ingot');
  const queries = [];
  for (const language of languages) {
    if (language === current) continue;
    for (const row of [stone, iron]) {
      const value = row && row.dictionary[language];
      if (value && plain(value) !== row.displayName) queries.push({language, query: plain(value).toLowerCase(), expect: [row.id]});
    }
  }
  result.search = [];
  for (const entry of queries) {
    const observed = await call('jei.search', {query: entry.query});
    const found = ids(observed.rows.filter(row => row.id));
    result.search.push({...entry, count: observed.count, found: found.slice(0, 20), pass: entry.expect.every(id => found.includes(id))});
  }
  await call('jei.search', {query: ''});
  result.searchPass = result.search.length > 0 && result.search.every(entry => entry.pass);
  if (!result.searchPass) failures.push('search');
  await record('search', result.search);

  const keys = await call('probe.coreItem', {keys: ['tile.water.name'], languages});
  result.hud = await hudCheck(world, stone, keys.keys['tile.water.name'], current, failures);
  await record('hud', result.hud);

  if (hasMod(world, 'appliedenergistics2')) {
    try { result.ae2 = await ae2(world); } catch (error) { result.ae2 = {pass: false, error: String(error.message || error)}; }
    await call('screen.close').catch(() => {});
    if (!result.ae2.pass) failures.push('ae2');
  }
  result.whyEnd = await why('end', languages);
  if (!result.whyEnd.pass) failures.push('why-end');
  result.failures = failures;
  result.pass = failures.length === 0;
  return result;
}

async function nameExport() {
  const exported = await call('probe.nameExport', {source: config.exportSource ?? 'auto', file: config.exportFile}, 1200000);
  await record('export', exported);
  return {export: exported, pass: exported.stacks > 0 && exported.languages.length > 0};
}

try {
  const menu = await waitFor(state => state.screen && !state.world && /GuiMainMenu|GuiCustom|MainMenu/.test(state.screen),
    (config.menuTimeoutS ?? 2400) * 1000, 'Main menu');
  await record('status-menu', menu);
  await pause(config.menuSettleMs ?? 20000);
  if (config.scenario === 'baseline') {
    await record('summary', {scenario: 'baseline', instance: config.instance, language: menu.language, pass: true});
  } else {
    if (config.language) {
      await record('language-set', await call('language.set', {language: config.language}, 600000));
      await pause(5000);
    }
    await call('world.create', {name: config.worldName});
    const world = await waitFor(state => state.world && state.player, (config.worldTimeoutS ?? 1200) * 1000, 'World readiness');
    await record('status-world', world);
    if (world.screen) await call('screen.close').catch(() => {});
    await pause(config.worldSettleMs ?? 15000);
    assert.ok(['ae2', 'rs', 'matrix', 'export'].includes(config.scenario));
    const result = config.scenario === 'ae2' ? await ae2() : config.scenario === 'rs' ? await rs()
      : config.scenario === 'export' ? await nameExport() : await matrix(world);
    await record('summary', {scenario: config.scenario, instance: config.instance, language: world.language,
      overrides: config.overrides, integrationOn, ...result});
  }
} finally {
  if (bridge) {
    await record('before-quit', {at: new Date().toISOString()}).catch(() => {});
    await call('quit', {}, 10000).catch(() => {});
    bridge.close();
  }
}
