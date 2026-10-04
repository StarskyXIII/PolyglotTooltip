import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';

export function canonical(value) {
  if (Array.isArray(value)) return `[${value.map(canonical).join(',')}]`;
  if (value && typeof value === 'object') return `{${Object.keys(value).sort().map(k => `${JSON.stringify(k)}:${canonical(value[k])}`).join(',')}}`;
  return JSON.stringify(value);
}
export const stackCanonicalization = 'itemstack-v1-neoforge-potion-cures-set';
export function canonicalStack(stack) {
  const value = structuredClone(stack);
  const effect = entry => {
    if (entry['neoforge:cures'] !== undefined) {
      const cures = entry['neoforge:cures'];
      assert(Array.isArray(cures) && cures.every(cure => typeof cure === 'string'), 'Invalid effect cure set');
      assert.equal(new Set(cures).size, cures.length, 'Duplicate effect cure');
      cures.sort();
    }
    if (entry.hidden_effect) effect(entry.hidden_effect);
  };
  for (const entry of value.components?.['minecraft:potion_contents']?.custom_effects ?? []) effect(entry);
  return canonical(value);
}
export const digest = value => createHash('sha256').update(canonicalStack(value)).digest('hex');
export const normalize = text => text.replace(/§[0-9a-fk-or]/gi, '');

export function freezeSamples(collected) {
  assert.equal(collected.complete, true, 'Collection incomplete');
  const samples = new Map();
  for (const row of collected.samples) {
    const id = digest(row.stack);
    if (!samples.has(id)) samples.set(id, {id, item: row.item, stack: row.stack, sources: []});
    const sample = samples.get(id);
    if (!sample.sources.includes(row.source)) sample.sources.push(row.source);
  }
  return [...samples.values()].sort((a, b) => a.id.localeCompare(b.id));
}

export function validateManifest(samples, requiredCoverage = true) {
  assert(Array.isArray(samples) && samples.length > 0, 'Empty sample manifest');
  assert.equal(new Set(samples.map(s => s.id)).size, samples.length, 'Duplicate frozen sample');
  for (const sample of samples) {
    assert.equal(sample.id, digest(sample.stack), 'Sample identity mismatch');
    assert.equal(sample.stack.id, sample.item, 'Item ID mismatch');
    assert.equal(sample.stack.count ?? 1, 1, 'Sample count must be one');
    assert(sample.sources?.length, 'Missing sample source');
  }
  if (!requiredCoverage) return;
  for (const item of ['mob_charm_fragment', 'mob_charm']) {
    for (const entity of ['zombie', 'skeleton']) {
      assert(samples.some(s => s.item === `reliquary:${item}` && s.stack.components?.['reliquary:entity_name'] === `minecraft:${entity}`), `Missing ${item} ${entity}`);
    }
  }
  const geas = samples.filter(s => s.item === 'malum:geas' && !s.sources.includes('control:literal-geas'))
    .map(s => s.stack.components?.['malum:geas_effect']?.geasEffectType)
    .filter(value => typeof value === 'string' && /^malum:[a-z0-9_/.-]+$/.test(value));
  assert(new Set(geas).size >= 2, 'Missing Geas variants');
  for (const control of ['stone', 'literal', 'translated', 'missing', 'literal-geas']) {
    assert.equal(samples.filter(s => s.sources.includes(`control:${control}`)).length, 1, `Missing/duplicate control ${control}`);
  }
}

export function validateRows(samples, rows, label) {
  assert.equal(rows.length, samples.length, `${label}: incomplete observations`);
  const mapped = new Map(rows.map(r => [r.id, r]));
  assert.equal(mapped.size, rows.length, `${label}: duplicate observation`);
  for (const sample of samples) {
    const row = mapped.get(sample.id);
    assert(row, `${label}: missing observation ${sample.id}`);
    assert(!row.error, `${label}: ${row.error}`);
    assert.equal(canonicalStack(row.before), canonicalStack(sample.stack), `${label}: decode changed stack`);
    assert.equal(canonicalStack(row.after), canonicalStack(sample.stack), `${label}: tooltip changed stack`);
    assert(row.tooltip?.length && row.tooltip.every(l => typeof l.text === 'string'), `${label}: invalid tooltip`);
    assert(Array.isArray(row.created) && Array.isArray(row.missingTitleKeys), `${label}: missing observer data`);
    assert.equal(typeof row.literalCustomName, 'boolean', `${label}: missing custom name classification`);
    for (const line of row.created) {
      assert(typeof line.text === 'string' && Array.isArray(line.indices), `${label}: invalid secondary observation`);
      for (const index of line.indices) {
        assert(Number.isInteger(index) && index >= 0 && index < row.tooltip.length, `${label}: invalid retained index`);
        assert.equal(line.text, row.tooltip[index].text, `${label}: retained text mismatch`);
      }
    }
  }
  return mapped;
}

export function compareSample(sample, english, primary, actual) {
  const en = normalize(english.tooltip[0].text);
  const zh = normalize(primary.tooltip[0].text);
  const title = normalize(actual.tooltip[0].text);
  const issues = [];
  let inconclusive = false;
  if (title !== zh) issues.push('primary-changed');
  const deliberateMissing = sample.sources.includes('control:missing');
  if (english.missingTitleKeys.length && !deliberateMissing) {
    issues.push('unresolved-native-english');
    inconclusive = true;
  }
  const requiresSecondary = !english.literalCustomName && en !== zh;
  const retained = actual.created.flatMap(line => line.indices.map(index => ({index, text: normalize(line.text)})));
  if (actual.created.some(line => line.indices.length === 0)) {
    issues.push('secondary-removed-or-copied');
    inconclusive = true;
  }
  if (requiresSecondary) {
    if (actual.created.length === 0) issues.push('secondary-missing');
    if (actual.created.length > 1 || retained.length > 1) issues.push('secondary-duplicated');
    for (const line of actual.created) if (normalize(line.text) !== en) issues.push('secondary-text-mismatch');
    if (retained.some(line => line.index !== 1)) issues.push('secondary-position');
  } else if (actual.created.length) issues.push('unexpected-secondary');
  const retainedIndices = new Set(retained.map(line => line.index));
  const nativeLines = primary.tooltip.map(line => normalize(line.text));
  const restoredLines = actual.tooltip.filter((_, index) => !retainedIndices.has(index)).map(line => normalize(line.text));
  const actualLines = actual.tooltip.map(line => normalize(line.text));
  const occurrences = (lines, text) => lines.filter(line => line === text).length;
  for (const name of new Set([en, zh])) {
    const allowed = occurrences(nativeLines, name) + (name === en && requiresSecondary ? 1 : 0);
    if (occurrences(actualLines, name) > allowed) issues.push('untracked-name-duplicate');
  }
  let nativeIndex = 0;
  const additionalBodyLines = [];
  for (const text of restoredLines) {
    if (nativeIndex < nativeLines.length && text === nativeLines[nativeIndex]) nativeIndex++;
    else additionalBodyLines.push(text);
  }
  if (nativeIndex !== nativeLines.length) issues.push('native-tooltip-changed');
  return {id: sample.id, item: sample.item, sources: sample.sources, expectedEnglishTitle: en,
    expectedPrimaryTitle: zh, actualPrimaryTitle: title, requiresSecondary,
    actualSecondary: actual.created, additionalBodyLines, issues: [...new Set(issues)],
    status: inconclusive ? 'inconclusive' : issues.length ? 'mismatch' : 'pass'};
}

export function compareRun(samples, observations, {requiredCoverage = true, requireControl = true} = {}) {
  validateManifest(samples, requiredCoverage);
  const en = validateRows(samples, observations['native-en'], 'native-en');
  const zh = validateRows(samples, observations['native-zh'], 'native-zh');
  const pt = validateRows(samples, observations['polyglot-zh'], 'polyglot-zh');
  for (const row of [...en.values(), ...zh.values()]) assert.equal(row.created.length, 0, 'Product observer fired in native baseline');
  const results = samples.map(s => compareSample(s, en.get(s.id), zh.get(s.id), pt.get(s.id)));
  if (requireControl) {
    const stone = results.find(row => row.sources.includes('control:stone'));
    assert(stone?.status === 'pass' && stone.requiresSecondary && stone.actualSecondary.length === 1, 'Stone positive control failed: product observer/configuration not verified');
  }
  const counts = {pass: 0, mismatch: 0, inconclusive: 0};
  for (const result of results) counts[result.status]++;
  return {infrastructure: 'passed', compatibility: counts.mismatch || counts.inconclusive ? 'findings' : 'passed', counts, results};
}

export function exitCode(report, strict = false) {
  if (report.infrastructure !== 'passed') return 1;
  return strict && report.compatibility !== 'passed' ? 2 : 0;
}
