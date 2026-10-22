import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';

export async function validateScoped({call, record, pause, run, config, attempt}) {
  const reference = await call('probe.nameReuse', {action: 'scopeState'});
  await record('scope-reference', reference);
  assert.equal(reference.state, 'waiting-for-saved-reference', 'One fresh native reference is sealed');
  const globals = await call('probe.nameReuse', {action: 'scopeGlobals'});
  await record('scope-globals-before', globals);
  await call('screen.close');
  const sample = async () => {
    const scope = await call('probe.nameReuse', {action: 'scopeState'});
    const input = await call('probe.nameReuse');
    const performance = await call('probe.performance', {action: 'state'});
    const visible = await call('jei.observe');
    return {at: new Date().toISOString(), scope, input, performance,
      iron: visible.rows.some(row => row.id === 'minecraft:iron_ingot'),
      filtered: visible.query === 'iron ingot' && !visible.rows.some(row => row.id === 'minecraft:stone'), query: visible.query,
      game: await call('status')};
  };
  const baseline = [];
  await call('probe.performance', {action: 'monitor'});
  const baselineDeadline = Date.now() + 20000;
  while (Date.now() < baselineDeadline) { baseline.push(await sample()); await pause(1000); }
  await record('scope-baseline-observations', baseline);
  await record('scope-baseline-performance', await call('probe.performance', {action: 'report', raw: true}));
  await call('probe.performance', {action: 'monitor'});
  const start = await call('probe.nameReuse', {action: 'scopeStart'});
  await record('scope-start', start);
  const observations = [], windows = [];
  let windowStart = Date.now(), final, screenshot;
  const deadline = Date.now() + 1800000;
  while (Date.now() < deadline) {
    const value = await sample(); observations.push(value); final = value.scope;
    await fs.writeFile(path.join(run, 'scope-progress.json'), JSON.stringify(value, null, 2));
    if (!screenshot && final.state === 'validating') {
      screenshot = {at: new Date().toISOString(), scope: final,
        result: await call('screenshot', {name: `${attempt}-scoped-progress.png`})};
      await record('scope-screenshot', screenshot);
      await fs.copyFile(screenshot.result.path, path.join(run, 'scoped-progress.png'));
    }
    if (['done', 'unavailable', 'stopped'].includes(final.state)) break;
    if (Date.now() - windowStart > 50000) {
      windows.push({at: new Date().toISOString(), endingScope: final,
        performance: await call('probe.performance', {action: 'report', raw: true})});
      await call('probe.performance', {action: 'monitor'}); windowStart = Date.now();
    }
    await pause(1000);
  }
  windows.push({at: new Date().toISOString(), endingScope: final,
    performance: await call('probe.performance', {action: 'report', raw: true})});
  await record('scope-observations', observations); await record('scope-performance-windows', windows);
  const after = await call('probe.nameReuse', {action: 'scopeGlobals'});
  await record('scope-globals-after', after);
  await record('scope-result', {reference, final, baselineSamples: baseline.length, samples: observations.length,
    unchangedGlobals: JSON.stringify(globals) === JSON.stringify(after),
    availabilityFailures: observations.filter(value => !value.input.table.ready || !value.performance.aliasReady || !value.iron || !value.filtered).length,
    measurement: 'Same stationary in-world scene; START-to-next-START frame intervals; identical 1-second polling. Monitoring starts before scopeStart; one marked progress screenshot is included in the first window; resets between windows leave explicit small gaps. Search evidence concerns retained filter results with the GUI closed. Same-launch native reference does not prove cold-loader equivalence.'});
  assert.equal(final.state, 'done', 'Scoped validation completed');
  assert.equal(final.failure, '', 'No validation failure');
  assert.ok(final.languages.length > 1, 'At least one non-primary target was compared');
  assert.ok(final.completedRows > 0, 'Nonzero complete paired rows');
  assert.equal(final.processed, final.cohort, 'Every retained raw key was classified');
  assert.equal(final.completedRows + Object.values(final.unavailable).reduce((a, b) => a + b, 0), final.cohort);
  assert.equal(final.mismatchCount, 0, 'No admitted paired native-name mismatch');
  assert.equal(final.comparisons, final.matched + final.mismatchCount);
  assert.equal(final.unavailable['validation-callback-unavailable'] ?? 0, 0, 'No admitted callback was lost');
  assert.equal(final.comparisons, final.expectedComparisonsForAdmitted, 'Every admitted pair completed');
  assert.equal(final.completedRows, final.fullyMatchedRows, 'Every completed row matched every language and primary repeat');
  assert.deepEqual(after, globals, 'Global dictionaries, language timestamp and font flags unchanged');
  assert.ok(observations.some(value => value.scope.state === 'validating') && final.comparisons > start.comparisons,
    'Actual scoped work occurred within the monitored interval');
  assert.ok(baseline.length > 0 && [...baseline, ...observations].every(value => value.input.table.ready && value.performance.aliasReady && value.iron && value.filtered), 'Search remains available');
  assert.ok(windows.every(value => value.performance.frameIntervalMs.count > 0 && !value.performance.capped
    && value.performance.expired === false), 'Observed unexpired and uncapped real frame intervals');
}
