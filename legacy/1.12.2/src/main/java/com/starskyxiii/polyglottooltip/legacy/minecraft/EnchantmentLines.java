package com.starskyxiii.polyglottooltip.legacy.minecraft;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.starskyxiii.polyglottooltip.legacy.Settings;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.translation.I18n;

public final class EnchantmentLines {
    private EnchantmentLines() {}

    @SuppressWarnings("deprecation")
    public static void apply(ItemStack stack, List<String> lines, Settings settings,
                             Map<String, SecondaryDictionary> dictionaries, String currentLanguage) {
        if (dictionaries.isEmpty() || !stack.func_77942_o()) {
            return;
        }
        for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.func_82781_a(stack).entrySet()) {
            Enchantment enchantment = entry.getKey();
            int level = entry.getValue();
            try {
                String nameKey = enchantment.func_77320_a();
                String levelKey = "enchantment.level." + level;
                boolean withLevel = !(level == 1 && enchantment.func_77325_b() == 1);
                String standard = compose(I18n.func_74838_a(nameKey), I18n.func_74838_a(levelKey), withLevel);
                Map<String, String> names = new LinkedHashMap<>();
                for (Map.Entry<String, SecondaryDictionary> dictionary : dictionaries.entrySet()) {
                    String name = compose(dictionary.getValue().get(nameKey), dictionary.getValue().get(levelKey),
                            withLevel);
                    if (name != null) {
                        names.put(dictionary.getKey(), name);
                    }
                }
                SecondaryNames secondary = SecondaryNames.of(standard, settings.displayLanguages(), names,
                        settings.alwaysShow(), currentLanguage);
                insert(lines, enchantment.func_77316_c(level), standard, secondary.names(), settings.tooltipPrefix());
            } catch (RuntimeException error) {
                FailureLog.debug("enchantment|" + FailureLog.failureKey(enchantment, error),
                        "Could not add secondary names for enchantment " + enchantment.getClass().getName(), error);
            }
        }
    }

    static String compose(String name, String levelText, boolean withLevel) {
        if (name == null || withLevel && levelText == null) {
            return null;
        }
        return withLevel ? name + " " + levelText : name;
    }

    static int insert(List<String> lines, String translated, String standard, List<String> names, String prefix) {
        if (translated == null || standard == null || names.isEmpty()
                || !SecondaryNames.plain(translated).equals(SecondaryNames.plain(standard))) {
            return 0;
        }
        int position = lines.isEmpty() ? -1 : lines.subList(1, lines.size()).indexOf(translated) + 2;
        if (position < 2) {
            return 0;
        }
        for (String name : names) {
            lines.add(position++, prefix + name);
        }
        return names.size();
    }
}
