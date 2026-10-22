import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {hash, equal, canonical} from './name-audit-core.mjs';
import {seal} from './name-audit-pack-core.mjs';

const [enPath, zhPath, productPath, output] = process.argv.slice(2);
assert.ok(output, 'Usage: native-en-hud native-zh-hud product-hud output-file');
const directories = [enPath, zhPath, productPath], checks = [], sessions = [], results = [];
const check = (name, actual, expected) => checks.push({name, pass: equal(actual, expected), ...(!equal(actual, expected) ? {actual, expected} : {})});
for (const directory of directories) {
  const result = JSON.parse(await fs.readFile(path.join(directory, 'result.json'), 'utf8'));
  const bytes = await fs.readFile(result.sessionFile);
  assert.equal(hash(bytes), result.sessionSha256);
  results.push(result); sessions.push(JSON.parse(bytes));
}
check('three independent roles', results.map(row => [row.language, row.product]), [['en_us', false], ['zh_cn', false], ['zh_cn', true]]);
check('completed HUD attempts', results.map(row => row.passed), [true, true, true]);
check('same HUD controller', results.map(row => row.controllerSha256), Array(3).fill(results[0].controllerSha256));
check('same item controller', sessions.map(row => row.controller), Array(3).fill(sessions[0].controller));
const mods = session => session.provenance.mods.filter(mod => mod.id !== 'polyglottooltip').map(mod => [mod.id, mod.version, mod.sha256]).sort((a, b) => canonical(a).localeCompare(canonical(b)));
for (let lane = 1; lane < 3; lane++) {
  check(lane + ': native mod and bridge bytes', mods(sessions[lane]), mods(sessions[0]));
  check(lane + ': resource pack bytes', sessions[lane].provenance.packs, sessions[0].provenance.packs);
}
check('original TOP artifact identity', results.map(row => row.coverage.originalTop), [true, true, true]);
for (const name of ['initial', 'changedType']) {
  const rows = results.map(result => result.cases[name]);
  check(name + ': same native subtype and key', rows.map(row => [row.state.type, row.state.key]), Array(3).fill([rows[0].state.type, rows[0].state.key]));
  const english = rows[0].render.texts[0], chinese = rows[1].render.texts[0];
  check(name + ': independently observed translated title', rows[2].render.texts[0], chinese + (english !== chinese ? ' ｜ ' + english : ''));
  check(name + ': native Chinese body preserved', rows[2].render.texts.slice(1), rows[1].render.texts.slice(1));
}
check('custom literal title', results.map(result => result.cases.custom.render.texts[0]), Array(3).fill('PT named chicken'));
check('custom native Chinese rows preserved', results[2].cases.custom.render.texts, results[1].cases.custom.render.texts);
check('removed entity absent', results.map(result => result.coverage.removedEntityAbsent), [true, true, true]);
check('HUD clears after aiming away', results.map(result => result.coverage.hudClearsAfterAimingAway), [true, true, true]);
const report = {schema: 1, scope: 'Observed TOP chicken subtype, custom-name and removal cases',
  sessions: results.map(result => result.sessionSha256), resultHashes: await Promise.all(directories.map(async directory => hash(await fs.readFile(path.join(directory, 'result.json'))))),
  checks, observedChecksPassed: checks.every(check => check.pass), configurationAdjudicated: false,
  compatibilityAccepted: false, fixture: 'Adult chicken with native jockey flag and fixed egg timer',
  untested: ['registry replacement', 'disabled entry', 'resource generation change', 'unknown providers', 'TOPCE', 'Hwyla', 'natural egg-timer body']};
await seal(path.resolve(output), report);
console.log(JSON.stringify({observedChecksPassed: report.observedChecksPassed, failures: checks.filter(check => !check.pass)}, null, 2));
