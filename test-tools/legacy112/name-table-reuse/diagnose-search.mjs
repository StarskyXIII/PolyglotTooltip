import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {Bridge} from '../../name-audit/runner/bridge.mjs';
import {root, read, save, sameOwned, pause} from '../name-table/runtime-common.mjs';

const [pack, attempt] = process.argv.slice(2);
assert.ok(['E2E', 'Meatball'].includes(pack));
assert.match(attempt, /^core-[a-z0-9-]+$/);
const run = path.join(root, 'docs/research/legacy112/name-table-reuse-2026-10-14', `run-${pack.toLowerCase()}-${attempt}`);
const config = await read(path.join(run, 'config.json')), owner = await read(path.join(run, 'process.json'));
const deadline = Date.now() + 900000;
while (!await fs.stat(path.join(run, 'diagnostic-ready.json')).catch(() => null)) {
  assert.ok(Date.now() < deadline, 'Diagnostic boot deadline'); await pause(1000);
}
const bridge = await Bridge.connect(config.port);
const call = (name, parameters = {}) => bridge.call(name, parameters, 240000);
try {
  sameOwned(await call('status'), config, owner);
  await save(path.join(run, 'search-performance-before.json'), await call('probe.performance', {action: 'state'}));
  await save(path.join(run, 'search-primary-control.json'), await call('jei.search', {query: '鐵錠'}));
  await save(path.join(run, 'search-secondary-initial.json'), await call('jei.search', {query: 'iron ingot'}));
  const observations = [], searchDeadline = Date.now() + 900000;
  let search, input, performance;
  do {
    performance = await call('probe.performance', {action: 'state'});
    input = await call('probe.nameReuse');
    search = await call('jei.observe');
    const value = {at: new Date().toISOString(), performance, table: input.table,
      query: search.query, count: search.count, iron: search.rows.some(row => row.id === 'minecraft:iron_ingot')};
    observations.push(value);
    await fs.writeFile(path.join(run, 'search-progress.json'), JSON.stringify(value, null, 2));
    await fs.writeFile(path.join(run, 'search-observations.json'), JSON.stringify(observations, null, 2));
    console.log(JSON.stringify({at: value.at, cursor: performance.aliasCursor, size: performance.aliasSize,
      ready: performance.aliasReady, revision: performance.libraryRevision, generation: input.table.generation,
      loads: input.table.loads, builds: input.table.builds, iron: value.iron}));
    if (value.iron) break;
    await pause(5000);
  } while (Date.now() < searchDeadline);
  await save(path.join(run, 'search-secondary-final.json'), search);
  await save(path.join(run, 'search-diagnostic-result.json'), {at: new Date().toISOString(), performance, table: input.table,
    query: search.query, count: search.count, iron: search.rows.some(row => row.id === 'minecraft:iron_ingot')});
} finally { bridge.close(); }
