package com.starskyxiii.polyglottooltip.mixin.jei;
//~ minecraft_116_packages

import com.llamalad7.mixinextras.sugar.Local;
import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.integration.jei.FabricJeiFluidNameHelper;
import mezz.jei.api.fabric.ingredients.fluids.IJeiFluidIngredient;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Pseudo
@Mixin(targets = "mezz.jei.gui.ingredients.ListElementInfo", remap = false)
public class FabricJeiListElementInfoMixin {
    @Shadow
    @Mutable
    @Final
    private List<String> names;

    @Inject(
            method = "<init>(Lmezz/jei/gui/ingredients/IListElement;Lmezz/jei/api/runtime/IIngredientManager;Lmezz/jei/api/helpers/IModIdHelper;)V",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private void polyglot$appendSecondaryLanguageNames(CallbackInfo ci,
                                                       @Local ITypedIngredient<?> value) {
        LanguageCache cache = LanguageCache.getInstance();
        Optional<ItemStack> itemStack = value.getItemStack();
        List<String> secondaryNames;
        if (itemStack.isPresent()) {
            secondaryNames = cache.resolveSearchNamesForAll(itemStack.get());
        } else if (value.getIngredient() instanceof IJeiFluidIngredient fluid
                && !FabricJeiFluidNameHelper.isEmpty(fluid)) {
            secondaryNames = cache.resolveComponentsForAll(FabricJeiFluidNameHelper.getDisplayName(fluid));
        } else {
            secondaryNames = List.of();
        }
        if (secondaryNames.isEmpty()) {
            return;
        }

        String primaryName = names.isEmpty() ? "" : names.get(0);
        List<String> combined = new ArrayList<>(names);
        for (String name : secondaryNames) {
            String normalized = name.toLowerCase(Locale.ROOT);
            if (!normalized.equals(primaryName) && !combined.contains(normalized)) {
                combined.add(normalized);
            }
        }
        if (combined.size() != names.size()) {
            names = combined;
        }
    }
}
