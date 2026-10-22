import assert from 'node:assert/strict';
import {run, plain} from './integration-runner.mjs';

const defaults = ['zero', 'duplicate', 'middle', 'duplicate', 'last'];
const values = state => state.entries.filter(row => row.class.endsWith('.StringElement'));
const count = (state, event) => state.callbacks.events.filter(row => row.event === event).length;
await run(async ({call, check, observations: o, product, render, stamp}) => {
  o.cases = [];
  const observe = () => call('carbon.observe');
  const draw = async label => { const shot = await render('carbon-' + label); return {state: await observe(), shot}; };
  const control = async (state, label) => {
    const button = state.controls.find(button => plain(button.text) === label);
    assert.ok(button, `Missing native Carbon ${label}: ${state.controls.map(button => plain(button.text))}`);
    return call('carbon.action', {action: 'control', identity: button.identity});
  };
  const enter = async name => {
    await call('screen.close'); await call('carbon.open', {name});
    for (let i = 0; i < 7; i++) {
      const step = await draw('navigation'), state = step.state;
      if (values(state).length || state.screen.endsWith('.ArrayScreen') || state.entries.some(row => row.class.endsWith('ArrayElement$AddElement'))) {
        if (state.autoSave) { await call('carbon.action', {action: 'control', identity: state.autoSaveControl.identity}); return (await draw('manual')).state; }
        return state;
      }
      const entry = state.entries.find(row => row.class.endsWith('$ConfigEntry') || row.class.endsWith('$DirectConfig')) || state.entries.find(row => row.class.endsWith('.ArrayElement')) || state.entries.find(row => row.class.endsWith('.FolderElement'));
      assert.ok(entry, 'Missing native Carbon navigation entry');
      const button = entry.controls.find(button => ['open', 'button', 'textBox'].includes(button.role)); assert.ok(button);
      await call('carbon.action', {action: 'control', identity: button.identity});
    }
    throw Error('Carbon navigation did not reach list');
  };
  const edit = (state, index, text) => call('carbon.action', {action: 'edit', identity: values(state)[index].identity, text});
  const remove = (state, index) => {
    const button = values(state)[index].controls.find(button => button.role === (state.modern ? 'delete' : 'setReset')); assert.ok(button);
    return call('carbon.action', {action: 'control', identity: button.identity});
  };
  const commit = async state => {
    const result = {};
    if (!state.modern) {
      result.applied = await control(state, 'Apply'); result.applyShot = await render('carbon-apply');
      state = await observe();
      if (!state.controls.some(button => plain(button.text) === 'Save')) { await control(state, 'Home'); await render('carbon-home'); state = await observe(); }
    }
    result.beforeSave = state;
    result.saved = state.modern ? await call('carbon.action', {action: 'control', identity: state.saveControl.identity}) : await control(state, 'Save');
    result.shot = await render('carbon-saved');
    return result;
  };
  const savedCheck = (name, state, expected) => {
    check(`${name} native Property order`, state.property, expected);
    check(`${name} independent persisted file order`, state.disk, expected);
    check(`${name} callback observer did not overflow or fail`, state.callbacks.error, undefined);
  };
  for (const index of [0, 2, 4, 3]) {
    const name = `list-${stamp}-${index}`, row = {name, index, before: await enter(name)}; o.cases.push(row);
    check(`${name} fresh native list`, row.before.model.current, defaults);
    const text = `edited-${index}`; row.edited = await edit(row.before, index, text); row.editShot = await render('carbon-edited');
    check(`${name} original native textbox callback changes chosen node`, values(row.edited)[index].value, text);
    check(`${name} chosen node identity retained`, values(row.edited)[index].node, values(row.before)[index].node);
    const expected = [...defaults]; expected[row.before.modern && !product ? 0 : index] = text;
    check(`${name} array update follows release behavior`, row.edited.model.current, row.before.modern ? expected : defaults);
    check(`${name} manual edit leaves Property and disk untouched`, [row.edited.property, row.edited.disk], [defaults, defaults]);
    check(`${name} private node save callback count`, count(row.edited, 'callback'), row.before.modern ? 1 : 0);
    row.commit = await commit(await observe()); savedCheck(name, row.commit.saved, expected);
    check(`${name} native config save events`, [count(row.commit.saved, 'OnConfigChangedEvent'), count(row.commit.saved, 'PostConfigChangedEvent')], [1, 1]);
    if (!row.before.modern) check(`${name} Apply saves each surviving native node once`, count(row.commit.applied, 'callback'), defaults.length);
    row.reopened = await enter(name); row.reopenShot = await render('carbon-reopened');
    check(`${name} new native editor reads persisted rows`, values(row.reopened).map(row => row.text), expected);
    check(`${name} reopen constructs a new array`, row.reopened.model.identity !== row.before.model.identity, true);
    row.expected = expected;
  }
  {
    const name = `structural-${stamp}`, row = {name, before: await enter(name)}; o.cases.push(row);
    const detached = values(row.before)[0].node;
    await call('carbon.action', {action: 'retain', identity: values(row.before)[0].identity});
    await remove(await observe(), 0); row.deleted = (await draw('deleted')).state;
    check('deleting earlier row retains surviving node identity', values(row.deleted)[2].node, values(row.before)[3].node);
    const beforeCallback = row.deleted.model.current;
    row.stale = await call('carbon.action', {action: 'retainedSave', identity: detached});
    check('deleted native callback leaves live array unchanged', row.stale.model.current, beforeCallback);
    check('deleted native callback leaves Property and disk unchanged', [row.stale.property, row.stale.disk], [defaults, defaults]);
    check('deleted native callback returns without config save', count(row.stale, 'OnConfigChangedEvent'), 0);
    row.edited = await edit(row.stale, 2, 'survivor-edited'); await render('carbon-survivor');
    const expected = defaults.slice(1); expected[row.before.modern && !product ? 0 : 2] = 'survivor-edited';
    row.commit = await commit(await observe()); savedCheck(name, row.commit.saved, expected);
    row.reopened = await enter(name); check('shifted duplicate edit persists correct native slot', values(row.reopened).map(row => row.text), expected);
  }
  {
    const name = `movement-${stamp}`, row = {name, before: await enter(name)}; o.cases.push(row);
    if (row.before.modern) row.moved = await call('carbon.action', {action: 'drag', identity: values(row.before)[2].identity, target: values(row.before)[1].identity});
    else {
      const button = values(row.before)[2].controls.find(button => button.role === 'moveUp'); assert.ok(button);
      row.moved = await call('carbon.action', {action: 'control', identity: button.identity});
    }
    row.moveShot = await render('carbon-moved'); row.moved = {...row.moved, ...(await observe())};
    const moved = ['zero', 'middle', 'duplicate', 'duplicate', 'last'];
    check('native movement updates actual model', row.moved.model.current, moved);
    check('native movement updates actual visible row order', values(row.moved).map(row => row.text), moved);
    check('native movement preserves release node semantics', values(row.moved)[1].node, values(row.before)[row.before.modern ? 2 : 1].node);
    row.edited = await edit(row.moved, 1, 'moved-edited'); await render('carbon-moved-edited');
    const expected = [...moved]; expected[row.before.modern && !product ? 0 : 1] = 'moved-edited';
    row.commit = await commit(await observe()); savedCheck(name, row.commit.saved, expected);
    row.reopened = await enter(name); check('native move then edit survives reopen', values(row.reopened).map(row => row.text), expected);
  }
  {
    const name = `empty-${stamp}`, row = {name, before: await enter(name)}; o.cases.push(row); let state = row.before;
    while (values(state).length) { await remove(state, 0); state = (await draw('remove-all')).state; }
    row.empty = state; row.commit = await commit(state); savedCheck('empty list', row.commit.saved, []);
    row.reopened = await enter(name); check('empty persisted list is native empty editor', values(row.reopened).length, 0);
    row.added = row.reopened.modern ? await call('carbon.action', {action: 'add'}) : await control(row.reopened, '+');
    state = (await draw('added')).state; check('native Add creates one actual row', values(state).length, 1);
    row.edited = await edit(state, 0, 'added-value'); await render('carbon-added-value');
    row.secondCommit = await commit(await observe()); savedCheck('native Add after empty reopen', row.secondCommit.saved, ['added-value']);
    row.final = await enter(name); check('new row survives native reopen', values(row.final).map(row => row.text), ['added-value']);
  }
  {
    const name = `cancel-${stamp}`, row = {name, before: await enter(name)}; o.cases.push(row);
    row.edited = await edit(row.before, 2, 'discard-value'); await render('carbon-before-cancel');
    if (row.before.modern) {
      row.prompt = await call('carbon.action', {action: 'close'}); row.promptShot = await render('carbon-cancel-prompt');
      check('native modern close shows unsaved dialog', row.prompt.screen.endsWith('.MultiChoiceScreen'), true);
      row.back = await control(await observe(), 'Go Back'); await render('carbon-cancel-back');
      check('dialog Back preserves original editor object', row.back.identity, row.before.identity);
      check('dialog Back preserves unsaved row', values(row.back)[2].text, 'discard-value');
      await call('carbon.action', {action: 'close'}); await render('carbon-discard-prompt'); row.discarded = await control(await observe(), 'Discard Changes');
    } else { row.discarded = await control(await observe(), 'Back'); }
    row.reopened = await enter(name); check('native discard keeps existing file and reopened rows', [row.reopened.disk, values(row.reopened).map(row => row.text)], [defaults, defaults]);
  }
  if (o.cases[0].before.modern) {
    const name = `autosave-${stamp}`, row = {name, before: await enter(name)}; o.cases.push(row);
    await call('carbon.action', {action: 'control', identity: row.before.autoSaveControl.identity});
    row.edited = await edit(await observe(), 4, 'auto-value'); row.shot = await render('carbon-autosaved');
    const expected = [...defaults]; expected[product ? 4 : 0] = 'auto-value'; savedCheck('native screen autosave', row.edited, expected);
    const save = (await observe()).saveControl;
    check('native Save is disabled after completed autosave', save.enabled, false);
    await call('input.click', {x: save.x + Math.floor(save.width / 2), y: save.y + Math.floor(save.height / 2)});
    row.unchanged = await observe();
    check('unchanged native Save emits no additional config event', count(row.unchanged, 'OnConfigChangedEvent'), count(row.edited, 'OnConfigChangedEvent'));
    row.reopened = await enter(name); check('native autosave survives reopen', values(row.reopened).map(row => row.text), expected);
  }
  o.restart = o.cases.filter(row => row.expected).map(row => ({name: row.name, expected: row.expected, file: row.reopened.file, sha256: row.reopened.sha256, oldArray: row.reopened.model.identity}));
  await call('screen.close');
});
