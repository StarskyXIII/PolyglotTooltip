import {run} from './integration-runner.mjs';

await run(async ({call,check,observations:o,product}) => {
  o.start = await call('probe.resources');
  if (product) {
    check('optional resource unit admitted',o.start.admission.state,'admitted');
    check('startup full baseline retained',o.start.resources.ready,true);
  }
  const scenario = async (label,action,outcome,count,ready) => {
    const data = await call('probe.resourceScenario',{action}); o[label]=data;
    check(`${label}: native completion`,data.outcome,outcome);
    if (count !== null) check(`${label}: native provider count`,data.calls.filter(row=>row.event==='provider').length,count);
    if (product) {
      check(`${label}: scopes drained`,data.after.active,0);
      check(`${label}: readiness`,data.after.ready,ready);
      if(action!=='observerFailure'&&label!=='disabledFull') for (const [index,row] of data.calls.entries()) {
        check(`${label}: callback ${index} stays unready`,row.state.ready,false);
        if(action!=='unknown') check(`${label}: callback ${index} inside real scope`,row.state.active>0,true);
      }
    }
    return data;
  };
  await scenario('registration','install','returned',1,true);
  await scenario('normal','full','returned',1,true);
  await scenario('nativeFallback','fallback','returned',2,true);
  await scenario('failure','bothFail','java.lang.IllegalStateException',2,false);
  await scenario('selectiveCannotRecover','selective','returned',1,false);
  await scenario('allRecovers','full','returned',1,true);
  await scenario('error','error','java.lang.AssertionError',1,false);
  await scenario('errorRecovery','full','returned',1,true);
  const nested = await scenario('nestedRegistration','nestedRegistration','returned',0,true);
  check('nested immediate registration native count',nested.calls.map(row=>row.event),['outerRegistration','innerRegistration']);
  if(product) check('nested immediate callback depth',nested.calls.map(row=>row.state.active),[1,2]);
  await scenario('failedRegistration','registrationFailure','java.lang.IllegalStateException',0,false);
  await scenario('registrationRecovery','full','returned',1,true);
  await scenario('unknownDirect','unknown','returned',1,false);
  await scenario('unknownSelective','selective','returned',1,false);
  await scenario('unknownRecovery','full','returned',1,true);
  await scenario('unknownNested','nestedUnknown','returned',2,false);
  await scenario('nestedRecovery','full','returned',1,true);
  await scenario('foreignManager','otherManager','returned',0,false);
  await scenario('foreignSelective','selective','returned',1,false);
  await scenario('foreignRecovery','full','returned',1,true);
  const failed = await scenario('selectiveFailure','selectiveFail','java.lang.IllegalStateException',2,false);
  check('native selective failure retains raw selector',failed.rawSelectorPresent,true);
  const unproven = await scenario('unclearedSelectorFull','full','returned',1,false);
  check('direct refresh does not clear native selector',unproven.rawSelectorPresent,true);
  await scenario('resetNativeSelector','resetSelector','returned',0,false);
  await scenario('provenFullRecovery','full','returned',1,true);
  await scenario('observerFailure','observerFailure','returned',1,false);
  if(product) check('observer failure permanently disables unit',o.observerFailure.after.disabled,true);
  await scenario('disabledFull','full','returned',1,false);
});
