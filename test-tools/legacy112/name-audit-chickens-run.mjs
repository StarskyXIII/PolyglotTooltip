import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
import {Bridge} from '../name-audit/runner/bridge.mjs';
import {hash, plain, equal} from './name-audit-core.mjs';
import {seal, validateIdentity} from './name-audit-pack-core.mjs';

const [sessionRoot, outputPath, quit] = process.argv.slice(2);
assert.ok(sessionRoot && outputPath, 'Usage: completed-session output-directory [quit]');
const root = path.resolve(sessionRoot), output = path.resolve(outputPath);
const sessionBytes = await fs.readFile(path.join(root, 'session.json'));
const session = JSON.parse(sessionBytes), completed = JSON.parse(await fs.readFile(path.join(root, 'result.json'), 'utf8'));
assert.equal(completed.completed, true);
const controllerPath = fileURLToPath(import.meta.url), controllerSha256 = hash(await fs.readFile(controllerPath));
await fs.mkdir(output, {recursive: false});
const bridge = await Bridge.connect(session.config.port);
const call = (method, params = {}, timeout = 40000) => bridge.call(method, params, timeout);
const report = {schema: 1, sessionSha256: hash(sessionBytes), controllerSha256, product: session.config.product,
  language: session.config.language, sessionFile: path.join(root, 'session.json'), cases: {}, mutations: {}, checks: [], coverage: {registryReplacement: false, disabledEntry: false,
    generationChange: false, unknownProviders: false, originalTop: false, topCe: false, hwyla: false}};
const check = (name, actual, expected) => {
  const pass = equal(actual, expected);
  report.checks.push({name, pass, actual, expected});
  assert.ok(pass, name);
};
let sequence = 0;
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const evidence = (entityId, params = {}) => call('audit.entityEvidence', {entityId, ...params});
async function synchronizedEntity(entityId, predicate, params = {}) {
  let row;
  for (let attempt = 0; attempt < 100; attempt++) {
    row = await evidence(entityId, params);
    if (predicate(row)) return row;
    await pause(100);
  }
  throw new Error('Client entity did not synchronize: ' + JSON.stringify(row));
}
async function aim(params) {
  const result = await call('hud.world', params);
  if (result.origin?.chickenType) {
    check('fixture adult jockey state', [result.origin.growingAge, result.origin.chickenJockey, result.origin.layProgress], [0, true, 10]);
  }
  await call('world.command', {command: 'replaceitem entity @p slot.weapon.mainhand theoneprobe:probe'});
  await call('wait.ticks', {ticks: 25});
  return result;
}
async function capture(label, entity, extra = {}) {
  const name = 'audit-chicken-' + session.config.lane + '-' + Date.now() + '-' + sequence++ + '.png';
  const result = await call('hud.capture', {kind: 'top', name,
    ...(entity ? {origin: {uuid: entity.uuid}, text: entity.nativeName, visible: true,
      target: {type: 'ENTITY', entity: {uuid: entity.uuid}}, aim: {type: 'ENTITY', entity: {uuid: entity.uuid}}} : {}), ...extra});
  const screenshot = path.resolve(result.path);
  assert.equal(path.dirname(screenshot).toLowerCase(), path.resolve(session.config.fixture, 'screenshots').toLowerCase());
  const bytes = await fs.readFile(screenshot);
  await fs.writeFile(path.join(output, name), bytes, {flag: 'wx'});
  result.screenshotSha256 = hash(bytes);
  result.texts = result.matched?.text.map(row => plain(row.text)) ?? [];
  await seal(path.join(output, label + '.json'), result);
  return result;
}
async function positive(entityId, label) {
  const state = await synchronizedEntity(entityId, row => row.present && !row.dead && !row.custom && row.child === false && row.layProgress === 10);
  const current = await evidence(entityId, {capture: true});
  check(label + ': stable client identity', current.identity, state.identity);
  if (report.product) check(label + ': fresh evidence valid', current.valid, true);
  const rendered = await capture(label, current);
  const aliases = report.product ? [...new Set(current.aliases.map(plain).filter(value => value !== plain(current.nativeName)))] : [];
  check(label + ': exact native title and aliases', rendered.texts[0],
    plain(current.nativeName) + (aliases.length ? ' ｜ ' + aliases.join(' ｜ ') : ''));
  return {state: current, render: rendered};
}
try {
  const status = await call('status');
  check('bound client PID', status.pid, session.pid);
  check('bound client nonce', status.fixtureNonce, session.config.nonce);
  report.beforeIdentity = await call('audit.identity');
  validateIdentity(report.beforeIdentity, session, report.product ? JSON.parse(await fs.readFile(path.join(root, 'observer.json'), 'utf8')) : undefined);
  const runtime = await call('pack.runtime');
  assert.ok(runtime.mods.some(mod => mod.id === 'theoneprobe'));
  report.loadedHud = runtime.mods.filter(mod => ['theoneprobe', 'waila'].includes(mod.id));
  report.coverage.hudArtifact = session.provenance.mods.find(mod => mod.id === 'theoneprobe');
  report.coverage.originalTop = report.coverage.hudArtifact.sha256 === 'f3e5048dbe58f37f98f8130886ede880ac5b3333ee73cae2381c337e500c4bd1';
  report.coverage.entityFixture = 'Adult chicken with native jockey flag and fixed egg timer';
  check('render audit enabled', runtime.jvmArguments.includes('-Dclientdevbridge.hudAudit=true'), true);
  await call('screen.close');
  const first = await aim({prepare: true, target: 'chicken'}), entityId = first.origin.id;
  report.cases.initial = await positive(entityId, 'initial');
  const other = await aim({target: 'chicken-other'});
  check('different enabled fixture types', other.origin.chickenType === first.origin.chickenType, false);
  await aim({target: 'chicken'});
  const beforeType = await positive(entityId, 'before-type');
  await aim({target: 'chicken', chickenType: other.origin.chickenType});
  const changed = await synchronizedEntity(entityId, row => row.present && row.type === other.origin.chickenType,
    report.product ? {token: beforeType.state.token} : {});
  check('type mutation preserves client entity identity', changed.identity, beforeType.state.identity);
  if (report.product) check('type mutation invalidates fresh old evidence', changed.retainedValid, false);
  report.mutations.type = {before: beforeType.state, after: changed};
  report.cases.changedType = await positive(entityId, 'changed-type');
  if (report.product) for (const alias of beforeType.state.aliases.filter(alias => !report.cases.changedType.state.aliases.includes(alias))) {
    check('old type alias is absent: ' + alias, report.cases.changedType.render.texts.some(text => text.includes(plain(alias))), false);
  }
  const beforeCustom = await positive(entityId, 'before-custom');
  await aim({target: 'chicken', customName: 'PT named chicken'});
  const named = await synchronizedEntity(entityId, row => row.present && row.custom,
    report.product ? {token: beforeCustom.state.token, capture: true} : {});
  check('custom mutation preserves client entity identity', named.identity, beforeCustom.state.identity);
  if (report.product) {
    check('custom name invalidates fresh old evidence', named.retainedValid, false);
    check('custom name has no new translation evidence', named.admitted, false);
  }
  const namedRender = await capture('custom', named);
  check('custom native HUD title stays literal', namedRender.texts[0], 'PT named chicken');
  if (report.product) for (const alias of beforeCustom.state.aliases.filter(alias => plain(alias) !== plain(beforeCustom.state.nativeName))) {
    check('custom HUD contains no former alias: ' + alias, namedRender.texts.some(text => text.includes(plain(alias))), false);
  }
  report.mutations.custom = {before: beforeCustom.state, after: named};
  report.cases.custom = {state: named, render: namedRender};
  await aim({target: 'chicken', customName: ''});
  const beforeRemoval = await positive(entityId, 'before-removal');
  await aim({target: 'chicken', remove: true});
  const removed = await synchronizedEntity(entityId, row => !row.present,
    report.product ? {token: beforeRemoval.state.token} : {});
  if (report.product) check('removal invalidates fresh old evidence', removed.retainedValid, false);
  report.mutations.removal = {before: beforeRemoval.state, after: removed};
  await aim({target: 'sky'});
  const observation = await call('hud.observe');
  report.cases.removed = {state: removed, render: await capture('removed', null,
    {negative: true, aim: observation.frame.aim})};
  report.coverage.removedEntityAbsent = !removed.present;
  report.coverage.hudClearsAfterAimingAway = report.cases.removed.render.negative;
  report.afterIdentity = await call('audit.identity');
  validateIdentity(report.afterIdentity, session, report.product ? JSON.parse(await fs.readFile(path.join(root, 'observer.json'), 'utf8')) : undefined);
  check('controller stayed fixed', hash(await fs.readFile(controllerPath)), controllerSha256);
  report.passed = report.checks.every(check => check.pass);
  await seal(path.join(output, 'result.json'), report);
  if (quit === 'quit') {
    await call('quit', {}, 15000);
    await seal(path.join(root, 'quit.json'), {requested: true, pid: session.pid});
  }
} catch (error) {
  report.passed = false;
  report.error = error.stack;
  await seal(path.join(output, 'failure.json'), report);
  process.exitCode = 1;
} finally { bridge.close(); }
console.log(JSON.stringify({passed: report.passed, checks: report.checks.length, error: report.error}));
