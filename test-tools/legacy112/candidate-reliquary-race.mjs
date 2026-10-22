import fs from 'node:fs/promises';
import path from 'node:path';
import readline from 'node:readline';
import {spawn} from 'node:child_process';
import {run,plain} from './integration-runner.mjs';

const lane=process.argv[6], current=lane==='current', jdiClasspath=process.argv[7];
if(!['archive','current'].includes(lane)||!jdiClasspath) throw new Error('Expected product lane and JDI classpath');
const debuggerGate=(job,file,line)=>{
  const child=spawn('C:/Users/StarskyXIII/.gradle/jdks/azul_systems__inc_-21-amd64-windows.2/bin/java.exe',
    ['--add-modules','jdk.jdi','-cp',jdiClasspath,'JdiGate','19142',job.thread,String(line),job.key,file],
    {windowsHide:true,stdio:['pipe','pipe','pipe']});
  const events=[],waiters=[],errors=[];
  let exited=false,code;
  child.stderr.on('data',data=>errors.push(String(data)));
  const wake=()=>{for(const waiter of [...waiters]) waiter();};
  readline.createInterface({input:child.stdout}).on('line',line=>{events.push(JSON.parse(line));wake();});
  child.on('exit',value=>{exited=true;code=value;wake();});
  child.on('error',error=>{events.push({event:'error',message:error.message});wake();});
  const until=(predicate)=>new Promise((resolve,reject)=>{
    const timeout=setTimeout(()=>finish(new Error('Debugger response timeout')),95000);
    const finish=(error,value)=>{clearTimeout(timeout);const index=waiters.indexOf(poll);if(index!==-1)waiters.splice(index,1);error?reject(error):resolve(value);};
    const poll=()=>{const value=predicate();if(value)return finish(null,value);const error=events.find(row=>row.event==='error');if(error||exited)finish(new Error(JSON.stringify({error,code,errors})));};
    waiters.push(poll);poll();
  });
  return {events,errors,event:name=>until(()=>events.find(row=>row.event===name)),
    release:async()=>{child.stdin.write('release\n');await until(()=>exited?{code}:null);if(code!==0)throw new Error(errors.join(''));},
    close:()=>child.stdin.end()};
};

await run(async({call,check,observations:o,fixture,configure})=>{
  o.lane=lane;o.cases=[];
  const name='candidate-reliquary-race',pack=path.join(fixture,'resourcepacks',name);
  const resources=async(revision='A')=>{
    await fs.mkdir(pack,{recursive:true});
    await fs.writeFile(path.join(pack,'pack.mcmeta'),JSON.stringify({pack:{pack_format:3,description:'Owned native cache race fixture'}}));
    const directory=path.join(pack,'assets','xreliquary','lang');await fs.mkdir(directory,{recursive:true});
    for(const [language,value] of [['en_us',revision],['zh_cn','B']]) {
      const rows=[...['charm','fragment'].map(family=>`xreliquary.item.mob_charm_${family==='fragment'?'fragment_':''}0.name={{!colors.red}}Race ${value} ${family}`),
        'xreliquary.item.mob_charm_0.tooltip={{!colors.red}}Body sentinel 314',
        'xreliquary.item.mob_charm_14.name={{!colors.red}}Variant sentinel 271',
        'xreliquary.xreliquary.item.mob_charm_0.name={{!colors.red}}Prefix sentinel 161'];
      await fs.writeFile(path.join(directory,language+'.lang'),rows.join('\n')+'\n','utf8');
    }
  };
  await configure([]);await resources();await call('resources.select',{packs:[name]});
  const snapshot=(held=false)=>call('candidate.race',{action:'snapshot',held});
  for(const family of (process.argv.includes('--cycle-only')?[]:['charm','fragment'])) for(const transition of ['resource','primary']) for(const seam of ['P1','P2','E','H1','H2']) {
    const label=`${lane}/${family}/${transition}/${seam}`;
    console.log('Running '+label);
    await call('screen.close');await resources();await call('language.set',{language:'en_us'});
    await call('resources.reload');await call('wait.ticks',{ticks:3});
    const job=await call('candidate.race',{action:'prepare',family,mode:seam.startsWith('H')?'helper':'item'});
    const row={label,job};o.cases.push(row);
    row.before=await call('candidate.race',{action:'sentinels'});
    check(label+' selected cache initially absent',row.before.present,false);
    check(label+' observed expected guard',row.before.guarded,current);
    check(label+' expected native cache binding',row.before.boundSame,current);
    if(current && !row.before.boundSame) throw new Error('Native cache guard did not bind');
    const a=`Race A ${family}`,b=`Race B ${family}`;
    if(seam.startsWith('H')) {row.warm=await call('candidate.race',{action:'evaluate'});check(label+' native A hit warmed',plain(row.warm.value),a);}
    const file=path.join(fixture,row.before.finalClassDirectory,'xreliquary','util','LanguageHelper.class');
    const gate=debuggerGate(job,file,seam==='E'?34:seam.startsWith('H')?35:51);
    row.debugger=gate.events;
    try {
      const armed=await gate.event('armed');
      check(label+' actual native helper bytecode coordinate',armed.codeIndex,(seam==='E'?7:seam.startsWith('H')?19:175)+(current?9:0));
      await call('candidate.race',{action:'go',serial:job.serial});const held=await gate.event('held');
      check(label+' debugger suspends only owned event thread',held.suspendPolicy,1);
      check(label+' native key',held.key,job.key);
      check(label+' captured A local',plain(held.localization.replace('{{!colors.red}}','')),a);
      check(label+' hold outside product/native monitors',held.ownedMonitorCount,0);
      check(label+' real helper caller',held.frames.some(frame=>frame.includes('ReliquaryRaceProbe$Job.evaluate')),true);
      if(!seam.startsWith('H')) check(label+' native ItemStack caller',held.frames.some(frame=>frame.includes('net.minecraft.item.ItemStack.func_82833_r')),true);
      row.held=await snapshot(true);row.statusBefore=await call('status');
      if(transition==='primary') await call('language.set',{language:'zh_cn'});
      else {await resources('B');await call('resources.reload');}
      await call('wait.ticks',{ticks:3});row.changed=await snapshot(true);row.statusAfter=await call('status');
      check(label+' client progresses during hold',row.statusAfter.ticks>=row.statusBefore.ticks+3,true);
      check(label+' new native context committed',row.changed.resourceEpoch>row.before.resourceEpoch && row.changed.generation>row.before.generation,true);
      if(current) check(label+' Store epoch advances while worker held',row.changed.cacheEpoch>row.before.cacheEpoch,true);
      check(label+' selected title evicted before release',row.changed.present,false);
      if(['P1','H2'].includes(seam)) {row.prefill=await call('candidate.race',{action:'evaluate',held:true});check(label+' native B prefill',plain(row.prefill.value),b);}
      await gate.release();
    } finally {gate.close();}
    for(let attempt=0;attempt<100;attempt++) {row.done=await call('candidate.race',{action:'state'});if(row.done.phase==='DONE')break;await call('wait.ticks',{ticks:1});}
    check(label+' owned worker finished',row.done.phase,'DONE');check(label+' native worker has no error',row.done.error,null);
    const expected=seam==='H1'&&!current?null:seam==='H2'&&!current?b:a;
    check(label+' retired caller exact result',row.done.value===null?null:plain(row.done.value),expected);
    row.after=await snapshot();
    const expectedCache=['P1','H2'].includes(seam)?(!current&&seam==='P1'?a:b):!current&&['P2','E'].includes(seam)?a:null;
    check(label+' shared cache after retired caller',row.after.cached==null?null:plain(row.after.cached),expectedCache);
    for(const [stage,value] of [['held',row.held],['changed',row.changed],['after',row.after]]) {
      check(label+' '+stage+' map object retained',value.cacheSame,true);
      check(label+' '+stage+' payload unchanged',value.unchanged,true);
      check(label+' '+stage+' sentinel entries retained',value.sentinels,row.before.sentinels);
    }
    row.fresh=await call('candidate.race',{action:'evaluate'});
    check(label+' next native result',plain(row.fresh.value),expectedCache===a?a:b);
  }
  await resources('B');await call('language.set',{language:'en_us'});await call('resources.reload');await call('wait.ticks',{ticks:3});
  o.cycleJob=await call('candidate.race',{action:'prepare',family:'fragment',mode:'item'});
  o.cycleBefore=await call('candidate.race',{action:'evaluate'});
  check('same primary starts at second resource revision',plain(o.cycleBefore.value),'Race B fragment');
  o.cycleSnapshot=await snapshot();
  check('rebuilt fixture observes actual native guard',o.cycleSnapshot.guarded,current);
  check('rebuilt fixture observes exact native binding',o.cycleSnapshot.boundSame,current);
  await resources('A2');await call('resources.reload');await call('wait.ticks',{ticks:3});
  o.a2=await call('candidate.race',{action:'evaluate'});check('same primary accepts third resource revision',plain(o.a2.value),'Race A2 fragment');
  o.beforeSecondary=await snapshot();await configure(['zh_cn']);o.afterSecondary=await snapshot();
  check('secondary-only settings preserve native cache epoch',o.afterSecondary.cacheEpoch,o.beforeSecondary.cacheEpoch);
  check('secondary-only settings preserve native resource epoch',o.afterSecondary.resourceEpoch,o.beforeSecondary.resourceEpoch);
  check('secondary-only settings preserve cached title',o.afterSecondary.cached,o.beforeSecondary.cached);
  check('secondary-only settings advance product generation',o.afterSecondary.generation>o.beforeSecondary.generation,true);
  await call('candidate.race',{action:'go',serial:o.cycleJob.serial});
  for(let attempt=0;attempt<100;attempt++) {o.cycleDone=await call('candidate.race',{action:'state'});if(o.cycleDone.phase==='DONE')break;await call('wait.ticks',{ticks:1});}
  check('cycle fixture worker exits normally',o.cycleDone.phase,'DONE');
  check('cycle fixture worker has no error',o.cycleDone.error,null);
});
