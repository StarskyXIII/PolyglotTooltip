package com.starskyxiii.polyglottooltip.integration.jade;

import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.PolyglotTooltip;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.FluidStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.TooltipPosition;
import snownee.jade.api.config.IPluginConfig;

import java.util.List;

public enum JadeBlockNameProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.parse(PolyglotTooltip.MODID + ":jade_block_name");
    private static final ResourceLocation SECONDARY_NAME_TAG = ResourceLocation.parse(PolyglotTooltip.MODID + ":jade_secondary_name");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!SecondaryTooltipUtil.shouldShowSecondaryLanguage() || !tooltip.get(SECONDARY_NAME_TAG).isEmpty()) {
            return;
        }

        ResolvedNames resolvedNames = resolveNames(accessor);
        List<String> secondaryNames = SecondaryTooltipUtil.getSecondaryNames(
                resolvedNames.names(),
                resolvedNames.primary().getString()
        );

        int insertAt = tooltip.isEmpty() ? 0 : 1;
        for (int i = secondaryNames.size() - 1; i >= 0; i--) {
            tooltip.add(insertAt, SecondaryTooltipUtil.createJadeSecondaryLine(secondaryNames.get(i)), SECONDARY_NAME_TAG);
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

    private ResolvedNames resolveNames(BlockAccessor accessor) {
        if (accessor.getBlock() instanceof LiquidBlock) {
            FluidState fluidState = accessor.getBlockState().getFluidState();
            if (!fluidState.isEmpty()) {
                FluidStack fluidStack = new FluidStack(fluidState.getType(), 1);
                Component primary = fluidState.getType().getFluidType().getDescription(fluidStack);
                return new ResolvedNames(primary, LanguageCache.getInstance().resolveComponentsForAll(primary));
            }
        }

        ItemStack pickedResult = accessor.getPickedResult();
        if (!pickedResult.isEmpty()) {
            return new ResolvedNames(
                    Component.literal(LanguageCache.getInstance().resolveCurrentDisplayName(pickedResult)),
                    LanguageCache.getInstance().resolveDisplayNamesForAll(pickedResult)
            );
        }

        Component primary = accessor.getBlock().getName();
        return new ResolvedNames(primary, LanguageCache.getInstance().resolveComponentsForAll(primary));
    }

    private record ResolvedNames(Component primary, List<String> names) {
    }
}
