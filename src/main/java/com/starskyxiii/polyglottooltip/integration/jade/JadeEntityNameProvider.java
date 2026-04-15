package com.starskyxiii.polyglottooltip.integration.jade;

import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.LegacyFormatStyleUtil;
import com.starskyxiii.polyglottooltip.PolyglotTooltip;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.TooltipPosition;
import snownee.jade.api.config.IPluginConfig;

import java.util.List;

public enum JadeEntityNameProvider implements IEntityComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = new ResourceLocation(PolyglotTooltip.MODID + ":jade_entity_name");
    private static final ResourceLocation SECONDARY_NAME_TAG = new ResourceLocation(PolyglotTooltip.MODID + ":jade_entity_secondary_name");

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!SecondaryTooltipUtil.shouldShowSecondaryLanguage()
                || accessor.getEntity() instanceof ItemEntity
                || !tooltip.get(SECONDARY_NAME_TAG).isEmpty()) {
            return;
        }

        Component primary = accessor.getEntity().getDisplayName();
        String primaryText = primary.getString();
        List<String> secondaryNames = LanguageCache.getInstance().resolveComponentsForAll(primary);
        int insertAt = tooltip.isEmpty() ? 0 : 1;
        for (int i = secondaryNames.size() - 1; i >= 0; i--) {
            String secondary = secondaryNames.get(i);
            if (secondary.equals(primaryText)) continue;
            tooltip.add(insertAt, Component.literal(secondary).withStyle(LegacyFormatStyleUtil.jadeSecondaryNameStyle()), SECONDARY_NAME_TAG);
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public int getDefaultPriority() {
        return TooltipPosition.HEAD - 50;
    }
}
