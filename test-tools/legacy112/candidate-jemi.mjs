import fs from 'node:fs/promises';
import path from 'node:path';
import {run} from './integration-runner.mjs';

await run(async({call,check,observations:o,product,baseline,configure,fixture,stamp})=>{
  const emi=(action,params={})=>call('emi',{action,...params}),jemi=(action,params={})=>call('jemi',{action,...params});
  const settled=row=>row.nativeLoaded&&row.worker===0&&(!product||row.coordinator.active&&!row.coordinator.needsBake&&!row.coordinator.baking&&row.retry===false&&row.pendingInputs===false&&row.coordinator.nativeBusy===false&&row.coordinator.reloads===0&&row.pairQuerySame&&row.pairResultsSame);
  const idle=async()=>{
    let row;for(let attempt=0;attempt<240;attempt++){
      row=await call('emi');
      if(settled(row)){
        await call('wait.ticks',{ticks:2});const next=await call('emi');
        if(settled(next)&&next.query===row.query&&next.results===row.results)return next;
      }
      await call('wait.ticks',{ticks:2});
    }
    o.timeout=row;throw new Error('Native JEMI search did not settle');
  };
  const query=async text=>{await emi('query',{text,paste:text.startsWith('#')});return idle();};
  const has=(row,id)=>row.matches.some(key=>key.startsWith(id+':0'));
  const member=(rows,id,subtype=0)=>rows.some(row=>row.stacks.some(stack=>stack.id===id&&stack.subtype===subtype));
  const exact=(rows,wanted)=>rows.some(row=>row.stacks.length===1&&['id','subtype','amount','nbt'].every(key=>row.stacks[0][key]===wanted[key]));
  const craftRows=state=>state.source.filter(row=>member([row],'minecraft:planks'));
  const craftResult=(label,state,enabled)=>check(label+' preserves full native craftable rows and recipe context',state.results,enabled?craftRows(state):[]);
  const widget=row=>({text:row.text,widget:row.widget,start:row.selectionStart,end:row.selectionEnd,history:row.history,index:row.searchHistoryIndex,type:row.type});
  await emi('open');await idle();o.profile=await call('probe.emiProfile');
  if(product){check('JEMI all nine transformations applied',o.profile.admission.receipts,'9');check('JEMI coordinator active',o.profile.coordinator.active,true);}
  if(product){await configure([]);await idle();}
  o.convert=await jemi('convert');check('native JEMI runtime and registry ready',o.convert.state.ready,true);
  check('host filter implements real blacklist accessor',o.convert.state.filterAccessor,true);
  check('unknown native conversion empty',o.convert.unknownEmpty,true);check('null native conversion empty',o.convert.nullEmpty,true);
  for(const row of o.convert.rows){
    check(row.key+' converter overloads agree',row.typed,row.untyped);
    check(row.key+' native converter returns nonempty',row.typed.empty,false);
    check(row.key+' native converter has typed roundtrip',row.typed.typedPresent,true);
    check(row.key+' native roundtrip preserves ingredient',row.typed.roundTrip,row.input);
    check(row.key+' native amount retained',row.typed.amount,row.input.amount);
    check(row.key+' native NBT retained',row.typed.nbt,row.input.nbt);
    check(row.key+' native stack owner',row.typed.class,'dev.emi.emi.api.stack.'+(row.key==='fluid'?'Fluid':'Item')+'EmiStack');
    if(baseline){const original=baseline.convert.rows.find(value=>value.key===row.key);check(row.key+' native conversion matches baseline',row.typed,original.typed);}
  }
  const packName='candidate-jemi-resources',pack=path.join(fixture,'resourcepacks',packName),language=path.join(pack,'assets/minecraft/lang');
  await fs.mkdir(language,{recursive:true});await fs.writeFile(path.join(pack,'pack.mcmeta'),JSON.stringify({pack:{pack_format:3,description:'Owned JEMI fixture'}}));
  const fluidKey=o.convert.fluidLanguageKey;
  await fs.writeFile(path.join(language,'zh_cn.lang'),`tile.stone.stone.name=共用石名\npotion.effect.swiftness=共用药水\n${fluidKey}=共用清水\ntile.wood.oak.name=共用木板\n`);
  await call('resources.select',{packs:[packName]});if(product)await configure(['zh_cn']);
  await emi('options',{searchTooltipByDefault:false,searchModNameByDefault:false,searchTagsByDefault:false});await idle();
  o.history=[];
  for(const [key,alias] of [['potion','共用药水'],['fluid','共用清水']]){
    const conversion=o.convert.rows.find(row=>row.key===key),record={key,alias,conversion};o.history.push(record);
    record.lookup=await jemi('lookup',{key});await emi('sidebar',{type:'LOOKUP_HISTORY'});
    check(key+' actual lookup contains exact converted ingredient',exact(record.lookup.lookup,conversion.typed),true);
    record.native=await query(conversion.typed.name);check(key+' converted native lookup searchable',has(record.native,conversion.typed.id),true);
    record.nativeState=await jemi('state');check(key+' native search contains exact converted ingredient',exact(record.nativeState.results,conversion.typed),true);
    record.alias=await query(alias);check(key+' converted alias lookup searchable',has(record.alias,conversion.typed.id),product);
    record.aliasState=await jemi('state');check(key+' alias search contains exact converted ingredient',exact(record.aliasState.results,conversion.typed),product);
    record.regex=await query('/'+alias+'/');check(key+' converted regex lookup searchable',has(record.regex,conversion.typed.id),product);
    record.regexState=await jemi('state');check(key+' regex search contains exact converted ingredient',exact(record.regexState.results,conversion.typed),product);
    for(const prefix of ['$','@','#']){
      const result=await query(prefix+alias);check(key+' alias does not enter '+prefix+' domain',has(result,conversion.typed.id),false);
    }
    if(product){
      record.retained=await query(alias);await emi('cursor',{start:1,end:2});record.widget=widget(await call('emi'));
      await configure([]);record.disabled=await idle();check(key+' disabled alias leaves native source',has(record.disabled,conversion.typed.id),false);
      record.disabledState=await jemi('state');check(key+' disabled exact ingredient is excluded',exact(record.disabledState.results,conversion.typed),false);
      check(key+' disable retains native widget',widget(record.disabled),record.widget);
      await configure(['zh_cn']);record.enabled=await idle();check(key+' unchanged alias recovers',has(record.enabled,conversion.typed.id),true);
      record.enabledState=await jemi('state');check(key+' unchanged alias recovers exact ingredient',exact(record.enabledState.results,conversion.typed),true);
      check(key+' enable retains native widget',widget(record.enabled),record.widget);
    }
  }
  o.custom=await jemi('lookup',{key:'custom'});await emi('sidebar',{type:'LOOKUP_HISTORY'});
  o.customNative=await query('JEMI custom vial');check('converted custom literal searchable',o.customNative.matches.some(key=>key.includes('JEMI custom vial')),true);
  o.customAlias=await query('共用药水');check('converted custom literal excludes base alias',o.customAlias.matches.some(key=>key.includes('JEMI custom vial')),false);
  await emi('sidebar',{type:'INDEX'});o.hostBefore=await query('stone');check('visible native stone before host blacklist',has(o.hostBefore,'minecraft:stone'),true);
  o.hostAliasBefore=await query('共用石名');check('visible translated stone before host blacklist',has(o.hostAliasBefore,'minecraft:stone'),product);
  try{
    await call('jei.hide',{item:'minecraft:stone',hidden:true});o.hostHidden=await jemi('state');check('JEMI sees real host blacklist',o.hostHidden.hostHidden,true);
    await emi('reload');await idle();o.hostReloaded=await jemi('state');
    for(const key of ['full','filtered'])check('host hidden absent from '+key,member(o.hostReloaded[key],'minecraft:stone'),false);
    o.hostNative=await query('stone');o.hostAlias=await query('共用石名');
    check('host hidden excludes native query',has(o.hostNative,'minecraft:stone'),false);check('host hidden excludes translated query',has(o.hostAlias,'minecraft:stone'),false);
    o.hostEmpty=await query('');check('host hidden empty keeps ordered source',o.hostEmpty.matches,o.hostEmpty.source);
    await emi('options',{editMode:true});o.hostEdit=await query('共用石名');check('EMI edit mode cannot resurrect host blacklist',has(o.hostEdit,'minecraft:stone'),false);
    if(product){await configure([]);await idle();await configure(['zh_cn']);o.hostToggled=await idle();check('secondary toggle cannot resurrect host blacklist',has(o.hostToggled,'minecraft:stone'),false);}
  }finally{await emi('options',{editMode:false});await call('jei.hide',{item:'minecraft:stone',hidden:false});await emi('reload');await idle();}
  o.hostRestored=await jemi('state');check('host blacklist removed',o.hostRestored.hostHidden,false);check('host item full index restored',member(o.hostRestored.full,'minecraft:stone'),true);
  o.hostAliasRestored=await query('共用石名');check('host restored alias result',has(o.hostAliasRestored,'minecraft:stone'),product);
  try{
    await jemi('hide',{hidden:true});o.emiHidden=await jemi('state');
    check('EMI user hidden state',o.emiHidden.emiHidden,true);check('EMI hidden retains full index',member(o.emiHidden.full,'minecraft:stone'),true);
    check('EMI hidden excluded from filtered index',member(o.emiHidden.filtered,'minecraft:stone'),false);
    o.hiddenQuery=await query('共用石名');check('EMI hidden excluded from normal source',has(o.hiddenQuery,'minecraft:stone'),false);
    if(product){await configure([]);await idle();await configure(['zh_cn']);o.hiddenToggled=await idle();check('secondary toggle preserves native hidden source',has(o.hiddenToggled,'minecraft:stone'),false);}
    await emi('options',{editMode:true});o.hiddenEdit=await query('共用石名');check('EMI edit mode uses full source alias',has(o.hiddenEdit,'minecraft:stone'),product);
    check('EMI edit mode has full native stone source',o.hiddenEdit.source.some(key=>key.startsWith('minecraft:stone:0*')),true);
  }finally{await emi('options',{editMode:false});await jemi('hide',{hidden:false});}
  o.visibleAgain=await query('共用石名');check('EMI hidden removal restores alias',has(o.visibleAgain,'minecraft:stone'),product);
  await call('screen.close');await call('world.command',{command:'gamemode survival @p'});await call('world.command',{command:'clear @p'});
  await call('world.command',{command:'give @p minecraft:log 1 0'});await call('wait.ticks',{ticks:5});await emi('open');await emi('sidebar',{type:'CRAFTABLES'});
  try{
    await jemi('recalculate');o.craftEmpty=await query('');o.craft=await jemi('state');
    check('craftables uses actual survival container',o.craft.container,'net.minecraft.inventory.ContainerPlayer');check('craftables has native recipe handler',o.craft.handlers>0,true);
    check('real log produces native plank craftable',member(o.craft.source,'minecraft:planks'),true);
    check('craftable empty search preserves rows and recipes',o.craft.results,o.craft.source);
    check('craftable source uses native recipe wrappers',o.craft.source.filter(row=>member([row],'minecraft:planks')).every(row=>row.class==='dev.emi.emi.runtime.EmiFavorite$Craftable'&&row.recipe!=='none'),true);
    o.craftNative=await query('Oak Wood Planks');check('native craftable title searchable',has(o.craftNative,'minecraft:planks'),true);
    o.craftAlias=await query('共用木板');check('translated craftable title searchable',has(o.craftAlias,'minecraft:planks'),product);
    o.craftAliasState=await jemi('state');craftResult('alias',o.craftAliasState,product);
    const outputs=new Map();
    for(const row of craftRows(o.craftAliasState)){
      const key=JSON.stringify(row.stacks);if(!outputs.has(key))outputs.set(key,new Set());outputs.get(key).add(row.recipe);
    }
    o.distinctRecipesSharingPlankOutput=[...outputs.values()].some(recipes=>recipes.size>1);
    if(product){
      await emi('cursor',{start:1,end:2});o.craftWidget=widget(await call('emi'));
      await configure([]);o.craftDisabled=await idle();check('craftable alias disabled',has(o.craftDisabled,'minecraft:planks'),false);
      o.craftDisabledState=await jemi('state');craftResult('disabled alias',o.craftDisabledState,false);
      await configure(['zh_cn']);o.craftEnabled=await idle();check('unchanged craftable alias restored',has(o.craftEnabled,'minecraft:planks'),true);
      o.craftEnabledState=await jemi('state');craftResult('restored unchanged alias',o.craftEnabledState,true);
      check('craftable refresh preserves native widget',widget(o.craftEnabled),o.craftWidget);
    }
    o.craftBeforeRemove=await jemi('state');await call('world.command',{command:'clear @p minecraft:log'});await call('wait.ticks',{ticks:5});await jemi('recalculate');
    o.craftRemoved=await idle();o.craftRemovedState=await jemi('state');check('removed ingredients remove native craftable source',member(o.craftRemovedState.source,'minecraft:planks'),false);
    check('indexed alias cannot widen empty craftable source',has(o.craftRemoved,'minecraft:planks'),false);
    await call('world.command',{command:'give @p minecraft:log 1 0'});await call('wait.ticks',{ticks:5});await jemi('recalculate');o.craftRestored=await idle();
    check('restored ingredients restore unchanged craftable query',has(o.craftRestored,'minecraft:planks'),product);
    o.craftRestoredState=await jemi('state');craftResult('restored inventory alias',o.craftRestoredState,product);
    o.craftFrame=await call('emi.frame',{name:`jemi-craftable-${stamp}.png`});check('JEMI actual natural frame',[o.craftFrame.pre,o.craftFrame.post,o.craftFrame.canceled],[1,1,false]);
    check('JEMI frame uses accepted native results',o.craftFrame.after.rendered,o.craftFrame.after.matches);
    o.unrelated=await query('共用石名');check('unrelated indexed alias excluded from craftable source',has(o.unrelated,'minecraft:stone'),false);
  }finally{await call('screen.close');await call('world.command',{command:'gamemode creative @p'});await emi('open');}
  o.multi=await jemi('multi');await emi('sidebar',{type:'LOOKUP_HISTORY'});o.multiEmpty=await query('');o.multiState=await jemi('state');
  check('native history contains real two-member ingredient',o.multiState.source.some(row=>row.stacks.length===2),true);
  check('empty native history keeps aggregate row and order',o.multiState.results,o.multiState.source);
  o.multiQueries=[];
  for(const text of ['stone','共用石名']){
    const value=await query(text),state=await jemi('state');o.multiQueries.push({text,value,state});
    check(text+' nonempty query excludes multi-stack native row',value.matches.some(key=>key.includes('|')),false);
    check(text+' query preserves original aggregate in native source',state.source.filter(row=>row.stacks.length===2),o.multiState.source.filter(row=>row.stacks.length===2));
    check(text+' actual native results exclude aggregate',state.results.some(row=>row.stacks.length===2),false);
  }
  o.hostSearch={};
  for(const [key,text] of [['native','diamond'],['secondary','钻石'],['absent','pt_jemi_no_match'],['phonetic','zuanshi']])o.hostSearch[key]=await call('jei.search',{query:text});
  check('host native search coexists with EMI',o.hostSearch.native.rows.some(row=>row.id==='minecraft:diamond'),true);
  check('host translated search coexists with EMI',o.hostSearch.secondary.rows.some(row=>row.id==='minecraft:diamond'),product);
  check('host absent search remains empty',o.hostSearch.absent.count,0);
  o.phoneticArtifacts=Object.entries(JSON.parse(await fs.readFile(path.join(fixture,'launch.json'),'utf8')).artifacts).filter(([name])=>/^(JEC|HEC)-/.test(name));
  const phonetic=o.phoneticArtifacts.length>0;
  if(phonetic)check('host phonetic comparison remains separate',o.hostSearch.phonetic.rows.some(row=>row.id==='minecraft:diamond'),product);
  await emi('sidebar',{type:'INDEX'});o.emiPhonetic=await query('zuanshi');check('JEMI does not infer EMI phonetic search',has(o.emiPhonetic,'minecraft:diamond'),false);
  await call('screen.close');
});
