package com.starskyxiii.polyglottooltip.mixin.jei;

import com.llamalad7.mixinextras.sugar.Local;
import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.integration.productivebees.ProductiveBeesNameHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Adds secondary-language item names to JEI's name index (no-prefix search),
 * so users can find items by their secondary-language name without needing
 * the # prefix for tooltip search.
 *
 * <p>JEI's plain-text search uses {@code IListElementInfo::getNames}, which
 * normally returns only the primary display name (e.g., "me 儲物箱"). This
 * mixin appends the secondary-language names (e.g., "me chest") to that list
 * at construction time so that searching "chest" or "me chest" also works.
 *
 * <p>{@code IListElement} is an internal JEI class absent from the compile-only
 * API jars. We avoid shadowing it by using MixinExtras {@code @Local} to capture
 * the {@code value} local variable (type {@code ITypedIngredient<?>}), which IS
 * part of the JEI API and is the first thing assigned in the constructor.
 */
@Mixin(targets = "mezz.jei.gui.ingredients.ListElementInfo", remap = false)
public class JeiListElementInfoMixin {

    @Shadow @Mutable @Final
    private List<String> names;

    @Inject(
            method = "<init>(Lmezz/jei/gui/ingredients/IListElement;Lmezz/jei/api/runtime/IIngredientManager;Lmezz/jei/api/helpers/IModIdHelper;)V",
            at = @At("RETURN"),
            remap = false
    )
    private void appendSecondaryLanguageNames(
            CallbackInfo ci,
            @Local ITypedIngredient<?> value) {
        LanguageCache cache = LanguageCache.getInstance();
        Optional<ItemStack> optStack = value.getItemStack();
        List<String> secondaryNames = optStack.isPresent()
                ? cache.resolveDisplayNamesForAll(optStack.get())
                : ProductiveBeesNameHelper.tryCreateBeeIngredientName(value.getIngredient())
                        .map(cache::resolveComponentsForAll)
                        .orElse(List.of());
        if (secondaryNames.isEmpty()) return;

        String primaryName = names.isEmpty() ? "" : names.get(0);
        List<String> toAdd = new ArrayList<>();
        for (String name : secondaryNames) {
            String lowercased = name.toLowerCase(Locale.ROOT);
            if (!lowercased.equals(primaryName) && !names.contains(lowercased)) {
                toAdd.add(lowercased);
            }
        }
        if (toAdd.isEmpty()) return;

        List<String> combined = new ArrayList<>(names);
        combined.addAll(toAdd);
        this.names = combined;
    }
}
