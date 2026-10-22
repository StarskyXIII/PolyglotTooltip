import {createHash} from 'node:crypto';

export const canonical = value => JSON.stringify(sort(value));
function sort(value) {
  return Array.isArray(value) ? value.map(sort) : value && typeof value === 'object'
    ? Object.fromEntries(Object.keys(value).sort().map(key => [key, sort(value[key])])) : value;
}
export const hash = value => createHash('sha256').update(value).digest('hex');
export const plain = text => text.replace(/\u00a7[0-9a-fk-or]/gi, '');
export const equal = (left, right) => canonical(left) === canonical(right);

export function manifest(collection) {
  const samples = new Map();
  for (const row of collection.samples) {
    const id = hash(canonical(row.stack));
    const source = {index: row.source, sourceUnchanged: equal(row.sourceBefore, row.sourceAfter), originalCount: row.originalCount};
    if (samples.has(id)) {
      if (!equal(samples.get(id).stack, row.stack)) throw new Error('Sample hash collision');
      samples.get(id).sources.push(source);
    } else samples.set(id, {id, item: row.item, metadata: row.metadata, serialized: row.serialized, stack: row.stack, sources: [source]});
  }
  return {schema: 1, complete: collection.complete, limits: collection.limits, failures: collection.failures,
    sources: collection.sources, samples: [...samples.values()]};
}

export function batches(samples, method) {
  const result = [], skipped = [];
  let batch = [];
  const bytes = rows => Buffer.byteLength(JSON.stringify({jsonrpc: '2.0', id: Number.MAX_SAFE_INTEGER, method, params: {samples: rows}}));
  for (const sample of samples) {
    const row = {id: sample.id, serialized: sample.serialized};
    if (bytes([row]) > 900000) {
      skipped.push({id: sample.id, observationAttempted: false, error: {stage: 'transport-limit', bytes: bytes([row]), limit: 900000}});
      continue;
    }
    if (batch.length === 32 || bytes([...batch, row]) > 900000) { result.push(batch); batch = []; }
    batch.push(row);
  }
  if (batch.length) result.push(batch);
  return {batches: result, skipped};
}

export function compareSample(sample, english, chinese, product, {allowMatchingNativeMutation = false} = {}) {
  const result = {id: sample.id, item: sample.item, metadata: sample.metadata};
  const nativeRows = [english, chinese];
  const lanes = [['english', english], ['chinese', chinese], ['product', product]];
  if (lanes.some(([, row]) => !row)) return {...result, status: 'infrastructure-invalid', reason: 'missing-row'};
  const skipped = lanes.filter(([, row]) => row.observationAttempted === false);
  if (skipped.length) return {...result, status: 'coverage-limit', skipped: skipped.map(([lane, row]) => ({lane, error: row.error}))};
  const flags = lanes.map(([, row]) => row.tooltipFlag ?? 'NORMAL');
  if (flags.some(flag => !['NORMAL', 'ADVANCED'].includes(flag)) || new Set(flags).size !== 1) {
    return {...result, status: 'infrastructure-invalid', reason: 'tooltip-flag-drift'};
  }
  result.tooltipFlag = flags[0];
  for (const [lane, row] of lanes) {
    if (typeof row.modifiers?.shift !== 'boolean' || typeof row.modifiers?.control !== 'boolean'
        || typeof row.captureFailed !== 'boolean' || (!row.error && (!Array.isArray(row.tooltip)
          || !Array.isArray(row.created) || typeof row.literalCustomName !== 'boolean' || !row.before || !row.after))) {
      return {...result, status: 'infrastructure-invalid', lane, reason: 'missing-required-fields'};
    }
  }
  if (sample.sources.some(source => !source.sourceUnchanged)) return {...result, status: 'collection-mutated'};
  if (nativeRows.some(row => row.error || !row.tooltip.length)) return {...result, status: 'native-invalid', nativeErrors: nativeRows.map(row => row.error ?? null)};
  const matchingNativeMutation = allowMatchingNativeMutation && !product.error
    && !equal(english.after, english.before) && equal(english.after, chinese.after) && equal(chinese.after, product.after);
  const validate = (lane, row) => {
    if (!equal(row.before, sample.stack)) return {...result, status: 'replay-mismatch', lane};
    if (!equal(row.after, row.before) && !matchingNativeMutation) return {...result, status: 'tooltip-mutated', lane};
    if (row.modifiers.shift || row.modifiers.control) return {...result, status: 'modifier-drift', lane};
    if (row.captureFailed) return {...result, status: 'observer-error', lane};
  };
  for (const [lane, row] of lanes.slice(0, 2)) { const invalid = validate(lane, row); if (invalid) return invalid; }
  if (nativeRows.some(row => row.created?.length)) return {...result, status: 'native-observer-contamination'};
  if (product.error || !product.tooltip.length) return {...result, status: 'product-error', error: product.error ?? null};
  const productInvalid = validate('product', product); if (productInvalid) return productInvalid;
  if (matchingNativeMutation) result.nativeMutation = {sameAfterStateAcrossLanes: true, afterSha256: hash(canonical(english.after))};
  const observed = lanes.every(([, row]) => row.nameObservation !== undefined);
  if (result.tooltipFlag === 'ADVANCED' && !observed) {
    return {...result, status: 'infrastructure-invalid', reason: 'missing-name-observation'};
  }
  if (observed) {
    for (const [lane, row] of lanes) {
      const observation = row.nameObservation;
      if (!observation || observation.nativeNameCaptured !== true || observation.initialTitleCaptured !== true
          || observation.rootList !== true || typeof observation.nativeName !== 'string' || typeof observation.initialTitle !== 'string') {
        return {...result, status: 'infrastructure-invalid', reason: 'invalid-name-observation', lane};
      }
    }
  }
  const nativeTitlesRetained = observed && nativeRows.every(row => plain(row.tooltip[0]) === plain(row.nameObservation.initialTitle));
  if (result.tooltipFlag === 'ADVANCED' && !nativeTitlesRetained) {
    return {...result, status: 'native-title-replaced', nameObservations: nativeRows.map(row => row.nameObservation)};
  }
  result.nameOracle = nativeTitlesRetained ? 'natural-display-name' : 'rendered-title';
  result.english = plain(nativeTitlesRetained ? english.nameObservation.nativeName : english.tooltip[0]);
  result.chinese = plain(nativeTitlesRetained ? chinese.nameObservation.nativeName : chinese.tooltip[0]);
  result.productTitle = plain(product.tooltip[0]);
  result.expectedSecondary = result.english !== result.chinese && !chinese.literalCustomName;
  result.nativeAlreadyContainsEnglish = chinese.tooltip.some(line => plain(line) === result.english);
  result.literalCustomName = chinese.literalCustomName;
  const created = product.created ?? [];
  const valid = created.filter(row => row.rootList && row.tooltipFrames === 1 && row.indices.length === 1
    && row.indices[0] > 0 && product.tooltip[row.indices[0]] === row.text);
  const secondary = valid.filter(row => plain(row.text) === result.english);
  result.createdEnglish = secondary;
  result.unattributedCreations = created.filter(row => !valid.includes(row));
  result.otherCreatedRows = valid.filter(row => !secondary.includes(row));
  const indices = new Set(secondary.map(row => row.indices[0]));
  result.bodyUnchanged = equal(product.tooltip.filter((_, index) => !indices.has(index)), chinese.tooltip);
  result.primaryUnchanged = product.tooltip[0] === chinese.tooltip[0];
  result.productTooltip = product.tooltip; result.nativeTooltip = chinese.tooltip;
  if (!result.primaryUnchanged) result.status = 'primary-changed';
  else if (result.unattributedCreations.length) result.status = 'inconclusive-attribution';
  else if (secondary.length > 1) result.status = 'duplicate-secondary';
  else if (!result.expectedSecondary && secondary.length) result.status = 'unexpected-secondary';
  else if (secondary.some(row => row.indices[0] !== 1)) result.status = 'misplaced-secondary';
  else if (result.otherCreatedRows.length) result.status = 'additional-product-rows';
  else if (result.expectedSecondary && secondary.length === 0) result.status = 'missing-secondary';
  else if (!result.bodyUnchanged) result.status = 'body-changed';
  else result.status = result.expectedSecondary ? 'pass' : 'no-secondary-needed';
  return result;
}
