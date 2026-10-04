import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';

export const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const json = async file => JSON.parse(await fs.readFile(file, 'utf8'));
export const writeJson = async (file, value) => fs.writeFile(file, JSON.stringify(value, null, 2) + '\n');
export const hashFile = async file => createHash('sha256').update(await fs.readFile(file)).digest('hex');
export function safeChild(parent, child) {
  const resolved = path.resolve(parent, child), relative = path.relative(path.resolve(parent), resolved);
  assert(relative && !relative.startsWith('..') && !path.isAbsolute(relative), 'Path escapes parent');
  return resolved;
}
export async function verify(file, expected) {
  assert.equal(await hashFile(file), expected, `Artifact hash mismatch: ${path.basename(file)}`);
}
export async function fetchDependencies() {
  const lock = await json(path.join(root, 'dependencies.json'));
  const cache = path.join(root, '.cache');
  await fs.mkdir(cache, {recursive: true});
  for (const artifact of lock.artifacts) {
    const file = safeChild(cache, artifact.file);
    try { await fs.access(file); }
    catch {
      const response = await fetch(artifact.url, {headers: {'User-Agent': 'PolyglotTooltip-name-audit/1.0'}, signal: AbortSignal.timeout(120000)});
      assert(response.ok, `Download failed: ${artifact.id} HTTP ${response.status}`);
      const bytes = Buffer.from(await response.arrayBuffer());
      assert.equal(createHash('sha256').update(bytes).digest('hex'), artifact.sha256, `Download hash mismatch: ${artifact.id}`);
      await fs.writeFile(file, bytes, {flag: 'wx'});
    }
    await verify(file, artifact.sha256);
  }
  const bridge = lock.artifacts.find(a => a.id === 'clientdevbridge');
  await fs.mkdir(path.join(root, 'companion/libs'), {recursive: true});
  await fs.copyFile(path.join(cache, bridge.file), path.join(root, 'companion/libs', bridge.file));
  return lock;
}
export async function prepare() {
  const config = await json(path.join(root, 'local.json'));
  for (const key of ['prismExecutable', 'prismData', 'javaExecutable']) {
    assert(typeof config[key] === 'string' && !/[\r\n"\x00]/.test(config[key]), `Invalid ${key}`);
    config[key] = path.resolve(root, config[key]);
    await fs.access(config[key]);
  }
  assert(Number.isInteger(config.basePort) && config.basePort >= 1024 && config.basePort <= 65533, 'basePort must be 1024..65533');
  const lock = await json(path.join(root, 'dependencies.json'));
  const artifacts = lock.artifacts.map(a => ({...a, source: path.join(root, '.cache', a.file)}));
  for (const artifact of artifacts) await verify(artifact.source, artifact.sha256);
  const companion = path.join(root, 'companion/build/libs/polyglot-name-audit-1.21.1-neoforge-0.1.0.jar');
  const product = path.resolve(root, config.productJar);
  artifacts.push({id: 'polyglotnameaudit', version: '0.1.0', source: companion, file: path.basename(companion), sha256: await hashFile(companion)});
  artifacts.push({id: 'polyglottooltip', version: '2.0', source: product, file: path.basename(product), sha256: await hashFile(product)});
  assert.equal(new Set(artifacts.map(a => a.file)).size, artifacts.length, 'Duplicate artifact filenames');
  return {config, lock, artifacts};
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const command = process.argv[2];
  assert(process.argv.length === 3 && ['fetch', 'prepare'].includes(command), 'Usage: node runner/setup.mjs fetch|prepare');
  const result = command === 'fetch' ? await fetchDependencies() : await prepare();
  console.log(JSON.stringify({status: 'ready', artifacts: result.artifacts.map(a => a.file)}));
}
