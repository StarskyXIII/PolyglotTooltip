package com.starskyxiii.polyglottooltip.integration.waila;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaEntityAccessor;
import mcp.mobius.waila.api.IWailaEntityProvider;

import com.starskyxiii.polyglottooltip.config.Config;
import com.starskyxiii.polyglottooltip.name.EntityDisplayNameResolver;
import com.starskyxiii.polyglottooltip.tooltip.SecondaryTooltipUtil;

public class WailaEntityTooltipProvider implements IWailaEntityProvider {

    @Override
    public Entity getWailaOverride(IWailaEntityAccessor accessor, IWailaConfigHandler config) {
        return null;
    }

    @Override
    public List<String> getWailaHead(
        Entity entity,
        List<String> currenttip,
        IWailaEntityAccessor accessor,
        IWailaConfigHandler config) {
        if (!config.getConfig(WailaRegistration.CONFIG_SHOW_SECONDARY_LANGUAGE, true)) {
            return currenttip;
        }

        String primaryName = EnumChatFormatting.getTextWithoutFormattingCodes(entity.getCommandSenderName());
        SecondaryTooltipUtil.insertSecondaryNames(
            currenttip,
            currenttip == null || currenttip.isEmpty() ? 0 : 1,
            EntityDisplayNameResolver.resolveSecondaryDisplayNames(entity),
            primaryName,
            "",
            Config.wailaSecondaryNameColor);
        return currenttip;
    }

    @Override
    public List<String> getWailaBody(
        Entity entity,
        List<String> currenttip,
        IWailaEntityAccessor accessor,
        IWailaConfigHandler config) {
        return currenttip;
    }

    @Override
    public List<String> getWailaTail(
        Entity entity,
        List<String> currenttip,
        IWailaEntityAccessor accessor,
        IWailaConfigHandler config) {
        return currenttip;
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, Entity ent, NBTTagCompound tag, World world) {
        return tag;
    }
}
