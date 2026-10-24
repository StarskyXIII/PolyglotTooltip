package com.starskyxiii.polyglottooltip.legacy.integration;

import java.util.List;
import java.util.function.Supplier;

import com.starskyxiii.polyglottooltip.legacy.Config;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryNames;
import com.starskyxiii.polyglottooltip.legacy.minecraft.TooltipLines;

public final class IngredientTooltips {
    private IngredientTooltips() {}

    public static void append(IntegrationGuard guard, List<String> lines, Supplier<SecondaryNames> names) {
        if (guard == null || lines == null || lines.isEmpty() || TooltipLines.skipped()) {
            return;
        }
        guard.run(() -> TooltipLines.insertAfterName(lines, names.get().names(), Config.settings().tooltipPrefix()));
    }
}
