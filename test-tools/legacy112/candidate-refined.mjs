import fs from 'node:fs/promises';
import path from 'node:path';
import {run, plain} from './integration-runner.mjs';

const fingerprint = row => JSON.stringify([row.id,row.damage ?? 0,row.nbt]);
const visible = state => state.visible.map(key => state.retained.find(row => row.key === key));
const immutable = state => state.retained.map(row => [row.key,row.identity,row.id,row.damage,row.nbt,row.quantity,row.craftable,row.otherId]).sort((a,b) => a[0].localeCompare(b[0]));
const editing = state => [state.screen,state.container,state.containerWindow,state.query,state.cursor,state.selection,state.focused];
const chosen = state => state.slots.filter(slot => slot.selected).map(slot => slot.nbt).sort();
const nameMatch = (name, query, more, manager) => {
  const arms = query.split('|').map(arm => arm.toLowerCase().trim().split(' ').filter(Boolean));
  return (!more && (manager || arms.length > 1)) ? arms.some(arm => arm.some(word => name.toLowerCase().includes(word))) : arms.some(arm => arm.every(word => name.toLowerCase().includes(word)));
};
await run(async ({call,check,observations:o,runtime,product,render,configure,baseline,fixture}) => {
  const more = runtime.mods.find(mod => mod.id === 'refinedstorage').version === '2.3.2', jec = runtime.mods.some(mod => mod.id === 'jecharacters');
  const waitFor = async (read, accepts, label) => {
    let value, error;
    for (let n = 0; n < 120; n++) {
      try { value = await read(); if (accepts(value)) return value; } catch (caught) { error = String(caught); }
      await call('wait.ticks', {ticks:5});
    }
    o.waitFailure = {label,value,error};
    throw new Error(`${label}: timeout; final state saved in waitFailure; ${error || ''}`);
  };
  o.setup = await call('rs.world', {operation:'setup'});
  o.ready = await waitFor(() => call('rs.world'), state => state.canRun && state.itemStorages > 0 && state.fluidStorages > 0 && state.itemCanUpdate && state.fluidCanUpdate && state.itemConnected && state.fluidConnected && state.groups['Fixture Alpha'] === 36 && state.groups['Fixture Beta'] === 36, 'Native network readiness');
  check('registered scrolling item domain', o.ready.domain.length, 101);
  const packName = 'refined-candidate-resources', pack = path.join(fixture,'resourcepacks',packName);
  const aliases = new Map();
  for (let n = 0; n < o.ready.domain.length; n++) {
    const row = o.ready.domain[n];
    const value = row.id === 'minecraft:diamond' ? '钻石' : row.id === 'minecraft:emerald' ? '绿宝石' : row.id === 'minecraft:coal' ? '煤炭' : `名称${n}`;
    if (!aliases.has(row.rawKey)) aliases.set(row.rawKey,value);
  }
  const resources = async revision => {
    await fs.mkdir(path.join(pack,'assets/minecraft/lang'), {recursive:true});
    await fs.writeFile(path.join(pack,'pack.mcmeta'), JSON.stringify({pack:{pack_format:3,description:'Owned native RS fixture'}}));
    await fs.writeFile(path.join(pack,'assets/minecraft/lang/zh_cn.lang'), [...aliases].map(([key,value]) => `${key}=样本${revision} ${value}`).join('\n')+'\n');
    await fs.writeFile(path.join(pack,'assets/minecraft/lang/zh_tw.lang'), 'item.diamond.name=样本A 鑽石繁\n');
  };
  await resources('A'); await call('resources.select', {packs:[packName]});
  if (product) await configure(['zh_cn']);
  o.stored = await call('rs.world', {operation:'insert'});
  const storedDiamond = o.stored.itemStored.find(row => row.id === 'minecraft:diamond' && row.nbt === 'null');
  check('actual native item disk quantity', storedDiamond.quantity, more ? 2147483664 : 64);
  check('actual native fluid disk quantity', o.stored.fluidStored.find(row => row.id === 'water').quantity, more ? 2147484671 : 23456);
  const open = async kind => {
    const server = await call('rs.world', {operation:'open',kind});
    const state = await waitFor(() => call('rs.ui'), state => state.active && state.containerWindow === server.containerWindow && (kind === 'manager' ? state.patterns?.length === 72 && state.patterns.every(row => !row.empty) : state.retained?.length >= (kind === 'fluid' ? 2 : 101)), `Native ${kind} packets`);
    check(`${kind} native screen class`, state.class, `com.raoulvdberge.refinedstorage.gui.${kind === 'manager' ? 'GuiCrafterManager' : 'grid.GuiGrid'}`);
    if (product) check(`${kind} exact integration unit enabled`, state.profile, true);
    return state;
  };
  o.gridInitial = await open('item');
  o.gridInitial = await call('rs.ui', {query:'',warm:true});
  check('native grid exceeds real viewport', o.gridInitial.visible.length > o.gridInitial.visibleRows * 9, true);
  check('native cache names stay primary', o.gridInitial.retained.every(row => row.name === row.nativeName), true);
  check('real quantity reaches native packet', o.gridInitial.retained.find(row => row.id === 'minecraft:diamond' && row.nbt === 'null' && row.quantity > 0).quantity, storedDiamond.quantity);
  if (product && baseline) {
    const native = new Map(baseline.gridInitial.retained.map(row => [fingerprint(row)+'/'+row.craftable,row]));
    for (const row of o.gridInitial.retained) {
      const source = native.get(fingerprint(row)+'/'+row.craftable);
      check(`native cached tooltip ${fingerprint(row)} ${row.craftable}`, row.tooltip, source.tooltip);
      if (more) check(`MRS uncached tooltip ${fingerprint(row)} ${row.craftable}`, row.freshTooltip, source.freshTooltip);
    }
  }
  o.gridQueries = [];
  const queries = ['diamond','diamond emerald','diamond emerald|coal','diamond|emerald','-diamond','!diamond','钻石','diamond 钻石','zuanshi','#样本','#sentinel','@样本','$样本'];
  for (const query of queries) {
    const state = await call('rs.ui', {query}); o.gridQueries.push({query,state});
    let expected;
    if (query === '#sentinel') expected = o.gridInitial.retained.filter(row => row.nbt?.includes('Only native lore sentinel'));
    else if (['#样本','@样本','$样本'].includes(query)) expected = [];
    else if (['钻石','diamond 钻石','zuanshi'].includes(query)) expected = product && (query !== 'zuanshi' || jec) ? o.gridInitial.retained.filter(row => row.id === 'minecraft:diamond' && !row.literal) : [];
    else expected = o.gridInitial.retained.filter(row => nameMatch(row.name, query, more, false));
    const expectedKeys = new Set(expected.map(row => fingerprint(row)));
    check(`native Grid parser ${query}`, [...new Set(visible(state).map(fingerprint))].sort(), [...expectedKeys].sort());
    check(`native Grid query ${query} retains all wrappers`, immutable(state), immutable(o.gridInitial));
  }
  o.itemHoverState = await call('rs.ui', {query:'diamond',scroll:0,hover:'minecraft:diamond',hoverNbt:'null'});
  o.itemHover = await render('refined-item-hover', {x:o.itemHoverState.x,y:o.itemHoverState.y,requireTooltip:true});
  check('actual item tooltip boundary captured', !!o.itemHover.refinedTooltip, true);
  if (product) check('native item hover adds one secondary title', o.itemHover.lines.filter(line => plain(line) === '样本A 钻石').length, 1);
  if (product && baseline) {
    const expected = [...baseline.itemHover.lines]; expected.splice(1,0,'§7样本A 钻石§r');
    check('whole native item tooltip preserved', o.itemHover.lines, expected);
    check('native quantity and detail rows preserved', o.itemHover.refinedTooltip.small, baseline.itemHover.refinedTooltip.small);
  }
  for (const variant of ['literal','lore']) {
    const row = o.gridInitial.retained.find(row => row.id === 'minecraft:diamond' && (variant === 'literal' ? row.literal : row.nbt.includes('Only native lore sentinel')));
    const state = await call('rs.ui', {query:'diamond',scroll:0,hover:'minecraft:diamond',hoverNbt:row.nbt});
    const frame = o[variant+'Hover'] = await render('refined-'+variant+'-hover', {x:state.x,y:state.y,requireTooltip:true});
    check(`${variant} hover reaches selected native item`, frame.refinedTooltip.item.nbt, row.nbt);
    if (product && baseline) {
      const expected = [...baseline[variant+'Hover'].lines];
      if (variant === 'lore') expected.splice(1,0,'§7样本A 钻石§r');
      check(`${variant} native tooltip preserved`, frame.lines, expected);
    }
  }
  await call('rs.ui', {query:'样本A'}); await render('refined-grid-scroll-layout');
  o.beforeDisable = await call('rs.ui', {scrollEnd:true,cursor:1,selection:2,focused:true,warm:true});
  if (product) {
    check('positive retained search has scroll before shrinking', o.beforeDisable.scroll > 0, true);
    o.shrinkFrame = await render('refined-grid-shrink', {integration:'rs',configuration:{languages:['zh_tw'],alwaysShow:false}});
    check('grid nonempty shrink retains query edit state', editing(o.shrinkFrame.after), editing(o.shrinkFrame.before));
    check('grid nonempty shrink exposes only target diamonds', visible(o.shrinkFrame.after).length > 0 && visible(o.shrinkFrame.after).every(row => row.id === 'minecraft:diamond' && !row.literal), true);
    check('grid nonempty shrink clamps actual offset', o.shrinkFrame.after.scroll, 0);
    o.disableFrame = await render('refined-grid-disable', {integration:'rs',configuration:{languages:[],alwaysShow:false}});
    check('grid first redraw sees unchanged query editing state', editing(o.disableFrame.after), editing(o.disableFrame.before));
    check('grid first redraw removes aliases', o.disableFrame.after.visible, []);
    check('grid first redraw clamps native scrollbar', [o.disableFrame.after.scroll,o.disableFrame.after.maxScroll], [0,o.disableFrame.after.rows-o.disableFrame.after.visibleRows]);
    check('grid refresh retains complete native map', immutable(o.disableFrame.after), immutable(o.disableFrame.before));
    o.enableFrame = await render('refined-grid-enable', {integration:'rs',configuration:{languages:['zh_cn'],alwaysShow:false}});
    check('hidden retained grid entries become visible on first redraw', o.enableFrame.after.visible.length, o.beforeDisable.visible.length);
    check('grid enable preserves editing state', editing(o.enableFrame.after), editing(o.enableFrame.before));
  }
  await call('rs.ui', {query:'样本A'}); await render('refined-grid-resource-layout');
  o.beforeResource = await call('rs.ui', {scrollEnd:true,cursor:1,selection:2,focused:true,warm:true});
  await resources('B'); await call('resources.reload');
  o.resourceFrame = await render('refined-grid-resource', {integration:'rs'});
  o.afterResource = await call('rs.ui', {warm:true});
  check('same-primary new resource removes old search matches', o.afterResource.visible, []);
  check('same-primary resource preserves editing state', editing(o.afterResource), editing(o.beforeResource));
  check('same-primary resource preserves native wrapper data', immutable(o.afterResource), immutable(o.beforeResource));
  o.resourceNewQuery = await call('rs.ui', {query:'样本B'});
  check('new resource aliases are searchable', o.resourceNewQuery.visible.length > 0, product);
  if (product) await configure([]);
  o.beforeLocale = await call('rs.ui', {query:'钻石',warm:true});
  await call('language.set', {language:'zh_cn'}); await render('refined-grid-primary', {integration:'rs'});
  o.primaryChinese = await call('rs.ui', {warm:true});
  if (product) {
    check('native item name cache is refreshed across all retained rows', o.primaryChinese.retained.every(row => row.name === row.nativeName), true);
    check('native tooltip cache is refreshed across all retained rows', o.primaryChinese.retained.every(row => plain(row.tooltip.split('\n')[0]) === plain(row.nativeName)), true);
    check('primary language shows previous hidden diamond rows', visible(o.primaryChinese).every(row => row.id === 'minecraft:diamond' && !row.literal) && o.primaryChinese.visible.length > 0, true);
    check('primary switch preserves native wrapper data', immutable(o.primaryChinese), immutable(o.beforeLocale));
  }
  await call('language.set', {language:'en_us'}); await resources('A'); await call('resources.reload');
  if (product) await configure(['zh_cn']);
  await render('refined-grid-restored');
  o.beforeDelta = await call('rs.ui', {query:'diamond'});
  o.deltaServer = await call('rs.world', {operation:'delta'});
  o.afterDelta = await waitFor(() => call('rs.ui'), state => state.retained.some(row => row.id === 'minecraft:diamond' && row.nbt === 'null' && row.quantity === storedDiamond.quantity+7), 'Native item delta');
  check('post-refresh real server delta reaches original identity', o.afterDelta.retained.find(row => row.id === 'minecraft:diamond' && row.nbt === 'null' && row.quantity > 0).key, o.beforeDelta.retained.find(row => row.id === 'minecraft:diamond' && row.nbt === 'null' && row.quantity > 0).key);
  await call('screen.close');
  o.fluidInitial = await open('fluid');
  o.fluidQueries = [];
  for (const query of ['water','水','shui','#水','@水','$水','water 水']) {
    const state = await call('rs.ui', {query}); o.fluidQueries.push({query,state});
    const expected = query === 'water' || product && (query === '水' || query === 'water 水' || jec && query === 'shui') ? ['water'] : [];
    check(`native fluid name-only query ${query}`, visible(state).map(row => row.id), expected);
  }
  o.fluidHoverState = await call('rs.ui', {query:'water',scroll:0,hover:'water'});
  o.fluidHover = await render('refined-fluid-hover', {x:o.fluidHoverState.x,y:o.fluidHoverState.y,requireTooltip:true});
  check('real fluid quantity retained', visible(o.fluidHoverState)[0].quantity, more ? 2147484671 : 23456);
  if (product && baseline) {
    const expected = [...baseline.fluidHover.lines]; expected.splice(1,0,'§7水§r');
    check('whole native fluid tooltip preserved', o.fluidHover.lines, expected);
    check('native fluid detail rows preserved', o.fluidHover.refinedTooltip.small, baseline.fluidHover.refinedTooltip.small);
  }
  await call('screen.close');
  o.managerInitial = await open('manager');
  o.managerInitial = await call('rs.ui', {query:''});
  check('native manager complete synchronization', o.managerInitial.patterns.length, 72);
  check('native manager has two actual group capacities', o.managerInitial.groups, {'Fixture Alpha':36,'Fixture Beta':36});
  check('native manager patterns exceed viewport', o.managerInitial.rows > o.managerInitial.visibleRows, true);
  o.managerQueries = [];
  for (const query of ['diamond','diamond emerald','diamond emerald|coal','diamond|emerald','-diamond','!diamond','钻石','diamond 钻石','zuanshi','#样本','@样本','$样本','水']) {
    const state = await call('rs.ui', {query}); o.managerQueries.push({query,state});
    let expected;
    if (['#样本','@样本','$样本','水'].includes(query)) expected = [];
    else if (['钻石','diamond 钻石','zuanshi'].includes(query)) expected = o.managerInitial.patterns.filter(row => row.outputs.some(output =>
      !more && query === 'diamond 钻石' && nameMatch(output.nativeName,'diamond',more,true)
      || product && (query !== 'zuanshi' || jec) && output.id === 'minecraft:diamond' && !output.literal));
    else expected = o.managerInitial.patterns.filter(row => row.outputs.some(output => nameMatch(output.nativeName,query,more,true)));
    check(`native manager parser ${query}`, chosen(state), expected.map(row => row.nbt).sort());
    check(`native manager query ${query} retains pattern NBT`, state.patterns.map(row => row.nbt), o.managerInitial.patterns.map(row => row.nbt));
  }
  await call('rs.ui', {query:'样本A'}); await render('refined-manager-scroll-layout');
  o.managerBeforeDisable = await call('rs.ui', {scrollEnd:true,cursor:1,selection:2,focused:true});
  if (product) {
    check('manager positive search starts scrolled', o.managerBeforeDisable.scroll > 0, true);
    o.managerShrink = await render('refined-manager-shrink', {integration:'rs',configuration:{languages:['zh_tw'],alwaysShow:false}});
    check('manager nonempty shrink preserves editing state', editing(o.managerShrink.after), editing(o.managerShrink.before));
    check('manager nonempty shrink leaves target pattern visible', o.managerShrink.after.slots.some(row => row.enabled && row.outputs.some(output => output.id === 'minecraft:diamond')), true);
    const shrinkOffset = Math.max(0,o.managerShrink.after.rows-o.managerShrink.after.visibleRows);
    check('manager nonempty shrink clamps actual offset', o.managerShrink.after.scroll, shrinkOffset);
    o.managerLayoutControl = await call('rs.ui', {query:'样本A',scroll:shrinkOffset,cursor:1,selection:2,focused:true});
    const layout = state => [state.headings,state.rows,state.slots.map(row => [row.nbt,row.x,row.y,row.selected,row.enabled])];
    check('manager first redraw has same slot layout as later native query rebuild', layout(o.managerShrink.after), layout(o.managerLayoutControl));
    o.managerDisable = await render('refined-manager-disable', {integration:'rs',configuration:{languages:[],alwaysShow:false}});
    check('manager first redraw removes alias-only patterns', chosen(o.managerDisable.after), []);
    check('manager first redraw clamps scroll', [o.managerDisable.after.scroll,o.managerDisable.after.maxScroll], [0,o.managerDisable.after.rows-o.managerDisable.after.visibleRows]);
    check('manager refresh preserves query edit and container state', editing(o.managerDisable.after), editing(o.managerDisable.before));
    o.managerEnable = await render('refined-manager-enable', {integration:'rs',configuration:{languages:['zh_cn'],alwaysShow:false}});
    check('manager first redraw restores patterns from retained inventories', chosen(o.managerEnable.after), chosen(o.managerBeforeDisable));
    check('manager patterns survive refresh unchanged', o.managerEnable.after.patterns.map(row => row.nbt), o.managerBeforeDisable.patterns.map(row => row.nbt));
    const after = o.managerEnable.after;
    check('manager accepted slot coordinates match native viewport after clamp', after.slots.filter(row => row.enabled).every(row => row.selected && row.y >= after.topHeight && row.y < after.topHeight+18*after.visibleRows), true);
  }
  await call('screen.close');
});
