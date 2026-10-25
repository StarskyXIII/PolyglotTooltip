package com.starskyxiii.polyglottooltip.legacy.integration.gtceu;

import java.util.List;

import com.starskyxiii.polyglottooltip.legacy.integration.Integration;
import com.starskyxiii.polyglottooltip.legacy.integration.IngredientTooltips;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.minecraft.FluidSecondaryNames;

import net.minecraftforge.fluids.FluidStack;

public final class GregTechIntegration implements Integration {
    public static final String ID = "gtceu";
    public static final String MODID = "gregtech";

    static volatile IntegrationGuard active;

    private final IntegrationGuard guard;

    public GregTechIntegration(IntegrationGuard guard) {
        this.guard = guard;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String modid() {
        return MODID;
    }

    @Override
    public void start() {
        active = guard;
    }

    public static List<String> fluidTooltip(List<String> lines, FluidStack stack) {
        if (stack == null || stack.getFluid() == null) {
            return lines;
        }
        return IngredientTooltips.withNames(active, lines, stack.getLocalizedName(), () -> FluidSecondaryNames.names(stack));
    }
}
