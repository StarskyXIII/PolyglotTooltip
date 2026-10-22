import test from 'node:test';
import assert from 'node:assert/strict';
import {assessSample, compareAnnotation, comparePreservation, compareSameCallBody} from './name-audit-acceptance.mjs';

const stack = {type: 10, value: {id: {type: 8, value: 'forge:bucketfilled'}}};
const sample = {id: 'one', item: 'forge:bucketfilled', metadata: 0, stack, sources: [{sourceUnchanged: true}]};
const row = tooltip => ({before: stack, after: stack, tooltip, created: [], modifiers: {shift: false, control: false}, captureFailed: false, literalCustomName: false});
const productRow = text => ({...row(['原文', text, 'footer']), created: [{text, rootList: true, tooltipFrames: 1, indices: [1]}]});

test('the frozen temperature failure remains a text pass while failing the independent style dimension', () => {
  const native = row(['Distilled Oil (§6Hot§r) Bucket', 'footer']);
  const nativeZh = row(['原文', 'footer']);
  const options = {secondaryFormat: '§e§o'};
  const failed = assessSample(sample, native, nativeZh, productRow('§e§oDistilled Oil (§6Hot§r) Bucket§r'), options);
  assert.equal(failed.strict.status, 'pass');
  assert.equal(failed.dimensions.text.status, 'pass');
  assert.equal(failed.dimensions.style.status, 'fail');
  assert.equal(failed.accepted, false);
  const fixed = assessSample(sample, native, nativeZh, productRow('§e§oDistilled Oil (§6§oHot§e§o) Bucket§r'), options);
  assert.equal(fixed.dimensions.style.status, 'pass');
  assert.equal(fixed.dimensions.preservation.status, 'pass');
  assert.equal(fixed.dimensions.lifecycle.status, 'unavailable');
  assert.equal(fixed.dimensions.integration.status, 'unavailable');
  assert.equal(fixed.accepted, false);
});

test('curse annotations inherit the native glyph style and restore its terminal state', () => {
  const original = '§c消失诅咒§r';
  assert.equal(compareAnnotation(original, original + '§7｜Curse of Vanishing', ['§cCurse of Vanishing']).status, 'fail');
  assert.equal(compareAnnotation(original, original + '§c ｜ Curse of Vanishing§r', ['§cCurse of Vanishing']).status, 'pass');
  assert.equal(compareAnnotation(original, original + '§c ｜ Curse of Vanishing', ['§cCurse of Vanishing']).status, 'fail');
  assert.equal(compareAnnotation(original, original + '§7 ｜ §cCurse of Vanishing§r', ['§cCurse of Vanishing']).status, 'fail');
  assert.equal(compareAnnotation('§cA§eB', '§cA§eB§7｜English', ['English']).status, 'unavailable');
});

test('unformatted annotations preserve the renderers gray prefix and require both spaces', () => {
  const original = '§7冰霜行者 II';
  assert.equal(compareAnnotation(original, original + ' ｜ Frost Walker II', ['Frost Walker II']).status, 'pass');
  assert.equal(compareAnnotation(original, original + ' ｜ §rFrost Walker II', ['Frost Walker II']).status, 'fail');
  assert.equal(compareAnnotation(original, original + '｜Frost Walker II', ['Frost Walker II']).status, 'fail');
  assert.equal(compareAnnotation('原文§r', '原文§r ｜ English', ['English']).status, 'fail');
  assert.equal(compareAnnotation('原§r文', '原§r文 ｜ English', ['English']).status, 'unavailable');
  assert.equal(compareAnnotation('§r原文', '§r原文 ｜ English', ['English']).status, 'pass');
});

test('an inline extension never excuses changes to unrelated body rows or unbound lore', () => {
  const raw = '§c消失诅咒';
  const native = ['原文', raw, 'lore'];
  const product = ['原文', raw + ' ｜ Curse of Vanishing', 'lore'];
  const source = {enchantment: 'minecraft:vanishing_curse', level: 1, inserted: true, surviving: true, sourceSha256: '1'.repeat(64)};
  const receipt = {index: 1, native: {...source, line: raw}, targets: [{...source, line: '§cCurse of Vanishing'}]};
  assert.equal(comparePreservation(native, product, [receipt]).status, 'pass');
  assert.equal(comparePreservation(native, [...product.slice(0, 2), 'changed lore'], [receipt]).status, 'fail');
  assert.equal(comparePreservation(native, product).status, 'fail');
  assert.equal(comparePreservation(native, product, [{...receipt, native: {...receipt.native, inserted: false}}]).status, 'unavailable');
  assert.equal(comparePreservation(native, product, [receipt, receipt]).status, 'unavailable');
});

const sameCall = () => ({
  captureFailed: false,
  nativeBodyObservation: {captured: true, rootList: true, rows: ['原文', '§c原附魔', 'same', 'same', ' lore ']},
  tooltip: ['原文', 'English', '§c原附魔§7｜§cNative English', 'same', 'same', ' lore '],
  created: [{rootList: true, tooltipFrames: 1, indices: [1], text: 'English'}],
  nativeEnchantments: [{rootList: true, inserted: true, line: '§c原附魔', annotatedLine: '§c原附魔§7｜§cNative English', annotationIndices: [2]}],
});

test('same-call body restores only identity-bound owned edits and keeps order, duplicates and formatting', () => {
  const product = sameCall(), original = structuredClone(product);
  assert.equal(compareSameCallBody(product).status, 'pass');
  assert.deepEqual(product, original);
  for (const changed of [
    ['原文', 'English', product.tooltip[2], 'same', ' lore ', 'same'],
    ['原文', 'English', product.tooltip[2], 'same', ' lore '],
    ['原文', 'English', product.tooltip[2], 'same', 'same', 'lore'],
    ['changed title', ...product.tooltip.slice(1)],
  ]) assert.equal(compareSameCallBody({...product, tooltip: changed}).status, 'fail');
  const unchanged = {captureFailed: false, nativeBodyObservation: {captured: true, rootList: true, rows: ['Title', 'Body']}, tooltip: ['Title', 'Body'], created: [], nativeEnchantments: []};
  assert.equal(compareSameCallBody(unchanged).status, 'pass');
});

test('same-call undo rejects missing, duplicate, cross-kind and title-index ownership evidence', () => {
  const variants = [
    value => { value.captureFailed = true; },
    value => { delete value.nativeBodyObservation; },
    value => { value.nativeBodyObservation.rootList = false; },
    value => { value.created[0].indices = [1, 3]; },
    value => { value.created[0].indices = [0]; value.created[0].text = value.tooltip[0]; },
    value => { value.created[0].tooltipFrames = 2; },
    value => { value.created.push({...value.created[0]}); },
    value => { value.nativeEnchantments[0].annotationIndices = [1]; value.nativeEnchantments[0].annotatedLine = value.tooltip[1]; },
    value => { value.nativeEnchantments[0].annotationIndices = []; },
    value => { value.nativeEnchantments[0].inserted = false; },
    value => { delete value.nativeEnchantments[0].line; },
    value => { delete value.nativeEnchantments[0].annotatedLine; },
    value => { delete value.created; },
    value => { delete value.nativeEnchantments; },
  ];
  for (const mutate of variants) { const product = sameCall(); mutate(product); assert.equal(compareSameCallBody(product).status, 'unavailable'); }
});

test('same-call body bounds and missing ownership never excuse an unrecorded mutation', () => {
  const product = sameCall();
  assert.equal(compareSameCallBody({...product, created: []}).status, 'fail');
  assert.equal(compareSameCallBody({...product, nativeEnchantments: []}).status, 'fail');
  for (const rows of [[null], ['x'.repeat(8193)], Array(1025).fill('x'), Array(33).fill('x'.repeat(8192))]) {
    assert.equal(compareSameCallBody({...product, nativeBodyObservation: {captured: true, rootList: true, rows}}).status, 'unavailable');
    assert.equal(compareSameCallBody({...product, tooltip: rows}).status, 'unavailable');
  }
});
