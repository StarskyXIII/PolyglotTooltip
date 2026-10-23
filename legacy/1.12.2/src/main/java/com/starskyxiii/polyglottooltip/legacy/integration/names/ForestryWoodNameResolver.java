package com.starskyxiii.polyglottooltip.legacy.integration.names;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ConsistencyCheck;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryDictionary;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SpecialItemNameResolver;

import net.minecraft.item.ItemStack;

public final class ForestryWoodNameResolver implements SpecialItemNameResolver {
    static final Set<String> ITEM_CLASSES = new HashSet<>(Arrays.asList(
            "forestry.arboriculture.items.ItemBlockWood",
            "forestry.arboriculture.items.ItemBlockWoodDoor",
            "forestry.arboriculture.items.ItemBlockWoodSlab"));

    static final class Wood {
        final String kind;
        final String type;
        final boolean fireproof;

        Wood(String kind, String type, boolean fireproof) {
            this.kind = kind;
            this.type = type;
            this.fireproof = fireproof;
        }
    }

    private final IntegrationGuard guard;

    public ForestryWoodNameResolver(IntegrationGuard guard) {
        this.guard = guard;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return guard.active() && ITEM_CLASSES.contains(stack.func_77973_b().getClass().getName());
    }

    @Override
    public String resolve(ItemStack stack, ConsistencyCheck.Result result, String languageCode,
                          SecondaryDictionary dictionary) {
        return guard.call(() -> {
            Wood wood = ForestryAccess.wood(stack);
            return wood == null ? null : name(wood, result.primary(), NameParts.PRIMARY, dictionary::get);
        }, null);
    }

    static String name(Wood wood, String primary, Function<String, String> primaryLang,
                       Function<String, String> secondaryLang) {
        return NameParts.checked(render(wood, primaryLang), primary, () -> render(wood, secondaryLang));
    }

    static String render(Wood wood, Function<String, String> lang) {
        String custom = lang.apply("tile.for." + wood.kind + "." + wood.type + ".name");
        String name = custom != null ? custom : NameParts.replace(lang.apply("for." + wood.kind + ".grammar"),
                "%TYPE", lang.apply("for.trees.woodType." + wood.type));
        return wood.fireproof ? NameParts.format(lang.apply("tile.for.fireproof"), name) : name;
    }
}
