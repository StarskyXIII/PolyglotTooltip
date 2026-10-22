import assert from 'node:assert/strict';
import {run} from './integration-runner.mjs';

const outputKeys = state => state.outputs.filter(row => !row.empty).map(row => row.key);
const stable = state => ({filter: state.filter, page: state.page, available: state.available, knowledge: state.knowledge, outputs: state.outputs, provider: {full: state.provider.full, emc: state.provider.emc, knowledge: state.provider.knowledge, inputs: state.provider.inputs}});
await run(async ({call, check, observations: o, runtime, product, configure, baseline, render}) => {
  if (product) await configure(['zh_cn']);
  const setup = async params => {
    const server = await call('pe.world', {prepare: true, ...params});
    let synced;
    for (let i = 0; i < 100; i++) { synced = await call('pe.observe'); if (synced.provider.emc === server.provider.emc && synced.provider.knowledge.length === server.provider.knowledge.length && JSON.stringify(synced.provider.inputs) === JSON.stringify(server.provider.inputs)) break; await call('wait.ticks', {ticks: 3}); }
    check('native ProjectE client knowledge sync completed', synced.provider.knowledge.length, server.provider.knowledge.length);
    check('native ProjectE EMC sync completed', synced.provider.emc, server.provider.emc);
    check('native ProjectE input and lock sync completed', synced.provider.inputs, server.provider.inputs);
    await call('pe.world', {open: true});
    for (let i = 0; i < 100; i++) { if ((await call('status')).screen.endsWith('.GUITransmutation')) break; await call('wait.ticks', {ticks: 3}); }
    const opened = await call('pe.observe'); assert.ok(opened.screen); await render('pe-open'); return {server, synced, opened};
  };
  o.setup = await setup({}); check('native learned domain supports pagination', o.setup.opened.provider.knowledge.length > 24, true);
  o.predicates = [];
  for (const row of [
    {query: 'stone', predicate: 'minecraft:stone', expected: true},
    {query: '石头', predicate: 'minecraft:stone', expected: product},
    {query: 'shitou', predicate: 'minecraft:stone', expected: product && runtime.mods.some(mod => mod.id === 'jecharacters')},
    {query: '', predicate: 'minecraft:stone', expected: true},
    {query: 'pt-no-such-title', predicate: 'minecraft:stone', expected: false},
    {query: 'literal', predicate: 'minecraft:stone', custom: 'Literal name 42', expected: true},
    {query: '石头', predicate: 'minecraft:stone', custom: 'Literal name 42', expected: false}
  ]) {
    const {expected, ...params} = row, result = await call('pe.ui', params); o.predicates.push({params, result});
    check(`native transformed ProjectE predicate ${JSON.stringify(params)}`, result.predicate, expected);
  }
  await call('pe.ui', {query: ''}); o.pageZero = await call('pe.observe');
  o.pageOne = await call('pe.ui', {next: true}); check('native ProjectE next-page control advances', o.pageOne.page, 1);
  if (baseline) check('native unfiltered pagination ordering preserved', stable(o.pageOne), stable(baseline.pageOne));
  await call('language.set', {language: 'zh_cn'});
  if (product) await configure(['en_us']);
  o.englishQuery = await call('pe.ui', {query: 'wool'});
  if (product) {
    check('English secondary domain has multiple native pages', o.englishQuery.knowledge.length > 12, true);
    o.englishPageOne = await call('pe.ui', {next: true}); check('secondary native query reaches page one', o.englishPageOne.page, 1);
    o.shrink = await render('pe-generation-shrink', {integration: 'pe', configuration: {languages: [], alwaysShow: false}});
    check('ProjectE shrink executes one uncanceled native frame', [o.shrink.frames, o.shrink.innerPreCanceled], [1, false]);
    check('generation change leaves pre-draw native cache untouched', o.shrink.changed, o.shrink.before);
    check('first native draw clamps invalid page after shrink', o.shrink.after.page, 0);
    check('first native draw removes secondary-only results', outputKeys(o.shrink.after), []);
    check('generation retains native screen inventory provider and query', [o.shrink.after.screen, o.shrink.after.inventory, o.shrink.after.provider, o.shrink.after.query, o.shrink.after.filter], [o.shrink.before.screen, o.shrink.before.inventory, o.shrink.before.provider, o.shrink.before.query, o.shrink.before.filter]);
    o.expand = await render('pe-generation-expand', {integration: 'pe', configuration: {languages: ['en_us'], alwaysShow: false}});
    check('ProjectE expansion executes one uncanceled native frame', [o.expand.frames, o.expand.innerPreCanceled], [1, false]);
    check('first draw restores retained secondary query', o.expand.after.knowledge.length > 12, true);
    check('restored query preserves clamped page', o.expand.after.page, 0);
  } else check('native-only secondary query has no output', outputKeys(o.englishQuery), []);
  await call('language.set', {language: 'en_us'}); if (product) await configure(['zh_cn']);
  o.locks = [];
  for (const params of [{lock: 'minecraft:diamond', emc: 1000000000}, {lock: 'minecraft:coal', emc: 1000000000}, {lock: 'minecraft:diamond', emc: 1}]) {
    const row = {params, setup: await setup(params)}; o.locks.push(row);
    row.miss = await call('pe.ui', {query: 'pt-no-such-title'});
    check('native lock remains in actual input slot eight', row.miss.provider.inputs[8].key, params.lock + ':0');
    check('native lock bypass and affordability preserved', outputKeys(row.miss), params.emc === 1 ? [] : [params.lock + ':0']);
    if (params.emc !== 1) check('native lock uses correct fuel or ordinary output slot', row.miss.outputs[params.lock.endsWith(':coal') ? 12 : 0].key, params.lock + ':0');
    row.shot = await render('pe-lock', {integration: 'pe', ...(product ? {configuration: {languages: [], alwaysShow: false}} : {})});
    check('generation retains EMC lock and provider state', row.shot.after.provider, row.shot.before.provider);
    check('generation preserves native lock outputs', outputKeys(row.shot.after), outputKeys(row.miss));
    if (baseline) check('lock behavior equals separate native process', stable(row.shot.after), stable(baseline.locks.find(other => JSON.stringify(other.params) === JSON.stringify(params)).shot.after));
    if (product) await configure(['zh_cn']);
  }
  await call('screen.close');
});
