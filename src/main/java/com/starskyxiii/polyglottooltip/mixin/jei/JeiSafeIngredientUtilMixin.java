package com.starskyxiii.polyglottooltip.mixin.jei;

import com.mojang.datafixers.util.Either;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import com.starskyxiii.polyglottooltip.integration.productivebees.ProductiveBeesNameHelper;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(targets = "mezz.jei.common.util.SafeIngredientUtil", remap = false)
public class JeiSafeIngredientUtilMixin {

    private static final String JEI_TOOLTIP = "mezz.jei.common.gui.JeiTooltip";
    private static final Map<String, Optional<Method>> METHOD_CACHE = new ConcurrentHashMap<>();

    @Inject(
            method = "getRichTooltip(Lmezz/jei/api/gui/builder/ITooltipBuilder;Lmezz/jei/api/runtime/IIngredientManager;Lmezz/jei/api/ingredients/IIngredientRenderer;Lmezz/jei/api/ingredients/ITypedIngredient;Lnet/minecraft/world/item/TooltipFlag;)V",
            at = @At("RETURN"),
            remap = false
    )
    private static <T> void onGetRichTooltip(ITooltipBuilder tooltip,
                                             IIngredientManager ingredientManager,
                                             IIngredientRenderer<T> ingredientRenderer,
                                             ITypedIngredient<T> typedIngredient,
                                             TooltipFlag tooltipFlag,
                                             CallbackInfo ci) {
        ProductiveBeesNameHelper.tryCreateBeeIngredientName(typedIngredient.getIngredient())
                .ifPresent(name -> insertSecondaryLinesIntoJeiTooltip(tooltip, name));
    }

    @Inject(method = "getPlainTooltipForSearch", at = @At("RETURN"), cancellable = true, remap = false)
    private static <T> void onGetPlainTooltipForSearch(IIngredientManager ingredientManager,
                                                       IIngredientRenderer<T> ingredientRenderer,
                                                       ITypedIngredient<T> typedIngredient,
                                                       TooltipFlag.Default tooltipFlag,
                                                       CallbackInfoReturnable<List<Component>> cir) {
        ProductiveBeesNameHelper.tryCreateBeeIngredientName(typedIngredient.getIngredient())
                .ifPresent(name -> {
                    List<Component> tooltip = new ArrayList<>(cir.getReturnValue());
                    SecondaryTooltipUtil.insertSecondaryName(tooltip, name);
                    cir.setReturnValue(tooltip);
                });
    }

    @SuppressWarnings("unchecked")
    private static void insertSecondaryLinesIntoJeiTooltip(ITooltipBuilder tooltip, Component sourceName) {
        if (!SecondaryTooltipUtil.shouldShowSecondaryLanguage()) return;
        if (tooltip == null || !JEI_TOOLTIP.equals(tooltip.getClass().getName())) return;

        List<Component> secondaryLines = buildSecondaryLines(sourceName);
        if (secondaryLines.isEmpty()) return;

        String cacheKey = tooltip.getClass().getName() + "#getLines";
        Optional<Method> getLines = METHOD_CACHE.computeIfAbsent(cacheKey,
                k -> findMethod(tooltip.getClass(), "getLines"));
        if (getLines.isEmpty()) return;

        try {
            Object rawLines = getLines.get().invoke(tooltip);
            if (!(rawLines instanceof List<?> lines)) return;

            int insertAt = Math.min(lines.isEmpty() ? 0 : 1, lines.size());
            for (Component secondary : secondaryLines) {
                if (containsLine(lines, secondary.getString())) continue;
                ((List<Object>) lines).add(insertAt++, Either.left(secondary));
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static List<Component> buildSecondaryLines(Component sourceName) {
        // insertSecondaryName inserts at index 0 when the list is empty,
        // so no dummy first element is needed.
        List<Component> lines = new ArrayList<>();
        SecondaryTooltipUtil.insertSecondaryName(lines, sourceName);
        return lines;
    }

    private static boolean containsLine(List<?> lines, String target) {
        for (Object line : lines) {
            if (!(line instanceof Either<?, ?> either)) continue;
            Optional<?> left = either.left();
            if (left.isPresent() && left.get() instanceof FormattedText text && target.equals(text.getString())) {
                return true;
            }
        }
        return false;
    }

    private static Optional<Method> findMethod(Class<?> startClass, String methodName) {
        try {
            Method method = startClass.getMethod(methodName);
            method.setAccessible(true);
            return Optional.of(method);
        } catch (NoSuchMethodException ignored) {
            // Fall through to declared-method lookup for non-public members.
        } catch (SecurityException ignored) {
            return Optional.empty();
        }

        Class<?> current = startClass;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return Optional.of(method);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            } catch (SecurityException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
