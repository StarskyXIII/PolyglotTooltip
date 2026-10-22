import {run} from './integration-runner.mjs';

const keys = state => state.visible.filter(row => row.class === 'KeyEntry').map(row => row.key);
const bindings = state => state.visible.filter(row => row.class === 'KeyEntry').map(({key,category,code,modifier,label})=>({key,category,code,modifier,label}));
const modes = ({query, conflicts, none, toggleFreeKeys, sortingType, boxSearchCategory, boxSearchKey}) => ({query, conflicts, none, toggleFreeKeys, sortingType, boxSearchCategory, boxSearchKey});
await run(async ({call, check, observations: o, runtime, product, configure, baseline, render}) => {
  if (product) await configure(['zh_cn']);
  o.opened = await call('controlling.open'); o.queries = [];
  const query = async (text, extra = {}) => { await call('controlling.ui', {query: text, ...extra}); await call('wait.ticks', {ticks: 3}); const state = await call('controlling.observe'); check(`native query settled ${text}`, state.refreshPending, false); return state; };
  try {
    for (const text of ['jump', '跳跃', 'tiaoyue', 'pt-no-such-key-42', '']) {
      const state = await query(text); o.queries.push({text, state});
      if (text === 'jump') check('native English binding remains searchable', keys(state).includes('key.jump'), true);
      if (text === '跳跃') check('secondary description search', keys(state).includes('key.jump'), product);
      if (text === 'tiaoyue') check('secondary pinyin description search follows JEC availability', keys(state).includes('key.jump'), product && runtime.mods.some(mod => mod.id === 'jecharacters'));
      if (text === 'pt-no-such-key-42') check('unknown binding search remains empty', keys(state), []);
      if (text === '' && baseline) check('all native keys preserved', keys(state), keys(baseline.queries.find(row => row.text === '').state));
    }
    o.category = await query('移动', {boxSearchCategory: true});
    check('secondary category includes movement bindings', keys(o.category).includes('key.jump'), product);
    o.bound = await query('鼠标左键', {boxSearchCategory: true, boxSearchKey: true});
    check('secondary bound mouse button selected', keys(o.bound).includes('key.attack'), product);
    o.categorySwitch = await query('移动', {boxSearchCategory: true});
    check('native category click deselects bound-key domain', [o.categorySwitch.boxSearchCategory, o.categorySwitch.boxSearchKey], [true, false]);
    check('category switch selects category aliases', keys(o.categorySwitch).includes('key.jump'), product);
    o.keySwitch = await query('鼠标左键', {boxSearchKey: true});
    check('native bound-key click deselects category domain', [o.keySwitch.boxSearchCategory, o.keySwitch.boxSearchKey], [false, true]);
    check('bound-key switch selects bound aliases', keys(o.keySwitch).includes('key.attack'), product);
    o.keyCategoryMiss = await query('移动'); check('bound-key mode does not union category aliases', keys(o.keyCategoryMiss).includes('key.jump'), false);
    await call('controlling.ui', {binding: 'key.jump', modifier: 'CONTROL', code: -100});
    o.modifier = await query('鼠标左键');
    check('bound modifier retains mouse alias domain', keys(o.modifier).includes('key.jump'), product);
    await call('controlling.ui', {restore: true});
    await call('controlling.ui', {boxSearchKey: true});
    o.generationBefore = await query('跳跃');
    o.positiveShot = await render('controlling-alias');
    if (product) {
      await configure([]); o.generationDisabled = await call('controlling.observe');
      check('settings generation removes stale binding result', keys(o.generationDisabled), []);
      check('settings generation preserves initialized native screen', o.generationDisabled.screen, o.generationBefore.screen);
      check('settings generation preserves native query and modes', modes(o.generationDisabled), modes(o.generationBefore));
      await configure(['zh_cn']); o.generationEnabled = await call('controlling.observe');
      check('settings generation restores binding result without typing', keys(o.generationEnabled).includes('key.jump'), true);
    }
    await call('controlling.ui', {buttonSorting: true}); await call('wait.ticks', {ticks: 3});
    o.sorted = await query('');
    if (baseline) check('native sorted bindings match separate baseline',bindings(o.sorted),bindings(baseline.sorted));
    if (product) {
      await configure([]); o.sortedDisabled = await call('controlling.observe');
      check('generation preserves sort mode', modes(o.sortedDisabled), modes(o.sorted));
      check('generation preserves native unfiltered sorted order', keys(o.sortedDisabled), keys(o.sorted));
      await configure(['zh_cn']);
    }
    o.conflicts = await query('', {buttonConflict: true});
    if (baseline) check('native conflicts match separate baseline',bindings(o.conflicts),bindings(baseline.conflicts));
    if (product) { await configure([]); o.conflictsDisabled = await call('controlling.observe'); check('generation keeps conflict mode', modes(o.conflictsDisabled), modes(o.conflicts)); check('generation preserves conflict membership and order', keys(o.conflictsDisabled), keys(o.conflicts)); await configure(['zh_cn']); }
    await call('controlling.ui', {buttonConflict: true});
    o.none = await query('', {buttonNone: true});
    if (baseline) check('native unbound bindings match separate baseline',bindings(o.none),bindings(baseline.none));
    if (product) { await configure([]); o.noneDisabled = await call('controlling.observe'); check('generation keeps unbound mode', modes(o.noneDisabled), modes(o.none)); check('generation preserves unbound membership and order', keys(o.noneDisabled), keys(o.none)); await configure(['zh_cn']); }
    await call('controlling.ui', {buttonNone: true});
    o.free = await query('', {buttonToggleKeys: true});
    check('native free-key view identified separately', o.free.listRole, 'suppressed-key-entry-list');
    if (product) { await configure([]); o.freeDisabled = await call('controlling.observe'); check('generation keeps free-key mode', modes(o.freeDisabled), modes(o.free)); await configure(['zh_cn']); }
    await call('controlling.ui', {buttonToggleKeys: true});
    o.afterFree = await query('跳跃'); check('leaving free-key view refreshes native search', keys(o.afterFree).includes('key.jump'), product);
  } finally { await call('controlling.ui', {restore: true}); await call('screen.close'); }
});
