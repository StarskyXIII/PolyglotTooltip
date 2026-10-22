import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {summarizeExport, readExport} from './name-export-report.mjs';

const manifest = {schema: 'polyglot-name-export-v1', source: 'explicit', languages: ['en_us', 'zh_tw'], mods: [{id: 'a'}, {id: 'unselected'}]};
const row = {kind: 'item', sequence: 0, mod: 'a', item: 'a:item', sampleId: 'identity', count: 1, stack: {type: 10}, serialized: 'nbt', languages: [
  {language: 'en_us', status: 'same-as-native', name: 'Shared name'}, {language: 'zh_tw', status: 'unresolved', reason: 'not-supported'},
]};
const footer = {state: 'COMPLETE', sourceExhausted: true, processed: 1, written: 1};

test('counts per language without treating same native names as confirmed gaps', () => {
  const report = summarizeExport(manifest, footer, [row]);
  assert.equal(report.selectedSourceComplete, true);
  assert.equal(report.candidates.length, 1);
  assert.deepEqual(report.candidates[0].stack, row.stack);
  assert.equal(report.mods.unselected.items, 0);
  assert.equal(report.mods.a.languages.en_us['same-as-native'], 1);
});
test('partial, truncated, source failures and missing languages cannot claim completeness', () => {
  for (const state of ['LIMIT_REACHED', 'CANCELLED', 'INVALIDATED', 'FAILED', 'RUNNING'])
    assert.equal(summarizeExport(manifest, {...footer, state, sourceExhausted: false}, [row]).selectedSourceComplete, false);
  assert.equal(summarizeExport(manifest, {...footer, written: 0}, [row]).integrityValid, false);
  assert.equal(summarizeExport(manifest, footer, [{...row, languages: row.languages.slice(0, 1)}]).integrityValid, false);
  assert.equal(summarizeExport(manifest, footer, [{...row, languages: 'damaged'}]).integrityValid, false);
  assert.equal(summarizeExport(manifest, footer, [{...row, languages: [null]}]).integrityValid, false);
  const warning = {kind: 'source-error', sequence: 0, mod: 'a', error: 'creative callback failed'};
  const report = summarizeExport(manifest, footer, [warning]);
  assert.equal(report.integrityValid, true); assert.equal(report.selectedSourceComplete, false);
  assert.equal(report.warnings.length, 1);
});
test('reader rejects malformed or unterminated JSONL and writer failures', async () => {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'name-export-report-'));
  try {
    await fs.writeFile(path.join(directory, 'manifest.json'), JSON.stringify(manifest));
    await fs.writeFile(path.join(directory, 'summary.json'), JSON.stringify(footer));
    await fs.writeFile(path.join(directory, 'names.jsonl'), JSON.stringify(row) + '\n');
    assert.equal((await readExport(directory)).integrityValid, true);
    await fs.writeFile(path.join(directory, 'names.jsonl'), JSON.stringify(row));
    assert.equal((await readExport(directory)).integrityValid, false);
    await fs.writeFile(path.join(directory, 'names.jsonl'), '{incomplete\n');
    assert.equal((await readExport(directory)).integrityValid, false);
    await fs.writeFile(path.join(directory, 'names.jsonl'), JSON.stringify(row) + '\n');
    await fs.writeFile(path.join(directory, 'failure.json'), '"disk failure"');
    assert.equal((await readExport(directory)).integrityValid, false);
  } finally { await fs.rm(directory, {recursive: true, force: true}); }
});
