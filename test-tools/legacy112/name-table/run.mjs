import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {Bridge} from '../../name-audit/runner/bridge.mjs';
import {root, report, corpus, read, save, hashFile, pause, sameOwned, environment, environmentChanges, buildTable, writeTraining} from './runtime-common.mjs';

const run = path.resolve(process.argv[2]), mode = process.argv[3], trainingRun = process.argv[4] && path.resolve(process.argv[4]);
assert.ok(run.startsWith(report + path.sep)); assert.ok(['train', 'holdout', 'controls'].includes(mode));
assert.ok(mode !== 'holdout' || trainingRun?.startsWith(report + path.sep));
const config = await read(path.join(run, 'config.json')), owner = await read(path.join(run, 'process.json')), launch = await read(path.join(run, 'launch.json'));
const directory = path.join(corpus, config.pack), selectionFile = path.join(directory, 'runtime-selection.json');
const seal = await read(path.join(directory, 'runtime-selection-seal.json'));
assert.equal(await hashFile(selectionFile), seal.sha256);
const selection = await read(selectionFile), samples = selection.acquisition;
await fs.copyFile(selectionFile, path.join(run, 'runtime-selection.json'), fs.constants.COPYFILE_EXCL);
await save(path.join(run, 'runtime-selection-seal.json'), seal);
const sourceDirectory = path.join(run, 'controller-sources'); await fs.mkdir(sourceDirectory);
const sourceFiles = [];
for (const file of ['run.mjs', 'runtime-common.mjs', 'launch_fixture.py', 'seal-selection.mjs', 'runtime-common.test.mjs', 'prepare_corpus.py']) {
  const source = path.join(root, 'test-tools/legacy112/name-table', file), destination = path.join(sourceDirectory, file);
  await fs.copyFile(source, destination, fs.constants.COPYFILE_EXCL); sourceFiles.push({file, sha256: await hashFile(destination)});
}
await recordSourceSeal();
async function recordSourceSeal() { await save(path.join(run, 'controller-source-seal.json'), {capturedBeforeFreshObservations: true, files: sourceFiles}); }
let bridge, stage = 'startup', bound = false, tableInfo;
const call = (method, params = {}, timeout = 360000) => bridge.call(method, params, timeout);
const probe = async (action, params = {}) => {
  const started=Date.now(), result=await call('probe.nameTable', {action, ...params});
  if(action==='load') result.totalActivationElapsedMs=Date.now()-started;
  return result;
};
const record = (name, value) => save(path.join(run, name + '.json'), value);
const progress = async value => {
  const entry = {at: new Date().toISOString(), stage, ...value};
  await fs.writeFile(path.join(run, 'progress.json'), JSON.stringify(entry, null, 2)); console.log(JSON.stringify(entry));
};
const ready = async () => {
  const deadline = Date.now() + 300000;
  while (Date.now() < deadline) {
    const state = await call('probe.performance', {action: 'state'});
    if (state.ready && !state.libraryPending) return state;
    await pause(300);
  }
  throw new Error('Language readiness deadline');
};
const switchLanguage = async language => {
  const from=(await call('status')).language, started=Date.now(), event={from,to:language,startedAt:new Date().toISOString(),changed:from!==language};
  try {
    if (from !== language) await call('language.set', {language});
    await ready(); assert.equal((await call('status')).language, language); event.complete=true;
  } catch(error) {event.error=String(error);throw error;}
  finally {event.elapsedMs=Date.now()-started;await fs.appendFile(path.join(run,'language-transitions.jsonl'),JSON.stringify(event)+'\n');}
};
const capture = async (label, input = samples, action = 'capture') => {
  stage = label; await ready(); const rows = [], batches = [], started = Date.now();
  const batchDirectory = path.join(run, label + '-batches'); await fs.mkdir(batchDirectory);
  for (let offset = 0; offset < input.length; offset += 16) {
    const batch = await probe(action, {samples: input.slice(offset, offset + 16)});
    await save(path.join(batchDirectory, String(offset).padStart(6, '0') + '.json'), {offset, action, expectedIds: input.slice(offset, offset + 16).map(row => row.sampleId), response: batch});
    assert.equal(batch.rows.length, Math.min(16, input.length - offset));
    assert.deepEqual(batch.rows.map(row => row.sampleId), input.slice(offset, offset + 16).map(row => row.sampleId));
    rows.push(...batch.rows.map(row => ({...row, batchContextChanged: batch.contextChanged})));
    const {rows: ignored, ...context} = batch; batches.push({offset, ...context});
    if (offset % 256 === 0) await progress({completed: rows.length, total: input.length});
  }
  await record(label, {action, elapsedMs: Date.now() - started, rows, batches});
  await progress({completed: rows.length, total: input.length}); return rows;
};
const runtimeEnvironment = async label => {
  const runtime = await call('pack.runtime'); await record(label + '-runtime', runtime);
  const manifest = await environment(config, runtime, launch); await record(label + '-environment', manifest); return manifest;
};
const activate = async (source, label = 'activation') => {
  stage=label; await progress({});
  const saved = await read(path.join(source, 'table-seal.json'));
  const target = path.join(config.fixture, 'exports/native-name-table'); await fs.mkdir(target, {recursive: true});
  const tableFile = path.join(source, 'table.jsonl'), envFile = path.join(source, 'table-environment.json');
  assert.equal(await hashFile(tableFile), saved.tableSha256); assert.equal(await hashFile(envFile), saved.environment);
  const table = label + '-' + config.nonce + '.jsonl', environmentFile = label + '-env-' + config.nonce + '.json';
  await fs.copyFile(tableFile, path.join(target, table), fs.constants.COPYFILE_EXCL);
  await fs.copyFile(envFile, path.join(target, environmentFile), fs.constants.COPYFILE_EXCL);
  tableInfo = {table, environmentFile, environment: saved.environment, tableSha256: saved.tableSha256};
  const result = await probe('load', tableInfo); await record(label, result); return result;
};

const supplementalControls = async () => {
  const folder = path.join(run, 'supplemental-controls'); await fs.mkdir(folder);
  await record('supplemental-control-reference', {folder, sampleId: selection.stone.sampleId,
    scope: 'One already-frozen stone state, freshly reacquired for current-session lifecycle controls only. Not held-out fidelity or broad ecosystem coverage.'});
  try {
    await switchLanguage('zh_tw'); const before = await runtimeEnvironment('supplemental-initial'), observations = {};
    for (const [phase, language] of [['primary-first','zh_tw'],['en_us','en_us'],['zh_cn','zh_cn'],['primary-repeat','zh_tw']]) {
      await switchLanguage(language); observations[phase] = await capture('supplemental-' + phase, [selection.stone]);
    }
    const after = await runtimeEnvironment('supplemental-after'), changes = environmentChanges(before, after);
    await record('supplemental-environment-changes', changes); assert.equal(changes.length, 0, 'Supplemental acquisition environment changed');
    await save(path.join(folder, 'table-environment.json'), after); const digest = await hashFile(path.join(folder,'table-environment.json'));
    const observationsFile = await writeTraining(folder, [selection.stone], observations, digest);
    const admission = await read(path.join(folder,'acquisition-admission.json'));
    assert.equal(admission.rejections.length,0,'Supplemental stone was not safely admitted');
    await buildTable(observationsFile, path.join(folder,'table.jsonl'), path.join(folder,'engine-metrics.json'), path.join(folder,'build-table.log'));
    await save(path.join(folder,'table-seal.json'), {environment:digest,tableSha256:await hashFile(path.join(folder,'table.jsonl')),
      observationsSha256:await hashFile(observationsFile),selectionSha256:seal.sha256,scope:'supplemental-stone-lifecycle-only',sealedAt:new Date().toISOString()});
    await activate(folder,'supplemental-activation'); await capture('supplemental-control-lookup',[selection.stone],'lookup');
    await capture('supplemental-edited-controls',selection.editedControls,'lookup');
    await lifecycle(); await record('supplemental-completion',{complete:true,scope:'lifecycle-controls-only'});
  } catch(error) { await record('supplemental-failure',{stage,error:String(error.stack??error)}); }
};
const lifecycle = async () => {
  stage = 'lifecycle'; const results = [];
  const row = async (name, action) => {
    try { const evidence = await action(); results.push({name, ...evidence}); }
    catch (error) { results.push({name, error: String(error.stack ?? error)}); }
    await fs.writeFile(path.join(run, 'lifecycle.json'), JSON.stringify(results, null, 2) + '\n');
    await progress({control: name});
  };
  const denied = async info => {
    try { return {rejected: false, state: await probe('load', info)}; }
    catch (error) { return {rejected: true, reason: String(error), state: await probe('state')}; }
  };
  await row('wrong-environment', () => denied({...tableInfo, environment: '0'.repeat(64)}));
  await probe('load', tableInfo);
  const input = path.join(config.fixture, 'config/polyglot-name-table-prototype-' + config.nonce + '.txt');
  await row('added-config-file', async () => {
    await fs.writeFile(input, 'owned prototype addition control\n', {flag: 'wx'});
    try { return await denied(tableInfo); } finally { await fs.unlink(input); }
  });
  await probe('load', tableInfo);
  const configFile = path.join(config.fixture, 'config/polyglottooltip.cfg'), before = await fs.readFile(configFile);
  await row('modified-config-file', async () => {
    await fs.appendFile(configFile, '\n# owned name table attestation control\n');
    try { return await denied(tableInfo); } finally { await fs.writeFile(configFile, before); }
  });
  await probe('load', tableInfo);
  await row('resource-reload', async () => {
    const before = await probe('state'); await call('resources.reload'); await ready();
    return {before, after: await probe('state'), query: await probe('lookup', {samples: [selection.stone]})};
  });
  await probe('load', tableInfo);
  await row('language-change', async () => {
    const before = await probe('state'); await switchLanguage('en_us');
    const after = await probe('state'), query = await probe('lookup', {samples: [selection.stone]});
    await switchLanguage('zh_tw'); return {before, after, query, afterRestoringLanguage: await probe('state')};
  });
  await probe('load', tableInfo);
  await row('world-change', async () => {
    const before = await probe('state'); await call('world.create', {name: config.worldName + '-alternate'});
    await call('wait.ticks', {ticks: 40}); await call('screen.close'); await ready();
    return {before, after: await probe('state'), query: await probe('lookup', {samples: [selection.stone]})};
  });
};

try {
  const deadline = Date.now() + 30 * 60000;
  while (Date.now() < deadline) {
    let state;
    try { bridge ??= await Bridge.connect(config.port); state = await bridge.call('status', {}, 15000); }
    catch { bridge?.close(); bridge = null; await pause(2000); continue; }
    sameOwned(state, config, owner); bound = true;
    if (['net.minecraft.client.gui.GuiMainMenu', 'lumien.custommainmenu.gui.GuiCustom'].includes(state.screen)) { await record('menu', state); break; }
    await pause(2000);
  }
  assert.ok(bound && await fs.stat(path.join(run, 'menu.json')).catch(() => null), 'Menu deadline');
  assert.equal((await call('status')).language, config.initialLanguage);
  assert.equal(config.initialLanguage, mode === 'holdout' ? 'en_us' : 'zh_tw');
  stage = 'world-setup'; await progress({}); await call('pack.setup'); await call('world.create', {name: config.worldName});
  await call('wait.ticks', {ticks: 60}); await call('screen.close'); await call('config.set', {languages: ['en_us', 'zh_cn']}); await ready();
  assert.equal((await call('status')).language, config.initialLanguage);
  const initial = await runtimeEnvironment('initial');
  if (mode === 'controls') {
    await supplementalControls();
  } else if (mode === 'train') {
    let before = initial, accepted;
    for (let attempt = 0; attempt < 2; attempt++) {
      const observations = {};
      for (const [phase, language] of [['primary-first', 'zh_tw'], ['en_us', 'en_us'], ['zh_cn', 'zh_cn'], ['primary-repeat', 'zh_tw']]) {
        await switchLanguage(language); observations[phase] = await capture('train' + attempt + '-' + phase);
      }
      const after = await runtimeEnvironment('train' + attempt + '-after'), changes = environmentChanges(before, after);
      await record('train' + attempt + '-environment-changes', changes);
      if (!changes.length) { accepted = {observations, manifest: after, attempt}; break; }
      before = after; await progress({environmentDrift: changes.length, retry: attempt === 0});
    }
    assert.ok(accepted, 'Environment changed across both training attempts');
    await record('table-environment', accepted.manifest); const digest = await hashFile(path.join(run, 'table-environment.json'));
    const observationFile = await writeTraining(run, samples, accepted.observations, digest);
    stage = 'build-table'; await progress({});
    await buildTable(observationFile, path.join(run, 'table.jsonl'), path.join(run, 'engine-metrics.json'), path.join(run, 'build-table.log'));
    await record('table-seal', {environment: digest, tableSha256: await hashFile(path.join(run, 'table.jsonl')),
      observationsSha256: await hashFile(observationFile), selectionSha256: seal.sha256, acceptedAttempt: accepted.attempt,
      sealedAt: new Date().toISOString(), sealedBeforeRestartHoldout: true});
    await activate(run); await capture('training-primary-lookup', samples, 'lookup');
    await capture('training-controls', selection.editedControls, 'lookup');
    await capture('training-tooltips', [...selection.tooltips, selection.editedControls.at(-1)], 'tooltipPair');
  } else {
    const sealed = await read(path.join(trainingRun, 'table-seal.json'));
    assert.equal(sealed.selectionSha256, seal.sha256); assert.equal(await hashFile(path.join(trainingRun, 'table.jsonl')), sealed.tableSha256);
    await record('training-reference', {trainingRun, ...sealed});
    await capture('holdout-cold-en_us');
    await switchLanguage('zh_cn'); await capture('holdout-switched-zh_cn');
    await switchLanguage('zh_tw'); await capture('holdout-primary-zh_tw');
    const current = await runtimeEnvironment('holdout-after'), trainingEnvironment = await read(path.join(trainingRun, 'table-environment.json'));
    const changes = [...environmentChanges(trainingEnvironment, current), ...environmentChanges(initial, current).map(row => ({...row, duringHoldout: true}))];
    await record('holdout-environment-changes', changes);
    if (!changes.length) {
      await activate(trainingRun); await capture('heldout-primary-lookup', samples, 'lookup');
      await capture('holdout-controls', selection.editedControls, 'lookup');
      await capture('holdout-tooltips', [...selection.tooltips, selection.editedControls.at(-1)], 'tooltipPair');
      await lifecycle();
    } else {
      await record('activation-skipped', {status: 'STALE_ENVIRONMENT', reason: 'Held-out environment differs; table was not relabeled or rebuilt', changes});
      await supplementalControls();
    }
  }
  stage = 'complete'; await record('completion', {mode, complete: true, at: new Date().toISOString()}); await progress({});
} catch (error) {
  await record('failure', {stage, error: String(error.stack ?? error)}); console.error(error); process.exitCode = 1;
} finally {
  if (bridge && bound) {
    try { sameOwned(await bridge.call('status', {}, 15000), config, owner); await bridge.call('quit', {}, 15000); }
    catch (error) { await record('quit-observation', {error: String(error)}); }
  }
  bridge?.close();
}
