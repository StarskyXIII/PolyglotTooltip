package com.starskyxiii.polyglottooltip.legacy.minecraft;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class ItemNameKeys {
    private ItemNameKeys() {}

    public static String of(ItemStack stack) {
        if (stack == null || stack.func_190926_b() || stack.func_82837_s()) {
            return null;
        }
        Item item = stack.func_77973_b();
        String base = item == null ? null : item.func_77657_g(stack);
        return base == null ? null : base + ".name";
    }
}
