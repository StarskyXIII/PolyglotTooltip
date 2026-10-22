import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import {writeTraining, sameOwned, rejection} from './runtime-common.mjs';

const sample = {sampleId: 'a', item: 'minecraft:stone', metadata: 0, damage: 0, count: 1, stack: {}};
const good = () => Object.fromEntries(['primary-first', 'en_us', 'zh_cn', 'primary-repeat'].map(phase => [phase,
  [{sampleId: 'a', language: phase.startsWith('primary') ? 'zh_tw' : phase,
    languageAfter: phase.startsWith('primary') ? 'zh_tw' : phase, inputMatches: true, metadataMatches: true,
    damageMatches: true, countMatches: true, name: phase.startsWith('primary') ? '石頭' : 'Stone'}]]));
test('ownership fails independently for wrong PID, nonce, or directory', () => {
  const config = {fixture: path.resolve('fixture'), nonce: 'owned'}, owner = {pid: 42};
  const actual = {pid: 42, fixtureNonce: 'owned', gameDirectory: config.fixture};
  sameOwned(actual, config, owner);
  for (const patch of [{pid: 43}, {fixtureNonce: 'other'}, {gameDirectory: path.resolve('other')}])
    assert.throws(() => sameOwned({...actual, ...patch}, config, owner));
});
test('training refuses incomplete, duplicate, or wrong-language phases before writing', async () => {
  const folder = await fs.mkdtemp(path.join(os.tmpdir(), 'pt-name-table-guards-'));
  const cases = [];
  let value = good(); delete value.en_us; cases.push(value);
  value = good(); value.en_us.push(value.en_us[0]); cases.push(value);
  value = good(); value.en_us[0].sampleId = 'unexpected'; cases.push(value);
  value = good(); value.en_us[0].language = 'zh_tw'; cases.push(value);
  for (const observations of cases) await assert.rejects(writeTraining(folder, [sample], observations, 'digest'));
  assert.deepEqual(await fs.readdir(folder), []); await fs.rmdir(folder);
});
test('native instability remains a rejected row, and batch drift is never admitted', async () => {
  const folder = await fs.mkdtemp(path.join(os.tmpdir(), 'pt-name-table-guards-')), observations = good();
  observations['primary-repeat'][0].name = 'different';
  const file = await writeTraining(folder, [sample], observations, 'digest');
  const rows = (await fs.readFile(file, 'utf8')).trim().split('\n').map(JSON.parse);
  assert.equal(rows[1].targets.en_us.rejection, 'primary-language-order-instability');
  assert.equal(rows[1].targets.zh_cn.rejection, 'primary-language-order-instability');
  assert.equal(rejection({...good().en_us[0], batchContextChanged: true}), 'acquisition-context-changed');
  await fs.unlink(file); await fs.unlink(path.join(folder, 'acquisition-admission.json')); await fs.rmdir(folder);
});
