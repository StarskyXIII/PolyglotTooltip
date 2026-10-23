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
    static final String SEPARATOR = " §7｜ ";

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
                String shown = enchantment.func_77316_c(level);
                String primary = I18n.func_74838_a(nameKey);
                boolean standard = shown != null && SecondaryNames.plain(shown)
                        .equals(SecondaryNames.plain(compose(primary, I18n.func_74838_a(levelKey), withLevel)));
                String suffix = standard ? null : levelSuffix(shown, primary);
                if (!standard && suffix == null) {
                    continue;
                }
                Map<String, String> names = new LinkedHashMap<>();
                for (Map.Entry<String, SecondaryDictionary> dictionary : dictionaries.entrySet()) {
                    String name = dictionary.getValue().get(nameKey);
                    name = standard ? compose(name, dictionary.getValue().get(levelKey), withLevel)
                            : name == null ? null : name + suffix;
                    if (name != null) {
                        names.put(dictionary.getKey(), name);
                    }
                }
                SecondaryNames secondary = SecondaryNames.of(shown, settings.displayLanguages(), names,
                        settings.alwaysShow(), currentLanguage);
                append(lines, shown, secondary.names());
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

    static String levelSuffix(String shown, String primary) {
        if (shown == null || primary == null) {
            return null;
        }
        String plainShown = SecondaryNames.plain(shown);
        String plainPrimary = SecondaryNames.plain(primary);
        if (plainPrimary.isEmpty() || !plainShown.startsWith(plainPrimary)) {
            return null;
        }
        String suffix = plainShown.substring(plainPrimary.length());
        return suffix.isEmpty() || suffix.charAt(0) == ' ' ? suffix : null;
    }

    static boolean append(List<String> lines, String translated, List<String> names) {
        if (translated == null || names.isEmpty()) {
            return false;
        }
        int position = lines.isEmpty() ? -1 : lines.subList(1, lines.size()).indexOf(translated) + 1;
        if (position < 1) {
            return false;
        }
        String style = leadingStyle(translated);
        String shown = SecondaryNames.plain(translated);
        StringBuilder line = new StringBuilder(translated);
        for (String name : names) {
            if (!SecondaryNames.plain(name).equals(shown)) {
                line.append(SEPARATOR).append(style).append(name);
            }
        }
        if (line.length() == translated.length()) {
            return false;
        }
        lines.set(position, line.toString());
        return true;
    }

    static String leadingStyle(String line) {
        int end = 0;
        while (end + 1 < line.length() && line.charAt(end) == '§') {
            end += 2;
        }
        return line.substring(0, end);
    }
}
