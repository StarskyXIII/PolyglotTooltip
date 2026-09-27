package com.starskyxiii.polyglottooltip.integration.jade;
//~ minecraft_116_packages

import com.starskyxiii.polyglottooltip.PolyglotTooltip;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import mcp.mobius.waila.api.ITaggableList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class LegacyJadeTooltipHelper {
    private static final ResourceLocation SECONDARY_NAME_TAG =
            new ResourceLocation(PolyglotTooltip.MODID, "jade_secondary_name");

    private LegacyJadeTooltipHelper() {
    }

    public static void appendName(List<Component> tooltip, Component source) {
        if (source == null || !SecondaryTooltipUtil.shouldShowSecondaryLanguage()) {
            return;
        }
        ITaggableList<ResourceLocation, Component> tagged =
                (ITaggableList<ResourceLocation, Component>) tooltip;
        if (tagged.getTag(SECONDARY_NAME_TAG) != null) {
            return;
        }
        List<String> names = SecondaryTooltipUtil.getSecondaryNames(source);
        int insertAt = Math.min(1, tooltip.size());
        for (String name : names) {
            Component line = SecondaryTooltipUtil.createJadeSecondaryLine(name);
            tagged.getTags().put(SECONDARY_NAME_TAG, line);
            tooltip.add(insertAt++, line);
        }
    }
}
