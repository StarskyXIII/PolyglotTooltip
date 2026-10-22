import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {corpus, read, save, hashFile} from './runtime-common.mjs';

for (const pack of ['E2E', 'Meatball']) {
  const directory = path.join(corpus, pack), samples = await read(path.join(directory, 'samples.json'));
  const {stone} = await read(path.join(directory, 'controls.json'));
  const tooltips = await read(path.join(directory, 'tooltip-samples.json'));
  const custom = samples.find(row => row.stack.value?.tag?.value?.display?.value?.Name?.type === 8);
  const rejected = samples.find(row => Object.values(row.historicalTargets).some(value => value.rejection));
  assert.ok(custom && rejected, 'Need actual custom and actual rejected tooltip controls');
  const unique = values => [...new Map(values.map(row => [row.sampleId, row])).values()];
  const value = {schema: 'native-name-table-frozen-runtime-selection-v1', pack,
    selected: samples, acquisition: unique([...samples, stone]),
    extraAcquisition: samples.some(row => row.sampleId === stone.sampleId) ? [] : [stone.sampleId],
    tooltips: unique([...tooltips, custom, rejected]),
    tooltipControls: {actualCustom: custom.sampleId, historicalRejected: rejected.sampleId}, stone,
    editedControls: [
      {...stone, sampleId: 'control-count', edit: {count: 7}},
      {...stone, sampleId: 'control-unseen-nbt', edit: {unknownNbt: 1122026}},
      {...stone, sampleId: 'control-damage', edit: {damage: 15}},
      {...stone, sampleId: 'control-literal', edit: {literalName: '原型 Literal §bName 112'}},
    ], sources: await Promise.all(['samples.json', 'controls.json', 'tooltip-samples.json', 'sample-seal.json'].map(async file => ({file, sha256: await hashFile(path.join(directory, file))}))) };
  const file = path.join(directory, 'runtime-selection.json');
  await save(file, value);
  await save(path.join(directory, 'runtime-selection-seal.json'), {file: path.basename(file), sha256: await hashFile(file),
    sealedBeforeFreshCapture: true, selectedStates: samples.length, acquisitionStates: value.acquisition.length,
    tooltipStates: value.tooltips.length, tooltipControls: value.tooltipControls});
  console.log(JSON.stringify({pack, selected: samples.length, acquisition: value.acquisition.length, tooltips: value.tooltips.length}));
}
