import {run} from './integration-runner.mjs';

await run(async({call,check,observations:o,product,baseline,fixture,configure})=>{
  const action=(action,params={})=>call('emi',{action,...params});
  const idle=async()=>{let row;for(let i=0;i<180;i++){row=await call('emi');if(row.nativeLoaded&&row.worker===0)return row;await call('wait.ticks',{ticks:2});}o.timeout=row;throw new Error('Native OFF worker did not finish');};
  o.profile=await call('probe.emiProfile');o.resource=await call('probe.resources');
  if(product) {
    const receipts=fixture.includes('missing-reload')?'3':fixture.includes('missing-regex')?'8':'0';
    check('optional unit has expected partial receipts',o.profile.admission.receipts,receipts);
    check('rejected unit never attaches',o.profile.coordinator.attached,false);
    check('rejected unit never activates',o.profile.coordinator.active,false);
    if(fixture.includes('unknown-transform'))check('extra native transformer rejects resource unit',o.resource.admission.state.startsWith('Unsupported resource transform profile:'),true);
    else check('incomplete target profile rejected',o.profile.admission.state.startsWith('Unsupported EMI transform profile:'),true);
    await configure(['zh_cn']);
  }
  await action('open');await action('options',{searchTooltipByDefault:false,searchModNameByDefault:false,searchTagsByDefault:false});
  o.cases=[];
  for(const text of ['stone','/stone/','stone -red','@minecraft stone','石头','','pt112_no_result']) {
    await action('query',{text});const row=await idle();o.cases.push({text,row});
    check(text+' native worker completes',row.worker,0);check(text+' widget text retained',row.text,text);
    if(text==='stone'||text==='/stone/')check(text+' native primary remains searchable',row.matches.some(key=>key.startsWith('minecraft:stone:0*')),true);
    if(text==='石头'||text==='pt112_no_result')check(text+' no name extension while OFF',row.matches.length,0);
    if(text==='')check('OFF empty apply preserves native source',row.matches,row.source);
    if(product) {check(text+' partial helpers do not establish runtime binding',row.binding,false);check(text+' partial helpers remain inactive',row.coordinator.active,false);}
    if(baseline)check(text+' native matched result order',row.matches,baseline.cases.find(value=>value.text===text).row.matches);
  }
  await action('reload',{count:2});o.reload=await idle();check('native reload completes while OFF',o.reload.nativeLoaded,true);
  await action('query',{text:'stone'});o.after=await idle();check('native query after OFF reload',o.after.matches.some(key=>key.startsWith('minecraft:stone:0*')),true);
  await action('clear');o.clear=await call('emi');check('native clear remains unloaded',o.clear.nativeLoaded,false);
  await action('reload');o.recovered=await idle();check('native clear/reload recovery',o.recovered.nativeLoaded,true);
  await call('screen.close');
});
