import fs from 'node:fs/promises';
import {createReadStream} from 'node:fs';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {readMaterials} from './microblock-families.mjs';

export async function sha256(file) {
  const hash = createHash('sha256');
  for await (const chunk of createReadStream(file)) hash.update(chunk);
  return hash.digest('hex');
}

// A new family supplies a selection manifest, not another hard-coded verifier branch.
// Frozen native comparisons and the runtime dependency census remain independent inputs.
export async function loadFamilyProfile(file, cases) {
  const profile = JSON.parse(await fs.readFile(file, 'utf8'));
  assert.equal(profile.schema, 'microblock-family-verification-v1');
  for (const input of [profile.inventory, profile.diagnostics, profile.previous]) {
    assert.equal(await sha256(input.path), input.sha256, 'Frozen verification input changed: ' + input.path);
  }
  assert.ok(Array.isArray(profile.blockClasses) && profile.blockClasses.length > 0 && profile.blockClasses.length <= 32);
  const diagnostics = JSON.parse(await fs.readFile(profile.diagnostics.path, 'utf8'));
  const materialIds = new Set(diagnostics.filter(row => profile.blockClasses.includes(row.blockClass)).map(row => row.material));
  const materials = (await readMaterials(profile.inventory.path)).filter(value => materialIds.has(value.material));
  assert.equal(materials.length, materialIds.size);
  const expected = new Map(materials.flatMap(value => value.samples).map(value => [value.sampleId, value]));
  assert.ok(expected.size > 0 && expected.size <= 10000);
  assert.equal(cases.length, expected.size); assert.equal(new Set(cases.map(value => value.sampleId)).size, cases.length);
  for (const sample of cases) assert.deepEqual(sample, expected.get(sample.sampleId), 'Case must match its frozen native baseline');
  assert.ok(Array.isArray(profile.tooltips) && profile.tooltips.length > 0 && profile.tooltips.length <= 8);
  assert.equal(new Set(profile.tooltips).size, profile.tooltips.length);
  assert.ok(profile.tooltips.every(value => materialIds.has(value)));
  assert.ok(materialIds.has(profile.mutation) && profile.tooltips.every(value => value !== profile.mutation));
  const previous = JSON.parse(await fs.readFile(profile.previous.path, 'utf8'));
  assert.ok(previous.length > 0 && previous.length <= 64);
  assert.ok(Array.isArray(profile.negativeMaterials) && profile.negativeMaterials.length <= 8);
  assert.ok(profile.negativeMaterials.every(value => !materialIds.has(value) && diagnostics.some(row => row.material === value)));
  return {states: cases.length, materials: materials.length, pattern: {test: value => materialIds.has(value)},
    tooltips: profile.tooltips, mutation: profile.mutation, previous, negativeMaterials: profile.negativeMaterials,
    woodGuards: profile.woodGuards === true};
}
