import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {Bridge} from '../name-audit/runner/bridge.mjs';

const [port, fixturePath, outputPath] = process.argv.slice(2);
const fixture = path.resolve(fixturePath), output = path.resolve(outputPath);
const launch = JSON.parse(await fs.readFile(path.join(fixture, 'launch.json'), 'utf8'));
const bridge = await Bridge.connect(Number(port));
const call = (method, params = {}) => bridge.call(method, params, 125000);
try {
  const runtime = await call('status');
  assert.equal(runtime.pid, String(launch.pid));
  assert.equal(path.resolve(runtime.gameDirectory).toLowerCase(), fixture.toLowerCase());
  assert.equal(runtime.language, 'en_us');
  await call('config.set', {languages: ['zh_tw']});
  await call('wait.ticks', {ticks: 5});
  const hover = await call('ae.fluid.hover');
  const rendered = await call('screen.render', {name: `fluid-render-${Date.now()}.png`, x: hover.x, y: hover.y});
  assert.deepEqual(rendered.lines, hover.lines);
  await fs.mkdir(output, {recursive: true});
  await fs.copyFile(rendered.path, path.join(output, 'fluid-render.png'));
  await fs.writeFile(path.join(output, 'fluid-render.json'), JSON.stringify({launch, hover, rendered}, null, 2));
  console.log(`Captured native fluid renderer: ${path.join(output, 'fluid-render.png')}`);
} finally { bridge.close(); }
