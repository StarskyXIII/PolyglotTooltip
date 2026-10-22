import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {Bridge} from '../name-audit/runner/bridge.mjs';

export const plain = value => value.replace(/§./g, '').trim();
export async function run(body) {
  const [port, fixturePath, outputPath, baselinePath] = process.argv.slice(2);
  const fixture = path.resolve(fixturePath), output = path.resolve(outputPath);
  const launch = JSON.parse(await fs.readFile(path.join(fixture, 'launch.json'), 'utf8'));
  assert.equal((await fs.readFile(path.join(fixture, '.polyglot-fixture'), 'utf8')).replace(/\r\n/g, '\n'), 'legacy112\n');
  assert.equal(launch.port, Number(port));
  const bridge = await Bridge.connect(Number(port)), checks = [], observations = {}, stamp = Date.now();
  const call = (method, params = {}) => bridge.call(method, params, 125000);
  const check = (name, actual, expected) => {
    try { assert.deepEqual(actual, expected); checks.push({name, pass: true}); }
    catch { checks.push({name, pass: false, actual, expected}); }
  };
  let frame = 0;
  const render = async (name, params = {}) => call('screen.render', {name: `${name}-${stamp}-${frame++}.png`, x: 10, y: 10, nativeEvents: true, ...params});
  const configure = async languages => { await call('config.set', {languages, alwaysShow: false}); await call('wait.ticks', {ticks: 3}); };
  const baselineResult = baselinePath ? JSON.parse(await fs.readFile(baselinePath, 'utf8')) : null;
  const baseline = baselineResult?.observations;
  try {
    const runtime = observations.runtime = await call('status');
    assert.equal(path.resolve(runtime.gameDirectory).toLowerCase(), fixture.toLowerCase());
    assert.equal(runtime.pid, String(launch.pid));
    const product = runtime.mods.some(mod => mod.id === 'polyglottooltip');
    assert.equal(product, Object.keys(launch.artifacts).some(name => name.startsWith('polyglottooltip-')));
    if (baselineResult) {
      assert.ok(product && baselineResult.checks.length > 0 && baselineResult.checks.every(check => check.pass), 'Pair requires a passing native baseline');
      assert.equal(baseline.runtime.mods.some(mod => mod.id === 'polyglottooltip'), false);
      assert.equal(Object.keys(baselineResult.launch.artifacts).some(name => name.startsWith('polyglottooltip-')), false);
      assert.deepEqual(Object.fromEntries(Object.entries(launch.artifacts).filter(([name]) => !name.startsWith('polyglottooltip-'))), baselineResult.launch.artifacts);
      assert.deepEqual(launch.libraries, baselineResult.launch.libraries);
      assert.equal(runtime.java, baseline.runtime.java);
      assert.equal(runtime.selectiveResourceReload, baseline.runtime.selectiveResourceReload);
    }
    await call('screen.close');
    if (!runtime.world) await call('world.create', {name: 'pt112-integrations'});
    for (let i = 0; i < 120 && !(await call('status')).player; i++) await call('wait.ticks', {ticks: 5});
    assert.ok((await call('status')).player);
    await call('screen.close');
    if (runtime.language !== 'en_us') await call('language.set', {language: 'en_us'});
    await body({call, check, observations, runtime, product, render, configure, baseline, stamp, fixture});
  } catch (error) { checks.push({name: 'runner', pass: false, error: error.stack}); }
  finally { bridge.close(); await fs.mkdir(path.dirname(output), {recursive: true}); await fs.writeFile(output, JSON.stringify({launch, checks, observations}, null, 2)); }
  const failures = checks.filter(check => !check.pass);
  console.log(JSON.stringify({passed: checks.length - failures.length, failed: failures.length, failures, output}, null, 2));
  if (failures.length) process.exitCode = 1;
}
