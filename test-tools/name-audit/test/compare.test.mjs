import {test} from 'node:test';
import assert from 'node:assert/strict';
import {canonical, canonicalStack, digest, freezeSamples, validateManifest, compareSample, compareRun, exitCode} from '../lib/compare.mjs';

function fixture(en = 'Zombie Charm', zh = '僵尸护符', secondary = en) {
  const stack = {id: 'reliquary:mob_charm', count: 1, components: {entity: 'minecraft:zombie'}};
  const sample = {id: digest(stack), item: stack.id, stack, sources: ['creative:test']};
  const row = text => ({id: sample.id, before: structuredClone(stack), after: structuredClone(stack), tooltip: [{text}], hoverName: {text}, literalCustomName: false, missingTitleKeys: [], created: []});
  const english = row(en), primary = row(zh), actual = row(zh);
  if (secondary !== null) { actual.tooltip.push({text: secondary}); actual.created.push({text: secondary, indices: [1]}); }
  return {sample, english, primary, actual};
}
const check = f => compareSample(f.sample, f.english, f.primary, f.actual);

test('full native title, including Geas suffix, is the oracle', () => {
  const f = fixture('Geas of the Reaper', '收割者之誓');
  f.english.hoverName.text = 'Geas';
  assert.equal(check(f).status, 'pass');
  f.actual.created[0].text = f.actual.tooltip[1].text = 'Geas';
  assert(check(f).issues.includes('secondary-text-mismatch'));
});
test('partial translation and swapped variants fail', () => {
  for (const wrong of ['僵尸 Charm', 'Skeleton Charm', 'zombie charm']) assert.equal(check(fixture(undefined, undefined, wrong)).status, 'mismatch');
});
test('native English line cannot masquerade as insertion', () => {
  const f = fixture(undefined, undefined, null);
  f.primary.tooltip.push({text: 'Zombie Charm'}); f.actual.tooltip.push({text: 'Zombie Charm'});
  assert(check(f).issues.includes('secondary-missing'));
});
test('created but removed or replaced is inconclusive', () => {
  const f = fixture(); f.actual.created[0].indices = [];
  assert.equal(check(f).status, 'inconclusive');
});
test('duplicate and misplaced product lines fail', () => {
  const f = fixture(); f.actual.created.push(structuredClone(f.actual.created[0]));
  assert(check(f).issues.includes('secondary-duplicated'));
  const g = fixture(); g.primary.tooltip.push({text: 'body'}); g.actual.tooltip.splice(1, 0, {text: 'body'}); g.actual.created[0].indices = [2];
  assert(check(g).issues.includes('secondary-position'));
});
test('same title and literal custom names suppress secondary', () => {
  assert.equal(check(fixture('Same', 'Same', null)).status, 'pass');
  const f = fixture('Custom: Reaper', 'Custom：收割者', null); f.english.literalCustomName = true;
  assert.equal(check(f).status, 'pass');
  assert(check(fixture('Same', 'Same')).issues.includes('unexpected-secondary'));
});
test('translatable custom names use normal language policy', () => assert.equal(check(fixture('Diamond', '钻石')).status, 'pass'));
test('unknown missing translation is inconclusive, explicit fallback control is checked', () => {
  const f = fixture('missing.key', 'missing.key', null); f.english.missingTitleKeys = ['missing.key'];
  assert.equal(check(f).status, 'inconclusive');
  f.sample.sources = ['control:missing']; assert.equal(check(f).status, 'pass');
});
test('primary and native body are preserved', () => {
  const f = fixture(); f.actual.tooltip[0].text = 'wrong';
  assert(check(f).issues.includes('primary-changed'));
  const g = fixture(); g.primary.tooltip.push({text: 'native body'});
  assert(check(g).issues.includes('native-tooltip-changed'));
});
test('only formatting codes are normalized', () => {
  assert.equal(check(fixture('Zombie Charm', '僵尸护符', '§7Zombie Charm')).status, 'pass');
  assert.equal(check(fixture('Zombie Charm', '僵尸护符', 'Zombie Charm ')).status, 'mismatch');
});
test('collector merges sources, frozen identities reject duplicates and empty manifests', () => {
  const f = fixture();
  const samples = freezeSamples({complete: true, samples: [{...f.sample, source: 'creative:a'}, {...f.sample, source: 'registry'}]});
  assert.deepEqual(samples[0].sources, ['creative:a', 'registry']);
  assert.throws(() => validateManifest([], false));
  assert.throws(() => validateManifest([...samples, ...samples], false));
  assert.throws(() => freezeSamples({complete: false, samples: []}));
  assert.equal(canonical({b: 2, a: 1}), canonical({a: 1, b: 2}));
});
test('missing, duplicate, mutated and errored observations fail infrastructure', () => {
  for (const mutate of [o => o['native-en'].pop(), o => o['native-en'].push(o['native-en'][0]), o => {o['polyglot-zh'][0].after.count = 2;}, o => {o['native-zh'][0].error = 'decode failure';}]) {
    const f = fixture(), observations = {'native-en': [f.english], 'native-zh': [f.primary], 'polyglot-zh': [f.actual]};
    mutate(observations);
    assert.throws(() => compareRun([f.sample], observations, {requiredCoverage: false, requireControl: false}));
  }
});
test('strict mode distinguishes findings from infrastructure failure', () => {
  assert.equal(exitCode({infrastructure: 'passed', compatibility: 'findings'}), 0);
  assert.equal(exitCode({infrastructure: 'passed', compatibility: 'findings'}, true), 2);
  assert.equal(exitCode({infrastructure: 'failed'}, false), 1);
});
test('coverage requires exact entity fields and two different populated Geas effects', () => {
  const raw = [];
  const add = (item, components, source = 'creative:test') => raw.push({item, stack: {id: item, count: 1, ...(components ? {components} : {})}, source});
  for (const item of ['mob_charm', 'mob_charm_fragment']) {
    add(`reliquary:${item}`);
    for (const entity of ['zombie', 'skeleton']) add(`reliquary:${item}`, {'reliquary:entity_name': `minecraft:${entity}`});
  }
  add('malum:geas');
  add('malum:geas', {'malum:geas_effect': {geasEffectType: 'malum:first'}});
  for (const control of ['stone', 'literal', 'translated', 'missing', 'literal-geas', 'translated-geas', 'patched-geas']) add('minecraft:stone', {control}, `control:${control}`);
  assert.throws(() => validateManifest(freezeSamples({complete: true, samples: raw})), /Missing Geas/);
  add('malum:geas', {'malum:geas_effect': {geasEffectType: 'malum:second'}});
  validateManifest(freezeSamples({complete: true, samples: raw}));
  const poisoned = structuredClone(raw);
  const zombie = poisoned.find(s => s.item === 'reliquary:mob_charm' && s.stack.components?.['reliquary:entity_name'] === 'minecraft:zombie');
  zombie.stack.components = {unrelated: 'minecraft:zombie'};
  assert.throws(() => validateManifest(freezeSamples({complete: true, samples: poisoned})), /Missing mob_charm zombie/);
});
test('NeoForge potion cures are an unordered set; effect order and membership stay significant', () => {
  const stack = {id: 'reliquary:potion', count: 1, components: {'minecraft:potion_contents': {custom_effects: [
    {id: 'minecraft:mining_fatigue', 'neoforge:cures': ['milk', 'protected_by_totem'], hidden_effect: {'neoforge:cures': ['milk', 'protected_by_totem']}},
    {id: 'minecraft:slowness', 'neoforge:cures': ['milk']}
  ]}}};
  const equivalent = structuredClone(stack);
  equivalent.components['minecraft:potion_contents'].custom_effects[0]['neoforge:cures'].reverse();
  equivalent.components['minecraft:potion_contents'].custom_effects[0].hidden_effect['neoforge:cures'].reverse();
  assert.equal(digest(stack), digest(equivalent));
  assert.notEqual(canonical(stack), canonical(equivalent));
  equivalent.components['minecraft:potion_contents'].custom_effects.reverse();
  assert.notEqual(canonicalStack(stack), canonicalStack(equivalent));
  const changed = structuredClone(stack); changed.components['minecraft:potion_contents'].custom_effects[0]['neoforge:cures'].pop();
  assert.notEqual(digest(stack), digest(changed));
  const duration = structuredClone(stack); duration.components['minecraft:potion_contents'].custom_effects[0].duration = 5;
  assert.notEqual(digest(stack), digest(duration));
  assert.notEqual(digest({id: 'x', components: {unrelated: {'neoforge:cures': ['a', 'b']}}}), digest({id: 'x', components: {unrelated: {'neoforge:cures': ['b', 'a']}}}));
});
test('additional details do not fail name audit; native body remains ordered and complete', () => {
  const f = fixture(); f.primary.tooltip.push({text: 'body one'}, {text: 'body two'});
  f.actual.tooltip.push({text: 'body one'}, {text: 'translated effect'}, {text: 'body two'});
  assert.equal(check(f).status, 'pass');
  assert.deepEqual(check(f).additionalBodyLines, ['translated effect']);
  for (const body of [['body two', 'body one'], ['changed', 'body two'], ['body one']]) {
    const changed = structuredClone(f); changed.actual.tooltip = changed.actual.tooltip.slice(0, 2).concat(body.map(text => ({text})));
    assert(check(changed).issues.includes('native-tooltip-changed'));
  }
});
test('untracked English and primary copies cannot hide among added details', () => {
  for (const text of ['Zombie Charm', '僵尸护符']) {
    const f = fixture(); f.actual.tooltip.push({text});
    assert(check(f).issues.includes('untracked-name-duplicate'));
  }
  const f = fixture('Custom', 'Custom', null); f.english.literalCustomName = true; f.actual.tooltip.push({text: 'Custom'});
  assert(check(f).issues.includes('untracked-name-duplicate'));
  const baseline = fixture(); baseline.primary.tooltip.push({text: 'Zombie Charm'}); baseline.actual.tooltip.push({text: 'Zombie Charm'});
  assert.equal(check(baseline).status, 'pass');
});
