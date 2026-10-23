package com.starskyxiii.polyglottooltip.legacy.integration.generic;

import java.lang.Character.UnicodeScript;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ConsistencyCheck;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ResolvedName;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryDictionary;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SpecialItemNameResolver;

import net.minecraft.entity.EntityList;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.translation.I18n;

public final class CompositeNameResolver implements SpecialItemNameResolver {
    private final IntegrationGuard guard;

    public CompositeNameResolver(IntegrationGuard guard) {
        this.guard = guard;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return guard.active();
    }

    @Override
    public String resolve(ItemStack stack, ConsistencyCheck.Result result, String languageCode,
                          SecondaryDictionary dictionary) {
        ResolvedName name = resolveName(stack, result, languageCode, dictionary);
        return name == null ? null : name.name();
    }

    @Override
    public ResolvedName resolveName(ItemStack stack, ConsistencyCheck.Result result, String languageCode,
                                    SecondaryDictionary dictionary) {
        return guard.call(() -> around(stack, result.key(), result.expected(), result.primary(),
                result.key() == null ? null : dictionary.get(result.key()), dictionary), null);
    }

    @SuppressWarnings("deprecation")
    static ResolvedName around(ItemStack stack, String nameKey, String expected, String actual, String anchor,
                               SecondaryDictionary dictionary) {
        if (expected == null || expected.isEmpty() || actual == null || !actual.contains(expected) || anchor == null) {
            return null;
        }
        Function<String, String> parts = translator(derivedKeys(stack, nameKey),
                key -> ReverseIndex.normalize(I18n.func_74838_a(key)), dictionary::get,
                text -> ReverseIndex.current().translate(text, dictionary));
        return compose(expected, actual, anchor, parts);
    }

    static List<String> derivedKeys(ItemStack stack, String nameKey) {
        List<String> keys = new ArrayList<>();
        Item item = stack.func_77973_b();
        if (item instanceof ItemBlock) {
            String block = ((ItemBlock) item).func_179223_d().func_149739_a() + ".name";
            if (!block.equals(nameKey)) {
                keys.add(block);
            }
        }
        if (item instanceof ItemMonsterPlacer) {
            ResourceLocation entity = ItemMonsterPlacer.func_190908_h(stack);
            String name = entity == null ? null : EntityList.func_191302_a(entity);
            if (name != null) {
                keys.add("entity." + name + ".name");
            }
        }
        return keys;
    }

    static Function<String, String> translator(List<String> derivedKeys, Function<String, String> primaryOf,
                                               Function<String, String> secondaryOf,
                                               Function<String, String> reverse) {
        return text -> {
            for (String key : derivedKeys) {
                if (text.equals(primaryOf.apply(key))) {
                    String value = secondaryOf.apply(key);
                    if (value != null) {
                        return value;
                    }
                }
            }
            return reverse.apply(text);
        };
    }

    static ResolvedName compose(String expected, String actual, String anchor, Function<String, String> parts) {
        if (expected == null || expected.isEmpty() || actual == null || anchor == null) {
            return null;
        }
        int at = actual.indexOf(expected);
        if (at < 0) {
            return null;
        }
        List<String> untranslated = new ArrayList<>();
        String prefix = part(actual.substring(0, at), parts, untranslated);
        String suffix = part(actual.substring(at + expected.length()), parts, untranslated);
        String composed = join(join(prefix, anchor), suffix);
        if (ReverseIndex.normalize(composed).equals(ReverseIndex.normalize(actual))) {
            return null;
        }
        return ResolvedName.of(composed, untranslated);
    }

    static boolean hasLetter(String text) {
        return text.codePoints().anyMatch(Character::isLetter);
    }

    private static String part(String text, Function<String, String> parts, List<String> untranslated) {
        int start = 0;
        int end = text.length();
        while (start < end && isSeparator(text.charAt(start))) {
            start++;
        }
        while (end > start && isSeparator(text.charAt(end - 1))) {
            end--;
        }
        if (start == end) {
            return text;
        }
        String core = text.substring(start, end);
        String translated = parts.apply(core);
        if (translated == null && hasLetter(core)) {
            untranslated.add(core);
        }
        return text.substring(0, start) + (translated != null ? translated : core) + text.substring(end);
    }

    private static boolean isSeparator(char c) {
        return !Character.isLetterOrDigit(c);
    }

    private static String join(String left, String right) {
        if (left.isEmpty() || right.isEmpty()) {
            return left + right;
        }
        return spaced(left.charAt(left.length() - 1)) && spaced(right.charAt(0))
                ? left + " " + right : left + right;
    }

    private static boolean spaced(char c) {
        if (!Character.isLetterOrDigit(c)) {
            return false;
        }
        UnicodeScript script = UnicodeScript.of(c);
        return script != UnicodeScript.HAN && script != UnicodeScript.HIRAGANA && script != UnicodeScript.KATAKANA;
    }
}
