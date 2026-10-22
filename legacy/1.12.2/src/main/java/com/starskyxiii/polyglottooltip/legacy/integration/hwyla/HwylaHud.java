package com.starskyxiii.polyglottooltip.legacy.integration.hwyla;

import java.util.ArrayList;
import java.util.List;

import com.starskyxiii.polyglottooltip.legacy.Config;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.integration.hud.HudLines;
import com.starskyxiii.polyglottooltip.legacy.integration.hud.HudNames;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryNames;

import mcp.mobius.waila.api.IWailaCommonAccessor;
import mcp.mobius.waila.api.event.WailaTooltipEvent;
import net.minecraft.entity.Entity;

public final class HwylaHud {
    private HwylaHud() {}

    public static List<String> tip(WailaTooltipEvent event) {
        List<String> tip = event.getCurrentTip();
        IntegrationGuard guard = HwylaIntegration.active;
        if (guard == null || tip == null || tip.isEmpty()) {
            return tip;
        }
        return guard.call(() -> withNames(tip, event.getAccessor()), tip);
    }

    private static List<String> withNames(List<String> tip, IWailaCommonAccessor accessor) {
        if (accessor == null) {
            return tip;
        }
        List<String> lines = HudLines.compose(names(accessor).names(), Config.settings().hudPrefix(), tip);
        if (lines.isEmpty()) {
            return tip;
        }
        List<String> result = new ArrayList<>(tip.size() + lines.size());
        result.addAll(tip);
        result.addAll(1, lines);
        return result;
    }

    private static SecondaryNames names(IWailaCommonAccessor accessor) {
        Entity entity = accessor.getEntity();
        if (entity != null) {
            return HudNames.entity(entity);
        }
        return HudNames.block(accessor.getBlock(), accessor::getStack);
    }
}
