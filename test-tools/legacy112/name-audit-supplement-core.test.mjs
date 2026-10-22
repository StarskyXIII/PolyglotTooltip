import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {hash, manifest} from './name-audit-core.mjs';
import {planCatalog, seal, mergePart as mergeNativePart} from './name-audit-pack-core.mjs';
import {boundedQuery, supplementPlan, validateCorpus, mergePart, callbackFixtures} from './name-audit-supplement-core.mjs';

test('callback coverage rejects omitted materials, missing literal cases and missing witnesses', () => {
  const materials = Array.from({length: 40}, (_, index) => ({material: 'material-' + index,
    cotMaterial: true, callbackPresent: true, callbackClass: 'SharedCallback', disposition: 'definition-recorded'}));
  const plannedCases = materials.flatMap(row => ['cot-callback:' + row.material, 'literal:cot-callback:' + row.material]);
  const part = {plannedCases, caseWitnesses: plannedCases.map(name => ({case: name, samples: ['shared-stack']}))};
  assert.equal(callbackFixtures({materials}, part).plannedCases, 80);
  assert.throws(() => callbackFixtures({materials}, {...part, plannedCases: plannedCases.slice(0, 78)}));
  assert.throws(() => callbackFixtures({materials}, {...part, plannedCases: plannedCases.filter(name => !name.startsWith('literal:'))}));
  assert.throws(() => callbackFixtures({materials}, {...part, caseWitnesses: part.caseWitnesses.slice(0, 79)}));
  assert.throws(() => callbackFixtures({materials: materials.slice(0, 39)}, part));
  const unsupported = callbackFixtures({materials: materials.map(row => ({...row, disposition: 'missing-or-oversized-definition'}))}, part);
  assert.deepEqual(JSON.parse(JSON.stringify(unsupported)), unsupported);
  assert.equal(unsupported.materials[0].sha256, null);
});

test('bounded queries bind normalization, capacity and fixture provenance', () => {
  const query = boundedQuery(['minecraft'], ['minecraft:stone'], 50000);
  assert.equal(query.querySha256, hash('{"queryVersion":2,"namespaces":["minecraft"],"items":["minecraft:stone"],"sampleLimit":50000}'));
  assert.deepEqual(query, boundedQuery(['minecraft', 'minecraft'], ['minecraft:stone'], 50000));
  assert.notEqual(query.querySha256, boundedQuery(['minecraft'], ['minecraft:stone'], 150000).querySha256);
  assert.notEqual(query.querySha256, boundedQuery(['minecraft'], ['minecraft:stone'], 50000, 'name-boundaries-v1').querySha256);
  for (const value of [49999, 150001, 50000.1, '50000', NaN]) assert.throws(() => boundedQuery(['minecraft'], ['minecraft:stone'], value));
});

test('supplements preserve the capped parent and reject changed scope or witnesses', async () => {
  const temporary = await fs.mkdtemp(path.join(os.tmpdir(), 'pt-audit-supplement-'));
  try {
    const parent = path.join(temporary, 'parent', 'corpus'), root = path.join(temporary, 'supplement');
    const items = ['microblockcbe:microblock', 'microblockcbe:saw_diamond', 'microblockcbe:saw_iron',
      'microblockcbe:saw_stone', 'microblockcbe:stone_rod'];
    const catalog = {items, sha256: hash(JSON.stringify(items))}, parentPlan = planCatalog(catalog);
    await seal(path.join(parent, 'catalog.json'), catalog);
    await seal(path.join(parent, 'plan.json'), parentPlan);
    const create = async (folder, part, capped, manual = false) => {
      const source = {id: 0, kind: manual ? 'control:manual:fixture' : 'ingredient-registry', owner: 'all-item-stacks', matched: 1, serialized: 1};
      const stack = {type: 10, value: {id: {type: 8, value: part.items[0]}}};
      if (manual) Object.assign(source, {seedStack: stack, seedSerialized: 'AA==', seedBinarySha256: hash(Buffer.from([0])), fixtureTargetStack: stack});
      const additional = manual ? [] : part.items.flatMap(owner => ['registry-default', 'creative-tabs', 'subitems:search'].map(kind => ({kind, owner, matched: 0, serialized: 0})));
      const corpus = manifest({complete: !capped, limits: {sampleLimit: part.sampleLimit ?? 50000, capped,
        ...(manual ? {plannedCases: ['fixture']} : {})}, failures: [],
        sources: [source, ...additional.map((value, i) => ({id: i + 1, ...value}))],
        samples: [{source: 0, item: part.items[0], metadata: 0, stack, serialized: 'AA==', originalCount: 1,
          sourceBefore: stack, sourceAfter: stack}]});
      await seal(path.join(folder, 'query.json'), part);
      const digest = await seal(path.join(folder, 'manifest.json'), corpus);
      return {corpus, digest};
    };
    const part = parentPlan.parts[0], data = await create(path.join(parent, 'parts', part.id), part, true);
    const merged = {parts: [], samples: new Map()};
    mergeNativePart(merged, part, data.corpus, data.digest);
    const sourceSessionSha256 = await seal(path.join(parent, '..', 'session.json'), {schema: 2});
    const parentIndex = {schema: 2, catalogSha256: catalog.sha256, catalogItems: items.length,
      parts: merged.parts, samples: [...merged.samples.values()], collectionComplete: false, sourceSessionSha256};
    await seal(path.join(parent, 'index.json'), parentIndex);
    const parentBytes = await fs.readFile(path.join(parent, 'index.json'));
    const plan = await supplementPlan(parent, part.id);
    await seal(path.join(root, 'catalog.json'), catalog);
    await seal(path.join(root, 'plan.json'), plan);
    const supplement = {parts: [], samples: new Map()};
    for (const part of plan.parts) {
      const data = await create(path.join(root, 'parts', part.id), part, false, part.coverage === 'manual-fixtures');
      mergePart(supplement, part, data.corpus, data.digest);
    }
    const index = {schema: 3, parent: plan.parent, catalogSha256: plan.catalogSha256, catalogItems: 5, selectedNativeItems: 5,
      parts: supplement.parts, samples: [...supplement.samples.values()], collectionComplete: true};
    await validateCorpus(root, index);
    await assert.rejects(validateCorpus(root, {...index, parts: index.parts.slice(1)}));
    const witness = structuredClone(index); witness.samples[0].sources[0].owner = 'foreign';
    await assert.rejects(validateCorpus(root, witness));
    assert.deepEqual(await fs.readFile(path.join(parent, 'index.json')), parentBytes);
    const changed = structuredClone(plan); changed.parts[0].items.pop();
    await fs.writeFile(path.join(root, 'plan.json'), JSON.stringify(changed));
    await assert.rejects(validateCorpus(root, index));
    await fs.writeFile(path.join(root, 'plan.json'), JSON.stringify(plan));
    await fs.writeFile(path.join(parent, 'index.json'), JSON.stringify({...parentIndex, collectionComplete: true}));
    await assert.rejects(validateCorpus(root, index));
  } finally {
    assert.equal(path.dirname(path.resolve(temporary)), path.resolve(os.tmpdir()));
    assert.ok(path.basename(temporary).startsWith('pt-audit-supplement-'));
    await fs.rm(temporary, {recursive: true});
  }
});
