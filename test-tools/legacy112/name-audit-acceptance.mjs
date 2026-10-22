import {compareSample, equal} from './name-audit-core.mjs';
import {compareSecondaryStyle, legacyGlyphs} from './name-audit-style.mjs';

const result = (status, detail = {}) => ({status, ...detail});
const sameStyle = (left, right) => left.color === right.color && equal(left.flags, right.flags);

export function compareAnnotation(nativeLine, actualLine, nativeAliases) {
  const inherited = {initial: {color: 'inherited', flags: []}};
  const original = legacyGlyphs(nativeLine, inherited), actual = legacyGlyphs(actualLine, inherited);
  if (!original.valid || !actual.valid || !original.glyphs.length) return result('unavailable', {reason: 'invalid-style'});
  if (!actualLine.startsWith(nativeLine)) return result('fail', {reason: 'original-prefix-changed'});
  const base = original.glyphs[0];
  if (!original.glyphs.every(glyph => sameStyle(glyph, base))) return result('unavailable', {reason: 'mixed-native-style'});
  const expected = [...original.glyphs];
  for (const alias of nativeAliases) {
    const parsed = legacyGlyphs(alias);
    if (!parsed.valid || !parsed.glyphs.length || !parsed.glyphs.every(glyph => sameStyle(glyph, parsed.glyphs[0]))) {
      return result('unavailable', {reason: 'mixed-target-style'});
    }
    expected.push(...[...' ｜ '].map(character => ({character, color: base.color, flags: base.flags})));
    expected.push(...parsed.glyphs.map(glyph => ({character: glyph.character, color: base.color, flags: base.flags})));
  }
  return result(equal(expected, actual.glyphs) && sameStyle(original.terminal, actual.terminal) ? 'pass' : 'fail',
    {expected, actual: actual.glyphs, terminalPreserved: sameStyle(original.terminal, actual.terminal)});
}

export function comparePreservation(nativeRows, actualRows, annotations = []) {
  if (nativeRows.length !== actualRows.length) return result('fail', {reason: 'row-count'});
  const indices = new Set();
  const checks = [];
  for (const annotation of annotations) {
    const {index, native, targets} = annotation;
    if (!Number.isInteger(index) || index < 1 || index >= nativeRows.length || indices.has(index)
        || !native || native.line !== nativeRows[index] || native.inserted !== true || native.surviving !== true
        || typeof native.enchantment !== 'string' || !native.enchantment || !Number.isInteger(native.level)
        || !/^[0-9a-f]{64}$/.test(native.sourceSha256 ?? '') || !Array.isArray(targets)
        || targets.some(target => !target || target.enchantment !== native.enchantment || target.level !== native.level
          || target.inserted !== true || typeof target.line !== 'string' || !/^[0-9a-f]{64}$/.test(target.sourceSha256 ?? ''))) {
      return result('unavailable', {reason: 'unbound-annotation'});
    }
    indices.add(index);
    checks.push({index, ...compareAnnotation(native.line, actualRows[index], targets.map(target => target.line))});
  }
  for (let index = 0; index < nativeRows.length; index++) {
    if (!indices.has(index) && nativeRows[index] !== actualRows[index]) return result('fail', {reason: 'unowned-change', index});
  }
  return result(checks.every(check => check.status === 'pass') ? 'pass' : checks.some(check => check.status === 'fail') ? 'fail' : 'unavailable', {annotations: checks});
}

export function compareSameCallBody(product) {
  const snapshot = product?.nativeBodyObservation;
  const boundedRows = rows => Array.isArray(rows) && rows.length <= 1024
    && rows.every(row => typeof row === 'string' && row.length <= 8192)
    && rows.reduce((total, row) => total + row.length, 0) <= 262144;
  if (product?.captureFailed !== false || snapshot?.captured !== true || snapshot.rootList !== true
      || !boundedRows(snapshot.rows) || !boundedRows(product.tooltip)
      || !Array.isArray(product.created) || product.created.length > 128
      || !Array.isArray(product.nativeEnchantments) || product.nativeEnchantments.length > 256) {
    return result('unavailable', {reason: 'missing-same-call-body'});
  }
  const used = new Set(), removals = [], replacements = [];
  const bind = (indices, text) => {
    if (!Array.isArray(indices) || indices.length !== 1 || typeof text !== 'string') return null;
    const index = indices[0];
    if (!Number.isInteger(index) || index < 1 || index >= product.tooltip.length || used.has(index)
        || product.tooltip[index] !== text) return null;
    used.add(index);
    return index;
  };
  for (const created of product.created) {
    if (!created || typeof created.rootList !== 'boolean') return result('unavailable', {reason: 'unbound-creation'});
    if (!created.rootList) continue;
    if (created.tooltipFrames !== 1) return result('unavailable', {reason: 'unbound-creation'});
    const index = bind(created.indices, created.text);
    if (index === null) return result('unavailable', {reason: 'unbound-creation'});
    removals.push(index);
  }
  for (const receipt of product.nativeEnchantments) {
    if (!receipt || typeof receipt.rootList !== 'boolean') return result('unavailable', {reason: 'unbound-annotation'});
    if (!receipt.rootList) continue;
    if (receipt.annotatedLine === undefined) {
      if (receipt.annotationIndices !== undefined) return result('unavailable', {reason: 'unbound-annotation'});
      continue;
    }
    if (receipt.inserted !== true || typeof receipt.line !== 'string' || receipt.line.length > 8192) return result('unavailable', {reason: 'unbound-annotation'});
    const index = bind(receipt.annotationIndices, receipt.annotatedLine);
    if (index === null) return result('unavailable', {reason: 'unbound-annotation'});
    replacements.push([index, receipt.line]);
  }
  const restored = [...product.tooltip];
  for (const [index, text] of replacements) restored[index] = text;
  for (const index of removals.sort((left, right) => right - left)) restored.splice(index, 1);
  if (!equal(restored, snapshot.rows)) return result('fail', {reason: 'same-call-body-changed', expected: snapshot.rows, actual: restored});
  return result('pass', {scope: 'post-forge-before-append', creations: removals.length, annotations: replacements.length});
}

export function assessSample(sample, english, chinese, product, options = {}) {
  const strict = compareSample(sample, english, chinese, product);
  const names = compareSample(sample, english, chinese, product, {allowMatchingNativeMutation: true});
  const dimensions = Object.fromEntries(['text', 'style', 'insertion', 'preservation', 'typedState', 'lifecycle', 'integration']
    .map(name => [name, result('unavailable')]));
  if (!names.createdEnglish || !Array.isArray(product?.tooltip) || !Array.isArray(chinese?.tooltip)) return {strict, names, dimensions, accepted: false};
  const insertions = names.createdEnglish;
  dimensions.text = result(names.expectedSecondary ? insertions.length === 1 ? 'pass' : 'fail' : insertions.length === 0 ? 'not-needed' : 'fail');
  dimensions.insertion = result(names.unattributedCreations.length || names.otherCreatedRows.length
    || insertions.some(row => row.indices[0] !== 1) ? 'fail' : 'pass');
  if (!names.expectedSecondary && !insertions.length) dimensions.style = result('not-needed');
  else if (insertions.length === 1 && typeof options.secondaryFormat === 'string') {
    const raw = english.nameObservation?.nativeNameCaptured ? english.nameObservation.nativeName : english.tooltip[0];
    const style = compareSecondaryStyle(raw, insertions[0].text, options.secondaryFormat);
    dimensions.style = {...style, status: style.status === 'style-pass' ? 'pass' : style.status === 'style-mismatch' ? 'fail' : 'unavailable'};
  }
  const insertedIndices = new Set(insertions.flatMap(row => row.indices));
  dimensions.preservation = comparePreservation(chinese.tooltip, product.tooltip.filter((_, index) => !insertedIndices.has(index)), options.annotations);
  dimensions.typedState = result([english, chinese, product].every(row => equal(row.before, sample.stack))
    && equal(english.after, chinese.after) && equal(chinese.after, product.after) ? 'pass' : 'fail',
    {nativeMutation: !equal(chinese.before, chinese.after), strictStatus: strict.status});
  const conclusive = Object.values(dimensions).every(dimension => ['pass', 'not-needed'].includes(dimension.status));
  return {strict, names, dimensions, accepted: conclusive};
}
