import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {run} from './integration-runner.mjs';

const source = JSON.parse(await fs.readFile(process.argv[6], 'utf8'));
await run(async ({call, check, observations: o, render, runtime, fixture}) => {
  assert.ok(source.checks.length && source.checks.every(check => check.pass));
  const launch = JSON.parse(await fs.readFile(path.join(fixture, 'launch.json'), 'utf8'));
  assert.deepEqual(launch.artifacts, source.launch.artifacts); assert.deepEqual(launch.libraries, source.launch.libraries);
  check('restart uses the same owned fixture', runtime.gameDirectory, source.observations.runtime.gameDirectory);
  check('restart uses a different native JVM', runtime.pid !== source.observations.runtime.pid, true);
  check('restart preserves native platform and language-reload setting', [runtime.java, runtime.selectiveResourceReload], [source.observations.runtime.java, source.observations.runtime.selectiveResourceReload]);
  assert.ok(source.observations.restart.length >= 4); o.cases = [];
  for (const previous of source.observations.restart) {
    const expectedPath = path.join(fixture, 'config', 'polyglot-devbridge', `carbon-${previous.name}.cfg`);
    assert.equal(path.resolve(previous.file).toLowerCase(), expectedPath.toLowerCase());
    assert.equal(createHash('sha256').update(await fs.readFile(expectedPath)).digest('hex'), previous.sha256);
    await call('screen.close'); const opened = await call('carbon.open', {name: previous.name, requireExisting: true});
    check(`${previous.name} native restart opens existing fixture`, opened.existed, true);
    let state, shot;
    for (let attempt = 0; attempt < 7; attempt++) {
      shot = await render('carbon-restart'); state = await call('carbon.observe');
      if (state.entries.some(row => row.class.endsWith('.StringElement'))) break;
      const entry = state.entries.find(row => row.class.endsWith('$ConfigEntry') || row.class.endsWith('$DirectConfig')) || state.entries.find(row => row.class.endsWith('.ArrayElement')) || state.entries.find(row => row.class.endsWith('.FolderElement'));
      assert.ok(entry, 'Missing native Carbon restart navigation');
      const button = entry.controls.find(button => ['open', 'button', 'textBox'].includes(button.role)); assert.ok(button);
      await call('carbon.action', {action: 'control', identity: button.identity});
    }
    o.cases.push({name: previous.name, state, shot});
    check(`${previous.name} restart reads exact existing file`, [state.file, state.sha256], [previous.file, previous.sha256]);
    check(`${previous.name} native property and file survive JVM restart`, [state.property, state.disk], [previous.expected, previous.expected]);
    check(`${previous.name} native visible row order survives JVM restart`, state.entries.filter(row => row.class.endsWith('.StringElement')).map(row => row.text), previous.expected);
    check(`${previous.name} restart inspection does not save`, state.callbacks.events.filter(event => event.event.endsWith('ConfigChangedEvent')).length, 0);
    check(`${previous.name} restart observer reports no error`, state.callbacks.error, undefined);
  }
  await call('screen.close');
});
