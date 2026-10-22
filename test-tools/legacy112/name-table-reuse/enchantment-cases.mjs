// Enchantment rows shared by nova-de-check.mjs and e2ee-check.mjs. Three times every enchantment row lost its
// secondary name silently (a stale digest pin refused Enchantment#getTranslatedName or UniversalTweaks' level-key
// handler) and the player found it first; these cases make that a failed check instead.
// probe.enchantment renders the stack's real tooltip through the product's capture and returns the
// NativeEnchantmentScope outcomes per row (recorded, annotated, unresolved-*), the capture mode and the admissions.
export const ENCHANTED = [
  {id: 'ench-book-sharpness-5', item: 'minecraft:enchanted_book', enchantment: 'minecraft:sharpness', nbt: '{StoredEnchantments:[{id:16s,lvl:5s}]}'},
  {id: 'ench-pickaxe-efficiency-5', item: 'minecraft:diamond_pickaxe', enchantment: 'minecraft:efficiency', nbt: '{ench:[{id:32s,lvl:5s}]}'},
  // Above 10 vanilla has no enchantment.level.N key: Bookshelf supplies the numeral, UniversalTweaks strips the prefix.
  {id: 'ench-pickaxe-efficiency-15', item: 'minecraft:diamond_pickaxe', enchantment: 'minecraft:efficiency', nbt: '{ench:[{id:32s,lvl:15s}]}'},
  // A curse row carries the red §c prefix.
  {id: 'ench-book-vanishing-curse', item: 'minecraft:enchanted_book', enchantment: 'minecraft:vanishing_curse', nbt: '{StoredEnchantments:[{id:71s,lvl:1s}]}'},
];

// Meatball only (EnchControl, mode 2): mod enchantments whose getters override vanilla's and are admitted by
// EnchantmentProviderShape (constant names and levels, enchantability via this receiver's own calculation).
// Given by level: the probe writes the runtime numeric id, which differs per pack.
export const MEATBALL_ENCHANTED = [
  {id: 'ench-book-cofh-holding-4', item: 'minecraft:enchanted_book', enchantment: 'cofhcore:holding', level: 4},
  {id: 'ench-sword-cofh-vorpal-2', item: 'minecraft:diamond_sword', enchantment: 'cofhcore:vorpal', level: 2},
  {id: 'ench-book-de-reaper-3', item: 'minecraft:enchanted_book', enchantment: 'draconicevolution:enchant_reaper', level: 3},
];

/** Runs every case; a case passes when each enchantment row it added was annotated and none was refused. */
export async function checkEnchantments(call, extra = []) {
  const cases = [];
  for (const entry of [...ENCHANTED, ...extra]) {
    const row = {...entry};
    try {
      const probe = await call('probe.enchantment', entry.nbt ? {item: entry.item, enchantment: entry.enchantment, nbt: entry.nbt}
        : {item: entry.item, enchantment: entry.enchantment, level: entry.level});
      const outcomes = probe.outcomes ?? [];
      row.mode = probe.mode;
      row.stripsLevelKey = probe.stripsLevelKey;
      row.outcomes = outcomes;
      row.tooltip = probe.tooltip;
      row.nativeTooltip = probe.nativeTooltipWithoutSecondary;
      row.unannotatedPlans = probe.unannotatedPlans;
      row.providerMethods = probe.providerMethods;
      row.annotated = outcomes.filter(value => value === 'annotated').length;
      row.refused = outcomes.filter(value => value.startsWith('unresolved') || value === 'invalid-trace' || value === 'not-inserted');
      row.pass = row.annotated > 0 && row.refused.length === 0;
      row.full = probe;
    } catch (error) { row.error = String(error).slice(0, 500); row.pass = false; }
    cases.push(row);
  }
  return {pass: cases.every(row => row.pass), cases};
}

/** The summary view of checkEnchantments: drops the full probe payload. */
export const enchantmentSummary = result => ({
  pass: result.pass,
  cases: result.cases.map(({full, providerMethods, unannotatedPlans, nativeTooltip, ...rest}) => rest),
});
