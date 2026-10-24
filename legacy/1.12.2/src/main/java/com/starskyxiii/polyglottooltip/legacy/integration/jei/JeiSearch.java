package com.starskyxiii.polyglottooltip.legacy.integration.jei;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;

import com.starskyxiii.polyglottooltip.legacy.Config;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.integration.mekanism.MekanismGasNames;
import com.starskyxiii.polyglottooltip.legacy.minecraft.FluidSecondaryNames;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ItemNames;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryLanguages;
import com.starskyxiii.polyglottooltip.legacy.minecraft.TooltipLines;

import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.util.Translator;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public final class JeiSearch {
    public static final long COMPLETE = Long.MAX_VALUE;
    private static final long TICK_BUDGET_NANOS = 4_000_000L;

    private static volatile IntegrationGuard guard;
    private static volatile WeakReference<SearchIndex> current = new WeakReference<>(null);

    private JeiSearch() {}

    static void attach(IntegrationGuard integrationGuard) {
        guard = integrationGuard;
    }

    public static int track(SearchIndex index) {
        current = new WeakReference<>(index);
        return SecondaryLanguages.generation();
    }

    public static int indexPositions(List<?> elements, int from, long budgetNanos, ObjIntConsumer<String> put) {
        IntegrationGuard active = guard;
        if (active == null || !active.active()) {
            return elements.size();
        }
        long start = System.nanoTime();
        int position = from;
        while (position < elements.size() && System.nanoTime() - start < budgetNanos) {
            for (String token : tokens(active, elements.get(position))) {
                put.accept(token, position);
            }
            position++;
        }
        return position;
    }

    public static int indexElements(List<?> elements, int from, long budgetNanos, BiConsumer<String, Object> put) {
        return indexPositions(elements, from, budgetNanos, (token, position) -> put.accept(token, elements.get(position)));
    }

    static void refresh() {
        IntegrationGuard active = guard;
        SearchIndex index = current.get();
        if (active != null && index != null && index.polyglot$generation() != SecondaryLanguages.generation()) {
            active.run(index::polyglot$rebuild);
        }
    }

    public static void indexBeforeSearch(SearchIndex index) {
        IntegrationGuard active = guard;
        if (active != null && Minecraft.func_71410_x().func_152345_ab()) {
            active.run(() -> index.polyglot$indexPending(COMPLETE));
        }
    }

    public static <T> T searchTooltip(Supplier<T> tooltip) {
        IntegrationGuard active = guard;
        if (active == null || !active.active()) {
            return tooltip.get();
        }
        return TooltipLines.withoutSecondaryLines(tooltip);
    }

    static void indexPending() {
        IntegrationGuard active = guard;
        SearchIndex index = current.get();
        if (active != null && index != null) {
            active.run(() -> index.polyglot$indexPending(TICK_BUDGET_NANOS));
        }
    }

    private static List<String> tokens(IntegrationGuard active, Object element) {
        if (!(element instanceof IIngredientListElement)) {
            return Collections.emptyList();
        }
        IIngredientListElement<?> listElement = (IIngredientListElement<?>) element;
        Object ingredient = listElement.getIngredient();
        if (!(ingredient instanceof ItemStack) && !(ingredient instanceof FluidStack)
                && !MekanismGasNames.isGas(ingredient)) {
            return Collections.emptyList();
        }
        return active.call(() -> SearchTokens.of(listElement.getDisplayName(), secondaryNames(ingredient),
                Config.settings().chineseScriptMatching(),
                Translator::toLowercaseWithLocale), Collections.<String>emptyList());
    }

    private static List<String> secondaryNames(Object ingredient) {
        if (ingredient instanceof ItemStack) {
            return ItemNames.of((ItemStack) ingredient).names();
        }
        if (ingredient instanceof FluidStack) {
            return FluidSecondaryNames.names((FluidStack) ingredient).names();
        }
        return MekanismGasNames.search(ingredient);
    }
}
