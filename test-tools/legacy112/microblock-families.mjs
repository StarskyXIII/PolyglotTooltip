import fs from 'node:fs/promises';
import {createReadStream} from 'node:fs';
import {createInterface} from 'node:readline';
import assert from 'node:assert/strict';
import {pathToFileURL} from 'node:url';

// Collapse the frozen comparison inventory before doing runtime work. A material can have
// many shapes and target languages; neither is an independent naming implementation.
export async function readMaterials(file, excludedSamples = new Set()) {
  const materials = new Map(), samples = new Map();
  for await (const line of createInterface({input: createReadStream(file), crlfDelay: Infinity})) {
    if (!line.trim()) continue;
    const row = JSON.parse(line);
    if (excludedSamples.has(row.sampleId)) continue;
    assert.equal(row.item, 'microblockcbe:microblock');
    assert.equal(row.classification, 'confirmed');
    const material = row.stack?.value?.tag?.value?.mat?.value;
    assert.equal(typeof material, 'string');
    assert.equal(typeof row.nativeTarget, 'string');
    let sample = samples.get(row.sampleId);
    if (!sample) {
      sample = {sampleId: row.sampleId, item: row.item, metadata: row.metadata, material,
        serialized: row.serialized, stack: row.stack, primary: row.nativePrimary,
        baselineStatus: row.resolverStatus, expected: {}};
      samples.set(row.sampleId, sample);
      if (!materials.has(material)) materials.set(material, {material, samples: []});
      materials.get(material).samples.push(sample);
    } else {
      assert.equal(sample.material, material); assert.equal(sample.metadata, row.metadata);
      assert.equal(sample.serialized, row.serialized); assert.deepEqual(sample.stack, row.stack);
      assert.equal(sample.primary, row.nativePrimary);
    }
    assert.ok(!Object.hasOwn(sample.expected, row.language), 'Duplicate sample/language pair');
    sample.expected[row.language] = row.nativeTarget;
  }
  return [...materials.values()].sort((a, b) => a.material.localeCompare(b.material));
}

export function groupMaterials(materials, diagnostics) {
  const byMaterial = new Map();
  for (const row of diagnostics) {
    assert.ok(!byMaterial.has(row.material), 'Duplicate diagnostic material');
    byMaterial.set(row.material, row);
  }
  assert.equal(byMaterial.size, materials.length, 'Diagnostic coverage must match input materials');
  const groups = new Map();
  for (const material of materials) {
    const row = byMaterial.get(material.material);
    assert.ok(row, 'Missing diagnostic for ' + material.material);
    const key = [row.stage, row.displayMethod?.owner ?? null, row.rawMethod?.owner ?? null,
      row.metadataMethod?.owner ?? null];
    const id = JSON.stringify(key);
    let group = groups.get(id);
    if (!group) {
      group = {stage: row.stage, displayOwner: key[1], rawOwner: key[2], metadataOwner: key[3],
        materials: [], states: 0, languageComparisons: 0, blocks: new Set(), itemClasses: new Set(),
        metadataSupported: 0, childCapturing: 0};
      groups.set(id, group);
    }
    group.materials.push(material.material); group.states += material.samples.length;
    group.languageComparisons += material.samples.reduce((sum, sample) => sum + Object.keys(sample.expected).length, 0);
    if (row.block) group.blocks.add(row.block);
    if (row.itemClass) group.itemClasses.add(row.itemClass);
    if (row.metadataSupported) group.metadataSupported++;
    if (row.childCapturing) group.childCapturing++;
  }
  const result = [...groups.values()].map(group => ({...group, materialCount: group.materials.length,
    blocks: [...group.blocks].sort(), itemClasses: [...group.itemClasses].sort()}))
    .sort((a, b) => b.materialCount - a.materialCount || JSON.stringify(a).localeCompare(JSON.stringify(b)));
  return {schema: 'microblock-dependency-groups-v1',
    note: 'Capture-ready is a diagnostic candidate, not proof of a translated name; acceptance requires frozen native-name comparisons.',
    materialCount: materials.length, states: result.reduce((sum, group) => sum + group.states, 0),
    languageComparisons: result.reduce((sum, group) => sum + group.languageComparisons, 0), groups: result};
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [inventory, diagnosticFile, output, ...exclusions] = process.argv.slice(2);
  assert.ok(inventory && diagnosticFile && output, 'inventory diagnostics output [verified-case-files...]');
  const excluded = new Set();
  for (const file of exclusions) for (const row of JSON.parse(await fs.readFile(file, 'utf8'))) excluded.add(row.sampleId);
  const materials = await readMaterials(inventory, excluded);
  const diagnostics = JSON.parse(await fs.readFile(diagnosticFile, 'utf8'));
  const report = groupMaterials(materials, diagnostics);
  await fs.writeFile(output, JSON.stringify(report, null, 2), {flag: 'wx'});
  console.log(JSON.stringify({materials: report.materialCount, states: report.states, groups: report.groups.length}));
}
