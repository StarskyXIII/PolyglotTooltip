package com.starskyxiii.polyglottooltip.mixin.jei;
//~ minecraft_116_packages

import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.integration.mekanism.MekanismTooltipHelper;
import com.starskyxiii.polyglottooltip.integration.productivebees.ProductiveBeesNameHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.common.ingredients.IListElementInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Pseudo
@Mixin(targets = "mezz.jei.common.search.ElementPrefixParser", remap = false)
public class LegacyJei10ElementPrefixParserMixin {
    @Inject(
            method = "lambda$static$1(Lmezz/jei/common/ingredients/IListElementInfo;)Ljava/util/Collection;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 1
    )
    private static void polyglot$appendSecondaryNames(IListElementInfo<?> element,
                                                       CallbackInfoReturnable<Collection<String>> cir) {
        ITypedIngredient<?> typedIngredient = element.getTypedIngredient();
        LanguageCache cache = LanguageCache.getInstance();
        Optional<ItemStack> stack = typedIngredient.getItemStack();
        List<String> secondaryNames = stack.isPresent()
                ? cache.resolveSearchNamesForAll(stack.get())
                : polyglot$resolveNonItemNames(typedIngredient.getIngredient(), cache);
        Collection<String> original = cir.getReturnValue();
        List<String> combined = new ArrayList<>(original);
        for (String name : secondaryNames) {
            String normalized = name.toLowerCase(Locale.ROOT);
            if (!normalized.isBlank() && !combined.contains(normalized)) {
                combined.add(normalized);
            }
        }
        cir.setReturnValue(combined);
    }

    private static List<String> polyglot$resolveNonItemNames(Object ingredient, LanguageCache cache) {
        if (ingredient instanceof FluidStack fluidStack) {
            return cache.resolveComponentsForAll(fluidStack.getDisplayName());
        }
        Optional<Component> chemicalName = MekanismTooltipHelper.getChemicalNameFromStack(ingredient);
        if (chemicalName.isPresent()) {
            return cache.resolveComponentsForAll(chemicalName.get());
        }
        return ProductiveBeesNameHelper.tryCreateBeeIngredientName(ingredient)
                .map(cache::resolveComponentsForAll)
                .orElse(List.of());
    }
}
