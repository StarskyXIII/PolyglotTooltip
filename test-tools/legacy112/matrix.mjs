import fs from 'node:fs/promises';
import path from 'node:path';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import assert from 'node:assert/strict';
import {Bridge} from '../name-audit/runner/bridge.mjs';

const [casesPath, outputPath] = process.argv.slice(2);
const cases = JSON.parse(await fs.readFile(casesPath, 'utf8'));
const output = path.resolve(outputPath);
const directory = path.dirname(fileURLToPath(import.meta.url));
const results = [];
await fs.mkdir(output, {recursive: true});
const run = (program, args, capture = false) => new Promise((resolve, reject) => {
  let stdout = '';
  const child = spawn(program, args, {windowsHide: true, stdio: capture ? ['ignore', 'pipe', 'inherit'] : 'inherit'});
  child.stdout?.on('data', data => stdout += data);
  child.on('error', reject);
  child.on('exit', code => resolve({code, stdout}));
});
const withoutProduct = artifacts => Object.fromEntries(Object.entries(artifacts).filter(([name]) => !name.startsWith('polyglottooltip-')));
const stop = async (launch, destination) => {
  const bridge = await Bridge.connect(launch.port);
  try { await bridge.call('quit', {}, 10000); } finally { bridge.close(); }
  for (let attempt = 0; attempt < 120; attempt++) {
    const processes = await run('tasklist', ['/FI', `PID eq ${launch.pid}`, '/FO', 'CSV', '/NH'], true);
    if (!processes.stdout.includes(`"${launch.pid}"`)) break;
    if (attempt === 119) throw new Error(`Owned client has not exited: ${launch.pid}`);
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  await fs.mkdir(destination, {recursive: true});
  await fs.copyFile(path.join(launch.fixture, 'logs', 'latest.log'), path.join(destination, 'client.log'));
};
for (const fixture of cases) {
  const pair = {};
  for (const baseline of [true, false]) {
    const kind = baseline ? 'baseline' : 'updated';
    const destination = path.join(output, fixture.name, kind);
    console.log(`Launching ${fixture.name} ${kind}`);
    const launched = await run('python', [path.join(directory, 'launch.py'), ...fixture.args, '--name', fixture.name, '--port', String(fixture.port), ...(baseline ? ['--baseline'] : fixture.product ? ['--product',fixture.product] : [])], true);
    if (launched.code !== 0) throw new Error(`Launch failed: ${fixture.name} ${kind}`);
    const launch = JSON.parse(await fs.readFile(path.join(JSON.parse(launched.stdout).fixture, 'launch.json'), 'utf8'));
    try {
      assert.equal(Object.keys(launch.artifacts).some(name => name.startsWith('polyglottooltip-')), !baseline, 'Wrong baseline/product artifact lane');
      const suite = await run(process.execPath, [path.join(directory, 'suite.mjs'), String(fixture.port), launch.fixture, destination,
        baseline ? '' : path.join(output, fixture.name, 'baseline'), ...(fixture.suites ? [fixture.suites.join(',')] : [])]);
      const summary = JSON.parse(await fs.readFile(path.join(destination, 'summary.json'), 'utf8'));
      pair[kind] = summary.launch;
      results.push({name: fixture.name, kind, code: suite.code, results: summary.results});
      await fs.writeFile(path.join(output, 'matrix.json'), JSON.stringify(results, null, 2));
      if (suite.code !== 0) throw new Error(`Suite failed: ${fixture.name} ${kind}`);
    } finally {
      await stop(launch, destination);
    }
    if (fixture.carbonRestart) {
      if (!fixture.suites?.includes('integration-carbon')) throw new Error('Carbon restart requires native edit suite');
      const restarted = await run('python', [path.join(directory, 'launch.py'), ...fixture.args, '--name', fixture.name, '--port', String(fixture.port), ...(baseline ? ['--baseline'] : fixture.product ? ['--product', fixture.product] : [])], true);
      if (restarted.code !== 0) throw new Error(`Restart failed: ${fixture.name} ${kind}`);
      const restart = JSON.parse(await fs.readFile(path.join(JSON.parse(restarted.stdout).fixture, 'launch.json'), 'utf8')), restartDestination = path.join(destination, 'restart');
      try {
        assert.deepEqual(restart.artifacts, launch.artifacts); assert.deepEqual(restart.libraries, launch.libraries);
        const suite = await run(process.execPath, [path.join(directory, 'suite.mjs'), String(fixture.port), restart.fixture, restartDestination, '', 'integration-carbon-restart', path.join(destination, 'integration-carbon.json')]);
        const summary = JSON.parse(await fs.readFile(path.join(restartDestination, 'summary.json'), 'utf8'));
        results.push({name: fixture.name, kind: kind + '-restart', code: suite.code, results: summary.results});
        await fs.writeFile(path.join(output, 'matrix.json'), JSON.stringify(results, null, 2));
        if (suite.code !== 0) throw new Error(`Restart suite failed: ${fixture.name} ${kind}`);
      } finally { await stop(restart, restartDestination); }
    }
  }
  assert.deepEqual(withoutProduct(pair.updated.artifacts), pair.baseline.artifacts, `${fixture.name} non-product artifacts differ`);
  assert.deepEqual(pair.updated.libraries, pair.baseline.libraries, `${fixture.name} libraries differ`);
  await fs.writeFile(path.join(output, fixture.name, 'pair.json'), JSON.stringify({matched: true, baseline: pair.baseline, updated: pair.updated}, null, 2));
}
if (results.some(result => result.code !== 0 || result.results.some(suite => suite.failed))) process.exitCode = 1;
