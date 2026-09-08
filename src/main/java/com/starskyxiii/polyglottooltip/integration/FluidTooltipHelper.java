package com.starskyxiii.polyglottooltip.integration;

import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.List;

public final class FluidTooltipHelper {
    private static final String LDLIB_EMPTY_FLUID_KEY = "ldlib.fluid.empty";
    private static final String GTCEU_EMPTY_FLUID_KEY = "gtceu.fluid.empty";

    private FluidTooltipHelper() {
    }

    public static List<Component> withSecondaryFluidName(List<Component> original) {
        if (original == null || original.isEmpty() || !SecondaryTooltipUtil.shouldShowSecondaryLanguage()) {
            return original;
        }

        Component fluidName = original.get(0);
        if (isEmptyFluidName(fluidName)) {
            return original;
        }

        List<Component> tooltip = new ArrayList<>(original);
        SecondaryTooltipUtil.insertSecondaryName(tooltip, fluidName);
        return tooltip;
    }

    private static boolean isEmptyFluidName(Component component) {
        if (component == null || component.getString().isBlank()) {
            return true;
        }
        if (component.getContents() instanceof TranslatableContents contents) {
            String key = contents.getKey();
            return LDLIB_EMPTY_FLUID_KEY.equals(key) || GTCEU_EMPTY_FLUID_KEY.equals(key);
        }
        return false;
    }
}
