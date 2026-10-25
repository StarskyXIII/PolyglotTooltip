package com.starskyxiii.polyglottooltip.legacy.integration.modularui;

import java.util.ArrayList;
import java.util.List;

import com.cleanroommc.modularui.screen.RichTooltip;
import com.starskyxiii.polyglottooltip.legacy.Config;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.minecraft.FluidSecondaryNames;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryNames;
import com.starskyxiii.polyglottooltip.legacy.minecraft.TooltipLines;

import net.minecraftforge.fluids.FluidStack;

public final class ModularUiTooltips {
    private ModularUiTooltips() {}

    public static void appendFluidNames(RichTooltip tooltip, FluidStack stack) {
        IntegrationGuard guard = ModularUiIntegration.active;
        if (guard == null || tooltip == null || stack == null || stack.getFluid() == null || TooltipLines.skipped()) {
            return;
        }
        guard.run(() -> {
            for (String line : lines(stack.getLocalizedName(), FluidSecondaryNames.names(stack).names(),
                    Config.settings().tooltipPrefix())) {
                tooltip.addLine(line);
            }
        });
    }

    static List<String> lines(String shown, List<String> names, String prefix) {
        List<String> lines = new ArrayList<>();
        String plain = SecondaryNames.plain(shown);
        for (String name : names) {
            if (!SecondaryNames.plain(name).equals(plain)) {
                lines.add(prefix + name);
            }
        }
        return lines;
    }
}
