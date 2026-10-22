import {run, plain} from './integration-runner.mjs';

const texts = capture => capture.matched.text.map(row => row.text);
const title = capture => plain(texts(capture)[0]);
const topology = tree => [tree.class, ...(tree.children || []).map(topology)];
const semantic = (capture, kind) => kind === 'top'
  ? {text: texts(capture), width: capture.matched.width, height: capture.matched.height, tree: topology(capture.matched.tree)}
  : {text: capture.matched.lines, drawn: texts(capture), width: capture.matched.w, height: capture.matched.h};
const containsElement = (tree, name) => tree.class.endsWith('.' + name) || (tree.children || []).some(child => containsElement(child, name));

await run(async ({call, check, observations: o, product, configure, baseline, stamp}) => {
  let sequence = 0;
  const capture = (kind, params = {}) => call('hud.capture', {kind, name: `hud-${kind}-${stamp}-${sequence++}.png`, ...params});
  const origin = (value, kind) => value.uuid ? {uuid: value.uuid} : {x: value.x, y: value.y, z: value.z, ...(kind === 'top' ? {dimension: value.dimension} : {})};
  const pair = async value => ({top: await capture('top', {origin: origin(value, 'top'), packet: true}), waila: await capture('waila', {origin: origin(value, 'waila')})});
  const geometry = (key, row) => {
    check(`${key} TOP completed native layout and draw`, [row.top.matched.complete, row.top.matched.drawn], [true, true]);
    check(`${key} TOP observes actual native box`, row.top.matched.boxes.length > 0, true);
    for (const drawn of row.top.matched.text) check(`${key} TOP drawn text fits its measured box ${plain(drawn.text)}`, row.top.matched.boxes.some(box => drawn.x >= box.x && drawn.y >= box.y && drawn.x + drawn.width <= box.x + box.width && drawn.y + 9 <= box.y + box.height), true);
    check(`${key} HWYLA performs both native passes`, [row.waila.matched.complete, row.waila.matched.passes], [true, 2]);
    check(`${key} HWYLA box matches native tooltip size`, row.waila.matched.boxes.some(box => box.width === row.waila.matched.w && box.height === row.waila.matched.h), true);
  };
  try {
    if (product) await configure([]);
    o.settings = await call('hud.settings', {modName: true, timeout: 100});
    o.community = Object.hasOwn(o.settings.after, 'overlayFadeAnimation');
    o.world = await call('hud.world', {prepare: true, target: 'stone'});
    o.cases = [];
    for (const definition of [
      {key: 'stone', target: 'stone'}, {key: 'furnace', target: 'furnace'},
      {key: 'pig', target: 'pig'}, {key: 'cow', target: 'cow'}, {key: 'custom', target: 'custom'},
      {key: o.community ? 'water-icon' : 'water-native', target: 'water'},
      {key: 'stone-no-mod', target: 'stone', modName: false},
      ...(o.community ? [{key: 'pig-model', target: 'pig', showEntityModel: true}, {key: 'water-bucket', target: 'water', showLiquidWithBucket: true}] : [])
    ]) {
      const {key, target, ...settings} = definition, row = {key}; o.cases.push(row);
      await call('hud.settings', {modName: true, timeout: 100, ...settings});
      if (product) await configure([]);
      row.aim = await call('hud.world', {target}); await call('wait.ticks', {ticks: 25});
      row.native = await pair(row.aim.origin); geometry(`${key} native`, row.native);
      if (key === 'water-icon') check('water icon variant uses actual native icon', containsElement(row.native.top.matched.tree, 'ElementIcon'), true);
      if (key === 'water-bucket') check('water bucket variant uses actual native item icon', containsElement(row.native.top.matched.tree, 'ElementItemStack'), true);
      if (key === 'pig-model') check('entity model variant renders actual native model element', containsElement(row.native.top.matched.tree, 'ElementEntity'), true);
      await call('language.set', {language: 'zh_cn'}); await call('wait.ticks', {ticks: 25});
      row.chinese = await pair(row.aim.origin);
      await call('language.set', {language: 'en_us'}); await call('wait.ticks', {ticks: 25});
      row.english = await pair(row.aim.origin);
      for (const kind of ['top', 'waila']) {
        check(`${key} ${kind} actual primary language round trip`, texts(row.english[kind]), texts(row.native[kind]));
        check(`${key} ${kind} native Chinese title ${target === 'custom' ? 'preserves custom name' : 'differs'}`, title(row.chinese[kind]) === title(row.native[kind]), target === 'custom');
        if (baseline) check(`${key} ${kind} native output equals separate baseline`, semantic(row.native[kind], kind), semantic(baseline.cases.find(other => other.key === key).native[kind], kind));
      }
      if (!product) continue;
      await configure(['zh_cn', 'zh_cn']); row.enabled = await pair(row.aim.origin); geometry(`${key} enabled`, row.enabled);
      for (const kind of ['top', 'waila']) {
        const alias = title(row.chinese[kind]), nativeTitle = title(row.native[kind]), translated = alias !== nativeTitle;
        if (kind === 'top') {
          const added = translated ? 1 : 0;
          check(`${key} TOP keeps the exact native title`, texts(row.enabled.top)[0], texts(row.native.top)[0]);
          check(`${key} TOP puts the alias on its own row`, texts(row.enabled.top).slice(1, 1 + added).map(plain), translated ? [alias] : []);
          if (translated) {
            const [first, second] = row.enabled.top.matched.text;
            check(`${key} TOP aligns the added row below the title`, [second.x, second.y], [first.x, first.y + 10]);
          }
          check(`${key} TOP keeps native body and mod rows`, texts(row.enabled.top).slice(1 + added), texts(row.native.top).slice(1));
          // Native icons or entity models can already be taller than the title.
          const extraHeight = row.enabled.top.matched.height - row.native.top.matched.height;
          check(`${key} TOP expands within the added row height`, extraHeight >= 0 && extraHeight <= added * 10, true);
          if (translated && row.enabled.top.matched.text.length > 2) check(`${key} TOP keeps body below the title rows`, row.enabled.top.matched.text[2].y >= row.enabled.top.matched.text[1].y + 10, true);
          check(`${key} TOP keeps native element topology`, topology(row.enabled.top.matched.tree), topology(row.native.top.matched.tree));
        } else {
          check(`${key} HWYLA title remains native`, row.enabled.waila.matched.lines[0], row.native.waila.matched.lines[0]);
          check(`${key} HWYLA copied alias once`, row.enabled.waila.matched.lines.filter(line => plain(line) === alias && alias !== nativeTitle).length, translated ? 1 : 0);
          check(`${key} HWYLA actually draws alias once`, texts(row.enabled.waila).filter(line => plain(line) === alias && alias !== nativeTitle).length, translated ? 1 : 0);
          check(`${key} HWYLA preserves native drawn body`, texts(row.enabled.waila).filter(line => !translated || plain(line) !== alias), texts(row.native.waila));
          check(`${key} HWYLA preserves metadata health and body rows`, row.enabled.waila.matched.lines.filter(line => !translated || plain(line) !== alias), row.native.waila.matched.lines);
          check(`${key} HWYLA native sizing includes added row`, row.enabled.waila.matched.h - row.native.waila.matched.h, translated ? 10 : 0);
        }
      }
      await configure([]); row.disabled = await pair(row.aim.origin);
      for (const kind of ['top', 'waila']) check(`${key} ${kind} disable removes alias`, semantic(row.disabled[kind], kind), semantic(row.native[kind], kind));
    }
    if (product) await configure(['zh_cn']);
    await call('hud.settings', {modName: true, timeout: 100});
    await call('hud.world', {target: 'stone'}); await call('wait.ticks', {ticks: 10});
    o.boundaries = [];
    for (const mode of ['event-title', 'unknown-head']) {
      await call('hud.boundary', {mode}); await call('wait.ticks', {ticks: 8});
      const row = {mode, capture: await capture('waila', {text: mode === 'event-title' ? 'PT external title' : 'Stone', origin: {x: 40, y: 4, z: 0}}), state: await call('hud.boundary')}; o.boundaries.push(row);
      check(`${mode} real native callback executed`, row.state.callbacks > 0 && !row.state.error, true);
      check(`${mode} no unsupported title alias`, row.capture.matched.lines.some(line => plain(line) === '石头'), false);
      if (baseline) check(`${mode} matches unmodified native behavior`, semantic(row.capture, 'waila'), semantic(baseline.boundaries.find(other => other.mode === mode).capture, 'waila'));
      await call('hud.boundary', {mode: 'off'});
    }
    o.afterBoundary = await capture('waila', {text: product ? '石头' : 'Stone'});
    check('HWYLA resumes valid core title after boundary cleanup', o.afterBoundary.matched.lines.some(line => plain(line) === '石头'), product);
    await call('hud.world', {target: 'furnace'}); await call('wait.ticks', {ticks: 45});
    o.nbt = await capture('waila', {text: 'Furnace', origin: {x: 54, y: 4, z: 0}});
    check('HWYLA actual network NBT belongs to rendered furnace', [o.nbt.matched.remoteTag.WailaX, o.nbt.matched.remoteTag.WailaY, o.nbt.matched.remoteTag.WailaZ], [54, 4, 0]);
    check('HWYLA packet observer has no errors', o.nbt.wailaPackets.some(event => event.error), false);
    check('HWYLA packet receive and native assignment observed', ['receive', 'assigned'].every(type => o.nbt.wailaPackets.some(event => event.event === type && event.identity === o.nbt.matched.remoteTag.identity && event.WailaX === 54 && event.WailaY === 4 && event.WailaZ === 0)), true);
    await call('hud.settings', {modName: true, timeout: 3000, waitingForServerTimeout: -1});
    o.delay = {}; o.delay.aimA = await call('hud.world', {target: 'pig'}); await call('wait.ticks', {ticks: 8});
    o.delay.a = await capture('top', {text: 'Pig', packet: true, origin: {uuid: o.delay.aimA.origin.uuid}});
    await call('hud.delay', {action: 'arm', target: {dimension: 0, x: 42, y: 4, z: 0}});
    const delayed = capture('top', {identity: o.delay.a.matched.identity, origin: {uuid: o.delay.aimA.origin.uuid}, target: {type: 'BLOCK', block: {x: 42, y: 4, z: 0}}, held: 1}); delayed.catch(() => {});
    o.delay.aimB = await call('hud.world', {target: 'stone', offset: 2}); o.delay.stale = await delayed;
    check('delayed packet keeps A title and aliases while native aim is B', texts(o.delay.stale), texts(o.delay.a));
    check('actual decoded B packet is held', o.delay.stale.delivery.held > 0, true);
    o.delay.release = await call('hud.delay', {action: 'release'});
    o.delay.b = await capture('top', {text: 'Stone', packet: true, origin: {dimension: 0, x: 42, y: 4, z: 0}});
    check('released B packet supplies B title and alias rows', texts(o.delay.b).slice(0, product ? 2 : 1).map(plain), product ? ['Stone', '石头'] : ['Stone']);
    if (o.community) {
      o.fade = {}; await call('hud.settings', {modName: true, overlayFadeAnimation: true, timeout: 3000, waitingForServerTimeout: -1});
      await call('hud.world', {target: 'cow'});
      for (let i = 0; i < 100; i++) {await call('wait.ticks', {ticks: 2}); if ((await call('hud.observe')).frame.top.some(row => row.complete && row.alpha >= 0.999)) break;}
      o.fade.visible = await capture('top', {text: 'Cow', visible: true});
      const fading = capture('top', {identity: o.fade.visible.matched.identity, visible: false, fading: true}); fading.catch(() => {});
      await call('hud.world', {target: 'sky'}); o.fade.fading = await fading;
      check('CE actual fade keeps old origin and title', [o.fade.fading.matched.origin.uuid, texts(o.fade.fading)], [o.fade.visible.matched.origin.uuid, texts(o.fade.visible)]);
      check('CE private fade path has partial alpha', o.fade.fading.matched.alpha > 0.001 && o.fade.fading.matched.alpha < 1, true);
    } else await call('hud.world', {target: 'sky'});
    o.gone = {};
    for (const kind of ['top', 'waila']) {
      o.gone[kind] = await capture(kind, {negative: true, aim: {type: 'MISS'}});
      check(`${kind} native MISS removes HUD`, o.gone[kind].negative && !o.gone[kind].frame[kind].some(row => row.complete), true);
    }
    if (baseline) check('paired HUD cases have identical native coverage', o.cases.map(row => row.key), baseline.cases.map(row => row.key));
  } finally {
    await call('hud.boundary', {mode: 'off'}); await call('hud.delay', {action: 'release'});
    o.restored = await call('hud.settings', {restore: true});
    check('HUD settings restore complete native snapshot', o.restored.after, o.settings.before);
    if ((await call('status')).language !== 'en_us') await call('language.set', {language: 'en_us'});
    if (product) await configure(['zh_cn']);
  }
});
