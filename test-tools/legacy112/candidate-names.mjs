import fs from 'node:fs/promises';
import path from 'node:path';
import {run, plain} from './integration-runner.mjs';

const selectedCharm = row => /^(charm|fragment)-\d+$/.test(row.key) || ['charm-damaged', 'charm-string-type'].includes(row.key);
const targetText = row => row.rawTranslation ? plain(row.rawTranslation.replace(/\{\{!colors\.[a-z_]+\}\}/gi, '')) : plain(row.nativeName);
const decorated = (lines, aliases, enchantment, targetEnchantment, hover = false) => {
  const result = [...lines];
  if (enchantment && plain(enchantment) !== plain(targetEnchantment)) {
    const positions = result.flatMap((line, index) => index > 0 && plain(line) === plain(enchantment) ? [index] : []);
    if (positions.length !== 1) throw new Error('Expected exactly one native enchantment title');
    result[positions[0]] += ` ｜ ${targetEnchantment}`;
  }
  const existing = new Set(result.map(plain));
  result.splice(1, 0, ...aliases.filter(alias => !existing.has(plain(alias))).map(alias => `${hover ? '§7' : ''}§7${alias}§r`));
  return result;
};
await run(async ({call, check, observations: o, runtime, product, render, configure, baseline, fixture}) => {
  const has = id => runtime.mods.some(mod => mod.id === id), name = 'candidate-name-resources';
  const pack = path.join(fixture, 'resourcepacks', name);
  const write = async (domain, language, lines) => {
    const directory = path.join(pack, 'assets', domain, 'lang'); await fs.mkdir(directory, {recursive: true});
    await fs.writeFile(path.join(directory, `${language}.lang`), lines.join('\n') + '\n', 'utf8');
  };
  const resources = async (revision = 'A', broken = false) => {
    await fs.mkdir(pack, {recursive: true});
    await fs.writeFile(path.join(pack, 'pack.mcmeta'), JSON.stringify({pack:{pack_format:3,description:'Owned candidate name fixture'}}));
    await write('extendedae', 'zh_cn', [`item.extendedae:infinity_cell.name=无限${revision}〔%s〕存储元件`, 'cell.unknown=未知无限存储元件']);
    await write('xreliquary', 'en_us', [
      'xreliquary.item.mob_charm_0.tooltip={{!colors.rose}}Body sentinel 314;Native punctuation stays.',
      'xreliquary.item.mob_charm_14.name={{!colors.gold}}Unknown sentinel',
      'xreliquary.xreliquary.item.mob_charm_0.name={{!colors.gold}}Prefixed sentinel',
      ...(revision === 'B' ? ['xreliquary.item.mob_charm_0.name={{!colors.light_green}}Reloaded primary B'] : [])
    ]);
    await write('xreliquary', 'zh_cn', [
      'xreliquary.item.mob_charm_0.tooltip={{!colors.rose}}本体文本不会由名称修正清空',
      'xreliquary.item.mob_charm_14.name={{!colors.gold}}Unknown sentinel',
      'xreliquary.xreliquary.item.mob_charm_0.name={{!colors.gold}}Prefixed sentinel',
      ...(broken ? ['xreliquary.item.mob_charm_0.name={{!colors.unknown}}不支持'] : [])
    ]);
  };
  await resources();
  await call('resources.select', {packs: [name]});
  if (product) await configure(['zh_cn']);
  o.english = await call('candidate.names', {reset:true});
  check('registered candidate stacks present', o.english.items.length > 0, true);
  for (const name of [
    'net.minecraft.item.ItemStack.<init>', 'net.minecraft.item.ItemStack.func_82833_r',
    'net.minecraft.item.ItemStack.func_77946_l', 'net.minecraft.item.ItemStack.func_77955_b',
    'net.minecraft.item.Item.func_77653_i', 'net.minecraftforge.fluids.FluidStack.getLocalizedName',
    'net.minecraftforge.fluids.FluidStack.copy', 'net.minecraftforge.fluids.FluidStack.writeToNBT',
    ...['ItemPotion','ItemSplashPotion','ItemLingeringPotion','ItemTippedArrow'].map(type => `net.minecraft.item.${type}.func_77653_i`),
    'net.minecraftforge.fluids.FluidRegistry$1.getLocalizedName', 'net.minecraftforge.fluids.FluidRegistry$2.getLocalizedName',
    ...['NBTTagCompound','NBTTagList','NBTTagString'].flatMap(type => ['hashCode','equals','toString','func_74737_b'].map(method => `net.minecraft.nbt.${type}.${method}`))
  ]) {
    check(`${name} monitor installed`, o.english.instrumented.some(key => key.startsWith(name+'(')), true);
    check(`${name} monitor positive control`, Object.entries(o.english.traceControl).some(([key,count]) => key.startsWith(name+'(') && count > 0), true);
  }
  const nbtConstructor = 'net.minecraft.item.ItemStack.<init>(Lnet/minecraft/nbt/NBTTagCompound;)V';
  check('exact native NBT constructor monitor installed', o.english.instrumented.includes(nbtConstructor), true);
  check('exact native NBT constructor positive control', o.english.traceControl[nbtConstructor] > 0, true);
  if (product) check('whole selected candidate profiles', o.english.profiles, {
    'reliquary-names':has('xreliquary'), 'reliquary-title-cache':has('xreliquary'), 'extendedae-infinity-uel567':has('extendedae'),
    'rs-1616-search-refresh':has('refinedstorage') && runtime.mods.find(mod => mod.id === 'refinedstorage').version === '1.6.16',
    'mrs-232-search-refresh':has('refinedstorage') && runtime.mods.find(mod => mod.id === 'refinedstorage').version === '2.3.2'
  });
  o.warmed = await call('candidate.cache', {warm:true});
  if (has('xreliquary')) {
    check('all twenty-eight real macro names warm native cache', Object.keys(o.warmed.selected).length, 28);
    check('real native out-of-scope entries are warmed', ['item.mob_charm_0.tooltip','item.mob_charm_14.name','xreliquary.item.mob_charm_0.name'].every(key => key in o.warmed.sentinels), true);
    if (product) {
      await configure(['zh_tw']); o.configOnly = await call('candidate.cache');
      check('secondary-only setting retains native cache exactly', o.configOnly, o.warmed);
      const context = await call('candidate.names');
      check('secondary-only setting preserves native resource revision', context.resourceEpoch, o.english.resourceEpoch);
      check('secondary-only setting advances product generation', context.generation > o.english.generation, true);
      await configure(['zh_cn']);
    }
  }
  await call('language.set', {language:'zh_cn'});
  o.chinese = await call('candidate.names');
  check('normal registered source objects survive locale switch', o.chinese.items.map(row => [row.key,row.identity]), o.english.items.map(row => [row.key,row.identity]));
  for (const row of o.english.items) {
    const target = o.chinese.items.find(value => value.key === row.key);
    check(`${row.key} leaves native data unchanged`, row.unchanged && target.unchanged, true);
    if (selectedCharm(row)) check(`${row.key} native cached primary behavior`, plain(target.nativeName), product ? targetText(target) : plain(row.nativeName));
    if (product) {
      const translated = targetText(target), expected = row.key.includes('custom') || row.key === 'cell-outer-literal' || plain(row.nativeName) === translated ? [] : [translated];
      check(`${row.key} actual secondary native-language title`, row.aliases.map(plain), expected);
      check(`${row.key} primary language removes duplicates`, target.aliases, []);
      if (row.key.startsWith('cell-')) check(`${row.key} independent name path performs no native decode, display, copy, or NBT recursion`, row.aliasCalls, {});
      if (baseline) {
        const native = baseline.english.items.find(value => value.key === row.key);
        check(`${row.key} native primary matches separate control`, row.nativeName, native.nativeName);
        check(`${row.key} complete ordered tooltip preserved`, row.tooltip, decorated(native.tooltip, row.aliases, native.enchantmentName, target.enchantmentName));
        const advanced = [...native.advanced], nativeId = `(#${String(native.numericId).padStart(4,'0')}`, currentId = `(#${String(row.numericId).padStart(4,'0')}`;
        check(`${row.key} advanced title contains actual native numeric ID`, row.advanced[0].includes(currentId), true);
        check(`${row.key} control advanced title contains its native numeric ID`, advanced[0].includes(nativeId), true);
        advanced[0] = advanced[0].replace(nativeId,currentId);
        check(`${row.key} complete advanced tooltip preserved with session registry ID`, row.advanced, decorated(advanced, row.aliases, native.enchantmentName, target.enchantmentName));
        check(`${row.key} payload matches separate control`, [row.nbt,row.count,row.damage], [native.nbt,native.count,native.damage]);
      }
    }
  }
  if (has('xreliquary')) {
    check('native selected cache map retains identity', o.chinese.cache.identity, o.warmed.identity);
    check('unselected native cached body and near-miss keys preserved', o.chinese.cache.sentinels, o.warmed.sentinels);
  }
  if (has('extendedae')) {
    const traced = o.english.items.find(row => row.key === 'cell-stone');
    check('normal native primary actually reaches cell decoder', Object.keys(traced.nativeCalls).some(key => key.includes('InfinityCell.getRecord')), true);
    check('native trace covers AE item and fluid decoders', ['AEItemStack.fromNBT','AEFluidStack.fromNBT'].every(name => o.english.instrumented.some(key => key.includes(name))), true);
    for (const name of ['InfinityCell.getRecord','InfinityCell.func_77653_i','AEItemStack.fromNBT','AEItemStack.fromItemStack','AEItemStackRegistry.getRegisteredStack','AEFluidStack.fromNBT','AEFluidStack.fromFluidStack']) {
      check(`${name} decoder monitor installed`, o.english.instrumented.some(key => key.includes(name+'(')), true);
      check(`${name} decoder positive control`, o.english.items.some(row => Object.entries(row.nativeCalls).some(([key,count]) => key.includes(name+'(') && count > 0)), true);
    }
    if (product) {
      await call('language.set', {language:'en_us'});
      o.guards = await call('candidate.guards');
      const positive = {'depth-1':'環[石頭]', 'depth-7':'環['.repeat(7)+'石頭'+']'.repeat(7), 'unknown-leaf-literal':'環[ §a%1$s 字 ]', 'empty-literal':'環[]', 'damage-short-overflow':'環[石頭]', 'format-%1$s %s %1$s':'石頭 石頭 石頭'};
      const wrapped = value => `无限A〔${value}〕存储元件`;
      const collectorPositive = {'depth-1':wrapped('石头'), 'depth-7':Array.from({length:7}).reduce(value => wrapped(value), '石头'), 'entry-depth-8':wrapped('石头'), 'unknown-leaf-literal':wrapped(' §a%1$s 字 '), 'empty-literal':wrapped(''), 'damage-short-overflow':wrapped('石头'), 'missing-effective-wrapper':wrapped('石头')};
      for (const row of o.guards.cases) {
        check(`${row.key} bounded resolver value`, row.value, positive[row.key] ?? null);
        check(`${row.key} public collector value`, row.aliases, row.key.startsWith('format-') ? [wrapped('石头')] : collectorPositive[row.key] ? [collectorPositive[row.key]] : []);
        check(`${row.key} direct resolver avoids native display, decoding and recursive data operations`, row.directCalls, {});
        check(`${row.key} public collector avoids native display, decoding and recursive data operations`, row.collectorCalls, {});
        check(`${row.key} preserves pathological payload reference and quantity`, [row.sameTag,row.sameCount], [true,true]);
      }
    }
  }
  await call('language.set', {language:'en_us'});
  o.captures = [];
  for (const key of ['charm-0','cell-water','cell-nested','backpack','prismatic-web','hell-infusion-book','compass']) {
    if (!o.english.items.some(row => row.key === key)) continue;
    const item = await call('candidate.item.open', {key});
    const shot = await render('candidate-item', {x:item.x,y:item.y,requireTooltip:true});
    o.captures.push({key,item,shot}); check(`${key} real registered slot`, item.slotSame, true);
    check(`${key} real native hover title`, plain(shot.lines[0]), plain(item.nativeName));
    if (product && baseline) {
      const native = baseline.captures.find(value => value.key === key);
      const target = o.chinese.items.find(value => value.key === key);
      check(`${key} complete native hover`, shot.lines, decorated(native.shot.lines, item.aliases, native.item.enchantmentName, target.enchantmentName, true));
    }
  }
  o.retained = [];
  for (const key of ['charm-0','cell-water']) {
    if (!o.english.items.some(row => row.key === key)) continue;
    await resources(); await call('resources.reload');
    const opened = await call('candidate.item.open', {key});
    const capture = async stage => {
      const shot = await render(`candidate-retained-${key}-${stage}`, {x:opened.x,y:opened.y,requireTooltip:true});
      const item = await call('candidate.item.open', {key,retained:true});
      o.retained.push({key,stage,item,shot});
      check(`${key} ${stage} retains actual screen, coordinates and slot object`, [item.screenIdentity,item.x,item.y,item.identity,item.slotSame], [opened.screenIdentity,opened.x,opened.y,opened.identity,true]);
      const source = o.english.items.find(row => row.key === key), translated = o.chinese.items.find(row => row.key === key);
      const primaryChinese = stage === 'primary-zh-cn', revisionB = stage === 'resource-B';
      const expectedPrimary = key === 'charm-0' && product ? primaryChinese ? targetText(translated) : revisionB ? 'Reloaded primary B' : plain(source.nativeName) : key === 'cell-water' && primaryChinese ? plain(translated.nativeName) : plain(source.nativeName);
      check(`${key} ${stage} first native hover primary`, plain(shot.lines[0]), expectedPrimary);
      if (product) {
        const expectedAliases = primaryChinese || stage === 'secondary-off' ? [] : key === 'cell-water' ? [`无限${revisionB ? 'B' : 'A'}〔水〕存储元件`] : [targetText(translated)];
        check(`${key} ${stage} expected aliases`, item.aliases.map(plain), expectedAliases);
        const native = baseline.retained.find(value => value.key === key && value.stage === stage);
        const nativeLines = [...native.shot.lines];
        if (key === 'charm-0' && (primaryChinese || revisionB)) {
          check(`${key} native hover title suffix is accounted for`, nativeLines[0].endsWith(native.item.tooltip[0]), true);
          nativeLines[0] = nativeLines[0].slice(0,nativeLines[0].length-native.item.tooltip[0].length) + item.tooltip[0];
        }
        check(`${key} ${stage} full native hover rows`, shot.lines, decorated(nativeLines,item.aliases,null,null,true));
      }
    };
    await capture('initial');
    if (product) await configure([]); await capture('secondary-off');
    if (product) await configure(['zh_cn']); await capture('secondary-on');
    await call('language.set', {language:'zh_cn'}); await capture('primary-zh-cn');
    await call('language.set', {language:'en_us'}); await capture('primary-en-us');
    await resources('B'); await call('resources.reload'); await capture('resource-B');
    await resources(); await call('resources.reload'); await capture('resource-A');
  }
  if (has('xreliquary')) {
    o.mutations = [];
    for (const [key,field,value,expectedKey] of [['charm-0','type',1,'charm-1'],['fragment-0','damage',1,'fragment-1'],['charm-0','type',0,'charm-0'],['fragment-0','damage',0,'fragment-0']]) {
      const changed = await call('candidate.names', {mutate:key,[field]:value});
      const row = changed.items.find(row => row.key === key), original = o.english.items.find(row => row.key === key), expected = o.english.items.find(row => row.key === expectedKey);
      o.mutations.push({key,field,value,row});
      check(`${key} ${field}=${value} keeps stack identity and quantity`, [row.identity,row.count], [original.identity,original.count]);
      check(`${key} ${field}=${value} native title`, row.nativeName, expected.nativeName);
      check(`${key} ${field}=${value} payload changes only selected variant`, [row.nbt,row.damage], [expected.nbt,expected.damage]);
      if (product) check(`${key} ${field}=${value} secondary title updates`, row.aliases, expected.aliases);
    }
  }
  o.beforeResource = await call('candidate.cache');
  await resources('B'); await call('resources.reload'); o.changed = await call('candidate.names');
  if (has('xreliquary')) {
    const changed = o.changed.items.find(row => row.key === 'charm-0'), before = o.english.items.find(row => row.key === 'charm-0');
    check('same-primary resource title replacement', plain(changed.nativeName), product ? 'Reloaded primary B' : plain(before.nativeName));
    check('same-primary reload preserves native map and unrelated cache', [o.changed.cache.identity,o.changed.cache.sentinels], [o.beforeResource.identity,o.beforeResource.sentinels]);
  }
  if (product && has('extendedae')) check('same stack wrapper responds to real resource edit', o.changed.items.find(row => row.key === 'cell-water').aliases.map(plain), ['无限B〔水〕存储元件']);
  if (product) {
    await configure([]); o.disabled = await call('candidate.names');
    check('secondary settings disable every candidate alias', o.disabled.items.every(row => row.aliases.length === 0), true);
    await resources('A'); await call('resources.reload'); o.disabledReload = await call('candidate.names');
    if (has('xreliquary')) check('native title cache repair remains active with aliases off', o.disabledReload.items.find(row => row.key === 'charm-0').nativeName, o.english.items.find(row => row.key === 'charm-0').nativeName);
    await configure(['zh_cn']); await resources('A', true); await call('resources.reload'); o.badMacro = await call('candidate.names');
    if (has('xreliquary')) check('unknown target-only color macro adds no title', o.badMacro.items.find(row => row.key === 'charm-0').aliases, []);
  }
  await resources(); await call('resources.reload'); await call('screen.close');
});
