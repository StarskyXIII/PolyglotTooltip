import fs from 'node:fs/promises';
import path from 'node:path';
import {run} from './integration-runner.mjs';

await run(async({call,check,observations:o,product,baseline,configure,fixture,stamp})=>{
  const state=()=>call('emi'),action=(action,params={})=>call('emi',{action,...params});
  const settled=row=>row.nativeLoaded&&row.worker===0&&(!product||row.coordinator.active&&!row.coordinator.needsBake&&!row.coordinator.baking&&row.retry===false&&row.pendingInputs===false&&row.coordinator.nativeBusy===false&&row.coordinator.reloads===0&&row.pairQuerySame&&row.pairResultsSame);
  const idle=async()=>{
    let row;
    for(let attempt=0;attempt<180;attempt++) {
      row=await state();
      if(settled(row)) {
        await call('wait.ticks',{ticks:2});const next=await state();if(next.query===row.query && next.results===row.results && settled(next))return next;
      }
      await call('wait.ticks',{ticks:2});
    }
    throw new Error('EMI did not settle: '+JSON.stringify({worker:row.worker,loaded:row.nativeLoaded,coordinator:row.coordinator,pair:row.pairQuerySame}));
  };
  const has=(row,id)=>row.matches.some(key=>key.startsWith(id+':0*'));
  const widget=row=>({widget:row.widget,text:row.text,start:row.selectionStart,end:row.selectionEnd,history:row.history,index:row.searchHistoryIndex,focused:row.focused});
  await action('open');await call('wait.ticks',{ticks:4});await action('query',{text:'stone'});o.initial=await idle();
  check('startup native query succeeds without manufactured resource event',has(o.initial,'minecraft:stone'),true);
  if(product) check('startup accepted query is stamped',o.initial.stamped,true);
  const packName='candidate-emi-resources',pack=path.join(fixture,'resourcepacks',packName,'assets','minecraft','lang');
  const write=async revision=>{
    await fs.mkdir(pack,{recursive:true});
    await fs.writeFile(path.join(fixture,'resourcepacks',packName,'pack.mcmeta'),JSON.stringify({pack:{pack_format:3,description:'Owned EMI search fixture'}}));
    await fs.writeFile(path.join(pack,'zh_cn.lang'),`tile.stone.stone.name=译名${revision}\ntile.stonebrick.name=译名乙\npotion.effect.swiftness=迅捷试剂\n`, 'utf8');
  };
  await write('甲');await call('resources.select',{packs:[packName]});if(product)await configure(['zh_cn']);
  await action('options',{searchTooltipByDefault:false,searchModNameByDefault:false,searchTagsByDefault:false});
  o.cases=[];
  const cases=[['native','stone',true],['secondary','译名甲',product],['quoted','"译名甲"',product],['regex','/译名甲/',product],
    ['negative','-译名甲',!product],['and','stone 译名甲',product],['or','译名甲 | pt_nomatch',product],
    ['mod-and','@minecraft 译名甲',product],['body-domain','$译名甲',false],['mod-domain','@译名甲',false],['tag-domain','#译名甲',false],
    ['absent','pt_impossible_921',false],['invalid-regex','/[/'],['native-body','$stone'],['native-mod','@minecraft'],['empty','',true]];
  for(const [name,text,expected] of cases) {
    await action('query',{text,paste:name==='tag-domain'});const row=await idle();o.cases.push({name,row});
    check(name+' retains native widget text',row.text,text);
    if(expected!==undefined)check(name+' stone membership',has(row,'minecraft:stone'),expected);
    if(name==='empty')check('empty native apply preserves ordered source',row.matches,row.source);
    if(product) {check(name+' accepted list and compiled query are one pair',row.pairQuerySame&&row.pairResultsSame,true);check(name+' accepted query stamped',row.stamped,true);}
    if(baseline && ['native','invalid-regex','native-body','native-mod','empty','body-domain','mod-domain','tag-domain','absent'].includes(name)) check(name+' unchanged native result ordering',row.matches,baseline.cases.find(value=>value.name===name).row.matches);
  }
  await action('query',{text:'译名甲'});await idle();await action('cursor',{start:1,end:2});o.retainedStart=await state();
  o.capture=await call('emi.frame',{name:`emi-secondary-${stamp}.png`});
  check('natural EMI draw uses accepted result list',o.capture.after.rendered,o.capture.after.matches);
  check('native inventory screen drawn',o.capture.screen,'net.minecraft.client.gui.inventory.GuiContainerCreative');
  check('native frame observed once without cancellation',[o.capture.pre,o.capture.post,o.capture.canceled],[1,1,false]);
  if(product) {
    await configure([]);o.disabled=await idle();check('secondary off refreshes unchanged query',has(o.disabled,'minecraft:stone'),false);
    check('secondary off preserves widget selection and history',widget(o.disabled),widget(o.retainedStart));
    await configure(['zh_cn']);o.enabled=await idle();check('secondary on refreshes unchanged query',has(o.enabled,'minecraft:stone'),true);
    check('secondary on preserves widget selection and history',widget(o.enabled),widget(o.retainedStart));
  }
  await write('丙');await call('resources.reload');o.replaced=await idle();
  check('same-primary resource replaces old alias',has(o.replaced,'minecraft:stone'),false);
  if(product)check('resource reload preserves widget selection and history',widget(o.replaced),widget(o.retainedStart));
  await action('query',{text:'译名丙'});o.resourceNew=await idle();check('new resource alias searchable',has(o.resourceNew,'minecraft:stone'),product);
  await call('language.set',{language:'zh_cn'});o.primaryChinese=await idle();check('primary change refreshes unchanged query',has(o.primaryChinese,'minecraft:stone'),true);
  await call('language.set',{language:'en_us'});o.primaryEnglish=await idle();check('primary restoration retains secondary match',has(o.primaryEnglish,'minecraft:stone'),product);
  await action('retain');await action('reload',{count:2});o.nativeReload=await idle();
  check('native repeated reload completes',o.nativeReload.nativeLoaded,true);check('native reload retains query',o.nativeReload.text,'译名丙');
  check('native reload uses current alias',has(o.nativeReload,'minecraft:stone'),product);
  if(product) {check('native reload publishes a new pair',!o.nativeReload.retainedQuerySame&&!o.nativeReload.retainedResultsSame,true);check('native reload ownership drained',o.nativeReload.coordinator.nativeBusy===false&&o.nativeReload.coordinator.reloads===0,true);}
  await call('world.command',{command:'give @p minecraft:stone 1'});await call('wait.ticks',{ticks:5});
  await action('query',{text:'stone'});await idle();await action('highlight',{value:true});o.overlay=await call('emi.frame',{name:`emi-highlight-${stamp}.png`});
  check('highlight frame contains actual native stone slot',o.overlay.after.inventorySlots.includes('minecraft:stone:0'),true);
  check('highlight draw keeps list and query paired',o.overlay.after.rendered,o.overlay.after.matches);
  await action('highlight',{value:false});
  await action('lookup');await action('sidebar',{type:'LOOKUP_HISTORY'});await action('query',{text:'迅捷试剂'});o.unbaked=await idle();
  check('native history sidebar selected',o.unbaked.type,'LOOKUP_HISTORY');
  check('unbaked translated potion searchable',o.unbaked.matches.some(key=>key.startsWith('minecraft:potion:')&&key.includes('swiftness')),product);
  await action('query',{text:'/迅捷试剂/'});o.unbakedRegex=await idle();check('unbaked regex translated potion searchable',o.unbakedRegex.matches.some(key=>key.startsWith('minecraft:potion:')&&key.includes('swiftness')),product);
  await action('lookup',{custom:'Literal vial 42'});await action('query',{text:'Literal vial 42'});o.custom=await idle();
  check('custom fixture enters actual native history',o.custom.source.some(key=>key.includes('Literal vial 42')),true);
  check('custom native name remains searchable',o.custom.matches.length,1);
  await action('query',{text:'迅捷试剂'});o.customAlias=await idle();check('custom literal excludes translated base alias',o.customAlias.matches.some(key=>key.includes('Literal vial 42')),false);
  await action('sidebar',{type:'INDEX'});await action('query',{text:''});await idle();await action('page',{offset:1});o.page=await state();
  check('native page preservation fixture reaches later page',o.page.page>0,true);
  if(product){await configure(['zh_tw']);o.pageAfter=await idle();check('unchanged empty query preserves native page',o.pageAfter.page,o.page.page);}
  await action('clear');o.cleared=await state();check('native clear removes loaded readiness',o.cleared.nativeLoaded,false);
  if(product)check('clear blocks new accepted generation',o.cleared.coordinator.nativeBusy,true);
  await action('reload');o.recovered=await idle();check('native reload after clear recovers',o.recovered.nativeLoaded,true);
  await call('screen.close');
});
