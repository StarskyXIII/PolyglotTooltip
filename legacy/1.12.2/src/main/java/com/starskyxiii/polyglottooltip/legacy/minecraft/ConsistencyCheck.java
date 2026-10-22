package com.starskyxiii.polyglottooltip.legacy.minecraft;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;

public final class ConsistencyCheck {
    private ConsistencyCheck() {}

    public static final class Result {
        private final String primary;
        private final Map<String, String> names;

        private Result(String primary, Map<String, String> names) {
            this.primary = primary;
            this.names = names;
        }

        public String primary() {
            return primary;
        }

        public boolean handOff() {
            return names == null;
        }

        public Map<String, String> names() {
            return names;
        }
    }

    @SuppressWarnings("deprecation")
    public static Result resolve(ItemStack stack, Map<String, SecondaryDictionary> dictionaries) {
        String actual = null;
        try {
            actual = TextFormatting.func_110646_a(stack.func_82833_r()).trim();
            String key = ItemNameKeys.of(stack);
            if (key == null) {
                return new Result(actual, null);
            }
            String expected = TextFormatting.func_110646_a(I18n.func_74838_a(key)).trim();
            if (!expected.equals(actual)) {
                return new Result(actual, null);
            }
            Map<String, String> names = new LinkedHashMap<>();
            for (Map.Entry<String, SecondaryDictionary> entry : dictionaries.entrySet()) {
                String name = entry.getValue().get(key);
                if (name != null) {
                    names.put(entry.getKey(), name);
                }
            }
            return new Result(actual, names);
        } catch (Throwable error) {
            Object item = stack == null ? null : stack.func_77973_b();
            FailureLog.debug("consistency|" + FailureLog.failureKey(item, error),
                    "Consistency check failed for " + (item == null ? "null" : item.getClass().getName()), error);
            return new Result(actual, null);
        }
    }
}
