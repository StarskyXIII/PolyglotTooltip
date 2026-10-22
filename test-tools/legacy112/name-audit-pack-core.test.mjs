import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {hash, canonical} from './name-audit-core.mjs';
import {planCatalog, seal, collectPart, mergePart, validateBatch, groupFailure, validateCorpus,
  validateIdentity, validateArtifactHashes} from './name-audit-pack-core.mjs';

const items = Array.from({length: 65}, (_, i) => 'test:item' + String(i).padStart(2, '0'));
const catalog = {items, sha256: hash(JSON.stringify(items))};
const stack = {type: 10, value: {id: {type: 8, value: 'cross:variant'}}};
const source = {id: 0, kind: 'subitems:search', owner: 'test:item00'};
const raw = {item: 'cross:variant', metadata: 0, stack, serialized: 'AA==', originalCount: 1,
  source: 0, sourceBefore: stack, sourceAfter: stack};
const removeTemporary = async folder => {
  assert.equal(path.dirname(path.resolve(folder)), path.resolve(os.tmpdir()));
  assert.ok(path.basename(folder).startsWith('pt-audit-'));
  await fs.rm(folder, {recursive: true});
};

test('catalog is immutable, sorted and split by registry source responsibility', () => {
  const plan = planCatalog(catalog);
  assert.deepEqual(plan.parts.map(p => p.items.length), [32, 32, 1]);
  assert.equal(plan.parts[0].querySha256, hash(JSON.stringify({namespaces: ['test'], items: items.slice(0, 32)})));
  assert.throws(() => planCatalog({...catalog, items: [...items].reverse()}));
  assert.throws(() => planCatalog({...catalog, sha256: 'wrong'}));
});

test('interrupted collection resumes sealed pages and rejects changed pages or identifiers', async () => {
  const folder = await fs.mkdtemp(path.join(os.tmpdir(), 'pt-audit-'));
  try {
    const part = planCatalog(catalog).parts[0];
    const first = {collectionId: part.collectionId, querySha256: part.querySha256, offset: 0, total: 2,
      nextOffset: 1, samples: [raw], complete: true, sources: [source], failures: [], limits: {capped: false}};
    await assert.rejects(collectPart(part, folder, async (_, query) => {
      if (query.offset === 0) return first;
      throw new Error('disconnect');
    }), /disconnect/);
    let calls = 0;
    const result = await collectPart(part, folder, async (_, query) => {
      calls++; assert.equal(query.offset, 1); assert.equal(query.collectionId, part.collectionId);
      return {...first, offset: 1, nextOffset: 2, samples: [raw]};
    });
    assert.equal(calls, 1); assert.equal(result.samples.length, 1); assert.equal(result.samples[0].sources.length, 2);
    assert.equal(result.samples[0].item, 'cross:variant');
    await assert.rejects(seal(path.join(folder, 'manifest.json'), {...result, complete: false}), /Frozen file differs/);
    const index = {parts: [], samples: new Map()};
    mergePart(index, part, result, 'one'); mergePart(index, {...part, id: 'next'}, result, 'two');
    assert.equal(index.samples.size, 1); assert.equal(index.samples.values().next().value.sources.length, 4);
    assert.equal(index.parts[1].ids.length, 0); assert.ok(index.parts[0].zeroSampleItems.includes('test:item00'));
  } finally { await removeTemporary(folder); }
});

test('foreign collection pages and duplicate or truncated replay batches cannot advance', async () => {
  const folder = await fs.mkdtemp(path.join(os.tmpdir(), 'pt-audit-'));
  try {
    const part = planCatalog(catalog).parts[0];
    await assert.rejects(collectPart(part, folder, async () => ({collectionId: 'stale'})));
    const samples = [{id: hash(canonical(stack))}, {id: 'other'}];
    assert.throws(() => validateBatch({rows: [samples[0]]}, samples));
    assert.throws(() => validateBatch({rows: [samples[0], samples[0]]}, samples));
    validateBatch({rows: samples}, samples);
    assert.equal(groupFailure({item: 'mod:item', status: 'missing-secondary'}, {diagnostics: {resolverNames: ['English']}}).reason,
      'resolved-but-not-inserted');
  } finally { await removeTemporary(folder); }
});

test('corpus validation rejects omitted parts, orphan samples and forged source witnesses', async () => {
  const folder = await fs.mkdtemp(path.join(os.tmpdir(), 'pt-audit-'));
  try {
    const catalog = {items: ['test:item00'], sha256: hash(JSON.stringify(['test:item00']))};
    const plan = planCatalog(catalog), part = plan.parts[0];
    await seal(path.join(folder, 'catalog.json'), catalog);
    await seal(path.join(folder, 'plan.json'), plan);
    const partFolder = path.join(folder, 'parts', part.id);
    const corpus = await collectPart(part, partFolder, async () => ({collectionId: part.collectionId,
      querySha256: part.querySha256, offset: 0, total: 1, nextOffset: 1, samples: [raw], complete: true,
      sources: [source], failures: [], limits: {capped: false}}));
    const merge = {parts: [], samples: new Map()};
    mergePart(merge, part, corpus, hash(await fs.readFile(path.join(partFolder, 'manifest.json'))));
    const index = {schema: 2, catalogSha256: catalog.sha256, catalogItems: 1, parts: merge.parts,
      samples: [...merge.samples.values()], collectionComplete: true};
    await validateCorpus(folder, index);
    await assert.rejects(validateCorpus(folder, {...index, parts: []}));
    await assert.rejects(validateCorpus(folder, {...index, samples: [...index.samples, {id: 'orphan'}]}));
    const changed = structuredClone(index); changed.samples[0].sources[0].owner = 'wrong:item';
    await assert.rejects(validateCorpus(folder, changed));
    const bytes = await fs.readFile(path.join(partFolder, 'manifest.json'), 'utf8');
    await fs.writeFile(path.join(partFolder, 'manifest.json'), bytes.replace('cross:variant', 'cross:changed'));
    await assert.rejects(validateCorpus(folder, index));
  } finally { await removeTemporary(folder); }
});

test('client, settings, observer and unreadable artifacts fail identity evidence', () => {
  const config = {nonce: 'one', fixture: path.resolve('fixture'), language: 'zh_cn', product: true};
  const identity = {fixtureNonce: 'one', gameDirectory: config.fixture, language: 'zh_cn', product: true,
    modifiers: {shift: false, control: false}, jvmStartTime: 100, worldGeneration: 1, settings: {languages: ['en_us']},
    observerInputSha256: 'a'.repeat(64), observerOutputSha256: 'b'.repeat(64)};
  const session = {config, identity}, observer = {input: 'a'.repeat(64), output: 'b'.repeat(64)};
  validateIdentity(identity, session, observer);
  assert.throws(() => validateIdentity(identity, session, undefined, {requireObserver: true}), /Missing product observer/);
  for (const drift of [{jvmStartTime: 101}, {worldGeneration: 2}, {settings: {languages: ['ja_jp']}},
    {language: 'en_us'}, {fixtureNonce: 'two'}, {observerInputSha256: 'c'.repeat(64)}]) {
    assert.throws(() => validateIdentity({...identity, ...drift}, session, observer));
  }
  const provenance = {mods: [{id: 'mod', sha256: 'a'.repeat(64)}], origins: {Minecraft: {sha256: 'b'.repeat(64)}}, packs: []};
  validateArtifactHashes(provenance);
  assert.throws(() => validateArtifactHashes({...provenance, mods: [{id: 'mod'}]}));
  assert.throws(() => validateArtifactHashes({...provenance, origins: {Minecraft: {}}}));
});
