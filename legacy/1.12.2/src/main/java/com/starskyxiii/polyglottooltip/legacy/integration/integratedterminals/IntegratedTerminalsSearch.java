package com.starskyxiii.polyglottooltip.legacy.integration.integratedterminals;

import java.util.function.Predicate;

import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.integration.StorageSearchText;
import com.starskyxiii.polyglottooltip.legacy.minecraft.FluidSecondaryNames;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ItemNames;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public final class IntegratedTerminalsSearch {
    static final StorageSearchText TEXT = new StorageSearchText();

    private IntegratedTerminalsSearch() {}

    public static boolean itemMatches(ItemStack stack, Predicate<String> matches) {
        IntegrationGuard guard = IntegratedTerminalsIntegration.active;
        if (guard == null || stack == null) {
            return false;
        }
        return guard.call(() -> anySecondary(TEXT.text(stack.func_82833_r(), ItemNames.of(stack)), matches), false);
    }

    public static boolean fluidMatches(FluidStack stack, Predicate<String> matches) {
        IntegrationGuard guard = IntegratedTerminalsIntegration.active;
        if (guard == null || stack == null || stack.getFluid() == null) {
            return false;
        }
        return guard.call(() -> anySecondary(TEXT.text(stack.getLocalizedName(), FluidSecondaryNames.names(stack)), matches),
                false);
    }

    static boolean anySecondary(String text, Predicate<String> matches) {
        if (text == null) {
            return false;
        }
        String[] lines = text.split(StorageSearchText.SEPARATOR);
        for (int index = 1; index < lines.length; index++) {
            if (matches.test(lines[index])) {
                return true;
            }
        }
        return false;
    }
}
