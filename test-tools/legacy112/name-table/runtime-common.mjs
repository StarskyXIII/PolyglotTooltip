import fs from 'node:fs/promises';
import {createReadStream} from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {spawn} from 'node:child_process';
import assert from 'node:assert/strict';

export const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
export const report = path.join(root, 'docs/research/legacy112/name-table-prototype-2026-10-14');
export const corpus = path.join(report, 'corpus-final');
export const read = async file => JSON.parse(await fs.readFile(file, 'utf8'));
export const sha = bytes => createHash('sha256').update(bytes).digest('hex');
export const hashFile = async file => {
  const hash = createHash('sha256'); for await (const chunk of createReadStream(file)) hash.update(chunk); return hash.digest('hex');
};
export const save = async (file, value) => fs.writeFile(file, JSON.stringify(value, null, 2) + '\n', {flag: 'wx'});
export const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
export const normalized = value => path.resolve(value).toLowerCase();
export function sameOwned(actual, config, owner) {
  assert.equal(actual.fixtureNonce, config.nonce, 'Unexpected fixture nonce');
  assert.equal(normalized(actual.gameDirectory), normalized(config.fixture), 'Unexpected game directory');
  // The Cleanroom relauncher runs the game in a child JVM, so the PID the runner recorded can be the launcher's:
  // the per-run nonce and the game directory carry the ownership proof, and the PID must agree only when no
  // nonce was issued.
  if (!config.nonce) assert.equal(String(actual.pid), String(owner.pid), 'Unexpected client PID');
}
export async function inventory(folder) {
  const files = [];
  const visit = async (directory, prefix) => {
    let entries;
    try { entries = await fs.readdir(directory, {withFileTypes: true}); }
    catch (error) { if (error.code === 'ENOENT' && !prefix) return false; throw error; }
    for (const entry of entries.sort((a, b) => a.name.localeCompare(b.name, 'en'))) {
      assert.ok(!entry.isSymbolicLink(), 'Symbolic input inventory is unsupported');
      const relative = prefix + entry.name;
      if (entry.isDirectory()) await visit(path.join(directory, entry.name), relative + '/');
      else if (entry.isFile()) files.push(relative);
      assert.ok(files.length <= 50000, 'Input inventory limit');
    }
    return true;
  };
  const exists = await visit(folder, ''); return {exists, files: files.sort()};
}
const sourcePath = value => fileURLToPath(value.startsWith('jar:') ? value.slice(4).split('!/')[0] : value);
export async function environment(config, runtime, launch) {
  const files = new Map(), roots = [], prelaunch = new Map(launch.prelaunchModFiles.map(row => [normalized(row.path), row.sha256]));
  const add = async (file, role, required = true) => {
    file = path.resolve(file); const key = normalized(file);
    let stat;
    try { stat = await fs.stat(file); }
    catch (error) { if (!required && error.code === 'ENOENT') return false; throw error; }
    if (!stat.isFile()) { if (!required) return false; throw new Error('Not a file: ' + file); }
    if (!files.has(key)) files.set(key, {path: file, role, sha256: await hashFile(file), bytes: stat.size});
    return true;
  };
  for (const role of ['mods', 'config', 'scripts', 'resources', 'resourcepacks']) {
    const folder = path.join(config.fixture, role), list = await inventory(folder);
    roots.push({role, path: folder, ...list});
    for (const relative of list.files) await add(path.join(folder, relative), 'input:' + role + '/' + relative);
  }
  const modSources = [];
  for (const mod of runtime.mods) {
    let effective = mod.source, fallback = false;
    if (!await add(effective, 'mod:' + mod.id, false)) {
      assert.ok(mod.implementationSource, 'No attested source for ' + mod.id);
      effective = sourcePath(mod.implementationSource); await add(effective, 'mod-implementation:' + mod.id); fallback = true;
    }
    const expected = prelaunch.get(normalized(effective));
    if (expected) assert.equal(files.get(normalized(effective)).sha256, expected, 'Mod changed since launch: ' + mod.id);
    modSources.push({id: mod.id, source: path.resolve(effective), implementationFallback: fallback});
  }
  for (const [name, origin] of Object.entries(runtime.origins)) {
    if (origin.absent) continue;
    assert.ok(origin.source && !origin.error, 'Unattested runtime origin: ' + name);
    await add(sourcePath(origin.source), 'origin:' + name);
  }
  for (const entry of runtime['java.class.path'].split(path.delimiter)) {
    if (await add(entry, 'runtime-classpath', false)) continue;
    const list = await inventory(entry);
    assert.ok(list.exists, 'Missing classpath directory');
    for (const relative of list.files) await add(path.join(entry, relative), 'runtime-classpath-directory/' + relative);
  }
  await add(path.join(runtime['java.home'], 'bin/java.exe'), 'java-executable');
  const bridge = runtime.mods.find(mod => mod.id === 'polyglotdevbridge');
  assert.equal(files.get(normalized(bridge.source)).sha256, launch.bridgeSha256, 'Wrong bridge artifact');
  const product = runtime.mods.find(mod => mod.id === 'polyglottooltip');
  assert.equal(files.get(normalized(product.source)).sha256, launch.productSha256, 'Wrong product artifact');
  return {schema: 'native-name-table-runtime-environment-v1', runtimeAttested: true,
    primaryLanguage: 'zh_tw', targetLanguages: ['en_us', 'zh_cn'], javaVersion: runtime['java.runtime.version'], javaVendor: runtime['java.vendor'], forge: runtime.forge,
    mods: runtime.mods.map(({id, version}) => ({id, version})).sort((a, b) => a.id < b.id ? -1 : a.id > b.id ? 1 : 0),
    modSources: modSources.sort((a, b) => a.id < b.id ? -1 : a.id > b.id ? 1 : 0), resourcePacks: runtime.resourcePacks,
    roots, files: [...files.values()].sort((a, b) => a.path < b.path ? -1 : a.path > b.path ? 1 : 0)};
}

export function environmentChanges(before, after) {
  const changes = [], old = new Map(before.files.map(row => [normalized(row.path), row.sha256])), next = new Map(after.files.map(row => [normalized(row.path), row.sha256]));
  for (const [file, digest] of old) if (next.get(file) !== digest) changes.push({path: file, kind: next.has(file) ? 'changed' : 'removed'});
  for (const file of next.keys()) if (!old.has(file)) changes.push({path: file, kind: 'added'});
  for (const field of ['javaVersion', 'javaVendor', 'forge', 'mods', 'modSources', 'resourcePacks', 'roots'])
    if (JSON.stringify(before[field]) !== JSON.stringify(after[field])) changes.push({field, kind: 'descriptor-changed'});
  return changes;
}

export async function buildTable(observations, table, metrics, log) {
  const runtime = await read(path.join(report, 'java8-runtime-v2.json'));
  const classpath = runtime.classes + path.delimiter + runtime.gson;
  const output = await fs.open(log, 'wx');
  try {
    await new Promise((resolve, reject) => {
      const child = spawn('C:/Program Files/Zulu/zulu-25/bin/java.exe', ['-Xms128m', '-Xmx1536m', '-cp', classpath,
        'com.starskyxiii.polyglottooltip.testbridge.nametable.NameTableTool', 'build', observations, table, metrics],
      {cwd: root, windowsHide: true, stdio: ['ignore', output.fd, output.fd]});
      child.once('error', reject); child.once('exit', code => code === 0 ? resolve() : reject(new Error('Table builder exit ' + code)));
    });
  } finally { await output.close(); }
  return read(metrics);
}

export function rejection(row) {
  if (!row || row.error) return 'native-callback-error';
  if (row.batchContextChanged || row.contextChanged || row.language !== row.languageAfter) return 'acquisition-context-changed';
  if (!row.inputMatches || !row.metadataMatches || !row.damageMatches || !row.countMatches) return 'native-input-roundtrip-mismatch';
  if (row.stateChanged) return 'observed-stack-mutation';
  if (typeof row.name !== 'string' || !row.name.trim()) return 'empty-native-name';
  return null;
}
export async function writeTraining(run, samples, observations, environmentDigest) {
  const expectedIds = samples.map(row => row.sampleId).sort();
  assert.equal(new Set(expectedIds).size, expectedIds.length, 'Duplicate selected sample ID');
  assert.deepEqual(Object.keys(observations).sort(), ['primary-first', 'en_us', 'zh_cn', 'primary-repeat'].sort(), 'Incomplete acquisition phases');
  for (const [phase, rows] of Object.entries(observations)) {
    const expectedLanguage = phase.startsWith('primary-') ? 'zh_tw' : phase;
    assert.equal(rows.length, samples.length, 'Incomplete phase: ' + phase);
    assert.deepEqual(rows.map(row => row.sampleId).sort(), expectedIds, 'Duplicate, missing or extra phase ID: ' + phase);
    assert.ok(rows.every(row => row.language === expectedLanguage), 'Wrong acquisition language: ' + phase);
  }
  const byPhase = Object.fromEntries(Object.entries(observations).map(([phase, rows]) => [phase, new Map(rows.map(row => [row.sampleId, row]))]));
  const lines = [{environment: environmentDigest, primaryLanguage: 'zh_tw', languages: ['en_us', 'zh_cn'], historicalOnly: false}], reasons = {}, details = [];
  for (const sample of samples) {
    const primary = byPhase['primary-first'].get(sample.sampleId), repeated = byPhase['primary-repeat'].get(sample.sampleId);
    const common = rejection(primary) ?? rejection(repeated) ?? (primary.name !== repeated.name ? 'primary-language-order-instability' : null);
    const targets = {};
    for (const language of ['en_us', 'zh_cn']) {
      const target = byPhase[language].get(sample.sampleId), reason = common ?? rejection(target);
      targets[language] = {name: target?.name ?? null, ...(reason ? {rejection: reason} : {})};
      if (reason) { reasons[reason] = (reasons[reason] ?? 0) + 1; details.push({sampleId: sample.sampleId, item: sample.item, language, reason}); }
    }
    lines.push({state: Object.fromEntries(['item', 'metadata', 'damage', 'count', 'stack'].map(key => [key, sample[key]])), primary: primary?.name ?? null, targets});
  }
  const file = path.join(run, 'training-observations.jsonl');
  await fs.writeFile(file, lines.map(value => JSON.stringify(value)).join('\n') + '\n', {flag: 'wx'});
  await save(path.join(run, 'acquisition-admission.json'), {states: samples.length, selectedPairs: samples.length * 2, rejectedByReason: reasons, rejections: details});
  return file;
}
