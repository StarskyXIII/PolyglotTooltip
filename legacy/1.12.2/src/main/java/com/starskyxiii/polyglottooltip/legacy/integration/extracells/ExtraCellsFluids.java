package com.starskyxiii.polyglottooltip.legacy.integration.extracells;

import java.util.List;

import com.starskyxiii.polyglottooltip.legacy.integration.IngredientTooltips;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.integration.StorageSearchText;
import com.starskyxiii.polyglottooltip.legacy.minecraft.FluidSecondaryNames;
import com.starskyxiii.polyglottooltip.legacy.mixin.extracells.ExtraCellsTerminalAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

public final class ExtraCellsFluids {
    static final StorageSearchText TEXT = new StorageSearchText();

    private ExtraCellsFluids() {}

    public static String searchText(FluidStack stack, String primary) {
        IntegrationGuard guard = ExtraCellsIntegration.active;
        if (guard == null || primary == null || stack == null) {
            return primary;
        }
        return guard.call(() -> TEXT.text(primary, FluidSecondaryNames.names(stack)), primary);
    }

    public static void appendNames(List<String> lines, FluidStack stack) {
        if (stack != null && stack.getFluid() != null) {
            IngredientTooltips.appendAfter(ExtraCellsIntegration.active, lines, stack.getLocalizedName(),
                    () -> FluidSecondaryNames.names(stack));
        }
    }

    public static List<String> withNames(List<String> lines, Fluid fluid) {
        if (fluid == null) {
            return lines;
        }
        FluidStack stack = new FluidStack(fluid, 1000);
        return IngredientTooltips.withNames(ExtraCellsIntegration.active, lines, stack.getLocalizedName(),
                () -> FluidSecondaryNames.names(stack));
    }

    static void refreshOpenTerminal() {
        GuiScreen screen = Minecraft.func_71410_x().field_71462_r;
        if (screen instanceof ExtraCellsTerminalAccessor) {
            ((ExtraCellsTerminalAccessor) screen).polyglot$updateFluids();
        }
    }
}
