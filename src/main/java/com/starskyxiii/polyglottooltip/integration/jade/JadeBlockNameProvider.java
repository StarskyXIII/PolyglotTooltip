package com.starskyxiii.polyglottooltip.integration.jade;

import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.LegacyFormatStyleUtil;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import com.starskyxiii.polyglottooltip.PolyglotTooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.fluids.FluidStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.TooltipPosition;
import snownee.jade.api.config.IPluginConfig;

import java.util.List;

public enum JadeBlockNameProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = new ResourceLocation(PolyglotTooltip.MODID + ":jade_block_name");
    private static final ResourceLocation SECONDARY_NAME_TAG = new ResourceLocation(PolyglotTooltip.MODID + ":jade_secondary_name");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!SecondaryTooltipUtil.shouldShowSecondaryLanguage() || !tooltip.get(SECONDARY_NAME_TAG).isEmpty()) {
            return;
        }

        List<NamePair> pairs = resolvePrimaryAndSecondaryNames(accessor);
        int insertAt = tooltip.isEmpty() ? 0 : 1;
        // Insert in reverse so config order ends up top-to-bottom (mirrors SecondaryTooltipUtil).
        // Note: Jade's ITooltip API differs from List<Component>, so SecondaryTooltipUtil cannot
        // be reused directly — but the same reverse-insertion strategy applies here.
        for (int i = pairs.size() - 1; i >= 0; i--) {
            String primary = pairs.get(i).primary().getString();
            String secondary = pairs.get(i).secondary();
            if (secondary.equals(primary)) continue;
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

    private List<NamePair> resolvePrimaryAndSecondaryNames(BlockAccessor accessor) {
        if (accessor.getBlock() instanceof LiquidBlock) {
            FluidState fluidState = accessor.getBlockState().getFluidState();
            if (!fluidState.isEmpty()) {
                FluidStack fluidStack = new FluidStack(fluidState.getType(), 1);
                Component primary = fluidState.getType().getFluidType().getDescription(fluidStack);
                return LanguageCache.getInstance().resolveComponentsForAll(primary).stream()
                        .map(secondary -> new NamePair(primary, secondary))
                        .toList();
            }
        }

        ItemStack pickedResult = accessor.getPickedResult();
        if (!pickedResult.isEmpty()) {
            Component primary = pickedResult.getHoverName();
            return LanguageCache.getInstance().resolveDisplayNamesForAll(pickedResult).stream()
                    .map(secondary -> new NamePair(primary, secondary))
                    .toList();
        }

        Component primary = accessor.getBlock().getName();
        return LanguageCache.getInstance().resolveComponentsForAll(primary).stream()
                .map(secondary -> new NamePair(primary, secondary))
                .toList();
    }

    private record NamePair(Component primary, String secondary) {
    }
}
