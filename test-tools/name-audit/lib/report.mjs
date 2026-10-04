const cell = value => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('|', '\\|').replaceAll(/\r?\n/g, ' ');
export function coverage(samples) {
  const items = {};
  for (const sample of samples) {
    const item = items[sample.item] ??= {variants: 0, sources: []};
    item.variants++;
    item.sources = [...new Set([...item.sources, ...sample.sources])].sort();
  }
  return {sampleCount: samples.length, itemCount: Object.keys(items).length, items};
}
export function markdownReport(comparison, samples) {
  const groups = new Map();
  for (const result of comparison.results.filter(row => row.status !== 'pass')) {
    const key = `${result.item}|${result.status}|${result.issues.join(',')}`;
    const group = groups.get(key) ?? {example: result, count: 0};
    group.count++; groups.set(key, group);
  }
  const lines = ['# Item name audit', '',
    `Infrastructure: **${comparison.infrastructure}**. Compatibility: **${comparison.compatibility}**.`, '',
    `Samples: ${samples.length}. Pass: ${comparison.counts.pass}; mismatch: ${comparison.counts.mismatch}; inconclusive: ${comparison.counts.inconclusive}.`, '',
    'Scope: Minecraft 1.21.1 / NeoForge 21.1.249; Reliquary and Malum release JARs; collected creative/search variants and registry defaults. The oracle is the final native English tooltip title with Polyglot absent. Render evidence uses a dedicated screen calling the native ItemStack tooltip renderer.', '',
    '## Findings grouped by item and issue', '',
    '| Item | Status | Samples | Issues | Native English example | Product secondary example |',
    '| --- | --- | ---: | --- | --- | --- |'];
  for (const {example, count} of groups.values()) lines.push(`| ${cell(example.item)} | ${example.status} | ${count} | ${cell(example.issues.join(', '))} | ${cell(example.expectedEnglishTitle)} | ${cell(example.actualSecondary.map(row => row.text).join(' / ') || '(none)')} |`);
  lines.push('', '## Controls', '', '| Control | Status | Issues |', '| --- | --- | --- |');
  for (const result of comparison.results.filter(row => row.sources.some(source => source.startsWith('control:')))) {
    lines.push(`| ${cell(result.sources.filter(source => source.startsWith('control:')).join(', '))} | ${result.status} | ${cell(result.issues.join(', '))} |`);
  }
  const additions = comparison.results.filter(row => row.additionalBodyLines.length > 0);
  lines.push('', '## Additional body lines', '', 'These lines are recorded separately. Native lines must remain in order, and extra copies of either title still fail.', '', '| Item | Added details |', '| --- | --- |');
  for (const row of additions) lines.push(`| ${cell(row.item)} | ${cell(row.additionalBodyLines.join(' / '))} |`);
  lines.push('', 'Raw components, full stack data, per-sample differences and screenshots are retained beside this report. Findings are not accepted expectations. Unobserved variants and other tooltip hosts are outside this run.', '');
  return lines.join('\n');
}
