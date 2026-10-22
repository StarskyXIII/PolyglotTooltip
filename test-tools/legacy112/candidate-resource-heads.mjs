import {run} from './integration-runner.mjs';

await run(async({call,check,observations:o,product,baseline})=>{
  o.profile=await call('probe.resources');o.emi=await call('probe.emiProfile');
  if(product){
    check('unknown HEAD participants reject resource integration',o.profile.admission.state.startsWith('Unsupported resource transform profile:'),true);
    check('unknown HEAD participants prevent EMI attachment',o.emi.coordinator.attached,false);
    check('unknown HEAD participants prevent EMI activation',o.emi.coordinator.active,false);
    check('unknown HEAD participants prevent late EMI queue',o.emi.admission.queued,'false');
    check('unknown HEAD participants leave no EMI receipts',o.emi.admission.receipts,'0');
    for(const [stage,stem] of [['client','Client'],['manager','Manager']]){
      const rows=o.profile.admission[stage+'.mixins'].split('\n');
      for(const [suffix,priority] of [['High',2000],['Low',500]])check(stage+' '+suffix+' actual HEAD participant',rows.some(row=>row.startsWith('com.starskyxiii.polyglottooltip.resourceheads.mixin.'+stem+suffix+'/'+priority+'/')),true);
    }
  }
  o.scenarios=[];
  for(const [action,outcome,providers,clients,full,registration] of [
    ['install','returned',1,0,0,1],['full','returned',1,1,1,0],
    ['fallback','returned',2,1,2,0],['bothFail','java.lang.IllegalStateException',2,1,2,0],
    ['full','returned',1,1,1,0],['error','java.lang.AssertionError',1,1,1,0],
    ['full','returned',1,1,1,0],['nestedRegistration','returned',0,0,0,2],
    ['registrationFailure','java.lang.IllegalStateException',0,0,0,1]
  ]){
    const value=await call('probe.resourceScenario',{action,heads:true}),events=value.headsAfter.slice(value.headsBefore.length);
    const row={action,value,events};o.scenarios.push(row);
    check(action+' HEAD history append-only',value.headsAfter.slice(0,value.headsBefore.length),value.headsBefore);
    check(action+' native outcome',value.outcome,outcome);
    check(action+' native provider count',value.calls.filter(entry=>entry.event==='provider').length,providers);
    for(const [family,count] of [['client',clients],['full',full],['registration',registration]])
      for(const suffix of ['high','low'])check(action+' '+family+' '+suffix+' exactly once per native invocation',events.filter(event=>event===family+'-'+suffix).length,count);
    if(baseline){
      const previous=baseline.scenarios[o.scenarios.length-1];
      check(action+' preserves native HEAD order',events,previous.events);
      check(action+' preserves native callback order',value.calls.map(entry=>entry.event),previous.value.calls.map(entry=>entry.event));
    }
  }
  await call('emi',{action:'open'});await call('emi',{action:'options',searchTooltipByDefault:false,searchModNameByDefault:false,searchTagsByDefault:false});
  await call('emi',{action:'query',text:'stone'});
  for(let attempt=0;attempt<180;attempt++){
    o.search=await call('emi');if(o.search.nativeLoaded&&o.search.worker===0)break;
    await call('wait.ticks',{ticks:2});
  }
  check('native reload completes with HEAD participants',o.search.nativeLoaded,true);
  check('native search completes with HEAD participants',o.search.worker,0);
  check('native stone remains searchable with HEAD participants',o.search.matches.some(key=>key.startsWith('minecraft:stone:0*')),true);
  if(product){check('partial resource helpers remain unbound',o.search.binding,false);check('resource unit remains OFF',o.search.coordinator.active,false);}
  if(baseline)check('HEAD participant native search order',o.search.matches,baseline.search.matches);
  await call('screen.close');
});
