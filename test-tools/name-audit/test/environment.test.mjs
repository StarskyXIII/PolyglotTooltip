import {test} from 'node:test';
import assert from 'node:assert/strict';
import {validateIdentity} from '../runner/environment.mjs';
import {argumentsFor} from '../runner/run.mjs';
test('unknown arguments are rejected before launch', () => {
  assert.throws(() => argumentsFor(['--limit', '10']));
  assert.deepEqual(argumentsFor(['--strict']), {strict: true});
});
test('foreign sessions, directories, language and versions are rejected', () => {
  const profile = {name: 'native-en', session: 'ours', gameDir: 'test/profile', language: 'en_us', artifacts: []};
  const identity = {session: 'ours', gameDir: 'test/profile', language: 'en_us', pid: 123, mods: {minecraft: {version: '1.21.1'}, neoforge: {version: '21.1.249'}}};
  validateIdentity(identity, profile);
  for (const patch of [{session: 'foreign'}, {gameDir: 'elsewhere'}, {language: 'zh_cn'}, {mods: {...identity.mods, polyglottooltip: {version: '2.0'}}}]) assert.throws(() => validateIdentity({...identity, ...patch}, profile));
});
