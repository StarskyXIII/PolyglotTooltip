import {run, plain} from './integration-runner.mjs';

const stableItem = ({identity, aliases, ...row}) => row;
await run(async ({call, check, observations: o, runtime, product, configure, baseline, render}) => {
  if (product) await configure(['zh_cn']);
  o.english = await call('integration.names', {reset: true});
  if (product) {
    const mod = id => runtime.mods.find(value => value.id === id);
    const expectedProfiles = {
      'if-names': !!mod('industrialforegoing'), 'gt-material-names': !!mod('gregtech'), 'gt-fluid-tooltips': !!mod('gregtech'),
      'mui-303': mod('modularui')?.version === '3.0.3', 'mui-304': mod('modularui')?.version === '3.0.4', 'mui-316': mod('modularui')?.version === '3.1.6',
      controlling: !!mod('controlling'), 'carbon-124-native': !!mod('carbonconfig')?.file.includes('-1.2.4.'), 'carbon-2021-repair': !!mod('carbonconfig')?.file.includes('-2.0.2.1.'),
      'pe-search': !!mod('projecte'), 'it-search-refresh': !!mod('integratedterminals'),
      'top-original': !!mod('theoneprobe') && !mod('theoneprobe').file.includes('-CE-'), 'top-ce': !!mod('theoneprobe')?.file.includes('-CE-'),
      hwyla: !!mod('waila'), 'jec-comparison': !!mod('jecharacters')
    };
    check('whole native profiles select exactly the installed known releases', o.english.profiles, expectedProfiles);
  }
  await call('language.set', {language: 'zh_cn'});
  o.chinese = await call('integration.names');
  check('native name fixture populated', o.english.items.length + o.english.fluids.length > 0, true);
  if (runtime.mods.some(mod => mod.id === 'industrialforegoing')) {
    check('all 32 native laser lens variants are present', o.english.items.filter(row => row.key.startsWith('laserLensItem') && !row.key.endsWith('-custom')).length, 32);
    check('all native captured-mob cases are present', o.english.items.filter(row => row.key.startsWith('mob-') && !row.key.endsWith('-custom')).length, 4);
  }
  if (runtime.mods.some(mod => mod.id === 'storagedrawers')) check('all native drawer and trim cases are present', o.english.items.filter(row => row.key.startsWith('drawer-') || row.key.startsWith('trim-')).length, 18);
  check('material fluid coverage follows native GT presence', o.english.fluids.length > 0, runtime.mods.some(mod => mod.id === 'gregtech'));
  for (const domain of ['items', 'fluids']) {
    check(`${domain} target-language fixture keys are identical`, o.chinese[domain].map(row => row.key || row.id).sort(), o.english[domain].map(row => row.key || row.id).sort());
    check(`${domain} leave payload unchanged`, [...o.english[domain], ...o.chinese[domain]].every(row => row.unchanged), true);
    for (const row of o.english[domain]) {
      const id = row.key || row.id, target = o.chinese[domain].find(other => (other.key || other.id) === id);
      if (row.tooltip) check(`${id} native primary title`, plain(row.tooltip[0]), plain(row.nativeName));
      if (!product) continue;
      const expected = plain(row.nativeName) === plain(target.nativeName) ? [] : [plain(target.nativeName)];
      check(`${id} alias equals actual native target-language result`, row.aliases.map(plain), expected);
      check(`${id} alias suppresses current primary language`, target.aliases, []);
      if (row.key?.endsWith('-custom')) check(`${id} custom literal remains unchanged`, row.aliases, []);
      if (row.tooltip) check(`${id} alias has one rendered row`, row.aliases.every(alias => row.tooltip.filter(line => plain(line) === plain(alias)).length === 1), true);
    }
  }
  await call('language.set', {language: 'en_us'});
  for (const [key, params, equivalent] of [['laserLensItem-0', {damage: 14}, 'laserLensItem-14'], ['mob-pig', {entity: 'minecraft:cow'}, 'mob-cow']]) {
    const before = o.english.items.find(row => row.key === key); if (!before) continue;
    const after = (await call('integration.names', {mutate: key, ...params})).items.find(row => row.key === key);
    const expected = o.english.items.find(row => row.key === equivalent);
    o[key + '-mutation'] = after;
    check(`${key} native stack identity retained across mutation`, after.identity, before.identity);
    check(`${key} new native content selected`, after.nativeName, expected.nativeName);
    if (product) check(`${key} aliases follow same-stack mutation`, after.aliases, expected.aliases);
  }
  await call('integration.names', {reset: true});
  o.captures = [];
  for (const key of ['laserLensItem-14', 'mob-pig', 'drawer-sealed']) {
    if (!o.english.items.some(row => row.key === key)) continue;
    const item = await call('integration.item.open', {key});
    const shot = await render('integration-item', {x: item.x, y: item.y, requireTooltip: true});
    o.captures.push({key, item, shot});
    check(`${key} real inventory slot retains native stack`, item.slotSame, true);
    check(`${key} native hover retains primary title`, plain(shot.lines[0]), plain(item.nativeName));
    if (product) check(`${key} real hover contains secondary title`, item.aliases.every(alias => shot.lines.map(plain).includes(plain(alias))), true);
  }
  await call('screen.close');
  if (product) {
    await configure([]); o.disabled = await call('integration.names');
    check('disabling settings removes item and fluid aliases', [...o.disabled.items, ...o.disabled.fluids].every(row => row.aliases.length === 0), true);
    for (const row of o.english.items) {
      const native = o.disabled.items.find(other => other.key === row.key), aliases = new Set(row.aliases.map(plain));
      check(`${row.key} native body unchanged`, row.tooltip.filter((line, index) => index === 0 || !aliases.has(plain(line))), native.tooltip);
    }
    if (baseline) {
      check('disabled item output matches separate native process', o.disabled.items.map(stableItem), baseline.english.items.map(stableItem));
      check('disabled fluid output matches separate native process', o.disabled.fluids.map(({aliases, ...row}) => row), baseline.english.fluids);
    }
    await configure(['zh_cn', 'zh_cn', 'en_us']); o.deduplicated = await call('integration.names');
    check('duplicate and primary secondary languages deduplicated', [...o.deduplicated.items, ...o.deduplicated.fluids].every(row => new Set(row.aliases.map(plain)).size === row.aliases.length && !row.aliases.map(plain).includes(plain(row.nativeName))), true);
    for (const domain of ['items', 'fluids']) check(`${domain} deduplication retains all distinct secondary names`, o.deduplicated[domain].map(row => ({key:row.key||row.id,aliases:row.aliases})), o.english[domain].map(row => ({key:row.key||row.id,aliases:row.aliases})));
    await configure(['zh_cn']);
    await call('resources.reload'); o.reloaded = await call('integration.names');
    check('full reload preserves item translation result', o.reloaded.items.map(({identity, ...row}) => row), o.english.items.map(({identity, ...row}) => row));
    check('full reload preserves fluid translation result', o.reloaded.fluids, o.english.fluids);
  }
});
