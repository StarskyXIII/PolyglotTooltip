package com.starskyxiii.polyglottooltip.legacy.integration.names;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;

import com.starskyxiii.polyglottooltip.legacy.PolyglotTooltipMod;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ConsistencyCheck;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ResolvedName;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryDictionary;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SpecialItemNameResolver;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class TinkersNameResolver implements SpecialItemNameResolver {
    private final IntegrationGuard guard;
    private final BiConsumer<String, Throwable> warn;
    private final TinkersAccess access;
    private volatile boolean broken;

    public TinkersNameResolver(IntegrationGuard guard) throws ReflectiveOperationException {
        this.guard = guard;
        this.warn = LogManager.getLogger(PolyglotTooltipMod.MOD_ID)::warn;
        this.access = new TinkersAccess(getClass().getClassLoader());
    }

    @Override
    public boolean accepts(ItemStack stack) {
        Item item = stack.func_77973_b();
        return !broken && guard.active() && (access.isPart(item) || access.isTool(item));
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
        if (broken || result.key() == null) {
            return null;
        }
        Item item = stack.func_77973_b();
        boolean isPart = access.isPart(item);
        List<String> materials;
        try {
            materials = isPart ? Collections.singletonList(access.partMaterial(stack)) : access.toolMaterials(stack);
        } catch (Throwable error) {
            broken = true;
            warn.accept("Disabled the Tinkers' Construct name resolver because a Tinkers call failed;"
                    + " it stays off until the game restarts.", error);
            return null;
        }
        String customKey = isPart ? item.func_77658_a() + "." + materials.get(0) : null;
        return guard.call(() -> name(customKey, materials, result.key(), result.primary(), NameParts.PRIMARY,
                dictionary::get, NameParts.reverse(dictionary)), null);
    }

    static ResolvedName name(String customKey, List<String> materials, String baseKey, String shown,
                             Function<String, String> primaryLang, Function<String, String> secondaryLang,
                             Function<String, String> reverse) {
        List<String> untranslated = new ArrayList<>();
        String custom = customKey == null ? null : primaryLang.apply(customKey);
        if (custom != null) {
            return new NameParts.Joined(reverse)
                    .part(custom, ResolvedName.of(secondaryLang.apply(customKey))).checked(shown);
        }
        String base = primaryLang.apply(baseKey);
        if (base == null || materials.isEmpty()) {
            return null;
        }
        base = base.trim();
        String primary = compose(materials, base, id -> primaryLang.apply(prefixKey(id)),
                id -> trimmed(primaryLang.apply(nameKey(id))));
        if (primary == null || !NameParts.normalize(primary).equals(NameParts.normalize(shown))) {
            return null;
        }
        String secondaryBase = NameParts.orPrimary(base, ResolvedName.of(secondaryLang.apply(baseKey)), reverse,
                untranslated);
        String secondary = compose(materials, secondaryBase, id -> secondaryLang.apply(prefixKey(id)), id -> {
            String value = secondaryLang.apply(nameKey(id));
            String text = trimmed(primaryLang.apply(nameKey(id)));
            return value != null || text == null ? value : NameParts.orPrimary(text, null, reverse, untranslated);
        });
        return secondary == null ? null : ResolvedName.of(NameParts.normalize(secondary), untranslated);
    }

    private static String compose(List<String> materials, String base, Function<String, String> prefix,
                                  Function<String, String> name) {
        if (materials.size() == 1) {
            String format = prefix.apply(materials.get(0));
            if (format != null) {
                return NameParts.format(format, base);
            }
        }
        StringBuilder names = new StringBuilder();
        for (String id : materials) {
            String value = name.apply(id);
            if (value == null) {
                return null;
            }
            names.append(names.length() > 0 ? "-" : "").append(value);
        }
        return names + " " + base;
    }

    private static String prefixKey(String id) {
        return "material." + id + ".prefix";
    }

    private static String nameKey(String id) {
        return "material." + id + ".name";
    }

    private static String trimmed(String text) {
        return text == null ? null : text.trim();
    }
}
