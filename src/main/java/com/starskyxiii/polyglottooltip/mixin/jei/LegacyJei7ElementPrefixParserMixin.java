package com.starskyxiii.polyglottooltip.mixin.jei;
//~ minecraft_116_packages

import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.integration.mekanism.MekanismTooltipHelper;
import com.starskyxiii.polyglottooltip.integration.productivebees.ProductiveBeesNameHelper;
import mezz.jei.ingredients.IIngredientListElementInfo;
import net.minecraft.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Pseudo
@Mixin(targets = "mezz.jei.search.ElementPrefixParser", remap = false)
public class LegacyJei7ElementPrefixParserMixin {
    @Inject(
            method = "lambda$static$1(Lmezz/jei/ingredients/IIngredientListElementInfo;)Ljava/util/Collection;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 1
    )
    private static void polyglot$appendSecondaryNames(IIngredientListElementInfo<?> element,
                                                     CallbackInfoReturnable<Collection<String>> cir) {
        Object ingredient = element.getElement().getIngredient();
        LanguageCache cache = LanguageCache.getInstance();
        List<String> names = ingredient instanceof ItemStack
                ? cache.resolveSearchNamesForAll((ItemStack) ingredient)
                : polyglot$resolveNonItemNames(ingredient, cache);
        List<String> combined = new ArrayList<>(cir.getReturnValue());
        for (String name : names) {
            String normalized = name.toLowerCase(Locale.ROOT);
            if (!normalized.codePoints().allMatch(Character::isWhitespace) && !combined.contains(normalized)) {
                combined.add(normalized);
            }
        }
        cir.setReturnValue(combined);
    }

    private static List<String> polyglot$resolveNonItemNames(Object ingredient, LanguageCache cache) {
        if (ingredient instanceof FluidStack) {
            return cache.resolveComponentsForAll(((FluidStack) ingredient).getDisplayName());
        }
        Optional<Component> chemicalName = MekanismTooltipHelper.getChemicalNameFromStack(ingredient);
        if (chemicalName.isPresent()) {
            return cache.resolveComponentsForAll(chemicalName.get());
        }
        return ProductiveBeesNameHelper.tryCreateBeeIngredientName(ingredient)
                .map(cache::resolveComponentsForAll)
                .orElse(Collections.emptyList());
    }
}
