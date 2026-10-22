import {run, plain} from './integration-runner.mjs';

await run(async ({call, check, observations: o, runtime, product, configure, baseline, render}) => {
  const kinds = [...(runtime.mods.some(mod => mod.id === 'modularui') ? ['mui'] : []), ...(runtime.mods.some(mod => mod.id === 'gregtech') ? ['gt'] : [])];
  check('native widget integration present', kinds.length > 0, true);
  o.cases = [];
  const names = await call('integration.names');
  const material = names.fluids.find(fluid => fluid.id === 'oxygen') || [...names.fluids].sort((a, b) => a.id.localeCompare(b.id))[0];
  for (const kind of kinds) for (const mode of kind === 'mui' ? ['ordinary', 'phantom', 'unbounded'] : ['ordinary']) {
    if (product) await configure([]);
    await call('integration.widget.open', {kind, fluid: 'water', phantom: mode !== 'ordinary', controlsAmount: mode !== 'unbounded'});
    await call('wait.ticks', {ticks: 15});
    const opened = await call('integration.widget.observe');
    check(`${kind}-${mode} native widget keys`, opened.widgets.map(widget => widget.key), kind === 'mui' ? ['slot'] : ['tank', 'phantom']);
    if (kind === 'mui') check(`${mode} native phantom and amount flags`, [opened.phantom, opened.controlsAmount], [mode !== 'ordinary', mode !== 'unbounded']);
    for (const fluid of ['water', 'lava', ...(material ? [material.id] : []), 'empty']) {
      await call('integration.widget.observe', {fluid, amount: 23456});
      await call('wait.ticks', {ticks: 4});
      await render('integration-widget-layout');
      const state = await call('integration.widget.observe');
      for (const widget of state.widgets) {
        const key = `${kind}-${mode}-${widget.key}-${fluid}`, row = {key, state}; o.cases.push(row);
        check(`${key} screen and widget retained`, [state.screenIdentity, widget.identity], [opened.screenIdentity, opened.widgets.find(other => other.key === widget.key).identity]);
        check(`${key} native fluid and amount reflect actual requested content`, widget.fluid ? [widget.fluid.id, widget.fluid.amount] : null, fluid === 'empty' ? null : [fluid, 23456]);
        const capture = configuration => render('integration-widget', {x: widget.x, y: widget.y, requireTooltip: fluid !== 'empty', ...(configuration ? {configuration} : {})});
        row.native = await capture();
        check(`${key} native Forge drawing dispatched`, row.native.nativeEvents, true);
        if (fluid !== 'empty') {
          check(`${key} observes real native tooltip event`, row.native.tooltipEvents.length > 0, true);
          if (kind === 'mui') check(`${key} rich tooltip event observed`, row.native.tooltipEvents.some(name => name.includes('RichTooltipEvent')), true);
        }
        if (!product) continue;
        await call('language.set', {language: 'zh_cn'});
        row.target = await call('integration.widget.observe');
        await call('language.set', {language: 'en_us'});
        row.enabled = await capture({languages: ['zh_cn'], alwaysShow: false});
        check(`${key} changed configuration renders on first native frame`, row.enabled.frames, 1);
        row.current = await call('integration.widget.observe');
        const title = widget.fluid?.nativeName, target = row.target.widgets.find(other => other.key === widget.key)?.fluid?.nativeName;
        const alias = title && target && plain(title) !== plain(target) ? plain(target) : null;
        if (alias) {
          check(`${key} translated title rendered once`, row.enabled.lines.filter(line => plain(line) === alias).length, 1);
          check(`${key} native amount capacity controls and spacing preserved`, row.enabled.lines.filter(line => plain(line) !== alias), row.native.lines);
        } else check(`${key} no unproven empty or same-name alias`, row.enabled.lines, row.native.lines);
        check(`${key} generation preserves native widget identity`, row.current.widgets.map(widget => widget.identity), state.widgets.map(widget => widget.identity));
        row.disabled = await capture({languages: [], alwaysShow: false});
        check(`${key} disabled configuration renders on first native frame`, row.disabled.frames, 1);
        check(`${key} settings disable removes live tooltip alias`, row.disabled.lines, row.native.lines);
        if (baseline) check(`${key} native output matches separate baseline`, row.native.lines, baseline.cases.find(other => other.key === key)?.native.lines);
      }
    }
    await call('screen.close');
  }
  if (baseline) check('paired native widget case coverage is identical', o.cases.map(row => row.key).sort(), baseline.cases.map(row => row.key).sort());
  if (product) await configure(['zh_cn']);
});
