import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {canonical, hash, equal, manifest} from './name-audit-core.mjs';

export function planCatalog(catalog) {
  assert.deepEqual(catalog.items, [...new Set(catalog.items)].sort());
  assert.equal(catalog.sha256, hash(JSON.stringify(catalog.items)));
  const parts = [], byNamespace = new Map();
  for (const item of catalog.items) {
    assert.match(item, /^[a-z0-9_.-]{1,64}:[a-z0-9_./-]+$/);
    const namespace = item.split(':')[0];
    if (!byNamespace.has(namespace)) byNamespace.set(namespace, []);
    byNamespace.get(namespace).push(item);
  }
  for (const [namespace, items] of byNamespace) {
    for (let offset = 0; offset < items.length; offset += 32) {
      const query = {namespaces: [namespace], items: items.slice(offset, offset + 32)};
      const querySha256 = hash(JSON.stringify(query));
      parts.push({id: String(parts.length).padStart(5, '0') + '-' + namespace, ...query, querySha256,
        collectionId: querySha256});
    }
  }
  return {schema: 2, catalogSha256: catalog.sha256, items: catalog.items, parts};
}

export async function readOptional(file) {
  try { return JSON.parse(await fs.readFile(file, 'utf8')); }
  catch (error) { if (error.code === 'ENOENT') return undefined; throw error; }
}

export async function seal(file, value) {
  const content = JSON.stringify(value);
  const existing = await readOptional(file);
  if (existing !== undefined) {
    assert.ok(equal(existing, value), 'Frozen file differs: ' + file);
    return hash(await fs.readFile(file));
  }
  await fs.mkdir(path.dirname(file), {recursive: true});
  const temporary = file + '.' + randomUUID() + '.pending';
  await fs.writeFile(temporary, content, {flag: 'wx'});
  await fs.link(temporary, file);
  await fs.unlink(temporary);
  return hash(content);
}

export function validatePage(page, part, offset, total) {
  assert.equal(page.collectionId, part.collectionId);
  assert.equal(page.querySha256, part.querySha256);
  assert.equal(page.offset, offset);
  assert.ok(Number.isSafeInteger(page.total) && page.total >= 0);
  if (total !== undefined) assert.equal(page.total, total);
  assert.ok(Array.isArray(page.samples));
  assert.equal(page.nextOffset, offset + page.samples.length);
  assert.ok(page.nextOffset <= page.total && (page.nextOffset > offset || page.total === 0));
  assert.equal(typeof page.complete, 'boolean');
  if (offset === 0) {
    assert.ok(Array.isArray(page.sources) && Array.isArray(page.failures) && page.limits);
    assert.deepEqual(page.sources.map(s => s.id), page.sources.map((_, i) => i));
  }
}

export async function collectPart(part, folder, request) {
  await seal(path.join(folder, 'query.json'), part);
  const frozen = await readOptional(path.join(folder, 'manifest.json'));
  if (frozen) return frozen;
  const collection = {samples: []};
  let offset = 0, total, complete;
  do {
    const file = path.join(folder, 'pages', String(offset).padStart(7, '0') + '.json');
    const saved = await readOptional(file);
    const page = saved ?? await request('audit.collect', {...part, start: offset === 0, offset, limit: 64});
    validatePage(page, part, offset, total);
    if (offset === 0) {
      total = page.total; complete = page.complete;
      Object.assign(collection, {complete, sources: page.sources, failures: page.failures, limits: page.limits});
    }
    assert.equal(page.complete, complete);
    await seal(file, page);
    collection.samples.push(...page.samples);
    offset = page.nextOffset;
  } while (offset < total);
  const result = manifest(collection);
  result.complete &&= result.samples.every(s => s.sources.every(source => source.sourceUnchanged));
  for (const sample of result.samples) {
    assert.equal(sample.id, hash(canonical(sample.stack)));
    for (const source of sample.sources) assert.ok(collection.sources[source.index]);
  }
  await seal(path.join(folder, 'manifest.json'), result);
  return result;
}

export function mergePart(index, part, corpus, manifestSha256) {
  const zeroSampleItems = part.items.filter(item => !corpus.samples.some(s => s.item === item
    && s.sources.some(source => !corpus.sources[source.index].kind.startsWith('control:'))));
  const entry = {id: part.id, manifestSha256, complete: corpus.complete, limits: corpus.limits,
    failures: corpus.failures, sourceErrors: corpus.sources.filter(s => s.error), zeroSampleItems, ids: []};
  index.parts.push(entry);
  for (const sample of corpus.samples) {
    assert.equal(sample.id, hash(canonical(sample.stack)));
    let current = index.samples.get(sample.id);
    if (!current) {
      current = {id: sample.id, part: part.id, item: sample.item, metadata: sample.metadata, sources: []};
      index.samples.set(sample.id, current); entry.ids.push(sample.id);
    }
    assert.equal(current.item, sample.item); assert.equal(current.metadata, sample.metadata);
    for (const source of sample.sources) current.sources.push({...source, part: part.id,
      kind: corpus.sources[source.index].kind, owner: corpus.sources[source.index].owner});
  }
}

export function validateBatch(result, samples, tooltipFlag) {
  assert.ok(Array.isArray(result.rows));
  assert.deepEqual(result.rows.map(row => row.id), samples.map(sample => sample.id));
  assert.equal(new Set(result.rows.map(row => row.id)).size, result.rows.length);
  if (tooltipFlag !== undefined) {
    assert.ok(['NORMAL', 'ADVANCED'].includes(tooltipFlag));
    assert.equal(result.tooltipFlag, tooltipFlag);
    for (const row of result.rows) assert.equal(row.tooltipFlag, tooltipFlag);
  }
}

export function groupFailure(row, diagnostics) {
  const info = diagnostics?.diagnostics;
  let reason = row.status;
  if (reason === 'missing-secondary') reason = info?.resolverNames?.length ? 'resolved-but-not-inserted' : 'unresolved-name';
  return {namespace: row.item.split(':')[0], status: row.status, reason,
    nameOwner: info?.nameOwner ?? null, keyOwner: info?.keyOwner ?? null};
}

export async function validateCorpus(root, index) {
  const catalog = await readOptional(path.join(root, 'catalog.json'));
  const plan = planCatalog(catalog);
  assert.ok(equal(plan, await readOptional(path.join(root, 'plan.json'))), 'Frozen plan changed');
  assert.equal(index.schema, 2);
  assert.equal(index.catalogSha256, plan.catalogSha256);
  assert.equal(index.catalogItems, plan.items.length);
  assert.deepEqual(index.parts.map(p => p.id), plan.parts.map(p => p.id));
  const expected = {parts: [], samples: new Map()};
  for (let number = 0; number < plan.parts.length; number++) {
    const part = plan.parts[number], folder = path.join(root, 'parts', part.id);
    assert.ok(equal(await readOptional(path.join(folder, 'query.json')), part));
    const bytes = await fs.readFile(path.join(folder, 'manifest.json'));
    assert.equal(hash(bytes), index.parts[number].manifestSha256);
    const corpus = JSON.parse(bytes);
    assert.equal(new Set(corpus.samples.map(s => s.id)).size, corpus.samples.length);
    assert.deepEqual(corpus.sources.map(s => s.id), corpus.sources.map((_, i) => i));
    for (const sample of corpus.samples) {
      assert.equal(sample.stack?.value?.id?.value, sample.item);
      assert.ok(sample.sources.length);
      for (const source of sample.sources) assert.ok(corpus.sources[source.index]);
    }
    mergePart(expected, part, corpus, hash(bytes));
  }
  assert.ok(equal(expected.parts, index.parts), 'Part ownership or coverage differs');
  assert.ok(equal([...expected.samples.values()], index.samples), 'Global sample identities or witnesses differ');
  assert.equal(index.collectionComplete, expected.parts.every(p => p.complete));
}

export function validateIdentity(identity, session, observer, {requireObserver = false} = {}) {
  const config = session.config;
  assert.equal(identity.fixtureNonce, config.nonce);
  assert.equal(path.resolve(identity.gameDirectory).toLowerCase(), path.resolve(config.fixture).toLowerCase());
  assert.equal(identity.language, config.language);
  assert.equal(identity.product, config.product);
  assert.deepEqual(identity.modifiers, {shift: false, control: false});
  assert.equal(identity.jvmStartTime, session.identity.jvmStartTime);
  assert.equal(identity.worldGeneration, session.identity.worldGeneration);
  assert.ok(equal(identity.settings, session.identity.settings), 'Product settings changed');
  if (config.product && requireObserver) assert.ok(observer, 'Missing product observer receipt');
  if (config.product && observer) {
    assert.match(identity.observerInputSha256, /^[a-f0-9]{64}$/);
    assert.match(identity.observerOutputSha256, /^[a-f0-9]{64}$/);
    assert.equal(identity.observerInputSha256, observer.input);
    assert.equal(identity.observerOutputSha256, observer.output);
  } else if (!config.product) {
    assert.ok(!identity.observerInputSha256 && !identity.observerOutputSha256);
  }
}

export function validateArtifactHashes(provenance) {
  for (const mod of provenance.mods) assert.match(mod.sha256 ?? '', /^[a-f0-9]{64}$/, 'Missing artifact bytes: ' + mod.id);
  for (const [name, origin] of Object.entries(provenance.origins)) assert.match(origin.sha256 ?? '', /^[a-f0-9]{64}$/, 'Missing origin: ' + name);
  for (const pack of provenance.packs) assert.match(pack.sha256 ?? '', /^[a-f0-9]{64}$/);
}
