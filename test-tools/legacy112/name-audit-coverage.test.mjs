import test from 'node:test';
import assert from 'node:assert/strict';
import {nativeCoverage} from './name-audit-coverage.mjs';
import path from 'node:path';
import {pathToFileURL} from 'node:url';

const aaFile = path.resolve('fixture-actuallyadditions.jar'), tinkerFile = path.resolve('fixture-tconstruct.jar');
const evidence = [{id: 'actuallyadditions', file: {path: aaFile, sha256: '21311e401f36a58222a50b6f0d66ac29614d7ab039c961dcc307fe499da030b6'}},
  {id: 'tconstruct', file: {path: tinkerFile, sha256: '80ea6b4fe648234477d74481ade97467b68634a288c256a3dea4a624562a0c0c'}}];
const inspect = data => nativeCoverage(data.corpus, [data.rows, data.rows], [data.details, data.details], [evidence, evidence]);

function fixture(energy = metadata => metadata % 2 ? '250000' : '0') {
  const samples = Array.from({length: 32}, (_, index) => ({id: String(index), item: 'actuallyadditions:item_drill', metadata: Math.floor(index / 2),
    sources: [{index: 0, sourceUnchanged: true}], stack: {type: 10, value: {tag: {type: 10, value: {Energy: {type: 3, value: energy(index)}}}}}}));
  const rows = samples.map(sample => ({id: sample.id, before: sample.stack, after: sample.stack, tooltip: ['Drill'], created: [], captureFailed: false,
    modifiers: {shift: false, control: false}, literalCustomName: false}));
  const details = samples.map(sample => ({id: sample.id, diagnostics: {class: 'de.ellpeck.actuallyadditions.mod.items.ItemDrill',
    item: sample.item, metadata: sample.metadata, classSource: pathToFileURL(aaFile).href}}));
  return {corpus: {samples, sources: [{kind: 'subitems:itemGroup.search'}]}, rows, details};
}
test('drill coverage requires all metadata and exact typed energy states, not just sample count', () => {
  const good = fixture(), wrong = fixture(() => '0');
  assert.equal(inspect(good).drillMissing.length, 0);
  assert.equal(inspect(wrong).drillMissing.length, 16);
  good.corpus.samples[0].stack.value.tag.value.Energy.type = 4;
  assert.equal(inspect(good).drillMissing.length, 1);
});
test('custom names, native errors and registry-only defaults cannot fill native variant coverage', () => {
  const data = fixture();
  data.rows[0].literalCustomName = true; data.rows[1].error = {stage: 'tooltip'};
  assert.equal(inspect(data).drillMissing.length, 2);
  data.corpus.sources[0].kind = 'registry-default';
  assert.equal(inspect(data).drillMissing.length, 32);
});

test('Cleanroom jar class URLs retain exact container and class-entry identity', () => {
  const data = fixture();
  for (const row of data.details) {
    const diagnostic = row.diagnostics;
    diagnostic.classSource = 'jar:' + diagnostic.classSource + '!/' + diagnostic.class.replaceAll('.', '/') + '.class';
  }
  assert.equal(inspect(data).drillMissing.length, 0);
  data.details[0].diagnostics.classSource = data.details[0].diagnostics.classSource.replace('ItemDrill.class', 'Other.class');
  assert.equal(inspect(data).drillMissing.length, 1);
  data.details[1].diagnostics.classSource = 'jar:' + pathToFileURL(tinkerFile).href + '!/' + data.details[1].diagnostics.class.replaceAll('.', '/') + '.class';
  assert.equal(inspect(data).drillMissing.length, 2);
});

function tinkersFixture() {
  const sources = [], samples = [], details = [];
  const add = (item, concrete, owner, value) => {
    const index = sources.length, id = String(index);
    sources.push({id: index, kind: 'subitems:search', owner: item, returned: 1, matched: 1, serialized: 1});
    samples.push({id, item, metadata: 0, sources: [{index, sourceUnchanged: true}], stack: {type: 10, value: {tag: {type: 10, value}}}});
    details.push({id, diagnostics: {item, metadata: 0, class: concrete, nameOwner: owner, classSource: pathToFileURL(tinkerFile).href}});
  };
  const part = 'slimeknights.tconstruct.library.tools.ToolPart', tool = 'slimeknights.tconstruct.library.tools.ToolCore';
  for (const material of ['wood', 'stone', 'string']) add('tconstruct:pick_head', part, part, {Material: {type: 8, value: material}});
  const materials = values => ({TinkerData: {type: 10, value: {Materials: {type: 9, elementType: 8, value: values.map(value => ({type: 8, value}))}}}});
  add('tconstruct:pickaxe', 'slimeknights.tconstruct.tools.tools.Pickaxe', tool, materials(['wood', 'stone', 'wood']));
  add('tconstruct:hammer', 'slimeknights.tconstruct.tools.tools.Hammer', tool, materials(['wood', 'stone', 'wood', 'stone']));
  add('tconstruct:longbow', 'slimeknights.tconstruct.tools.ranged.item.LongBow', tool, materials(['wood', 'stone', 'wood', 'string']));
  const rows = samples.map(sample => ({id: sample.id, before: sample.stack, after: sample.stack, tooltip: ['Native name'], created: [],
    captureFailed: false, modifiers: {shift: false, control: false}, literalCustomName: false}));
  return {corpus: {samples, sources}, rows, details};
}

test('Tinkers native parts witness IDs and exact repair positions control tool name material diversity', () => {
  const data = tinkersFixture(), result = inspect(data);
  assert.equal(result.minimumMaterialCoverage, true);
  assert.deepEqual(result.materialTools.map(row => row.selectedNameMaterials), [['stone'], ['stone', 'wood'], ['wood', 'stone']]);
  assert.deepEqual(result.multipleNameMaterialsInOneTool, ['4', '5']);
  assert.equal(result.sharpeningKitCovered, false);
  assert.equal(result.ordinaryPartCovered, true);
});

test('observed coverage admits matching native capability additions without changing material or source checks', () => {
  const data = tinkersFixture();
  for (const row of data.rows) row.after = {type: 10, value: {...row.before.value, ForgeCaps: {type: 10, value: {cap: {type: 3, value: '1'}}}}};
  assert.equal(inspect(data).minimumMaterialCoverage, false);
  const options = {matchingNativeMutations: data.rows.map(row => row.id)};
  const observed = () => nativeCoverage(data.corpus, [data.rows, data.rows], [data.details, data.details], [evidence, evidence], options);
  assert.equal(observed().minimumMaterialCoverage, true);
  data.rows[3].after = structuredClone(data.rows[3].after);
  data.rows[3].after.value.tag.value.TinkerData.value.Materials.value[1].value = 'changed';
  assert.equal(observed().materialTools.some(row => row.id === '3'), false);
  data.rows[4].before = data.rows[4].after;
  assert.equal(observed().materialTools.some(row => row.id === '4'), false);
});

test('Tinkers coverage rejects unnamed source guesses, unrelated class sources, local names and inconsistent providers', () => {
  for (const mutate of [
    data => data.corpus.sources.forEach(row => row.kind = 'ingredient-registry'),
    data => data.corpus.sources.forEach(row => row.owner = 'tconstruct:unrelated'),
    data => data.corpus.sources.forEach(row => row.error = {stage: 'enumeration'}),
    data => data.corpus.sources.forEach(row => row.serialized = 2),
    data => data.details.forEach(row => row.diagnostics.classSource = pathToFileURL(aaFile).href),
    data => data.details.forEach(row => row.diagnostics.class += 'AddonSubclass'),
    data => data.corpus.samples.forEach(row => row.stack.value.tag.value.display = {type: 10, value: {LocName: {type: 8, value: 'item.other'}}})
  ]) {
    const data = tinkersFixture(); mutate(data);
    assert.equal(inspect(data).minimumMaterialCoverage, false);
  }
  const data = tinkersFixture();
  data.corpus.samples[3].stack.value.tag.value.TinkerData.value.Materials.value[0].value = 'unregistered';
  assert.deepEqual(inspect(data).unprovenMaterialTools.map(row => row.id), ['3']);
  data.corpus.samples[4].stack.value.tag.value.TinkerData.value.Materials.value.pop();
  assert.equal(inspect(data).materialTools.some(row => row.id === '4'), false);
});
