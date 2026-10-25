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
  if (config.rsFluids) result.fluids = await rsFluids();
  result.pass = result.grid.every(entry => entry.pass) && result.tooltipPass && (!result.fluids || result.fluids.pass);
  return result;
}

// Grid item tooltips come from the layer-1 tooltip event, so they keep the secondary line with the rs integration off;
// fluid search and fluid tooltips belong to the rs integration.
async function rsFluids() {
  const result = {};
  await record('rs-open-item-warm', await call('rs.world', {operation: 'open', kind: 'item'}));
  await call('wait.ticks', {ticks: 20});
  await call('rs.ui', {query: ''});
  const items = await call('rs.ui', {warm: true});
  await record('grid-item-warm', items);
  const diamond = items.retained.find(row => row.id === 'minecraft:diamond' && !row.literal && row.tooltip);
  result.itemTooltip = diamond ? diamond.tooltip : null;
  result.itemTooltipPass = Boolean(diamond) && diamond.tooltip.split('\n').some(line => plain(line) === 'Diamond');
  await call('screen.close');
  await record('rs-open-fluid', await call('rs.world', {operation: 'open', kind: 'fluid'}));
  await call('wait.ticks', {ticks: 20});
  await call('rs.ui', {query: 'water'});
  await call('wait.ticks', {ticks: 5});
  const searched = await call('rs.ui', {});
  await record('grid-fluid-water', searched);
  const byKey = new Map(searched.retained.map(row => [row.key, row.id]));
  const found = [...new Set(searched.visible.map(key => byKey.get(key)).filter(Boolean))];
  result.search = {...judge({query: 'water', expect: ['water'], secondary: true}, found), typed: searched.query};
  if (found.includes('lava')) result.search.pass = false;
  await call('rs.ui', {query: ''});
  const warm = await call('rs.ui', {warm: true});
  await record('grid-fluid-warm', warm);
  const water = warm.retained.find(row => row.id === 'water');
  result.fluidTooltip = water ? water.tooltip : null;
  const hasLine = Boolean(water && water.tooltip) && water.tooltip.split('\n').some(line => plain(line) === 'Water');
  result.fluidTooltipPass = integrationOn ? hasLine : Boolean(water && water.tooltip) && !hasLine;
  await call('screen.close');
  result.pass = result.itemTooltipPass && result.search.pass && result.fluidTooltipPass;
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

const PARTIAL_PREFERENCE = ['appeng.items.parts.ItemFacade', 'mod.chiselsandbits.items.ItemChiseledBit',
  'slimeknights.tconstruct.library.tools.ToolPart'];

// /polyglot why on one held partial name from the coverage samples; recorded, not part of the pass condition.
async function whyPartial(coverage) {
  const classes = coverage.topPartialClasses || [];
  const ordered = [...classes.filter(entry => PARTIAL_PREFERENCE.includes(entry.name)), ...classes];
  const sample = ordered.flatMap(entry => entry.partialSamples || [])[0];
  if (!sample) return {sample: null, pass: false};
  const nbt = sample.nbt ? ' ' + sample.nbt : '';
  const count = await call('world.command', {command: `/replaceitem entity @p slot.hotbar.0 ${sample.id} 1 ${sample.metadata}${nbt}`});
  await call('wait.ticks', {ticks: 10});
  const observed = await call('client.command', {command: '/polyglot why', capture: true});
  const untranslated = observed.messages.filter(message => message.key === 'polyglottooltip.command.untranslated')
    .map(message => ({language: message.args?.[0]?.text, parts: message.args?.[1]?.text}));
  const source = observed.messages.find(message => message.key === 'polyglottooltip.command.source')?.args?.[0]?.text ?? null;
  const expected = Object.entries(sample.untranslated || {}).map(([language, parts]) => ({language, parts: parts.join(', ')}));
  const pass = count > 0 && expected.length > 0
    && expected.every(entry => untranslated.some(line => line.language === entry.language && line.parts === entry.parts));
  const result = {sample, count, source, untranslated, expected, pass};
  await record('why-partial', {observed, ...result});
  return result;
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

  // DEBUG: cost breakdown of the work the product adds around the recipe-viewer filter; recorded, not part of the pass condition.
  result.indexCost = await call('probe.indexCost', {}, 1800000).catch(error => ({error: String(error)}));
  await record('index-cost', result.indexCost);

  const coverage = await call('probe.coverage', {samples: 5, classes: 30}, 1800000);
  await record('coverage', coverage);
  result.coverage = {stacks: coverage.stacks, withSecondary: coverage.withSecondary, share: coverage.share,
    complete: coverage.complete, partial: coverage.partial, partialShare: coverage.partialShare, consistent: coverage.consistent, consistentShare: coverage.consistentShare, bySource: coverage.bySource,
    misses: coverage.misses, errors: coverage.errors, meanMicros: coverage.meanMicros, elapsedMs: coverage.elapsedMs};
  result.whyPartial = await whyPartial(coverage);

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

// Scenario peripherals: AE2 add-on terminals and tooltips on a bridged clone. Every check that belongs to an integration
// expects the secondary (en_us) name when that integration is on and no secondary name when it is turned off through
// --set integrations.<id>=false; layer 1 and the recipe viewer are expected to work in both cases.
const lower = text => plain(text).toLowerCase();
const hasLineWith = (lines, name) => (lines || []).some(line => plain(line) === name);
const containsName = (text, name) => String(text || '').split('\n').some(line => plain(line) === name);
const isOn = id => !config.overrides.includes(`integrations.${id}=false`);

function expectation(id, hit, extra = {}) {
  const on = isOn(id);
  return {integration: id, on, hit, ...extra, pass: on ? hit : !hit};
}

function partsExpectation(id, parts, extra = {}) {
  const on = isOn(id);
  return {integration: id, on, parts, ...extra, pass: on ? Object.values(parts).every(Boolean) : !Object.values(parts).some(Boolean)};
}

async function step(result, name, action) {
  try { result[name] = await action(); } catch (error) { result[name] = {pass: false, error: String(error.message || error)}; }
  await call('screen.close').catch(() => {});
  await record('peripheral-' + name, result[name]);
}

async function aeFluid() {
  const widgets = await call('ae.fluid.widgets', {});
  const emptyUnchanged = !/Water|Lava/.test(String(widgets.empty.tank) + String(widgets.empty.slot));
  await call('world.command', {command: '/tp @p 0 5 3'});
  await call('wait.ticks', {ticks: 60});
  await record('ae-fluid-world', await call('ae.world', {fluid: true}));
  await call('wait.ticks', {ticks: 20});
  await record('ae-fluid-open', await call('ae.world', {open: true}));
  await call('wait.ticks', {ticks: 20});
  const hover = await call('ae.fluid.hover', {});
  const english = {water: 'Water', lava: 'Lava'}[hover.fluid];
  const parts = {tankWater: containsName(widgets.water.tank, 'Water'), slotWater: containsName(widgets.water.slot, 'Water'),
    tankLava: containsName(widgets.lava.tank, 'Lava'), terminal: hasLineWith(hover.lines, english)};
  const result = partsExpectation('ae2', parts, {widgets, hover, emptyUnchanged});
  result.pass = result.pass && emptyUnchanged;
  return result;
}

async function aeLabels() {
  await call('world.command', {command: '/tp @p 4 5 3'});
  await call('wait.ticks', {ticks: 40});
  await record('ae-interface-world', await call('ae.world', {mode: 'interface'}));
  await call('wait.ticks', {ticks: 40});
  await record('ae-interface-open', await call('ae.world', {mode: 'interface', open: true}));
  await call('wait.ticks', {ticks: 40});
  const before = await call('ae.interface', {});
  const entry = before.rows.find(row => row.raw);
  if (!entry) return {integration: 'ae2', pass: false, error: 'no interface entry', before};
  const candidates = [entry.raw + '.name', entry.raw];
  const keys = await call('probe.coreItem', {keys: candidates, languages: ['en_us']});
  const english = candidates.map(key => keys.keys[key] && keys.keys[key].dictionary && keys.keys[key].dictionary.en_us).find(Boolean);
  const word = english && plain(english).toLowerCase().split(/\s+/).find(part => part.length > 2 && !lower(entry.label).includes(part));
  // Older AE2-UEL builds send the label already localized on the server, so there is no key to look up.
  if (!english && entry.raw === entry.label) return {integration: 'ae2', applicable: false, pass: true, reason: 'label sent without a key', before, keys};
  if (!word) return {integration: 'ae2', pass: false, error: 'no secondary label word', before, keys};
  await call('ae.interface', {focus: 'searchFieldNames'});
  await call('input.key', {key: 14, repeat: 64});
  await call('input.type', {text: word});
  await call('wait.ticks', {ticks: 10});
  const after = await call('ae.interface', {});
  return expectation('ae2', after.rows.some(row => row.raw === entry.raw), {label: entry.label, raw: entry.raw, english, word, before, after});
}

async function mekeng() {
  await call('world.command', {command: '/tp @p 20 5 3'});
  await call('wait.ticks', {ticks: 40});
  await record('mek-world', await call('addon.world', {mode: 'gas'}));
  await call('wait.ticks', {ticks: 40});
  await record('mek-open', await call('addon.world', {mode: 'gas', open: true}));
  await call('wait.ticks', {ticks: 40});
  const all = await call('addon.ui', {query: ''});
  const searched = await call('addon.ui', {query: 'hydrogen'});
  await call('addon.ui', {query: ''});
  await call('wait.ticks', {ticks: 5});
  const hover = await call('addon.hover', {});
  const widgets = await call('addon.widgets', {});
  const row = key => widgets.rows.find(entry => entry.key === key) || {lines: []};
  await call('screen.close').catch(() => {});
  await record('mek-config-world', await call('addon.world', {mode: 'gasConfig'}));
  await call('wait.ticks', {ticks: 40});
  await record('mek-config-open', await call('addon.world', {mode: 'gasConfig', open: true}));
  await call('wait.ticks', {ticks: 40});
  const configAll = await call('addon.ui', {query: ''});
  const configSearched = await call('addon.ui', {query: 'hydrogen'});
  const parts = {
    search: searched.rows.includes('hydrogen') && !searched.rows.includes('oxygen'),
    hover: hasLineWith(hover.lines, 'Hydrogen') || hasLineWith(hover.lines, 'Oxygen'),
    slot: hasLineWith(row('mek-slot-hydrogen').lines, 'Hydrogen'), tank: hasLineWith(row('mek-tank-hydrogen').lines, 'Hydrogen'),
    configSearch: configAll.rows.length > 0 && configSearched.rows.length > 0,
  };
  return partsExpectation('mekanismenergistics', parts, {all, searched, hover, configAll, configSearched,
    widgets: widgets.rows.filter(entry => entry.key.startsWith('mek-'))});
}

async function addonSearch() {
  const setup = await call('addon.search.setup', {});
  await record('addon-search-setup', setup);
  const result = {};
  if (setup.p2p) {
    const query = '@type=redstone';
    const observed = await call('addon.search.query', {owner: 'p2p', query});
    const types = new Set(observed.p2p.rows.map(row => row.type));
    const nativeHit = observed.p2p.types.some(type => types.has(type.index) && lower(type.currentNative).includes('redstone'));
    result.betterp2p = expectation('betterp2p', observed.p2p.rows.length > 0 && types.size === 1 && !nativeHit,
      {query, rows: observed.p2p.rows, types: observed.p2p.types});
    await call('addon.search.query', {owner: 'p2p', query: ''});
  }
  if (setup.lazy) {
    const observed = await call('addon.search.query', {owner: 'lazy', query: 'diamond'});
    result.lazyae2 = expectation('lazyae2', observed.lazy.length > 0 && observed.lazy.every(slot => slot.matches), {slots: observed.lazy});
  }
  if (setup.nee) {
    const observed = await call('addon.search.query', {owner: 'nee', query: 'diamond'});
    const rows = observed.nee.rows;
    result.notenoughenergistics = expectation('notenoughenergistics',
      rows.length > 0 && rows.every(row => row.startsWith('minecraft:diamond#')), {rows});
  }
  result.pass = Object.values(result).every(entry => entry.pass);
  return result;
}

async function fluidCrafting(world) {
  const contents = [['water', 'Water'], ['lava', 'Lava'], ...(hasMod(world, 'mekeng') ? [['hydrogen', 'Hydrogen']] : [])];
  const renders = {};
  for (const [content] of contents) renders[content] = await call('addon.fc', {render: content});
  const widgets = await call('addon.widgets', {});
  const tank = widgets.rows.find(entry => entry.key === 'fc-tank-water') || {lines: []};
  const inspected = await call('addon.fc', {}).catch(error => ({error: String(error.message || error)}));
  const parts = {tank: hasLineWith(tank.lines, 'Water')};
  for (const [content, english] of contents) parts[content] = hasLineWith(renders[content].lines, english);
  return partsExpectation('ae2fc', parts, {renders, tank, inspected});
}

async function extraCells() {
  await call('world.command', {command: '/tp @p 20 5 3'});
  await call('wait.ticks', {ticks: 40});
  await record('ec-world', await call('addon.world', {mode: 'ec'}));
  await call('wait.ticks', {ticks: 40});
  await record('ec-open', await call('addon.world', {mode: 'ec', open: true}));
  await call('wait.ticks', {ticks: 40});
  const all = await call('addon.ui', {query: ''});
  const searched = await call('addon.ui', {query: 'water'});
  await call('addon.ui', {query: ''});
  const selector = await call('addon.hover', {});
  const request = await call('addon.hover', {request: true});
  const widgets = await call('addon.widgets', {});
  const row = key => widgets.rows.find(entry => entry.key === key) || {lines: []};
  const parts = {search: searched.rows.includes('water') && !searched.rows.includes('lava'),
    selector: hasLineWith(selector.lines, 'Water'), request: hasLineWith(request.lines, 'Water'),
    tankInternal: hasLineWith(row('ec-tank-INTERNAL-water').lines, 'Water'),
    tankNorth: hasLineWith(row('ec-tank-NORTH-water').lines, 'Water'), slot: hasLineWith(row('ec-slot-water').lines, 'Water')};
  return partsExpectation('extracells', parts, {all, searched, selector, request,
    widgets: widgets.rows.filter(entry => entry.key.startsWith('ec-'))});
}

async function thaumicEnergistics() {
  await call('world.command', {command: '/tp @p 20 5 3'});
  await call('wait.ticks', {ticks: 40});
  await record('the-world', await call('addon.world', {mode: 'the'}));
  await call('wait.ticks', {ticks: 40});
  await record('the-open', await call('addon.world', {mode: 'the', open: true}));
  await call('wait.ticks', {ticks: 40});
  const all = await call('addon.ui', {query: ''});
  // The terminal also matches tooltips, which carry the layer-1 line, so the name predicate decides the search part.
  const searched = await call('addon.ui', {query: 'diamond', nameQuery: 'diamond'});
  await call('addon.ui', {query: ''});
  await call('screen.close').catch(() => {});
  const essentia = await call('pack.item', {item: 'thaumicenergistics:dummy_aspect', nbt: '{aspect:"aqua"}'});
  const parts = {search: searched.nameMatches.includes('minecraft:diamond') && !searched.nameMatches.includes('minecraft:apple'),
    essentia: hasLineWith(essentia.tooltip.slice(1), 'Water')};
  return partsExpectation('thaumicenergistics', parts, {all, searched, essentia: {name: essentia.nativeName, tooltip: essentia.tooltip}});
}

async function peripherals(world) {
  const languages = ['en_us'];
  const result = {};
  const off = config.overrides.filter(entry => entry.endsWith('=false')).map(entry => entry.slice('integrations.'.length, -'=false'.length));
  result.whyStart = await why('start', languages);
  const stone = await call('pack.item', {item: 'minecraft:stone'});
  await call('screen.close').catch(() => {});
  const viewer = await call('jei.search', {query: 'stone'});
  await call('jei.search', {query: ''});
  result.layer1 = {tooltip: stone.tooltip, recipeViewer: viewer.count,
    pass: hasLineWith(stone.tooltip.slice(1), 'Stone') && ids(viewer.rows.filter(row => row.id)).includes('minecraft:stone')};
  if (hasMod(world, 'appliedenergistics2')) {
    await step(result, 'aeFluid', aeFluid);
    await step(result, 'aeLabels', aeLabels);
  }
  if (hasMod(world, 'mekeng')) await step(result, 'mekanismenergistics', mekeng);
  if (hasMod(world, 'threng') || hasMod(world, 'neenergistics')) await step(result, 'addonSearch', addonSearch);
  if (hasMod(world, 'ae2fc')) await step(result, 'ae2fc', () => fluidCrafting(world));
  if (hasMod(world, 'extracells')) await step(result, 'extracells', extraCells);
  if (hasMod(world, 'thaumicenergistics')) await step(result, 'thaumicenergistics', thaumicEnergistics);
  result.whyEnd = await why('end', languages);
  const disabledOk = parsed => parsed.parsable && parsed.disabled.length === off.length && off.every(id => parsed.disabled.includes(id));
  result.whyPass = disabledOk(result.whyStart) && disabledOk(result.whyEnd);
  const checks = Object.entries(result).filter(([name]) => !['whyStart', 'whyEnd', 'whyPass'].includes(name));
  result.failures = checks.filter(([, value]) => !value.pass).map(([name]) => name).concat(result.whyPass ? [] : ['why']);
  result.pass = result.failures.length === 0;
  return result;
}

// Scenario t19: Controlling, ProjectE, Integrated Terminals and ModularUI checks plus Reliquary and Guide-API names on a
// bridged clone, each run when its mod is loaded. Queries are the en_us names of fixtures whose shown (zh) names do not
// contain them, so a hit needs the integration and --set integrations.<id>=false must turn it into a miss.
const englishNames = async keys => {
  const observed = await call('probe.coreItem', {keys, languages: ['en_us']});
  return Object.fromEntries(keys.map(key => [key, plain(observed.keys[key]?.dictionary?.en_us ?? '')]));
};
const keyRows = state => state.visible.filter(row => row.class === 'KeyEntry');

async function controlling() {
  await call('controlling.open');
  await call('wait.ticks', {ticks: 5});
  const query = async (text, extra = {}) => {
    await call('controlling.ui', {query: text, ...extra});
    await call('wait.ticks', {ticks: 3});
    return call('controlling.observe');
  };
  const english = await englishNames(['key.jump', 'key.categories.movement']);
  const all = await query('');
  const jump = keyRows(all).find(row => row.key === 'key.jump');
  const name = await query(english['key.jump'].toLowerCase());
  const category = await query(english['key.categories.movement'].toLowerCase(), {boxSearchCategory: true});
  const movement = keyRows(all).filter(row => row.category === 'key.categories.movement').map(row => row.key);
  await query('', {boxSearchCategory: true});
  const shownHas = (text, word) => lower(text).includes(word.toLowerCase());
  const parts = {
    name: keyRows(name).some(row => row.key === 'key.jump') && !keyRows(name).some(row => row.key === 'key.attack'),
    category: movement.length > 0 && movement.every(key => keyRows(category).some(row => row.key === key))
      && keyRows(category).every(row => row.category === 'key.categories.movement'),
  };
  const valid = Boolean(jump) && english['key.jump'] && !shownHas(jump.label, english['key.jump']);
  const result = partsExpectation('controlling', parts, {english, jumpLabel: jump?.label, all: keyRows(all).length,
    name: keyRows(name).map(row => row.key), category: keyRows(category).map(row => row.key), boxes: [category.boxSearchCategory, category.boxSearchKey]});
  result.pass = result.pass && valid;
  return result;
}

async function projecte() {
  await call('world.command', {command: '/tp @p 70 5 3'});
  await call('wait.ticks', {ticks: 60});
  const server = await call('pe.world', {prepare: true});
  for (let i = 0; i < 100; i++) {
    const synced = await call('pe.observe');
    if (synced.provider.knowledge.length === server.provider.knowledge.length) break;
    await call('wait.ticks', {ticks: 3});
  }
  await call('pe.world', {open: true});
  for (let i = 0; i < 100; i++) { if ((await call('status')).screen?.endsWith('.GUITransmutation')) break; await call('wait.ticks', {ticks: 3}); }
  const outputs = state => state.outputs.filter(row => !row.empty).map(row => row.key);
  const all = await call('pe.ui', {query: ''});
  const english = (await englishNames(['item.diamond.name']))['item.diamond.name'];
  const searched = await call('pe.ui', {query: english.toLowerCase(), predicate: 'minecraft:diamond'});
  const control = await call('pe.ui', {predicate: 'minecraft:iron_ingot'});
  await call('pe.ui', {query: ''});
  const parts = {output: outputs(searched).includes('minecraft:diamond:0') && !outputs(searched).includes('minecraft:iron_ingot:0'),
    predicate: searched.predicate === true};
  const result = partsExpectation('projecte', parts, {english, knowledge: all.knowledge.length, outputs: outputs(searched),
    allOutputs: outputs(all), control: control.predicate});
  result.pass = result.pass && control.predicate === false && all.knowledge.length > 0;
  return result;
}

async function integratedTerminals() {
  const item = 'minecraft:itemstack', fluid = 'minecraft:fluidstack';
  const view = (state, key, kind = 'filtered') => state.tabs.find(tab => tab.key === key)?.[kind].find(entry => entry.channel === -1)?.rows || [];
  await call('world.command', {command: '/tp @p 80 5 3'});
  await call('wait.ticks', {ticks: 60});
  await call('it.world', {prepare: true});
  let ready = null;
  for (let i = 0; i < 100; i++) {
    ready = await call('it.world');
    if (ready.isInitialized && !ready.isCrashed && ready.states.every(state => state.valid && state.network)) break;
    await call('wait.ticks', {ticks: 5});
  }
  await call('wait.ticks', {ticks: 10});
  await call('it.world', {open: true});
  for (let i = 0; i < 100; i++) { if ((await call('status')).screen?.endsWith('.GuiTerminalStorage')) break; await call('wait.ticks', {ticks: 3}); }
  const selectTab = async key => {
    const state = await call('it.observe');
    for (let index = 0; index < state.tabs.filter(tab => tab.enabled === 'true').length; index++) {
      const selected = await call('it.ui', {tab: index});
      if (selected.tab === key) return selected;
    }
    throw new Error('terminal tab unavailable: ' + key);
  };
  await selectTab(item);
  let initial = null;
  for (let i = 0; i < 100; i++) {
    await call('it.ui', {channel: -1, query: '', prime: true});
    initial = await call('it.observe');
    if (view(initial, item, 'views').length === 96 && view(initial, fluid, 'views').length === 2) break;
    await call('wait.ticks', {ticks: 5});
  }
  const rows = view(initial, item, 'views');
  const english = await englishNames([...new Set(rows.map(row => row.nameKey))]);
  const candidates = rows.filter(row => english[row.nameKey] && !lower(row.nativeName).includes(english[row.nameKey].toLowerCase()));
  const target = candidates.find(row => rows.filter(other => english[other.nameKey]?.toLowerCase().includes(english[row.nameKey].toLowerCase())).length === 1);
  if (!target) return {integration: 'integratedterminals', pass: false, error: 'no item with a distinct en_us name', rows: rows.length};
  const itemQuery = english[target.nameKey].toLowerCase();
  await call('it.ui', {query: itemQuery});
  await call('wait.ticks', {ticks: 5});
  const itemSearch = await call('it.observe');
  await call('it.ui', {query: ''});
  await selectTab(fluid);
  const fluidQuery = (await englishNames(['tile.water.name']))['tile.water.name'].toLowerCase();
  await call('it.ui', {channel: -1, query: fluidQuery});
  await call('wait.ticks', {ticks: 5});
  const fluidSearch = await call('it.observe');
  await call('it.ui', {query: ''});
  const itemKeys = view(itemSearch, item).map(row => row.key), fluidKeys = view(fluidSearch, fluid).map(row => row.key);
  const parts = {item: itemKeys.includes(target.key) && itemKeys.length < rows.length,
    fluid: fluidKeys.includes('water') && !fluidKeys.includes('lava')};
  return partsExpectation('integratedterminals', parts, {ready, target, itemQuery, itemKeys, fluidQuery, fluidKeys,
    native: view(fluidSearch, fluid, 'views').map(row => row.nativeName)});
}

async function modularUi() {
  await call('integration.widget.open', {kind: 'mui', fluid: 'water'});
  await call('wait.ticks', {ticks: 15});
  const state = await call('integration.widget.observe');
  const widget = state.widgets.find(row => row.key === 'slot');
  const english = (await englishNames(['tile.water.name']))['tile.water.name'];
  const render = await call('screen.render', {name: `pt112-t19-mui-${config.attempt}-${Date.now()}.png`, x: widget.x, y: widget.y,
    nativeEvents: true, requireTooltip: true, frames: 3}, 120000);
  const lines = render.lines.map(plain);
  const shown = widget.fluid?.nativeName;
  const result = expectation('modularui', hasLineWith(render.lines, english), {english, shown, lines, events: render.tooltipEvents, path: render.path});
  result.pass = result.pass && lines.some(line => line === plain(shown)) && plain(shown) !== english;
  return result;
}

async function heldNames(label, id, metadata = 0) {
  const count = await call('world.command', {command: `/replaceitem entity @p slot.hotbar.0 ${id} 1 ${metadata}`});
  await call('wait.ticks', {ticks: 10});
  const why = await call('client.command', {command: '/polyglot why', capture: true});
  const item = await call('pack.item', {item: id, metadata});
  await call('screen.close').catch(() => {});
  const messages = why.messages.map(message => ({key: message.key, args: (message.args || []).map(arg => arg?.text ?? arg)}));
  const languages = messages.filter(message => /\.(hit|fallback|missing)$/.test(message.key || ''));
  const english = languages.find(message => message.args[0] === 'en_us');
  const source = messages.find(message => message.key === 'polyglottooltip.command.source')?.args[0] ?? null;
  const tooltip = item.tooltip.slice(1).map(plain);
  // why lists the dictionary hits of the standard key; a special resolver's names show up only in the tooltip.
  const resolved = /Resolver$/.test(source || '') && tooltip.length > 0 ? tooltip[0] : null;
  const secondary = english && english.key.endsWith('.hit') ? plain(english.args[1]) : resolved;
  let search = null;
  if (secondary) {
    const observed = await call('jei.search', {query: secondary.toLowerCase()});
    await call('jei.search', {query: ''});
    search = ids(observed.rows.filter(row => row.id)).includes(id);
  }
  const result = {label, id, metadata, count, name: item.nativeName, rawName: item.rawNativeName, source, secondary, tooltip, search, messages,
    pass: count > 0 && Boolean(secondary) && tooltip.includes(secondary) && search === true};
  await record('held-' + label, result);
  return result;
}

const RELIQUARY_ITEMS = [['feather', 'xreliquary:angelic_feather'], ['harvest-rod', 'xreliquary:harvest_rod'], ['fragment', 'xreliquary:mob_charm_fragment']];
const GUIDE_BOOKS = ['guideapi:bloodmagic-guide', 'guideapi:cyclicmagic-guide', 'guideapi:woot-guide'];

async function t19(world) {
  const languages = ['en_us'];
  const result = {};
  const off = config.overrides.filter(entry => entry.endsWith('=false')).map(entry => entry.slice('integrations.'.length, -'=false'.length));
  result.whyStart = await why('start', languages);
  const stone = await call('pack.item', {item: 'minecraft:stone'});
  await call('screen.close').catch(() => {});
  const viewer = await call('jei.search', {query: 'stone'});
  await call('jei.search', {query: ''});
  result.layer1 = {tooltip: stone.tooltip, recipeViewer: viewer.count,
    pass: hasLineWith(stone.tooltip.slice(1), 'Stone') && ids(viewer.rows.filter(row => row.id)).includes('minecraft:stone')};
  if (hasMod(world, 'controlling')) await step(result, 'controlling', controlling);
  if (hasMod(world, 'projecte')) await step(result, 'projecte', projecte);
  if (hasMod(world, 'integratedterminals')) await step(result, 'integratedterminals', integratedTerminals);
  if (hasMod(world, 'modularui')) await step(result, 'modularui', modularUi);
  // Recorded, not part of the pass condition: layer-1 names of Reliquary items and Guide-API books.
  const recorded = {};
  if (hasMod(world, 'xreliquary')) {
    recorded.reliquary = [];
    for (const [label, id] of RELIQUARY_ITEMS) recorded.reliquary.push(await heldNames('reliquary-' + label, id).catch(error => ({id, error: String(error.message || error)})));
  }
  if (hasMod(world, 'guideapi')) {
    recorded.guideapi = [];
    for (const id of GUIDE_BOOKS) recorded.guideapi.push(await heldNames('guide-' + id.split(':')[1], id).catch(error => ({id, error: String(error.message || error)})));
  }
  await record('t19-recorded', recorded);
  result.whyEnd = await why('end', languages);
  const disabledOk = parsed => parsed.parsable && parsed.disabled.length === off.length && off.every(id => parsed.disabled.includes(id));
  result.whyPass = disabledOk(result.whyStart) && disabledOk(result.whyEnd);
  const checks = Object.entries(result).filter(([name]) => !['whyStart', 'whyEnd', 'whyPass'].includes(name));
  result.failures = checks.filter(([, value]) => !value.pass).map(([name]) => name).concat(result.whyPass ? [] : ['why']);
  result.recorded = recorded;
  result.pass = result.failures.length === 0;
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
    assert.ok(['ae2', 'rs', 'matrix', 'export', 'peripherals', 't19'].includes(config.scenario));
    const result = config.scenario === 'ae2' ? await ae2() : config.scenario === 'rs' ? await rs()
      : config.scenario === 'peripherals' ? await peripherals(world) : config.scenario === 't19' ? await t19(world)
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
