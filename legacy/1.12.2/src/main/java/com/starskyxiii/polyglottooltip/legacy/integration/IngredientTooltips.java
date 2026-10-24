package com.starskyxiii.polyglottooltip.legacy.integration;

import java.util.ArrayList;
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

    public static void appendAfter(IntegrationGuard guard, List<String> lines, String nameLine,
                                   Supplier<SecondaryNames> names) {
        if (guard == null || lines == null || nameLine == null || TooltipLines.skipped()) {
            return;
        }
        guard.run(() -> TooltipLines.insertAfterLine(lines, nameLine, names.get().names(),
                Config.settings().tooltipPrefix()));
    }

    public static List<String> withNames(IntegrationGuard guard, List<String> lines, String nameLine,
                                         Supplier<SecondaryNames> names) {
        if (guard == null || lines == null || nameLine == null || TooltipLines.skipped()) {
            return lines;
        }
        return guard.call(() -> {
            List<String> found = names.get().names();
            if (found.isEmpty()) {
                return lines;
            }
            List<String> copy = new ArrayList<>(lines);
            return TooltipLines.insertAfterLine(copy, nameLine, found, Config.settings().tooltipPrefix()) ? copy : lines;
        }, lines);
    }

    public static String message(IntegrationGuard guard, String name, Supplier<SecondaryNames> names) {
        if (guard == null || name == null || TooltipLines.skipped()) {
            return name;
        }
        return guard.call(() -> {
            List<String> lines = new ArrayList<>();
            lines.add(name);
            TooltipLines.insertAfterName(lines, names.get().names(), Config.settings().tooltipPrefix());
            return String.join("\n", lines);
        }, name);
    }
}
