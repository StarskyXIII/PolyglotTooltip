import test from 'node:test';
import assert from 'node:assert/strict';
import {legacyGlyphs, compareSecondaryStyle} from './name-audit-style.mjs';

test('rejects the observed hot-bucket style despite identical visible text', () => {
  const name = 'Distilled Oil (§6Hot§r) Bucket';
  const result = compareSecondaryStyle(name, '§e§o' + name + '§r', '§e§o');
  assert.equal(result.status, 'style-mismatch');
  assert.equal(result.mismatches.find(row => row.expected.character === 'H').actual.flags.length, 0);
  assert.equal(result.mismatches.at(-1).actual.color, 'host');
  assert.equal(compareSecondaryStyle(name, '§e§oDistilled Oil (§6§oHot§e§o) Bucket§r', '§e§o').status, 'style-pass');
});

test('separates reset, explicit white, source flags and ordered base flags', () => {
  assert.deepEqual(legacyGlyphs('§o§e').terminal, {color: 'e', flags: []});
  assert.deepEqual(legacyGlyphs('§e§o').terminal, {color: 'e', flags: ['o']});
  assert.equal(compareSecondaryStyle('§l4§r uses', '§7§l§o4§7§o uses§r', '§7§o').status, 'style-pass');
  assert.equal(compareSecondaryStyle('§fWhite', '§f§oWhite§r', '§e§o').status, 'style-pass');
  assert.equal(compareSecondaryStyle('§fWhite', '§e§oWhite§r', '§e§o').status, 'style-mismatch');
  assert.equal(compareSecondaryStyle('§6Hot§r suffix', '§6Hot§r suffix§r', '').status, 'style-pass');
});

test('preserves spaces, Unicode and literal ampersands and validates row isolation', () => {
  assert.deepEqual(legacyGlyphs(' 甲😀 &a ').glyphs.map(value => value.character), [' ', '甲', '😀', ' ', '&', 'a', ' ']);
  assert.equal(compareSecondaryStyle('A', '§7A', '§7').isolated, false);
  assert.equal(compareSecondaryStyle('A', '§7A§r', '§7').status, 'style-pass');
  assert.equal(compareSecondaryStyle('A', '§7B§r', '§7').status, 'style-text-mismatch');
  assert.equal(legacyGlyphs('§xbroken').valid, false);
  assert.equal(legacyGlyphs('broken§').valid, false);
  assert.equal(compareSecondaryStyle('A', 'A§r', 'prefix').status, 'style-unavailable');
});
