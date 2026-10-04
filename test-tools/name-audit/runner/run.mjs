import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {spawn, execFileSync} from 'node:child_process';
import {randomUUID} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {root, prepare, writeJson, safeChild} from './setup.mjs';
import {Bridge} from './bridge.mjs';
import {poll, alive, occupied, validateIdentity, verifyProfile} from './environment.mjs';
import {freezeSamples, validateManifest, validateRows, compareRun, normalize, exitCode, stackCanonicalization} from '../lib/compare.mjs';
import {coverage, markdownReport} from '../lib/report.mjs';

export function argumentsFor(args) {
  assert(args.length === 0 || (args.length === 1 && args[0] === '--strict'), 'Usage: node runner/run.mjs [--strict]');
  return {strict: args.includes('--strict')};
}
async function makeProfile(setup, name, index, runId) {
  const session = randomUUID(), instanceId = `PT-name-audit-${runId}-${name}`;
  const instances = path.join(setup.config.prismData, 'instances');
  const target = safeChild(instances, instanceId);
  const staging = safeChild(path.join(setup.config.prismData, 'bridge-staging'), instanceId);
  await fs.mkdir(staging, {recursive: true});
  const game = path.join(staging, '.minecraft');
  for (const folder of ['mods', 'config', 'resourcepacks']) await fs.mkdir(path.join(game, folder), {recursive: true});
  const artifacts = setup.artifacts.filter(a => name === 'polyglot-zh' || a.id !== 'polyglottooltip');
  for (const artifact of artifacts) await fs.copyFile(artifact.source, path.join(game, 'mods', artifact.file));
  const port = setup.config.basePort + index, language = name === 'native-en' ? 'en_us' : 'zh_cn';
  await writeJson(path.join(staging, 'mmc-pack.json'), {formatVersion: 1, components: [
    {uid: 'org.lwjgl3', version: '3.3.3', dependencyOnly: true},
    {uid: 'net.minecraft', version: setup.lock.minecraft, important: true},
    {uid: 'net.neoforged', version: setup.lock.neoforge}
  ]});
  const args = `-Dclientdevbridge.enabled=true -Dclientdevbridge.port=${port} -Dclientdevbridge.eval=false -Dnameaudit.enabled=true -Dnameaudit.session=${session}`;
  await fs.writeFile(path.join(staging, 'instance.cfg'), `[General]\nConfigVersion=1.3\nInstanceType=OneSix\nname=${instanceId}\niconKey=neoforge\nAutomaticJava=false\nOverrideJavaLocation=true\nJavaPath=${setup.config.javaExecutable.replaceAll('\\', '/')}\nOverrideJavaArgs=true\nJvmArgs="${args}"\nOverrideMemory=true\nMinMemAlloc=1024\nMaxMemAlloc=4096\nOverrideWindow=true\nMinecraftWinWidth=1280\nMinecraftWinHeight=720\nLaunchMaximized=false\nShowConsole=false\nCloseAfterLaunch=false\n`);
  await fs.writeFile(path.join(game, 'options.txt'), `lang:${language}\nguiScale:2\nmaxFps:60\nrenderDistance:4\nsimulationDistance:4\npauseOnLostFocus:false\nautoJump:false\nresourcePacks:[]\n`);
  await fs.writeFile(path.join(game, 'config/polyglottooltip-client.toml'), 'displayLanguage = ["en_us"]\nalwaysShow = false\n');
  await fs.rename(staging, target);
  return {name, session, instanceId, instancePath: target, gameDir: path.join(target, '.minecraft'), port, language, artifacts};
}
async function closeProfile(bridge, profile, identity) {
  const current = await bridge.call('audit.identity');
  validateIdentity(current, profile);
  assert.equal(current.pid, identity.pid, 'JVM changed before shutdown');
  await bridge.call('world.leave', {}, 120000);
  const snapshot = await bridge.call('screen.snapshot');
  assert.equal(snapshot.screenClass, 'net.minecraft.client.gui.screens.TitleScreen');
  const buttons = [];
  const walk = node => {
    if (node.extra?.kind === 'button' && node.visible && node.active && JSON.parse(node.extra.component ?? '{}').translate === 'menu.quit') buttons.push(node);
    for (const child of node.children ?? []) walk(child);
  };
  walk(snapshot.root); assert.equal(buttons.length, 1, 'Quit button unavailable');
  const b = buttons[0].bounds; assert(b.w > 0 && b.h > 0);
  await bridge.call('input.mouseClick', {x: b.x + b.w / 2, y: b.y + b.h / 2, space: 'gui', button: 0});
  await poll(async () => !alive(identity.pid) && !(await occupied(profile.port)), 'JVM and port shutdown', 120000);
  bridge.close();
  return {normalQuit: true, pidExited: true, portClosed: true, settingsRestored: 'not-needed; dedicated profile retained'};
}
function representatives(samples) {
  return [samples.find(s => s.sources.includes('control:stone')),
    samples.find(s => s.item === 'reliquary:mob_charm_fragment' && JSON.stringify(s.stack).includes('minecraft:zombie')),
    samples.find(s => s.item === 'reliquary:mob_charm' && JSON.stringify(s.stack).includes('minecraft:zombie')),
    samples.find(s => s.item === 'malum:geas' && s.sources.some(source => source.startsWith('creative:')))].filter(Boolean);
}
async function render(bridge, sample, observation, folder) {
  await bridge.call('audit.render', {sample});
  const fresh = async () => {
    const state = await bridge.call('audit.renderState');
    return state.visible && state.id === sample.id && state.frames >= 3 && state.ageMs < 1000 && state.lines.length ? state : false;
  };
  await poll(fresh, 'native tooltip rendering');
  const screenshot = await bridge.call('screenshot', {afterTicks: 3});
  const state = await fresh(); assert(state, 'Render became stale');
  const prefix = observation.tooltip.slice(0, 1 + (observation.created.length ? 1 : 0)).map(line => normalize(line.text));
  assert.deepEqual(state.lines.slice(0, prefix.length).map(normalize), prefix, 'Rendered title differs from semantic tooltip');
  assert(typeof screenshot.png === 'string' && screenshot.png.length > 100, 'Missing PNG');
  const file = `${sample.item.replace(':', '-')}-${sample.id.slice(0, 10)}.png`;
  await fs.writeFile(path.join(folder, file), Buffer.from(screenshot.png, 'base64'));
  return {id: sample.id, file, route: 'GuiGraphics.renderTooltip(ItemStack) / ClientTextTooltip.renderText', state, titleVerified: true};
}

export async function run({strict}) {
  const lockPath = path.join(root, '.run.lock');
  const lock = await fs.open(lockPath, 'wx');
  const runId = new Date().toISOString().replaceAll(/[:.]/g, '-');
  const folder = path.join(root, 'evidence', runId);
  const report = {infrastructure: 'running', compatibility: 'pending', strict, started: new Date().toISOString(), profiles: [], stackCanonicalization, scope: 'Collected creative/search variants and registry defaults; 1.21.1 NeoForge; native tooltip fixture renderer'};
  let preserveLock = false;
  await fs.mkdir(folder, {recursive: true});
  await lock.writeFile(JSON.stringify({runnerPid: process.pid, folder}));
  const save = () => writeJson(path.join(folder, 'result.json'), report);
  try {
    const setup = await prepare();
    report.inputs = setup.artifacts;
    report.revision = execFileSync('git', ['rev-parse', 'HEAD'], {cwd: root, encoding: 'utf8', windowsHide: true}).trim();
    report.workingTree = execFileSync('git', ['status', '--porcelain'], {cwd: root, encoding: 'utf8', windowsHide: true}).trim();
    const observations = {};
    let samples;
    for (const [index, name] of ['native-en', 'native-zh', 'polyglot-zh'].entries()) {
      assert(!(await occupied(setup.config.basePort + index)), 'Bridge port already in use');
      const profile = await makeProfile(setup, name, index, runId);
      const row = {profile, status: 'starting'}; report.profiles.push(row); await save();
      await verifyProfile(profile);
      const profileFolder = path.join(folder, name); await fs.mkdir(profileFolder);
      let bridge, identity;
      let launched = false, closed = false;
      try {
        const launcher = spawn(setup.config.prismExecutable, ['--dir', setup.config.prismData, '--launch', profile.instanceId], {windowsHide: true, stdio: 'ignore'});
        await new Promise((resolve, reject) => {launcher.once('spawn', resolve); launcher.once('error', reject);});
        launcher.unref(); launched = true;
        console.log(`${name}: starting dedicated client`);
        await poll(async () => await occupied(profile.port), 'Bridge startup', 240000);
        bridge = await Bridge.connect(profile.port);
        identity = await bridge.call('audit.identity');
        validateIdentity(identity, profile); row.identity = identity; await save();
        row.world = await bridge.call('world.reset', {name: 'CDB-name-audit'}, 180000);
        await poll(async () => (await bridge.call('audit.identity')).ready, 'world and resources');
        if (name === 'native-en') {
          const collected = await bridge.call('audit.collect', {}, 120000);
          await writeJson(path.join(folder, 'collection.json'), collected);
          samples = freezeSamples(collected); validateManifest(samples);
          await writeJson(path.join(folder, 'samples.json'), samples);
          report.sampleCount = samples.length;
          await writeJson(path.join(folder, 'coverage.json'), coverage(samples));
        }
        const rows = [];
        for (let offset = 0; offset < samples.length; offset += 32) {
          const batch = await bridge.call('audit.observe', {samples: samples.slice(offset, offset + 32)});
          assert.equal(batch.complete, true, 'Incomplete batch');
          rows.push(...batch.rows);
          await writeJson(path.join(profileFolder, 'observations.json'), rows);
        }
        validateRows(samples, rows, name); observations[name] = rows;
        row.renders = [];
        for (const sample of representatives(samples)) row.renders.push(await render(bridge, sample, rows.find(r => r.id === sample.id), profileFolder));
        row.status = 'observed';
        console.log(`${name}: ${rows.length} samples, ${row.renders.length} rendered checks`);
      } catch (error) {
        row.status = 'failed'; row.error = String(error); throw error;
      } finally {
        if (identity && bridge) {
          try { row.close = await closeProfile(bridge, profile, identity); closed = true; }
          catch (error) { row.closeError = String(error); row.status = 'failed'; throw error; }
          finally { bridge.close(); if (!closed) preserveLock = true; await save(); }
        } else if (launched) {
          bridge?.close();
          preserveLock = true;
          row.recovery = 'Close only this named dedicated instance manually, confirm its JVM and port have exited, then remove .run.lock. Identity was not verified; no shutdown was attempted.';
          await save();
        }
      }
      assert(closed, 'Client shutdown not confirmed');
    }
    const comparison = compareRun(samples, observations);
    await writeJson(path.join(folder, 'comparison.json'), comparison);
    await fs.writeFile(path.join(folder, 'report.md'), markdownReport(comparison, samples));
    Object.assign(report, {infrastructure: comparison.infrastructure, compatibility: comparison.compatibility, counts: comparison.counts});
  } catch (error) {
    report.infrastructure = 'failed'; report.error = String(error);
  } finally {
    report.finished = new Date().toISOString(); report.lockRetained = preserveLock;
    report.exitCode = exitCode(report, strict);
    await save(); await lock.close();
    if (!preserveLock) await fs.unlink(lockPath);
  }
  console.log(JSON.stringify({folder, infrastructure: report.infrastructure, compatibility: report.compatibility, counts: report.counts, error: report.error, exitCode: report.exitCode, lockRetained: preserveLock}, null, 2));
  return exitCode(report, strict);
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) process.exitCode = await run(argumentsFor(process.argv.slice(2)));
