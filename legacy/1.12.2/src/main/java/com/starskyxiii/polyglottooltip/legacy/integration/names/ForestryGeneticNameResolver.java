package com.starskyxiii.polyglottooltip.legacy.integration.names;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ConsistencyCheck;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryDictionary;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SpecialItemNameResolver;

import net.minecraft.item.ItemStack;

public final class ForestryGeneticNameResolver implements SpecialItemNameResolver {
    private static final Map<String, String> GROUPS = new HashMap<>();

    static {
        GROUPS.put("forestry.arboriculture.items.ItemGermlingGE", "trees");
        GROUPS.put("forestry.lepidopterology.items.ItemButterflyGE", "butterflies");
        GROUPS.put("forestry.apiculture.items.ItemBeeGE", "bees");
    }

    private final IntegrationGuard guard;

    public ForestryGeneticNameResolver(IntegrationGuard guard) {
        this.guard = guard;
    }

    static String group(String itemClass) {
        return GROUPS.get(itemClass);
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return guard.active() && group(stack.func_77973_b().getClass().getName()) != null;
    }

    @Override
    public String resolve(ItemStack stack, ConsistencyCheck.Result result, String languageCode,
                          SecondaryDictionary dictionary) {
        return guard.call(() -> {
            String group = group(stack.func_77973_b().getClass().getName());
            String[] genetic = group == null ? null : ForestryAccess.genetic(stack, group);
            return genetic == null ? null
                    : name(group, genetic[0], genetic[1], result.primary(), NameParts.PRIMARY, dictionary::get);
        }, null);
    }

    static String name(String group, String type, String species, String primary,
                       Function<String, String> primaryLang, Function<String, String> secondaryLang) {
        return NameParts.checked(render(group, type, species, primaryLang), primary,
                () -> render(group, type, species, secondaryLang));
    }

    static String render(String group, String type, String species, Function<String, String> lang) {
        String custom = lang.apply("for." + group + ".custom." + type + "."
                + species.replace("for." + group + ".species.", ""));
        if (custom != null) {
            return custom;
        }
        String grammar = "for." + group + ".grammar." + type;
        return NameParts.replace(NameParts.replace(lang.apply(grammar), "%SPECIES", lang.apply(species)),
                "%TYPE", lang.apply(grammar + ".type"));
    }
}
