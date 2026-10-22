import test from 'node:test';
import assert from 'node:assert/strict';
import {canonical, manifest, batches, compareSample} from './name-audit-core.mjs';

const stack = {type: 10, value: {Count: {type: 1, value: '1'}, id: {type: 8, value: 'test:item'}}};
const sample = {id: 'one', item: 'test:item', metadata: 0, stack, sources: [{sourceUnchanged: true}]};
const row = tooltip => ({id: 'one', before: stack, after: stack, tooltip, created: [], modifiers: {shift: false, control: false}, captureFailed: false, literalCustomName: false});
const inserted = {text: '\u00a7eEnglish', rootList: true, tooltipFrames: 1, indices: [1]};
test('root English insertion passes only with identity attribution and exact preserved native body', () => {
  const en = row(['English', 'body']), zh = row(['中文', 'body']);
  const product = {...row(['中文', '\u00a7eEnglish', 'body']), created: [inserted]};
  assert.equal(compareSample(sample, en, zh, product).status, 'pass');
  assert.equal(compareSample(sample, en, zh, {...product, created: [{...inserted, rootList: false}]}).status, 'inconclusive-attribution');
  assert.equal(compareSample(sample, en, zh, {...product, created: [{...inserted, tooltipFrames: 2}]}).status, 'inconclusive-attribution');
  assert.equal(compareSample(sample, en, zh, {...product, tooltip: ['中文', 'body', '\u00a7eEnglish'], created: [{...inserted, indices: [2]}]}).status, 'misplaced-secondary');
  assert.equal(compareSample(sample, en, zh, {...product, tooltip: [...product.tooltip, 'wrong'], created: [inserted, {...inserted, text: 'wrong', indices: [3]}]}).status, 'additional-product-rows');
  assert.equal(compareSample(sample, en, row(['English']), {...row(['English', 'wrong']), created: [{...inserted, text: 'wrong'}]}).status, 'additional-product-rows');
  assert.equal(compareSample(sample, en, zh, {...product, tooltip: ['中文', '\u00a7eEnglish', 'changed']}).status, 'body-changed');
  assert.equal(compareSample(sample, en, zh, {...product, before: {...stack, extra: 1}}).status, 'replay-mismatch');
  assert.equal(compareSample(sample, en, zh, {...product, after: {...stack, extra: 1}}).status, 'tooltip-mutated');
  assert.equal(compareSample(sample, {...en, error: {stage: 'tooltip'}}, zh, product).status, 'native-invalid');
  assert.equal(compareSample(sample, en, {...zh, after: {...stack, extra: 1}}, {...product, error: {stage: 'tooltip'}}).status, 'tooltip-mutated');
  assert.equal(compareSample(sample, en, zh, undefined).status, 'infrastructure-invalid');
  assert.equal(compareSample(sample, en, zh, {...product, modifiers: undefined}).status, 'infrastructure-invalid');
  assert.equal(compareSample(sample, en, zh, {observationAttempted: false, error: {stage: 'transport-limit'}}).status, 'coverage-limit');
});
test('native English footer never substitutes for an attributed secondary name', () => {
  const en = row(['English']), zh = row(['中文', 'English']);
  assert.equal(compareSample(sample, en, zh, zh).status, 'missing-secondary');
  const product = {...row(['中文', '\u00a7eEnglish', 'English']), created: [inserted]};
  assert.equal(compareSample(sample, en, zh, product).status, 'pass');
  assert.equal(compareSample(sample, en, zh, {...product, tooltip: ['中文', '\u00a7eEnglish']}).status, 'body-changed');
  assert.equal(compareSample(sample, en, zh, {...product, created: []}).status, 'missing-secondary');
  const styled = row(['中文', '\u00a7eEnglish']);
  assert.equal(compareSample(sample, en, styled, styled).status, 'missing-secondary');
  assert.equal(compareSample(sample, en, styled, {...row(['中文', '\u00a7eEnglish', '\u00a7eEnglish']), created: [inserted]}).status, 'pass');
  assert.equal(compareSample(sample, en, row(['English']), row(['English'])).status, 'no-secondary-needed');
  assert.equal(compareSample(sample, en, row(['English ']), row(['English '])).status, 'missing-secondary');
  assert.equal(compareSample(sample, en, {...zh, literalCustomName: true}, zh).status, 'no-secondary-needed');
});

test('matching native mutations permit separate name analysis while every other quality gate remains strict', () => {
  const after = {type: 10, value: {...stack.value, ForgeCaps: {type: 10, value: {cap: {type: 3, value: '1'}}}}};
  const en = {...row(['English', 'body']), after}, zh = {...row(['中文', 'body']), after};
  const product = {...row(['中文', '\u00a7eEnglish', 'body']), after, created: [inserted]};
  const options = {allowMatchingNativeMutation: true};
  assert.equal(compareSample(sample, en, zh, product).status, 'tooltip-mutated');
  const accepted = compareSample(sample, en, zh, product, options);
  assert.equal(accepted.status, 'pass'); assert.equal(accepted.nativeMutation.sameAfterStateAcrossLanes, true);
  assert.equal(compareSample(sample, en, zh, {...product, after: stack}, options).status, 'tooltip-mutated');
  assert.equal(compareSample(sample, en, zh, {...product, before: after}, options).status, 'replay-mismatch');
  assert.equal(compareSample(sample, en, {...zh, created: [inserted]}, product, options).status, 'native-observer-contamination');
  assert.equal(compareSample(sample, en, zh, {...product, captureFailed: true}, options).status, 'observer-error');
  assert.equal(compareSample(sample, en, zh, {...product, modifiers: {shift: true, control: false}}, options).status, 'modifier-drift');
  assert.equal(compareSample(sample, en, zh, {...product, tooltip: ['中文', '\u00a7eEnglish', 'changed']}, options).status, 'body-changed');
  assert.equal(compareSample(sample, en, zh, {...product, created: [{...inserted, rootList: false}]}, options).status, 'inconclusive-attribution');
  assert.equal(compareSample(sample, en, zh, {...product, tooltip: ['中文', 'body'], created: []}, options).status, 'missing-secondary');
});
test('full typed identity keeps numeric types and ordered lists while ignoring compound key order', () => {
  assert.equal(canonical({b: 1, a: 2}), canonical({a: 2, b: 1}));
  const entry = {source: 0, stack, sourceBefore: stack, sourceAfter: stack, item: 'test:item', serialized: 'AA==', originalCount: 1};
  const data = manifest({samples: [entry, entry, {...entry, stack: {type: 10, value: {...stack.value, Count: {type: 3, value: '1'}}}}]});
  assert.equal(data.samples.length, 2); assert.equal(data.samples[0].sources.length, 2);
});
test('wire batching bounds full envelope and never sends typed NBT or silently drops oversized rows', () => {
  const samples = Array.from({length: 80}, (_, id) => ({id: String(id), serialized: 'a'.repeat(50000), stack}));
  samples.push({id: 'oversized', serialized: 'a'.repeat(900000)});
  const result = batches(samples, 'audit.observe');
  assert.equal(result.skipped.length, 1); assert.equal(result.skipped[0].observationAttempted, false);
  assert.equal(result.batches.flat().length, 80);
  for (const batch of result.batches) {
    assert.ok(batch.length <= 32);
    assert.ok(Buffer.byteLength(JSON.stringify({jsonrpc: '2.0', id: Number.MAX_SAFE_INTEGER, method: 'audit.observe', params: {samples: batch}})) <= 900000);
    assert.deepEqual(Object.keys(batch[0]), ['id', 'serialized']);
  }
});

test('advanced names use natural observations without dropping the decorated native title', () => {
  const observed = (name, title, body = []) => ({...row([title, ...body]), tooltipFlag: 'ADVANCED', nameObservation: {
    nativeNameCaptured: true, nativeName: name, initialTitleCaptured: true, initialTitle: title, rootList: true}});
  const en = observed('English', 'English (#1234/1)', ['test:item', 'body']);
  const zh = observed('中文', '中文 (#1234/1)', ['test:item', 'body']);
  const product = {...zh, tooltip: [zh.tooltip[0], inserted.text, ...zh.tooltip.slice(1)], created: [inserted]};
  const result = compareSample(sample, en, zh, product);
  assert.equal(result.status, 'pass'); assert.equal(result.english, 'English');
  assert.equal(result.tooltipFlag, 'ADVANCED'); assert.equal(result.nameOracle, 'natural-display-name');
  assert.equal(compareSample(sample, {...en, tooltipFlag: 'NORMAL'}, zh, product).reason, 'tooltip-flag-drift');
  assert.equal(compareSample(sample, en, zh, {...product, nameObservation: undefined}).reason, 'missing-name-observation');
  assert.equal(compareSample(sample, {...en, nameObservation: {...en.nameObservation, rootList: false}}, zh, product).reason, 'invalid-name-observation');
  assert.equal(compareSample(sample, en, {...zh, tooltip: ['Replacement', 'body']}, product).status, 'native-title-replaced');
  assert.equal(compareSample(sample, en, zh, {...product, tooltip: ['中文', inserted.text, ...zh.tooltip.slice(1)]}).status, 'primary-changed');
  const literal = observed('Literal (#9)', 'Literal (#9) (#1234/1)');
  assert.equal(compareSample(sample, literal, literal, literal).english, 'Literal (#9)');
});
