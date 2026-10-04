import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {safeChild, hashFile, verify} from '../runner/setup.mjs';
test('paths cannot escape staging directory', () => {
  assert.throws(() => safeChild('stage', '../outside'));
  assert.throws(() => safeChild('stage', '.'));
  assert.equal(safeChild('stage', 'mod.jar'), path.resolve('stage/mod.jar'));
});
test('hash verification rejects altered artifacts', async () => {
  const folder = await fs.mkdtemp(path.join(os.tmpdir(), 'name-audit-test-'));
  try {
    const file = path.join(folder, 'test.jar'); await fs.writeFile(file, 'original');
    const hash = await hashFile(file); await verify(file, hash);
    await fs.writeFile(file, 'altered'); await assert.rejects(verify(file, hash));
  } finally { await fs.rm(folder, {recursive: true}); }
});
