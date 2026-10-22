import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {readMaterials, groupMaterials} from './microblock-families.mjs';
import {loadFamilyProfile, sha256} from './microblock-family-profile.mjs';

const row = (sampleId, metadata, language, material = 'test:wood[variant=a]') => ({sampleId, metadata, language,
  item: 'microblockcbe:microblock', classification: 'confirmed', nativeTarget: language + metadata,
  nativePrimary: 'primary' + metadata, serialized: 'serialized' + sampleId, resolverStatus: 'unresolved',
  stack: {value: {tag: {value: {mat: {value: material}}}}}});
const withInventory = async (rows, action) => {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'microblock-families-'));
  const file = path.join(directory, 'inventory.jsonl');
  try { await fs.writeFile(file, rows.map(value => JSON.stringify(value)).join('\n')); return await action(file); }
  finally { await fs.unlink(file).catch(() => {}); await fs.rmdir(directory); }
};

test('shape and language multiplication collapses without losing independent expected names', async () => {
  const rows = [];
  for (const metadata of [1, 2, 4, 257, 258, 260, 513, 514, 516, 769, 770, 772])
    for (const language of ['en_us', 'zh_cn']) rows.push(row('id' + metadata, metadata, language));
  await withInventory(rows, async file => {
    const materials = await readMaterials(file);
    assert.equal(materials.length, 1); assert.equal(materials[0].samples.length, 12);
    assert.deepEqual(materials[0].samples[0].expected, {en_us: 'en_us1', zh_cn: 'zh_cn1'});
    const report = groupMaterials(materials, [{material: materials[0].material, stage: 'child-name',
      displayMethod: {owner: 'SharedWood'}, rawMethod: {owner: 'BlockItem'}, metadataMethod: {owner: 'SharedLog'}}]);
    assert.equal(report.materialCount, 1); assert.equal(report.states, 12); assert.equal(report.languageComparisons, 24);
    assert.equal(report.groups.length, 1);
    assert.equal((await readMaterials(file, new Set(['id1'])))[0].samples.length, 11);
  });
});

test('conflicting or duplicate baselines cannot silently inflate or overwrite coverage', async () => {
  await withInventory([row('a', 1, 'en_us'), row('a', 1, 'en_us')], file => assert.rejects(readMaterials(file), /Duplicate/));
  await withInventory([row('a', 1, 'en_us'), row('a', 2, 'zh_cn')], file => assert.rejects(readMaterials(file)));
});

test('missing diagnostics and independent failure stages remain visible', async () => {
  await withInventory([row('a', 1, 'en_us'), row('b', 1, 'en_us', 'test:stone')], async file => {
    const materials = await readMaterials(file);
    assert.throws(() => groupMaterials(materials, []), /coverage/);
    const diagnostics = materials.map((value, index) => ({material: value.material, stage: index ? 'diagnostic-error' : 'state-metadata'}));
    const report = groupMaterials(materials, diagnostics);
    assert.equal(report.groups.length, 2); assert.equal(report.states, 2);
    assert.throws(() => groupMaterials(materials, [diagnostics[0], diagnostics[0]]), /Duplicate/);
  });
});

test('a verification selection is tied to the full frozen family and rejects changed expectations', async () => {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'microblock-profile-'));
  const inventory = path.join(directory, 'inventory.jsonl'), diagnostics = path.join(directory, 'diagnostics.json');
  const previous = path.join(directory, 'previous.json'), manifest = path.join(directory, 'manifest.json');
  try {
    const materials = ['test:first', 'test:second', 'test:unsupported'];
    await fs.writeFile(inventory, materials.flatMap((material, index) => ['en_us', 'zh_cn'].map(language =>
      JSON.stringify(row('id' + index, 1, language, material)))).join('\n'));
    await fs.writeFile(diagnostics, JSON.stringify(materials.map((material, index) => ({material, blockClass: index < 2 ? 'Shared' : 'Other'}))));
    await fs.writeFile(previous, JSON.stringify([{sampleId: 'old'}]));
    const profile = {schema: 'microblock-family-verification-v1',
      inventory: {path: inventory, sha256: await sha256(inventory)}, diagnostics: {path: diagnostics, sha256: await sha256(diagnostics)},
      previous: {path: previous, sha256: await sha256(previous)}, blockClasses: ['Shared'],
      tooltips: ['test:first'], mutation: 'test:second', negativeMaterials: ['test:unsupported']};
    await fs.writeFile(manifest, JSON.stringify(profile));
    const cases = (await readMaterials(inventory)).filter(value => value.material !== 'test:unsupported').flatMap(value => value.samples);
    const selected = await loadFamilyProfile(manifest, cases); assert.equal(selected.materials, 2); assert.equal(selected.states, 2);
    await assert.rejects(loadFamilyProfile(manifest, cases.slice(0, 1)));
    await assert.rejects(loadFamilyProfile(manifest, [{...cases[0], expected: {en_us: 'manufactured'}}, cases[1]]), /frozen/);
    await fs.appendFile(inventory, '\n');
    await assert.rejects(loadFamilyProfile(manifest, cases), /input changed/);
  } finally {
    for (const file of [inventory, diagnostics, previous, manifest]) await fs.unlink(file).catch(() => {});
    await fs.rmdir(directory);
  }
});
