import fs from 'node:fs/promises';
import path from 'node:path';
import {pathToFileURL} from 'node:url';

const statuses = new Set(['translated', 'same-as-native', 'literal', 'unresolved', 'error']);

// Product output identifies candidates. Only an independent native-language baseline can confirm a wrong name.
export function summarizeExport(manifest, summary, rows, readErrors = []) {
  const errors = [...readErrors], mods = Object.create(null), candidates = [], warnings = [];
  const languages = manifest?.languages;
  if (manifest?.schema !== 'polyglot-name-export-v1') errors.push('Unsupported or missing manifest schema');
  if (!Array.isArray(languages) || !languages.length || new Set(languages).size !== languages.length) errors.push('Invalid manifest languages');
  const mod = id => mods[id] ??= {items: 0, sourceErrors: 0, stateChanges: 0, languages: Object.create(null)};
  for (const value of manifest?.mods ?? []) mod(value.id);
  let items = 0, sourceErrors = 0;
  const seen = new Set();
  for (const [index, row] of rows.entries()) {
    if (row.sequence !== index) errors.push(`Row ${index}: invalid sequence`);
    const owner = mod(row.mod ?? '(unknown)');
    if (row.kind === 'source-error') {
      sourceErrors++; owner.sourceErrors++;
      warnings.push({sequence: row.sequence, mod: row.mod, item: row.item, source: row.source, error: row.error});
      continue;
    }
    if (row.kind !== 'item') { errors.push(`Row ${index}: invalid kind`); continue; }
    items++; owner.items++;
    if (!row.sampleId || !row.stack || !row.serialized || !Number.isInteger(row.count) || seen.has(row.sampleId)) errors.push(`Row ${index}: invalid or duplicate stack identity`);
    seen.add(row.sampleId);
    if (row.stateChanged) owner.stateChanges++;
    const values = Array.isArray(row.languages) ? row.languages : [];
    const actual = values.map(value => value?.language);
    if (!Array.isArray(actual) || JSON.stringify(actual) !== JSON.stringify(languages)) errors.push(`Row ${index}: language coverage mismatch`);
    for (const value of values) {
      if (!value || !statuses.has(value.status) || (['translated', 'same-as-native', 'literal'].includes(value.status) && typeof value.name !== 'string')) {
        errors.push(`Row ${index}: invalid language result`); continue;
      }
      const counts = owner.languages[value.language] ??= Object.fromEntries([...statuses].map(status => [status, 0]));
      counts[value.status]++;
      if (value.status === 'unresolved' || value.status === 'error') candidates.push({
        sequence: row.sequence, sampleId: row.sampleId, mod: row.mod, item: row.item, metadata: row.metadata,
        damage: row.damage, count: row.count, stack: row.stack, serialized: row.serialized,
        nativeName: row.nativeName, language: value.language, status: value.status, reason: value.reason,
      });
    }
  }
  if (!summary || !Number.isInteger(summary.processed) || !Number.isInteger(summary.written)
      || summary.processed !== rows.length || summary.written !== rows.length) errors.push('Missing footer or row counts disagree');
  if (summary?.failure) errors.push(`Export failed: ${summary.failure}`);
  const clean = errors.length === 0;
  return {
    schema: 'polyglot-name-export-report-v1', source: manifest?.source, selector: manifest?.selector,
    primaryLanguage: manifest?.primaryLanguage, languages, state: summary?.state ?? 'UNFINISHED',
    integrityValid: clean,
    selectedSourceComplete: clean && summary?.state === 'COMPLETE' && summary?.sourceExhausted === true && sourceErrors === 0,
    scope: 'Only the selected finite source. This does not establish complete mod, NBT, tooltip-title or enchantment coverage.',
    interpretation: 'Unresolved/error rows are candidates, not confirmed translation defects. Same-as-native may be legitimate or fallback. Translated names require independent verification.',
    counts: {rows: rows.length, items, sourceErrors, candidateLanguageResults: candidates.length},
    errors, mods, warnings, candidates,
  };
}

export async function readExport(directory) {
  const errors = [];
  const read = async name => {
    try { return JSON.parse(await fs.readFile(path.join(directory, name), 'utf8')); }
    catch (error) { errors.push(`${name}: ${error.code ?? error.message}`); return null; }
  };
  const manifest = await read('manifest.json'), summary = await read('summary.json');
  try { await fs.access(path.join(directory, 'failure.json')); errors.push('Writer failure marker exists'); }
  catch (error) { if (error.code !== 'ENOENT') errors.push(`failure.json: ${error.code}`); }
  const names = (await fs.readdir(directory)).filter(name => ['names.jsonl', 'names.partial.jsonl'].includes(name));
  if (names.length !== 1) errors.push('Expected exactly one JSONL output');
  const rows = [];
  if (names.length === 1) {
    const input = await fs.readFile(path.join(directory, names[0]), 'utf8');
    if (input && !input.endsWith('\n')) errors.push('Unterminated final JSONL row');
    const lines = input.split(/\r?\n/); if (lines.at(-1) === '') lines.pop();
    for (const [index, line] of lines.entries()) {
      try { const value = JSON.parse(line); if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Expected object'); rows.push(value); }
      catch (error) { errors.push(`JSONL line ${index + 1}: ${error.message}`); }
    }
    if ((summary?.state === 'COMPLETE') !== (names[0] === 'names.jsonl')) errors.push('Footer state disagrees with output filename');
  }
  return summarizeExport(manifest, summary, rows, errors);
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  if (!process.argv[2] || !process.argv[3]) throw new Error('Usage: node name-export-report.mjs EXPORT_DIRECTORY OUTPUT_JSON');
  const report = await readExport(path.resolve(process.argv[2]));
  await fs.writeFile(path.resolve(process.argv[3]), JSON.stringify(report, null, 2) + '\n', {flag: 'wx'});
  console.log(JSON.stringify({state: report.state, selectedSourceComplete: report.selectedSourceComplete, counts: report.counts, errors: report.errors}));
  if (!report.integrityValid) process.exitCode = 1;
}
