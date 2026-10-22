import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {run} from './integration-runner.mjs';

const tab = (state, key) => state.tabs.find(tab => tab.key === key);
const view = (state, key, channel = -1, kind = 'filtered') => tab(state, key)?.[kind].find(view => view.channel === channel)?.rows || [];
const payload = rows => rows.map(({key, quantity, nbt}) => ({key, quantity, nbt}));
const contents = rows => payload(rows).sort((a, b) => a.key.localeCompare(b.key) || a.nbt.localeCompare(b.nbt));
const identities = state => state.tabs.map(tab => ({key: tab.key, identity: tab.identity, views: tab.views.map(view => ({channel: view.channel, identity: view.identity, rows: payload(view.rows)}))}));
await run(async ({call, check, observations: o, runtime, product, configure, baseline, render, stamp, fixture}) => {
  const item = 'minecraft:itemstack', fluid = 'minecraft:fluidstack';
  if (product) await configure(['zh_cn']);
  o.network = await call('it.world', {prepare: true});
  for (let i = 0; i < 100; i++) { o.ready = await call('it.world'); if (o.ready.isInitialized && !o.ready.isCrashed && !o.ready.isKilled && o.ready.states.every(state => state.valid && state.network)) break; await call('wait.ticks', {ticks: 5}); }
  check('native terminal network and all ten storage interfaces ready', o.ready.isInitialized && !o.ready.isCrashed && !o.ready.isKilled && o.ready.states.length === 10 && o.ready.states.every(state => state.valid && state.network), true);
  await call('wait.ticks', {ticks: 10}); await call('it.world', {open: true});
  for (let i = 0; i < 100; i++) { if ((await call('status')).screen.endsWith('.GuiTerminalStorage')) break; await call('wait.ticks', {ticks: 3}); }
  const selectTab = async key => {
    const state = await call('it.observe');
    for (let index = 0; index < state.tabs.filter(tab => tab.enabled === 'true').length; index++) {
      const selected = await call('it.ui', {tab: index}); if (selected.tab === key) return selected;
    }
    throw Error('Native terminal tab unavailable: ' + key);
  };
  await selectTab(item); await call('it.ui', {channel: -1, query: '', prime: true});
  for (let i = 0; i < 100; i++) {
    o.initial = await call('it.observe');
    if (view(o.initial, item, -1, 'views').length === 96 && view(o.initial, fluid, -1, 'views').length === 2) break;
    await call('wait.ticks', {ticks: 5}); await call('it.ui', {prime: true});
  }
  check('native item network supplies 96 entries', view(o.initial, item, -1, 'views').length, 96);
  check('native fluid network supplies both tanks', payload(view(o.initial, fluid, -1, 'views')).sort((a,b) => a.key.localeCompare(b.key)), [{key:'lava',quantity:16000,nbt:'null'},{key:'water',quantity:16000,nbt:'null'}]);
  o.predicates = [];
  const queries = [
    {item:'minecraft:stone',query:'stone',expected:true}, {item:'minecraft:stone',query:'石头',expected:product},
    {item:'minecraft:stone',query:'shitou',expected:product && runtime.mods.some(mod=>mod.id==='jecharacters')},
    {fluid:'water',query:'water',expected:true}, {fluid:'water',query:'水',expected:product},
    {item:'minecraft:stone',query:'',expected:true}, {item:'minecraft:stone',query:'[',expected:false},
    {item:'minecraft:stone',query:'sto.*',expected:!runtime.mods.some(mod=>mod.id==='jecharacters')}, {item:'minecraft:stone',query:'pt-miss|stone',expected:true},
    {item:'minecraft:stone',query:'stone pt-miss',expected:false}, {item:'minecraft:stone',query:'石头 pt-miss',expected:false},
    {item:'minecraft:stone',query:'@minecraft',expected:true}, {item:'minecraft:stone',query:'@石头',expected:false},
    {item:'minecraft:stone',query:'$stone',expected:true}, {item:'minecraft:stone',query:'$石头',expected:false},
    {item:'minecraft:stone',query:'#石头',expected:false}, {item:'minecraft:stone',query:'#stone',expected:true},
    {item:'minecraft:stone',query:'#ptbody314',lore:'PTBody314 native lore',expected:true},
    {item:'minecraft:stone',query:'#石头',lore:'石头 native lore',expected:true},
    {item:'minecraft:stone',query:'literal',custom:'Literal name 42',expected:true},
    {item:'minecraft:stone',query:'石头',custom:'Literal name 42',expected:false}
  ];
  for (const {expected, ...params} of queries) {
    const result = await call('it.predicate', params); o.predicates.push({params,result});
    if (expected !== undefined) check(`native query grammar ${JSON.stringify(params)}`, result.result, expected);
    else if (baseline) check(`native tooltip query mode unchanged ${JSON.stringify(params)}`, result.result, baseline.predicates.find(row=>JSON.stringify(row.params)===JSON.stringify(params)).result.result);
  }
  o.channels = [];
  for (const key of [item, fluid]) {
    await selectTab(key);
    for (const channel of [-1, 0, 1]) {
      await call('it.ui', {channel, query: '', prime: true}); await render('it-channel');
      const state = await call('it.observe'); o.channels.push({key,channel,state});
      check(`${key} native channel ${channel} visible count`, view(state,key,channel).length, key===item ? channel===-1?96:48 : channel===-1?2:1);
      if (baseline) check(`${key} channel ${channel} baseline contents and quantities`, contents(view(state,key,channel)), contents(view(baseline.channels.find(row=>row.key===key&&row.channel===channel).state,key,channel)));
    }
  }
  const packName = 'polyglot-it-generation-' + stamp, folder = path.join(fixture, 'resourcepacks', packName);
  await fs.mkdir(path.join(folder, 'assets/minecraft/lang'), {recursive:true});
  await fs.writeFile(path.join(folder,'pack.mcmeta'), JSON.stringify({pack:{pack_format:3,description:'Native language reload fixture'}}));
  const all = [...view(o.initial,item,-1,'views'), ...view(o.initial,fluid,-1,'views')];
  assert.ok(all.length === 98 && all.every(row=>/^[A-Za-z0-9_.-]+$/.test(row.nameKey)));
  const languageFile = path.join(folder,'assets/minecraft/lang/zh_cn.lang');
  const writePack = token => fs.writeFile(languageFile, [...new Map(all.map(row=>[row.nameKey === 'fluid.tile.water' ? 'tile.water.name' : row.nameKey === 'fluid.tile.lava' ? 'tile.lava.name' : row.nameKey,token+' '+row.key])).entries()].map(([key,value])=>key+'='+value).join('\n')+'\n');
  await writePack('ptgenold');
  try {
    await call('resources.select',{packs:[packName]}); await call('language.set',{language:'zh_cn'});
    o.nativeTarget = await call('it.observe');
    const nativeTargetKeys = view(o.nativeTarget,item,-1,'views').filter(row=>row.nativeName.toLowerCase().includes('ptgenold')).map(row=>row.key).sort();
    check('native resource translation provides scrollable target domain', nativeTargetKeys.length>45,true);
    check('native resource translation reaches both vanilla fluids',view(o.nativeTarget,fluid,-1,'views').every(row=>row.nativeName.includes('ptgenold')),true);
    await call('language.set',{language:'en_us'});
    for (const key of [item,fluid]) {
      await selectTab(key);
      for (const channel of [-1,0,1]) await call('it.ui',{channel,query:'ptgenold',prime:true});
    }
    await selectTab(item); await call('it.ui',{channel:-1,query:'ptgenold',prime:true}); await render('it-alias-domain');
    o.aliases = await call('it.observe');
    check('native UI results equal actual target-language titles',view(o.aliases,item).map(row=>row.key).sort(),product?nativeTargetKeys:[]);
    if (product) {
      o.selected = await call('it.ui',{select:0});
      const selected = tab(o.selected,item); assert.ok(selected.active);
      check('actual native slot click selects expected ingredient',selected.active.key,view(o.aliases,item)[0].key);
      check('native first click sets active index and bounded amount',[selected.activeSlotId,selected.activeSlotQuantity],['0','64']);
      o.retained = await render('it-generation-retain',{integration:'it',configuration:{languages:['zh_cn','zh_cn'],alwaysShow:false}});
      check('IT retention executes one uncanceled native frame',[o.retained.frames,o.retained.innerPreCanceled],[1,false]);
      check('IT generation does not refresh during observer',o.retained.changed,o.retained.before);
      check('IT generation retains selected ingredient and quantity',[tab(o.retained.after,item).active,tab(o.retained.after,item).activeSlotQuantity],[selected.active,selected.activeSlotQuantity]);
      await call('it.ui',{scroll:1,prime:true}); o.scrolled=await call('it.observe'); check('native large result set supports positive scroll',o.scrolled.firstRow>0,true);
      o.shrink=await render('it-generation-shrink',{integration:'it',configuration:{languages:[],alwaysShow:false}});
      check('IT shrink executes one uncanceled native frame',[o.shrink.frames,o.shrink.innerPreCanceled],[1,false]);
      check('IT shrink observer leaves pre-draw caches intact',o.shrink.changed,o.shrink.before);
      check('IT shrink keeps screen container tab channel and text',[o.shrink.after.screen,o.shrink.after.container,o.shrink.after.tab,o.shrink.after.channel,o.shrink.after.query],[o.shrink.before.screen,o.shrink.before.container,o.shrink.before.tab,o.shrink.before.channel,o.shrink.before.query]);
      check('IT shrink keeps original ingredient views and quantities',identities(o.shrink.after),identities(o.shrink.before));
      check('IT first draw clears results and clamps scroll',[view(o.shrink.after,item).length,o.shrink.after.firstRow,o.shrink.after.scroll],[0,0,0]);
      check('IT first draw removes invalid active ingredient',[tab(o.shrink.after,item).activeSlotId,tab(o.shrink.after,item).active],['-1',undefined]);
      for(const before of o.shrink.before.tabs) {
        const after=tab(o.shrink.after,before.key);
        check(`${before.key} all channel queries remain native`,after.queries,before.queries);
        for(const cache of before.filtered) check(`${before.key} channel ${cache.channel} stale cache invalidated`,after.filtered.some(row=>row.identity===cache.identity),false);
      }
      o.expand=await render('it-generation-expand',{integration:'it',configuration:{languages:['zh_cn'],alwaysShow:false}});
      check('IT restores retained native query after expansion',view(o.expand.after,item).map(row=>row.key).sort(),nativeTargetKeys);
      o.hidden=[];
      for(const key of [item,fluid]) { await selectTab(key); for(const channel of [-1,0,1]) {await call('it.ui',{channel});await render('it-hidden');const state=await call('it.observe');o.hidden.push({key,channel,state});check(`${key} hidden channel ${channel} repopulates on visit`,view(state,key,channel).map(row=>row.key).sort(),view(o.nativeTarget,key,channel,'views').filter(row=>row.nativeName.includes('ptgenold')).map(row=>row.key).sort());} }
      await selectTab(item); await call('it.ui',{channel:-1});
      await writePack('ptgennew'); await call('resources.reload'); await render('it-resources-reloaded');o.resourceChanged=await call('it.observe');
      check('full resource reload removes old alias without typing',view(o.resourceChanged,item).length,0);
      await call('it.ui',{query:'ptgennew'}); await render('it-new-resource-alias');o.resourceNew=await call('it.observe');
      check('full resource reload exposes new native alias',view(o.resourceNew,item).map(row=>row.key).sort(),nativeTargetKeys);
    }
  } finally {await call('resources.select',{packs:[]});await call('language.set',{language:'en_us'});if(product)await configure(['zh_cn']);await call('screen.close');}
});
