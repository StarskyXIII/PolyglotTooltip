import fs from 'node:fs/promises';
import path from 'node:path';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {Bridge} from '../name-audit/runner/bridge.mjs';

const [port, fixturePath, outputPath, baselinePath, requestedSuites, sourcePath] = process.argv.slice(2);
const fixture = path.resolve(fixturePath), output = path.resolve(outputPath);
const directory = path.dirname(fileURLToPath(import.meta.url));
const launch = JSON.parse(await fs.readFile(path.join(fixture, 'launch.json'), 'utf8'));
if (launch.port !== Number(port)) throw new Error('Fixture port mismatch');
let runtime;
for (let attempt = 0; attempt < 90; attempt++) {
  let bridge;
  try {
    bridge = await Bridge.connect(Number(port));
    runtime = await bridge.call('status', {}, 10000);
    break;
  } catch (error) {
    if (attempt === 89) throw error;
    await new Promise(resolve => setTimeout(resolve, 2000));
  } finally { bridge?.close(); }
}
if (runtime.pid !== String(launch.pid) || path.resolve(runtime.gameDirectory).toLowerCase() !== fixture.toLowerCase()) throw new Error('Fixture identity mismatch');
if (runtime.mods.some(mod => mod.id === 'polyglottooltip') !== Object.keys(launch.artifacts).some(name => name.startsWith('polyglottooltip-'))) throw new Error('Product presence differs from launch artifacts');
await fs.mkdir(output, {recursive: true});
const results = [];
const available = ['core', 'ae-repo', 'recipe', 'ae-world', 'ae-interface', 'ae-fluid', 'mek', 'addon-names', 'addon-widgets', 'addon-repo', 'addon-fc', 'addon-search', 'addon-world', 'addon-inherited', 'integration-names', 'integration-widgets', 'integration-controls', 'integration-carbon', 'integration-pe', 'integration-it', 'integration-language', 'integration-hud', 'integration-carbon-restart', 'candidate-names', 'candidate-refined', 'candidate-emi-profile', 'candidate-emi', 'candidate-emi-races', 'candidate-emi-off', 'candidate-resources'];
available.push('candidate-emi-constructor','candidate-resource-profile','candidate-resource-heads','candidate-jemi');
const suites = requestedSuites ? requestedSuites.split(',') : ['ae-repo', 'recipe', 'ae-world', 'ae-interface', 'ae-fluid', ...(runtime.mods.some(mod => mod.id === 'mekanism') ? ['mek'] : [])];
if (!suites.length || suites.some(name => !available.includes(name)) || new Set(suites).size !== suites.length) throw new Error('Invalid suite selection');
for (const name of suites) {
  const resultFile = path.join(output, `${name}.json`);
  const args = [path.join(directory, `${name}.mjs`), port, fixture, resultFile];
  if (name === 'integration-carbon-restart') { if (!sourcePath) throw new Error('Carbon restart requires prior results'); args.push('', path.resolve(sourcePath)); }
  else if ((name.startsWith('integration-') || name.startsWith('candidate-') || ['core', 'mek', 'ae-fluid', 'addon-names', 'addon-widgets', 'addon-repo', 'addon-fc', 'addon-search', 'addon-world', 'addon-inherited'].includes(name)) && baselinePath) args.push(path.join(path.resolve(baselinePath), `${name}.json`));
  console.log(`Running ${name} for ${launch.profile}`);
  const code = await new Promise((resolve, reject) => {
    const child = spawn(process.execPath, args, {stdio: 'inherit', windowsHide: true});
    child.on('error', reject);
    child.on('exit', resolve);
  });
  const result = JSON.parse(await fs.readFile(resultFile, 'utf8'));
  results.push({name, code, passed: result.checks.filter(check => check.pass).length, failed: result.checks.filter(check => !check.pass).length, resultFile});
}
if (suites.includes('ae-fluid') && runtime.mods.some(mod => mod.id === 'polyglottooltip') && results.every(result => result.code === 0)) {
  const code = await new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [path.join(directory, 'capture-fluid.mjs'), port, fixture, output], {stdio: 'inherit', windowsHide: true});
    child.on('error', reject);
    child.on('exit', resolve);
  });
  results.push({name: 'fluid-render', code, passed: code === 0 ? 1 : 0, failed: code === 0 ? 0 : 1, resultFile: path.join(output, 'fluid-render.json')});
}
await fs.writeFile(path.join(output, 'summary.json'), JSON.stringify({launch, results}, null, 2));
console.log(JSON.stringify(results, null, 2));
if (results.some(result => result.code !== 0 || result.failed)) process.exitCode = 1;
