const colors = '0123456789abcdef';
const flags = 'klmno';
const empty = () => ({color: 'host', flags: []});
const state = value => ({color: value.color, flags: [...value.flags]});

export function legacyGlyphs(text, {base = empty(), scoped = false, initial = empty()} = {}) {
  if (typeof text !== 'string' || text.length > 32768) return {valid: false, reason: 'text-limit'};
  let current = state(initial);
  const glyphs = [];
  for (let offset = 0; offset < text.length;) {
    if (text[offset] === '§') {
      if (offset + 1 === text.length) return {valid: false, reason: 'terminal-section-sign', offset};
      const code = text[offset + 1].toLowerCase();
      if (colors.includes(code)) current = {color: code, flags: []};
      else if (flags.includes(code)) current.flags = [...new Set([...current.flags, code])].sort();
      else if (code === 'r') current = empty();
      else return {valid: false, reason: 'unknown-code', offset};
      offset += 2;
    } else {
      const character = String.fromCodePoint(text.codePointAt(offset));
      const effective = scoped ? {
        color: current.color === 'host' ? base.color : current.color,
        flags: [...new Set([...base.flags, ...current.flags])].sort(),
      } : state(current);
      glyphs.push({character, ...effective});
      offset += character.length;
    }
  }
  const terminal = scoped ? {
    color: current.color === 'host' ? base.color : current.color,
    flags: [...new Set([...base.flags, ...current.flags])].sort(),
  } : state(current);
  return {valid: true, glyphs, terminal};
}

export function compareSecondaryStyle(nativeName, inserted, format) {
  const parsedBase = legacyGlyphs(format);
  if (!parsedBase.valid || parsedBase.glyphs.length) return {status: 'style-unavailable', reason: 'invalid-base'};
  const expected = legacyGlyphs(nativeName, {base: parsedBase.terminal, scoped: true});
  if (!expected.valid) return {status: 'style-unsupported', reason: expected.reason};
  const actual = legacyGlyphs(inserted);
  if (!actual.valid) return {status: 'style-invalid', reason: actual.reason};
  const visible = glyphs => glyphs.map(glyph => glyph.character).join('');
  if (visible(expected.glyphs) !== visible(actual.glyphs)) return {status: 'style-text-mismatch', expected, actual};
  const mismatches = expected.glyphs.flatMap((glyph, index) => {
    const value = actual.glyphs[index];
    return glyph.color === value.color && glyph.flags.join('') === value.flags.join('') ? [] : [{index, expected: glyph, actual: value}];
  });
  const isolated = actual.terminal.color === 'host' && actual.terminal.flags.length === 0;
  return {status: mismatches.length || !isolated ? 'style-mismatch' : 'style-pass', mismatches, isolated};
}
