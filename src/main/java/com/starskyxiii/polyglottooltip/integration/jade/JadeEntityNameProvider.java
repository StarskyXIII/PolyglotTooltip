package com.starskyxiii.polyglottooltip.integration.jade;
//~ minecraft_identifier minecraft_116_packages

import com.starskyxiii.polyglottooltip.PolyglotTooltip;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
//? if <=1.18.2 {
/*import mcp.mobius.waila.api.EntityAccessor;
import mcp.mobius.waila.api.IEntityComponentProvider;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.config.IPluginConfig;
*///?} else {
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.TooltipPosition;
import snownee.jade.api.config.IPluginConfig;
//?}

import java.util.List;

public enum JadeEntityNameProvider implements IEntityComponentProvider {
    INSTANCE;

    //? if <=1.20.1 {
    /*private static final ResourceLocation UID = new ResourceLocation(PolyglotTooltip.MODID, "jade_entity_name");
    private static final ResourceLocation SECONDARY_NAME_TAG = new ResourceLocation(PolyglotTooltip.MODID, "jade_entity_secondary_name");
    *///?} else {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(PolyglotTooltip.MODID, "jade_entity_name");
    private static final ResourceLocation SECONDARY_NAME_TAG = ResourceLocation.fromNamespaceAndPath(PolyglotTooltip.MODID, "jade_entity_secondary_name");
    //?}

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!SecondaryTooltipUtil.shouldShowSecondaryLanguage()
                || accessor.getEntity() instanceof ItemEntity
                || !tooltip.get(SECONDARY_NAME_TAG).isEmpty()) {
            return;
        }

        Component primary = accessor.getEntity().getDisplayName();
        List<String> secondaryNames = SecondaryTooltipUtil.getSecondaryNames(primary);
        int insertAt = tooltip.isEmpty() ? 0 : 1;
        for (int i = secondaryNames.size() - 1; i >= 0; i--) {
            tooltip.add(insertAt, SecondaryTooltipUtil.createJadeSecondaryLine(secondaryNames.get(i)), SECONDARY_NAME_TAG);
        }
    }

    //? if >1.18.2 {
    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public int getDefaultPriority() {
        return TooltipPosition.HEAD - 50;
    }
    //?}
}
