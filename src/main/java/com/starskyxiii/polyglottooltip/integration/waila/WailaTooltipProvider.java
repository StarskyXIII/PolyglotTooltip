package com.starskyxiii.polyglottooltip.integration.waila;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;

import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;

public class WailaTooltipProvider implements IWailaDataProvider {

    @Override
    public ItemStack getWailaStack(IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return null;
    }

    @Override
    public List<String> getWailaHead(
        ItemStack itemStack,
        List<String> currenttip,
        IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        if (!config.getConfig(WailaRegistration.CONFIG_SHOW_SECONDARY_LANGUAGE, true)) {
            return currenttip;
        }

        SecondaryTooltipUtil.insertSecondaryNames(currenttip, itemStack);
        return currenttip;
    }

    @Override
    public List<String> getWailaBody(
        ItemStack itemStack,
        List<String> currenttip,
        IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return currenttip;
    }

    @Override
    public List<String> getWailaTail(
        ItemStack itemStack,
        List<String> currenttip,
        IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return currenttip;
    }

    @Override
    public NBTTagCompound getNBTData(
        EntityPlayerMP player,
        TileEntity te,
        NBTTagCompound tag,
        World world,
        int x,
        int y,
        int z) {
        return tag;
    }
}
