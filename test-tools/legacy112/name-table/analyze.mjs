import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {report, corpus, read, hashFile, rejection} from './runtime-common.mjs';

const maybe = async file => { try { return await read(file); } catch (error) { if (error.code === 'ENOENT') return null; throw error; } };
const mapRows = value => new Map((value?.rows ?? []).map(row => [row.sampleId, row]));
const count = (object, key, amount = 1) => { object[key] = (object[key] ?? 0) + amount; };
const canonical = value => JSON.stringify(value, (_, v) => v && typeof v === 'object' && !Array.isArray(v) ? Object.fromEntries(Object.keys(v).sort().map(k => [k, v[k]])) : v);
const identical = (a, b) => canonical(a) === canonical(b);
const quantiles = values => {
  values = values.filter(Number.isFinite).sort((a,b) => a-b);
  if (!values.length) return {n:0};
  return {n: values.length, p50: values[Math.ceil(values.length*.5)-1], p95: values[Math.ceil(values.length*.95)-1], p99: values[Math.ceil(values.length*.99)-1], max: values.at(-1)};
};
const runNames = process.argv[2] ? JSON.parse(await fs.readFile(process.argv[2], 'utf8')) : {E2E: {training:'run-e2e-training1', holdout:'run-e2e-holdout1', controls:'run-e2e-controls1'}, Meatball: {training:'run-meatball-training2', holdout:'run-meatball-holdout1', controls:'run-meatball-holdout1'}};
const historical = await read(path.join(corpus, 'summary.json'));
const output = {schema: 'native-name-table-experiment-analysis-v1', generatedAt: new Date().toISOString(), historical, packs: {}, attempts: [], evidence: []};

for (const pack of ['E2E', 'Meatball']) {
  const selected = await read(path.join(corpus, pack, 'samples.json')), selectedMap = new Map(selected.map(row => [row.sampleId, row]));
  const selection = await read(path.join(corpus, pack, 'runtime-selection.json'));
  const train = path.join(report, runNames[pack].training), holdout = path.join(report, runNames[pack].holdout);
  const controlsRun = path.join(report, runNames[pack].controls??runNames[pack].holdout);
  const frozenSeal = await read(path.join(corpus, pack, 'runtime-selection-seal.json'));
  const originalSeal = await read(path.join(corpus, pack, 'sample-seal.json'));
  assert.equal(await hashFile(path.join(corpus, pack, 'samples.json')), originalSeal.samplesSha256);
  assert.equal(await hashFile(path.join(corpus, pack, 'runtime-selection.json')), frozenSeal.sha256);
  assert.deepEqual(selection.selected.map(row=>row.sampleId), selected.map(row=>row.sampleId));
  assert.equal(selection.acquisition.length, new Set(selection.acquisition.map(row=>row.sampleId)).size);
  const tableSeal = await maybe(path.join(train, 'table-seal.json'));
  for(const folder of [train,holdout]) {
    const runSeal=await maybe(path.join(folder,'runtime-selection-seal.json'));
    if(runSeal) { assert.equal(runSeal.sha256,frozenSeal.sha256); assert.equal(await hashFile(path.join(folder,'runtime-selection.json')),frozenSeal.sha256); }
  }
  if(tableSeal) {
    assert.equal(tableSeal.selectionSha256,frozenSeal.sha256);
    assert.equal(await hashFile(path.join(train,'training-observations.jsonl')),tableSeal.observationsSha256);
    assert.equal(await hashFile(path.join(train,'table.jsonl')),tableSeal.tableSha256);
    assert.equal(await hashFile(path.join(train,'table-environment.json')),tableSeal.environment);
    const reference=await maybe(path.join(holdout,'training-reference.json'));
    if(reference) { assert.equal(reference.tableSha256,tableSeal.tableSha256); assert.equal(reference.observationsSha256,tableSeal.observationsSha256); }
  }
  const oldMetrics = await read(path.join(report, 'corpus-v2', pack, 'engine-metrics.json'));
  assert.equal(await hashFile(path.join(corpus, pack, 'observations.jsonl')), oldMetrics.sourceSha256, 'Historical metrics provenance differs');
  const result = {pack, runs: runNames[pack], selectedStates: selected.length, selectedPairs: selected.length*2,
    namespaces: new Set(selected.map(row => row.mod)).size, extraControlStates: selection.extraAcquisition.length,
    frozenSeal, tableSeal, historicalMetrics: oldMetrics,
    trainingMetrics: await maybe(path.join(train, 'engine-metrics.json')), trainingAdmission: await maybe(path.join(train, 'acquisition-admission.json')),
    trainingCompletion: await maybe(path.join(train, 'completion.json')), holdoutCompletion: await maybe(path.join(holdout, 'completion.json')),
    trainingFailure: await maybe(path.join(train, 'failure.json')), holdoutFailure: await maybe(path.join(holdout, 'failure.json')),
    runtime: await maybe(path.join(train, 'initial-runtime.json')), holdoutRuntime: await maybe(path.join(holdout, 'initial-runtime.json')),
    environmentChanges: await maybe(path.join(holdout, 'holdout-environment-changes.json')),
    activation: await maybe(path.join(holdout, 'activation.json')), skipped: await maybe(path.join(holdout, 'activation-skipped.json')),
    counts: {}, perMod: {}, outcomes: [], disagreements: [], historicalChanges: [], acquisitionRejected: [], unchangedExamples: [], tooltipResults: [], lifecycle: [], timings: {}, sameSessionCounts: {}, sameSessionTimings: {}};
  const phases = {}, hold = {}, expectedIds=selection.acquisition.map(row=>row.sampleId).sort();
  const validatePhase = (artifact, language) => {
    if(!artifact) return new Map();
    assert.deepEqual(artifact.rows.map(row=>row.sampleId).sort(),expectedIds,'Wrong phase IDs');
    assert.ok(artifact.rows.every(row=>row.language===language),'Wrong phase language'); return mapRows(artifact);
  };
  for (const phase of ['primary-first','en_us','zh_cn','primary-repeat']) phases[phase] = validatePhase(await maybe(path.join(train, 'train'+(tableSeal?.acceptedAttempt??0)+'-'+phase+'.json')),phase.startsWith('primary')?'zh_tw':phase);
  for (const [language, name] of [['en_us','holdout-cold-en_us'],['zh_cn','holdout-switched-zh_cn'],['zh_tw','holdout-primary-zh_tw']]) hold[language] = validatePhase(await maybe(path.join(holdout, name+'.json')),language);
  const queryRows = await maybe(path.join(holdout, 'heldout-primary-lookup.json')), queries = mapRows(queryRows);
  if(queryRows) validatePhase(queryRows,'zh_tw');
  const freshTrainingRows = tableSeal ? (await fs.readFile(path.join(train, 'training-observations.jsonl'), 'utf8')).trim().split('\n').slice(1).map(JSON.parse) : [];
  const trainingById = new Map(selection.acquisition.map((sample,i) => [sample.sampleId, freshTrainingRows[i]]));
  if (tableSeal) {
    assert.equal(freshTrainingRows.length, selection.acquisition.length);
    for(const [index,sample] of selection.acquisition.entries())
      assert.ok(identical(freshTrainingRows[index].state,Object.fromEntries(['item','metadata','damage','count','stack'].map(key=>[key,sample[key]]))),'Training state/order differs');
  }
  const sameSessionRows=await maybe(path.join(train,'training-primary-lookup.json'));
  result.sameSessionCounts.selectedPairs=selected.length*2;
  result.sameSessionCounts.observedPairs=0;
  if(sameSessionRows) {
    validatePhase(sameSessionRows,'zh_tw');
    for(const query of sameSessionRows.rows.filter(row=>selectedMap.has(row.sampleId))) for(const language of ['en_us','zh_cn']) {
      count(result.sameSessionCounts,'observedPairs');
      const outcome=query.outputs?.[language]??{status:query.status??'CALLBACK_ERROR'}, native=phases[language].get(query.sampleId), primary=phases['primary-repeat'].get(query.sampleId);
      count(result.sameSessionCounts,outcome.status);
      if(['HIT','LITERAL'].includes(outcome.status)) {
        const ineligible=rejection(query)??rejection(native)??rejection(primary)??(query.name!==primary?.name?'primary-oracle-drift':null)??(query.serializedStateChangedAcrossLookupObservation?'serialized-state-changed-across-query':null);
        if(ineligible) count(result.sameSessionCounts,'inconclusive');
        else {count(result.sameSessionCounts,'comparable');count(result.sameSessionCounts,outcome.name===native.name?'exact':'wrong');}
      }
    }
  }
  result.sameSessionCounts.unobservedPairs=result.sameSessionCounts.selectedPairs-result.sameSessionCounts.observedPairs;
  for (const sample of selected) {
    const perMod = result.perMod[sample.mod] ??= {selectedStates:0, counts:{}}; perMod.selectedStates++;
    const primary = phases['primary-first'].get(sample.sampleId), hPrimary = hold.zh_tw.get(sample.sampleId), query = queries.get(sample.sampleId);
    for (const language of ['en_us','zh_cn']) {
      const training = phases[language].get(sample.sampleId), actual = hold[language].get(sample.sampleId), observation = trainingById.get(sample.sampleId);
      const admitted = !!observation && !observation.targets[language].rejection;
      const tally = key => { count(result.counts,key); count(perMod.counts,key); };
      const reason = observation?.targets[language].rejection;
      if (observation) {
        tally(admitted ? 'trainingAdmitted' : 'trainingRejected');
        if (!admitted) { tally('trainingReject:'+reason); result.acquisitionRejected.push({sampleId:sample.sampleId,item:sample.item,language,reason}); }
        else { tally(training.name === primary.name ? 'trainingSameAsPrimary' : 'trainingDifferentFromPrimary');
          if(training.name===primary.name && result.unchangedExamples.filter(row=>row.item===sample.item).length<1 && result.unchangedExamples.length<30)
            result.unchangedExamples.push({item:sample.item,language,primary:primary.name,target:training.name}); }
      } else tally('trainingUnobserved');
      if(!actual) tally('holdoutTargetUnobserved');
      else tally(rejection(actual)?'holdoutTargetIneligible':'holdoutTargetEligible');
      const historicalTarget = sample.historicalTargets[language];
      if (training && !rejection(training) && !historicalTarget.rejection && typeof historicalTarget.name==='string') {
        tally('historicalComparable');
        if (historicalTarget.name!==training.name) { tally('historicalChanged'); result.historicalChanges.push({sampleId:sample.sampleId,item:sample.item,language,historical:historicalTarget.name,fresh:training.name}); }
      }
      if (admitted && actual && !rejection(actual)) {
        tally('repeatabilityComparable'); tally('repeatabilityComparable:'+language);
        if (training.name === actual.name) tally('repeatabilityExact');
        else { tally('repeatabilityChanged'); tally('repeatabilityChanged:'+language); result.disagreements.push({sampleId:sample.sampleId,item:sample.item,language,trained:training.name,heldout:actual.name,
          trainedPrimary:primary?.name,heldoutPrimary:hPrimary?.name,primarySame:primary?.name===hPrimary?.name,kind:'native-restart-or-order-change'}); }
      } else if(actual) tally('repeatabilityIneligible');
      else tally('repeatabilityUnobserved');
      if (!query) { tally('queryUnobserved'); continue; }
      const outcome = query.outputs?.[language] ?? {status:query.status ?? 'CALLBACK_ERROR', reason:query.error};
      tally('query:'+outcome.status);
      const evalReason = rejection(actual) ?? rejection(hPrimary) ?? rejection(query)
        ?? (query.name!==hPrimary.name?'primary-oracle-drift':null)
        ?? (query.serializedStateChangedAcrossLookupObservation?'serialized-state-changed-across-query':null);
      const row = {sampleId:sample.sampleId,item:sample.item,mod:sample.mod,language,...outcome,heldoutNative:actual?.name,heldoutPrimary:hPrimary?.name,comparable:!evalReason};
      if (['HIT','LITERAL'].includes(outcome.status)) {
        tally('queryOutput');
        if (!evalReason) { tally('queryComparableOutput'); row.exact = outcome.name === actual.name;
          tally(row.exact ? 'queryExactOutput' : 'queryWrongOutput');
          if (!row.exact) result.disagreements.push({...row,kind:'wrong-runtime-output'});
        } else { tally('queryOutputInconclusive'); row.inconclusiveReason=evalReason; }
      }
      result.outcomes.push(row);
    }
  }
  for (const [name, folder] of [['training',train],['holdout',holdout]]) {
    const artifact = await maybe(path.join(folder,name+'-tooltips.json'));
    if (!artifact) { result.counts[name+'TooltipUnobserved']= (selection.tooltips.length+1)*2; continue; }
    assert.deepEqual(artifact.rows.map(row=>row.sampleId).sort(),[...selection.tooltips.map(row=>row.sampleId),selection.editedControls.at(-1).sampleId].sort(),'Wrong tooltip sample IDs');
    for (const row of artifact.rows) for (const mode of ['NORMAL','ADVANCED']) {
      const obs = row.observations?.filter(value => value.mode===mode) ?? [];
      const byPhase = Object.fromEntries(obs.map(value=>[value.phase,value]));
      const a=byPhase['inactive-before'], b=byPhase['active-shadow'], c=byPhase['inactive-after'];
      const valid = identical(obs.map(value=>value.phase).sort(),['inactive-before','active-shadow','inactive-after'].sort()) && obs.every(value=>!value.error && value.contextCurrent && value.nativeNameCaptured && Array.isArray(value.tooltip)) && !row.batchContextChanged && row.contextBefore?.active && row.contextAfter?.active && row.contextBefore.contextIdentity===row.contextAfter.contextIdentity;
      const baselineStable = valid && identical(a.tooltip,c.tooltip), equal = baselineStable && identical(a.tooltip,b.tooltip);
      const inputStable = valid && obs.every(value=>identical(value.before,value.after)) && identical(a.before,b.before) && identical(a.before,c.before);
      result.tooltipResults.push({phase:name,item:row.item,sampleId:row.sampleId,mode,valid,baselineStable,exact:equal,inputStable,
        status:!valid?'INCONCLUSIVE':!baselineStable?'UNSTABLE_BASELINE':!equal?'CHANGED_WITH_SHADOW':!inputStable?'SERIALIZED_STATE_CHANGED':'EXACT',
        ...(equal&&inputStable?{}:{observations:obs})});
    }
  }
  result.tooltipCounts={}; for(const row of result.tooltipResults) count(result.tooltipCounts,row.status);
  const supplemental=await maybe(path.join(controlsRun,'supplemental-control-reference.json'));
  result.supplementalControls={reference:supplemental,activation:await maybe(path.join(controlsRun,'supplemental-activation.json')),completion:await maybe(path.join(controlsRun,'supplemental-completion.json')),failure:await maybe(path.join(controlsRun,'supplemental-failure.json'))};
  let supplementalAttested=false;
  if(supplemental) {
    assert.equal(supplemental.sampleId,selection.stone.sampleId);
    const folder=path.join(controlsRun,'supplemental-controls'), seal=await maybe(path.join(folder,'table-seal.json'));
    if(seal) {
      for(const [file,digest] of [['table.jsonl',seal.tableSha256],['table-environment.json',seal.environment],['training-observations.jsonl',seal.observationsSha256]]) assert.equal(await hashFile(path.join(folder,file)),digest);
      assert.equal(seal.scope,'supplemental-stone-lifecycle-only');
      assert.equal(seal.selectionSha256,frozenSeal.sha256);
      const observations=(await fs.readFile(path.join(folder,'training-observations.jsonl'),'utf8')).trim().split('\n').map(JSON.parse);
      assert.equal(observations.length,2);assert.ok(identical(observations[1].state,Object.fromEntries(['item','metadata','damage','count','stack'].map(key=>[key,selection.stone[key]]))));
      assert.deepEqual(await read(path.join(controlsRun,'supplemental-environment-changes.json')),[]);
      result.supplementalControls.seal=seal;
      const activated=result.supplementalControls.activation;
      if(activated) {
        assert.equal(activated.environment,seal.environment);assert.equal(activated.tableSha256,seal.tableSha256);assert.equal(activated.active,true);
        supplementalAttested=true;
      }
    }
  }
  const controls = await maybe(path.join(controlsRun,'lifecycle.json'));
  for(const row of controls??[]) {
    let passed=false;
    if(!row.error && ['wrong-environment','added-config-file','modified-config-file'].includes(row.name)) {
      const expected = row.name==='wrong-environment' ? 'Environment manifest changed' : row.name==='added-config-file' ? 'Input inventory changed: config' : 'Attested input changed:';
      passed = row.rejected===true && row.reason.includes(expected) && row.state.active===false;
    } else if(!row.error) {
      passed = row.before?.active===true && row.after?.active===false && row.query?.rows?.length===1 && row.query.rows[0].status==='STALE_ENVIRONMENT';
      if(row.name==='language-change') passed=passed && row.after.language==='en_us' && row.afterRestoringLanguage?.language==='zh_tw' && row.afterRestoringLanguage.active===false;
      else if(row.name==='world-change') passed=passed && row.before.worldIdentity!==row.after.worldIdentity;
      else if(row.name==='resource-reload') passed=passed && row.before.contextIdentity!==row.after.contextIdentity;
      else passed=false;
    }
    if(supplemental && !supplementalAttested) passed=false;
    result.lifecycle.push({...row,passed});
  }
  for(const name of ['wrong-environment','added-config-file','modified-config-file','resource-reload','language-change','world-change'])
    if(!result.lifecycle.some(row=>row.name===name)) result.lifecycle.push({name,passed:false,notRun:true});
  result.editedControls = {training:await maybe(path.join(train,'training-controls.json')), holdout:await maybe(path.join(holdout,'holdout-controls.json'))};
  result.supplementalControls.edited=await maybe(path.join(controlsRun,'supplemental-edited-controls.json'));
  const timed=(queryRows?.rows??[]).filter(row=>selectedMap.has(row.sampleId));
  for(const field of ['decodeNs','nativeNameNs','liveCaptureBeforeNs','liveCaptureAfterNameNs','liveCaptureAfterLookupNs']) result.timings[field]=quantiles(timed.map(row=>row[field]));
  result.timings.guardedAdapterKeyAndLookupNs=quantiles(timed.flatMap(row=>Object.values(row.outputs??{}).map(out=>out.frozenKeyAndLookupNs)));
  const sameTimed=(sameSessionRows?.rows??[]).filter(row=>selectedMap.has(row.sampleId));
  for(const field of ['decodeNs','nativeNameNs','liveCaptureBeforeNs','liveCaptureAfterNameNs','liveCaptureAfterLookupNs']) result.sameSessionTimings[field]=quantiles(sameTimed.map(row=>row[field]));
  result.sameSessionTimings.guardedAdapterKeyAndLookupNs=quantiles(sameTimed.flatMap(row=>Object.values(row.outputs??{}).map(out=>out.frozenKeyAndLookupNs)));
  result.counts.serializedStateChangedAcrossLookup = timed.filter(row=>row.serializedStateChangedAcrossLookupObservation).length;
  result.counts.rowsDeclaringPureEngineContract = timed.filter(row=>row.directNativeNameCallsInsideEngine===0).length;
  result.phaseFiles={};
  for(const folder of [train,holdout]) {
    const files=await fs.readdir(folder).catch(error=>error.code==='ENOENT'?[]:Promise.reject(error));
    result.phaseFiles[path.basename(folder)]=files.filter(file=>file.endsWith('.json'));
  }
  output.packs[pack]=result;
}
for(const name of (await fs.readdir(report)).filter(name=>name.startsWith('run-'))) {
  const folder=path.join(report,name);
  const launch=await maybe(path.join(folder,'launch.json')), exit=await maybe(path.join(folder,'exit.json'));
  const phaseFiles=(await fs.readdir(folder)).filter(file=>file.endsWith('.json') && /^(train\d+-|holdout-(cold|switched|primary)-|supplemental-(primary-first|primary-repeat|en_us|zh_cn))/.test(file));
  const captures=[];for(const file of phaseFiles) {const value=await read(path.join(folder,file));if(value.rows && value.elapsedMs!==undefined) captures.push({file,rows:value.rows.length,elapsedMs:value.elapsedMs});}
  let transitions=[];try{transitions=(await fs.readFile(path.join(folder,'language-transitions.jsonl'),'utf8')).trim().split('\n').filter(Boolean).map(JSON.parse);}catch(error){if(error.code!=='ENOENT')throw error;}
  output.attempts.push({name, config:await maybe(path.join(folder,'config.json')), completion:await maybe(path.join(folder,'completion.json')),
    failure:await maybe(path.join(folder,'failure.json')), supplementalFailure:await maybe(path.join(folder,'supplemental-failure.json')),
    exit,controller:await maybe(path.join(folder,'controller-source-seal.json')),startedAt:launch?.launchedAt,
    sessionElapsedMs:launch && exit?Date.parse(exit.exitedAt)-Date.parse(launch.launchedAt):null,captures,transitions,
    trainingActivation:await maybe(path.join(folder,'activation.json')),supplementalActivation:await maybe(path.join(folder,'supplemental-activation.json'))});
}
for(const file of ['design.md','review-design-architecture.md','review-design-evidence.md','review-implementation-architecture.md','review-implementation-evidence.md','java8-gson280-tests.log','java8-gson280-tests-v2.log','runtime-guards-tests.log'])
  output.evidence.push({file,sha256:await hashFile(path.join(report,file))});
await fs.writeFile(path.join(report,'analysis.json'),JSON.stringify(output,null,2)+'\n');
console.log(JSON.stringify(Object.fromEntries(Object.entries(output.packs).map(([pack,value])=>[pack,{counts:value.counts,tooltips:value.tooltipCounts,lifecycle:value.lifecycle.map(row=>({name:row.name,passed:row.passed})),failure:value.holdoutFailure??value.trainingFailure}]))));
