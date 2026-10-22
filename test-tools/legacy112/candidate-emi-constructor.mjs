import fs from 'node:fs/promises';
import path from 'node:path';
import readline from 'node:readline';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {run} from './integration-runner.mjs';

const directory=path.dirname(fileURLToPath(import.meta.url));
const gsonRoot='C:/Users/StarskyXIII/.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.10.1';
let gson;
for(const folder of await fs.readdir(gsonRoot)){const file=path.join(gsonRoot,folder,'gson-2.10.1.jar');try{await fs.access(file);gson=file;break;}catch{}}
if(!gson)throw new Error('Missing host JDI JSON library');
const gate=(dump,product)=>{
  const child=spawn(process.env.PT112_JDI_JAVA??'C:/Users/StarskyXIII/.gradle/jdks/azul_systems__inc_-21-amd64-windows.2/bin/java.exe',
    ['--add-modules','jdk.jdi','-cp',path.join(directory,'bridge/build/jdi')+';'+gson,'JdiEmiGate','19142',dump,'stone',String(product)],
    {windowsHide:true,stdio:['pipe','pipe','pipe']});
  const events=[],waiters=[],errors=[];let exited=false,closed=false,code;
  const wake=()=>{for(const waiter of [...waiters])waiter();};
  child.stderr.on('data',data=>errors.push(String(data)));
  readline.createInterface({input:child.stdout}).on('line',line=>{try{events.push(JSON.parse(line));}catch{events.push({event:'error',message:line});}wake();});
  child.on('exit',value=>{exited=true;code=value;wake();});child.on('close',()=>{closed=true;wake();});
  const failed=error=>{events.push({event:'error',message:error.message});wake();};
  child.on('error',failed);child.stdin.on('error',failed);
  const send=command=>{if(exited||child.stdin.destroyed)throw new Error('Debugger input closed');child.stdin.write(command+'\n');};
  const until=predicate=>new Promise((resolve,reject)=>{
    const timeout=setTimeout(()=>finish(new Error('Constructor debugger response timeout')),95000);
    const finish=(error,value)=>{clearTimeout(timeout);const index=waiters.indexOf(poll);if(index!==-1)waiters.splice(index,1);error?reject(error):resolve(value);};
    const poll=()=>{const error=events.find(row=>row.event==='error');if(error)return finish(new Error(JSON.stringify({error,errors})));const value=predicate();if(value)return finish(null,value);if(closed)finish(new Error(JSON.stringify({code,errors})));};
    waiters.push(poll);poll();
  });
  return {events,event:name=>until(()=>events.find(row=>row.event===name)),
    check:async()=>{const count=events.filter(row=>row.event==='stillHeld').length;send('check');return until(()=>events.filter(row=>row.event==='stillHeld')[count]);},
    release:async()=>{send('release');await until(()=>events.find(row=>row.event==='terminated'));},
    finish:async()=>{send('finish');await until(()=>closed?{code}:null);if(code!==0||!events.some(row=>row.event==='finished'))throw new Error(errors.join(''));},
    close:async()=>{if(closed)return;let timedOut=false;await new Promise(resolve=>{let fallback;const timeout=setTimeout(()=>{timedOut=true;child.kill();fallback=setTimeout(resolve,5000);},10000);child.once('close',()=>{clearTimeout(timeout);clearTimeout(fallback);resolve();});if(!child.stdin.destroyed)child.stdin.end();});if(timedOut)throw new Error('Debugger EOF cleanup timed out; closed='+closed);}};
};

await run(async({call,check,observations:o,product,configure,fixture,stamp})=>{
  const action=(action,params={})=>call('emi',{action,...params});
  const idle=async()=>{let row;for(let attempt=0;attempt<180;attempt++){
    row=await call('emi');if(row.nativeLoaded&&row.worker===0&&(!product||row.coordinator.active&&!row.coordinator.needsBake&&!row.coordinator.baking&&row.retry===false&&row.pendingInputs===false&&row.coordinator.nativeBusy===false&&row.coordinator.reloads===0&&row.pairQuerySame&&row.pairResultsSame))return row;
    await call('wait.ticks',{ticks:2});
  }o.timeout=row;throw new Error('Constructor fixture did not settle');};
  await action('open');await action('sidebar',{type:'INDEX'});
  o.profile=await call('probe.resources');o.cases=[];
  for(const change of ['query','settings','reload']){
    await action('options',{searchTooltipByDefault:false,searchModNameByDefault:false,searchTagsByDefault:false});
    if(product)await configure(['zh_cn']);
    await action('query',{text:'iron'});const before=await idle();
    const controller=gate(path.join(fixture,o.profile.finalClassDirectory),product),row={change,before,debugger:controller.events};o.cases.push(row);
    let failure;
    try {
      await controller.event('armed');await action('query',{text:'stone'});row.held=await controller.event('held');
      check(change+' real constructor entry',[row.held.query,row.held.codeIndex,row.held.suspendPolicy,row.held.suspendCount],['stone',0,1,1]);
      check(change+' constructor holds expected native compile gate',row.held.gateOwned,product);
      check(change+' constructor owns no native or product monitor',row.held.ownedMonitorCount,0);
      row.during=await call('emi');row.ticks=(await call('status')).ticks;
      if(change==='settings'){if(product)await configure(['zh_tw']);await action('options',{searchTooltipByDefault:true});}
      if(change==='reload')await action('reload');
      await action('query',{text:'dirt'});await call('wait.ticks',{ticks:3});
      row.frame=await call('emi.frame',{name:`emi-constructor-${change}-${stamp}.png`});row.changed=await call('emi');
      check(change+' client frame progresses while constructor held',(await call('status')).ticks>=row.ticks+3,true);
      if(product){
        check(change+' accepted pair stays coherent while constructor held',row.changed.pairQuerySame&&row.changed.pairResultsSame,true);
        check(change+' held constructor retains accepted pair identity',[row.changed.query,row.changed.results],[row.during.query,row.during.results]);
        check(change+' constructor does not partially publish indexes',row.changed.indexes,row.during.indexes);
        if(change==='reload')check('native reload invalidates during held constructor',row.changed.coordinator.nativeBusy,true);
      }
      row.stillHeld=await controller.check();check(change+' same constructor remains suspended',row.stillHeld.thread,row.held.thread);
      await controller.release();row.terminated=await controller.event('terminated');
      check(change+' actual original worker terminated',row.terminated.status,0);
      await call('wait.ticks',{ticks:3});row.after=await idle();
      check(change+' actual latest native dirt result',row.after.matches.some(key=>key.startsWith('minecraft:dirt:0*')),true);
      check(change+' latest results exclude old stone',row.after.matches.some(key=>key.startsWith('minecraft:stone:0*')),false);
      check(change+' latest results exclude initial iron block',row.after.matches.some(key=>key.startsWith('minecraft:iron_block:0*')),false);
      if(product){
        check(change+' latest accepted query differs from initial',row.after.query!==row.before.query,true);
        check(change+' latest native pair is coherent',row.after.pairQuerySame&&row.after.pairResultsSame,true);
        check(change+' accepted query owns the current complete bundle',row.after.stampBundleCurrent,true);
        check(change+' accepted query owns current capture epoch',row.after.acceptedCaptureEpoch,row.after.currentCaptureEpoch);
        check(change+' accepted query owns latest coordinator epoch',row.after.acceptedCaptureEpoch,row.after.coordinator.epoch);
        if(change!=='query')check(change+' accepts updated capture epoch',row.after.coordinator.epoch>row.during.coordinator.epoch,true);
      }
      await controller.finish();
      check(change+' publication watcher observes actual writes',row.debugger.some(event=>event.event==='publication'),true);
      check(change+' retired constructor publication',row.debugger.some(event=>event.event==='publication'&&event.retiredConstructor),!product);
    }catch(error){failure=error;throw error;}
    finally{try{await controller.close();}catch(error){row.cleanupError=error.stack;if(!failure)throw error;}}
  }
  await call('screen.close');
});
