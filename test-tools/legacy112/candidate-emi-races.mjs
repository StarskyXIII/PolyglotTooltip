import fs from 'node:fs/promises';
import path from 'node:path';
import {run} from './integration-runner.mjs';

await run(async({call,check,observations:o,product,configure,fixture,stamp})=>{
  const state=()=>call('emi'),action=(action,params={})=>call('emi',{action,...params});
  const wait=async(read,accept,label)=>{
    let row;
    for(let attempt=0;attempt<240;attempt++){row=await read();if(accept(row))return row;await call('wait.ticks',{ticks:2});}
    o.waitFailure={label,row};throw new Error(label+' timed out');
  };
  const idle=()=>wait(state,row=>row.nativeLoaded&&row.worker===0&&(!product||row.coordinator.active&&!row.coordinator.needsBake&&!row.coordinator.baking&&row.retry===false&&row.pendingInputs===false&&row.coordinator.nativeBusy===false&&row.coordinator.reloads===0&&row.pairResultsSame&&row.pairQuerySame),'Native accepted query');
  const held=()=>wait(()=>call('emi.provider'),row=>row.phase==='HELD','Native provider entry');
  const frame=name=>call('emi.frame',{name:`emi-race-${name}-${stamp}.png`});
  const stillHeld=async(name,token)=>{const row=await call('emi.provider');check(name+' overlap completes while original provider remains held',[row.serial,row.phase,row.timedOut],[token.serial,'HELD',false]);return row;};
  const verify=(name,row)=>{
    check(name+' uses actual native operation',row.frames.some(value=>value.startsWith(row.mode==='bake'?'dev.emi.emi.search.EmiSearch.bake':'dev.emi.emi.search.RegexNameQuery.matches')),true);
    check(name+' does not block client',row.clientReached,false);check(name+' has not timed out',row.timedOut,false);
  };
  const packName='candidate-emi-race-resources',pack=path.join(fixture,'resourcepacks',packName);
  for(const domain of ['minecraft','polyglotdevbridge'])await fs.mkdir(path.join(pack,'assets',domain,'lang'),{recursive:true});
  await fs.writeFile(path.join(pack,'pack.mcmeta'),JSON.stringify({pack:{pack_format:3,description:'Owned native EMI provider fixture'}}));
  await fs.writeFile(path.join(pack,'assets/polyglotdevbridge/lang/en_us.lang'),'item.polyglotdevbridge.emi_gate.name=Native gate item\n');
  await fs.writeFile(path.join(pack,'assets/minecraft/lang/zh_cn.lang'),'tile.stone.stone.name=别名甲\n');
  await fs.writeFile(path.join(pack,'assets/minecraft/lang/zh_tw.lang'),'tile.stone.stone.name=別名乙\n');
  await call('resources.select',{packs:[packName]});if(product)await configure(['zh_cn']);
  await action('open');await action('query',{text:'stone'});o.start=await idle();
  o.races=[];
  for(const change of ['query','sidebar','exception','error']) {
    await action('sidebar',{type:'INDEX'});await action('query',{text:'stone'});await idle();
    const token=await call('emi.arm',{mode:'worker',...(['exception','error'].includes(change)?{failure:change}:{})});
    const row={change,token};o.races.push(row);
    try {
      await action('query',{text:'/Native gate item/'});
      if(['exception','error'].includes(change)) {
        row.provider=await wait(()=>call('emi.provider'),value=>value.phase==='FINISHED'&&value.threadState==='TERMINATED','Native provider failure');verify(change,row.provider);
        if(product) {row.recovered=await idle();check(change+' failed worker cleanup permits latest retry',row.recovered.matches.some(key=>key.startsWith('polyglotdevbridge:emi_gate:')),true);}
        else {await action('query',{text:'stone'});row.recovered=await idle();check(change+' native control recovers after another query',row.recovered.matches.some(key=>key.startsWith('minecraft:stone:0*')),true);}
        continue;
      }
      row.provider=await held();verify(change,row.provider);row.held=await state();
      row.ticksBefore=(await call('status')).ticks;await call('wait.ticks',{ticks:3});row.frame=await frame(change);
      check(change+' client draws while provider held',(await call('status')).ticks>=row.ticksBefore+3,true);
      if(product)check(change+' old accepted pair retained during worker',row.held.pairQuerySame&&row.held.pairResultsSame,true);
      if(change==='sidebar') {await action('lookup',{custom:'Race source vial'});await action('sidebar',{type:'LOOKUP_HISTORY'});}
      await action('query',{text:change==='query'?'dirt':'Race source vial'});row.newer=await idle();
      check(change+' newer request has a new compiled query',row.newer.query!==row.held.query,true);
      if(change==='query') {
        check('newer native dirt result published',row.newer.matches.some(key=>key.startsWith('minecraft:dirt:0*')),true);
        check('newer native query excludes old stone result',row.newer.matches.some(key=>key.startsWith('minecraft:stone:0*')),false);
      } else {
        check('newer native source is actual lookup history',row.newer.type,'LOOKUP_HISTORY');
        check('newer native source publishes selected custom vial',row.newer.matches.length===1&&row.newer.matches[0].includes('Race source vial'),true);
      }
      row.beforeRelease=await stillHeld(change,token);
      await call('emi.release',{serial:token.serial});row.terminated=await wait(()=>call('emi.provider'),value=>value.phase==='FINISHED'&&value.threadState==='TERMINATED','Retired worker terminated');
      check(change+' original worker finished without watchdog release',row.terminated.timedOut,false);
      await call('wait.ticks',{ticks:3});row.after=await idle();
      check(change+' old worker cannot replace newer list',row.after.matches,row.newer.matches);
      check(change+' old worker cannot replace newer compiled query',row.after.query,row.newer.query);
      check(change+' current ownership is cleared',row.after.worker,0);
    } finally {await call('emi.release',{serial:token.serial});}
  }
  await action('sidebar',{type:'INDEX'});await action('query',{text:'stone'});await idle();
  const nativeToken=await call('emi.arm',{mode:'bake'});o.native={token:nativeToken};
  try {
    await action('reload');o.native.provider=await held();verify('native reload bake',o.native.provider);o.native.held=await state();
    await action('reload');await action('query',{text:'dirt'});o.native.repeated=await state();o.native.frame=await frame('native-bake');
    if(product)check('repeated native request stays busy while own bake held',o.native.repeated.coordinator.nativeBusy,true);
    o.native.beforeRelease=await stillHeld('native repeated bake',nativeToken);
    await call('emi.release',{serial:nativeToken.serial});o.native.after=await idle();
    o.native.terminated=await wait(()=>call('emi.provider'),value=>value.phase==='FINISHED'&&value.threadState==='TERMINATED','Original native reload terminated');
    check('native repeated bake finished without watchdog release',o.native.terminated.timedOut,false);
    check('native repeated bake eventually uses latest query',o.native.after.text,'dirt');
    check('native repeated bake publishes actual latest dirt result',o.native.after.matches.some(key=>key.startsWith('minecraft:dirt:0*')),true);
    check('native repeated bake excludes obsolete stone result',o.native.after.matches.some(key=>key.startsWith('minecraft:stone:0*')),false);
    check('native repeated bake replaces prior compiled query',o.native.after.query!==o.native.held.query,true);
  } finally {await call('emi.release',{serial:nativeToken.serial});}
  if(product) {
    o.productBakes=[];
    for(const change of ['inputs','resource','reload','clear','error']) {
      await configure(['zh_cn']);await action('query',{text:'stone'});const before=await idle();
      const token=await call('emi.arm',{mode:'bake',...(change==='error'?{failure:'error'}:{})}),row={change,before,token};o.productBakes.push(row);
      try {
        await configure(['zh_tw']);
        if(change==='error') {row.provider=await wait(()=>call('emi.provider'),value=>value.phase==='FINISHED'&&value.threadState==='TERMINATED','Product bake Error');verify(change,row.provider);row.after=await idle();check('Error bake releases guard and retries',row.after.coordinator.needsBake,false);continue;}
        row.provider=await held();verify(change,row.provider);row.held=await state();
        check(change+' five complete native index mirrors remain old while baking',row.held.indexes,before.indexes);
        check(change+' old accepted pair remains during product bake',[row.held.query,row.held.results],[before.query,before.results]);
        await action('options',{appendItemModId:false});await configure(['zh_cn']);
        if(change==='resource')await call('resources.reload');
        if(change==='reload'||change==='clear')await action(change);
        row.changed=await state();row.frame=await frame('product-'+change);
        check(change+' held bake cannot publish after invalidation',row.changed.indexes,before.indexes);
        if(change==='reload'||change==='clear')check(change+' native request invalidates without client guard wait',row.changed.coordinator.nativeBusy,true);
        row.beforeRelease=await stillHeld('product '+change,token);
        await call('emi.release',{serial:token.serial});
        row.terminated=await wait(()=>call('emi.provider'),value=>value.phase==='FINISHED'&&value.threadState==='TERMINATED','Retired product bake terminated');
        check(change+' product bake finished without watchdog release',row.terminated.timedOut,false);
        if(change==='clear')await action('reload');
        row.after=await idle();check(change+' user append setting is not restored over',row.after.appendItemModId,false);
        check(change+' current complete pair published',row.after.pairQuerySame&&row.after.pairResultsSame,true);
        await action('query',{text:'别名甲'});row.latest=await idle();check(change+' only latest secondary context is accepted',row.latest.matches.some(key=>key.startsWith('minecraft:stone:0*')),true);
      } finally {await call('emi.release',{serial:token.serial});}
    }
  }
  await call('screen.close');
});
