import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {Bridge} from '../name-audit/runner/bridge.mjs';
import {hash, canonical, equal, batches} from './name-audit-core.mjs';
import {planCatalog, seal, readOptional, collectPart, mergePart, validateBatch, validateCorpus,
  validateIdentity, validateArtifactHashes} from './name-audit-pack-core.mjs';

const [configPath, outputPath, corpusPath] = process.argv.slice(2);
assert.ok(configPath && outputPath, 'Usage: config.json output-directory [corpus-directory]');
const configBytes = await fs.readFile(configPath), config = JSON.parse(configBytes);
const tooltipFlag = config.tooltipFlag ?? 'NORMAL';
assert.ok(['NORMAL', 'ADVANCED'].includes(tooltipFlag), 'Invalid tooltip flag');
const output = path.resolve(outputPath), corpusRoot = path.resolve(corpusPath ?? path.join(output, 'corpus'));
const ownSources = ['name-audit-pack-run.mjs', 'name-audit-pack-core.mjs', 'name-audit-core.mjs', '../name-audit/runner/bridge.mjs'];
const directory = path.dirname(fileURLToPath(import.meta.url));
const sourceHashes = async () => Object.fromEntries(await Promise.all(ownSources.map(async name => [name,
  hash(await fs.readFile(path.join(directory, name)))])));
const controller = await sourceHashes();
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const canonPath = value => path.resolve(value).toLowerCase();
const statusCheck = status => {
  assert.equal(status.fixtureNonce, config.nonce);
  assert.equal(canonPath(status.gameDirectory), canonPath(config.fixture));
  assert.equal(status.language, config.language);
  if (session) assert.equal(status.pid, session.pid, 'Client restarted; use a new output directory');
};
const identityCheck = identity => {
  assert.equal(identity.fixtureNonce, config.nonce);
  assert.equal(canonPath(identity.gameDirectory), canonPath(config.fixture));
  assert.equal(identity.language, config.language);
  assert.equal(identity.product, config.product);
  assert.deepEqual(identity.modifiers, {shift: false, control: false});
  assert.ok(Number.isSafeInteger(identity.jvmStartTime));
  if (session) {
    validateIdentity(identity, session);
  }
};
let bridge, session, completed = false;
const failures = [];
const call = (method, params = {}, timeout = 610000) => bridge.call(method, params, timeout);
const ownership = async () => {
  const status = await call('status', {}, 15000); statusCheck(status);
  identityCheck(await call('audit.identity'));
  return status;
};
const environmentOf = runtime => Object.fromEntries(['forge', 'java.runtime.version', 'java.vendor', 'java.home',
  'jvmArguments', 'origins', 'cleanroom.relauncher.parent'].map(key => [key, runtime[key]]));

async function attest(runtime) {
  const files = new Map(), mods = [];
  const originFile = origin => fileURLToPath(origin.startsWith('jar:') ? origin.slice(4).split('!/')[0] : origin);
  for (const mod of runtime.mods) {
    let sha256;
    if (files.has(mod.source)) sha256 = files.get(mod.source);
    else {
      try { sha256 = hash(await fs.readFile(mod.source)); }
      catch (error) {
        if (error.code !== 'ENOENT' || canonPath(mod.source) !== canonPath(path.join(config.fixture, 'minecraft.jar'))) throw error;
      }
      files.set(mod.source, sha256);
    }
    const declaredSourceAvailable = Boolean(sha256);
    if (!sha256) {
      assert.ok(mod.implementationSource, 'Missing implementation origin: ' + mod.id);
      sha256 = hash(await fs.readFile(originFile(mod.implementationSource)));
    }
    mods.push({id: mod.id, version: mod.version, source: mod.source, sha256, declaredSourceAvailable,
      implementationClass: mod.implementationClass, implementationSource: mod.implementationSource});
  }
  assert.equal(mods.filter(m => m.id === 'polyglotdevbridge').length, 1);
  assert.equal(mods.find(m => m.id === 'polyglotdevbridge').sha256, config.bridgeSha256);
  const products = mods.filter(m => m.id === 'polyglottooltip');
  assert.equal(products.length, config.product ? 1 : 0);
  if (config.product) assert.equal(products[0].sha256, config.productSha256);
  const packs = await Promise.all(runtime.resourcePacks.map(async name => ({name,
    sha256: hash(await fs.readFile(path.join(config.fixture, 'resourcepacks', name)))})));
  assert.deepEqual(packs, config.resourcePacks);
  const origins = {};
  for (const [name, origin] of Object.entries(runtime.origins)) {
    assert.ok(origin.source && !origin.error, 'Missing native class origin: ' + name);
    origins[name] = {...origin, sha256: hash(await fs.readFile(originFile(origin.source)))};
  }
  const result = {mods, packs, origins};
  validateArtifactHashes(result);
  return result;
}

async function inputs() {
  const result = {};
  const visit = async (folder, relative) => {
    let entries;
    try { entries = await fs.readdir(folder, {withFileTypes: true}); }
    catch (error) { if (error.code === 'ENOENT') return; throw error; }
    for (const entry of entries) {
      const key = relative + '/' + entry.name, file = path.join(folder, entry.name);
      if (entry.isDirectory()) await visit(file, key);
      else if (entry.isFile()) result[key] = hash(await fs.readFile(file));
    }
  };
  for (const folder of ['config', 'scripts', 'resources']) await visit(path.join(config.fixture, folder), folder);
  return result;
}

async function connect() {
  const saved = await readOptional(path.join(output, 'session.json'));
  if (saved) {
    assert.equal(saved.configSha256, hash(configBytes));
    assert.ok(equal(saved.controller, controller), 'Controller changed; use a new output directory');
    session = saved;
  }
  if (config.prismExecutable && !session) {
    const child = spawn(config.prismExecutable, ['--launch', config.instance], {windowsHide: true, stdio: 'ignore'});
    child.once('error', error => failures.push({stage: 'prism-launch', message: error.message}));
    child.unref();
  }
  let lastError, status, runtime;
  const deadline = Date.now() + (session ? 30000 : 1800000);
  while (Date.now() < deadline) {
    try {
      bridge = await Bridge.connect(config.port);
      status = await call('status', {}, 15000);
      runtime = await call('pack.runtime');
    } catch (error) {
      lastError = error; bridge?.close(); bridge = null;
      await pause(2000);
      continue;
    }
    statusCheck(status);
    if (!session && status.world) throw new Error('A new audit attempt must start from the main menu');
    if (runtime.loaderAvailable && (session || runtime.nativeMainMenu || status.screen === 'lumien.custommainmenu.gui.GuiCustom')) break;
    bridge.close(); bridge = null;
    await pause(2000);
  }
  if (!bridge) throw lastError ?? new Error('Client startup timed out');
  const provenance = await attest(runtime);
  if (session) {
    assert.ok(status.world && status.player, 'Resume requires the same ready client world');
    assert.ok(equal(provenance, session.provenance), 'Runtime artifacts changed');
    assert.ok(equal(environmentOf(runtime), session.environment), 'Runtime environment changed');
    assert.ok(equal(await inputs(), session.replayInputs), 'Input files changed; use a new attempt');
    identityCheck(await call('audit.identity'));
    return;
  }
  assert.ok(!status.world && (runtime.nativeMainMenu || status.screen === 'lumien.custommainmenu.gui.GuiCustom'));
  const environment = environmentOf(runtime);
  const menuInputs = await inputs();
  const setup = await call('pack.setup');
  await call('world.create', {name: 'pt112-name-audit-pack'});
  let ready;
  for (let index = 0; index < 180; index++) {
    ready = await call('status', {}, 15000); statusCheck(ready);
    if (ready.world && ready.player) break;
    if (index === 179) throw new Error('World startup timed out');
    await pause(1000);
  }
  await call('screen.close');
  const identity = await call('audit.identity'); identityCheck(identity);
  assert.equal(ready.pid, status.pid, 'Client changed during world setup');
  session = {schema: 2, pid: ready.pid, configSha256: hash(configBytes), controller,
    config, environment, provenance, setup, identity, menuInputs, replayInputs: await inputs()};
  await seal(path.join(output, 'session.json'), session);
}

async function corpus() {
  const existing = await readOptional(path.join(corpusRoot, 'index.json'));
  if (existing) {
    if (!corpusPath) assert.equal(existing.sourceSessionSha256, hash(await fs.readFile(path.join(output, 'session.json'))));
    return existing;
  }
  assert.ok(!corpusPath && !config.product && config.language === 'en_us', 'Only the native English lane collects samples');
  const savedCatalog = await readOptional(path.join(corpusRoot, 'catalog.json'));
  const catalog = await call('audit.catalog');
  if (savedCatalog) assert.ok(equal(catalog, savedCatalog), 'Registry catalog changed');
  await seal(path.join(corpusRoot, 'catalog.json'), catalog);
  const plan = planCatalog(catalog);
  await seal(path.join(corpusRoot, 'plan.json'), plan);
  const index = {parts: [], samples: new Map()};
  for (let number = 0; number < plan.parts.length; number++) {
    const part = plan.parts[number], folder = path.join(corpusRoot, 'parts', part.id);
    await ownership();
    const collected = await collectPart(part, folder, call);
    const bytes = await fs.readFile(path.join(folder, 'manifest.json'));
    mergePart(index, part, collected, hash(bytes));
    console.log(JSON.stringify({stage: 'collect', part: number + 1, parts: plan.parts.length, id: part.id,
      samples: collected.samples.length, unique: index.samples.size, complete: collected.complete}));
  }
  const frozen = {schema: 2, catalogSha256: plan.catalogSha256, catalogItems: plan.items.length,
    parts: index.parts, samples: [...index.samples.values()], collectionComplete: index.parts.every(p => p.complete),
    sourceSessionSha256: hash(await fs.readFile(path.join(output, 'session.json')))};
  await seal(path.join(corpusRoot, 'index.json'), frozen);
  return frozen;
}

async function replay(index, method) {
  for (let number = 0; number < index.parts.length; number++) {
    const part = index.parts[number];
    const bytes = await fs.readFile(path.join(corpusRoot, 'parts', part.id, 'manifest.json'));
    assert.equal(hash(bytes), part.manifestSha256, 'Frozen samples changed: ' + part.id);
    const data = JSON.parse(bytes), selected = new Set(part.ids);
    const samples = data.samples.filter(sample => selected.has(sample.id));
    assert.deepEqual(samples.map(s => s.id), part.ids);
    const plan = batches(samples, method), folder = path.join(output, method, part.id);
    await seal(path.join(folder, 'transport.json'), {batches: plan.batches.map(b => b.map(s => s.id)), skipped: plan.skipped});
    for (let offset = 0; offset < plan.batches.length; offset++) {
      const batch = plan.batches[offset], file = path.join(folder, String(offset).padStart(5, '0') + '.json');
      const saved = await readOptional(file);
      let result = saved;
      if (!result) {
        await ownership();
        result = await call(method, {samples: batch, ...(method === 'audit.observe' ? {tooltipFlag} : {})});
      }
      validateBatch(result, batch, method === 'audit.observe' ? tooltipFlag : undefined);
      if (method === 'audit.observe') {
        identityCheck(result.identity);
        if (config.product) {
          const observer = {input: result.identity.observerInputSha256, output: result.identity.observerOutputSha256};
          validateIdentity(result.identity, session, observer);
          await seal(path.join(output, 'observer.json'), observer);
        }
      }
      await seal(file, result);
    }
    await seal(path.join(folder, 'complete.json'), {samples: samples.length, batches: plan.batches.length,
      skipped: plan.skipped.length, manifestSha256: part.manifestSha256});
    if (number % 5 === 0 || number + 1 === index.parts.length) console.log(JSON.stringify({stage: method,
      part: number + 1, parts: index.parts.length, id: part.id, samples: samples.length}));
  }
}

try {
  assert.equal(config.launchMethod, 'native-prism');
  assert.ok(!await readOptional(path.join(output, 'result.json')), 'This output already completed; use a new attempt');
  await connect();
  console.log(JSON.stringify({stage: 'world-ready', lane: config.lane, pid: session.pid}));
  const index = await corpus();
  await validateCorpus(corpusRoot, index);
  const indexBytes = await fs.readFile(path.join(corpusRoot, 'index.json'));
  const corpusBinding = {root: corpusRoot, sha256: hash(indexBytes), samples: index.samples.length};
  await seal(path.join(output, 'corpus-binding.json'), corpusBinding);
  assert.equal(new Set(index.samples.map(s => s.id)).size, index.samples.length);
  await replay(index, 'audit.observe');
  await seal(path.join(output, 'primary-complete.json'), {corpus: corpusBinding, identity: await call('audit.identity')});
  await replay(index, 'audit.diagnose');
  assert.equal(hash(await fs.readFile(path.join(corpusRoot, 'index.json'))), corpusBinding.sha256);
  assert.ok(equal(controller, await sourceHashes()), 'Controller changed during collection');
  const identity = await call('audit.identity'); identityCheck(identity);
  const status = await ownership();
  const afterInputs = await inputs();
  await seal(path.join(output, 'after-inputs.json'), afterInputs);
  const afterProvenance = await attest(await call('pack.runtime'));
  assert.ok(equal(afterProvenance, session.provenance), 'Runtime artifacts changed during observation');
  await seal(path.join(output, 'result.json'), {completed: true, config, pid: session.pid, corpus: corpusBinding,
    collectionComplete: index.collectionComplete, identity, status, inputsStable: equal(afterInputs, session.replayInputs), failures});
  completed = true;
  if (config.keepOpen) {
    await call('screen.close');
    await seal(path.join(output, 'manual-handoff.json'), {...config, pid: session.pid, status});
  } else {
    await call('quit', {}, 15000);
    await seal(path.join(output, 'quit.json'), {requested: true, pid: session.pid});
  }
} catch (error) {
  failures.push({message: error.stack});
  await fs.mkdir(output, {recursive: true});
  await fs.writeFile(path.join(output, 'failure-' + Date.now() + '.json'), JSON.stringify({pid: session?.pid, config, failures}, null, 2));
  process.exitCode = 1;
} finally { bridge?.close(); }
console.log(JSON.stringify({stage: 'finished', completed, failures}));
