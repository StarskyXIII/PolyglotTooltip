package com.starskyxiii.polyglottooltip.integration.jade;
//~ minecraft_identifier forge_neoforge_package minecraft_legacy_components

import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.PolyglotTooltip;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FluidState;
//? fabric {
/*import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
*///?} else {
import net.neoforged.neoforge.fluids.FluidStack;
//?}
//? if <=1.18.2 {
/*import mcp.mobius.waila.api.BlockAccessor;
import mcp.mobius.waila.api.IComponentProvider;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.config.IPluginConfig;
*///?} else {
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.TooltipPosition;
import snownee.jade.api.config.IPluginConfig;
//?}

import java.util.List;

//? if <=1.18.2 {
/*public enum JadeBlockNameProvider implements IComponentProvider {
*///?} else {
public enum JadeBlockNameProvider implements IBlockComponentProvider {
//?}
    INSTANCE;

    //? if <=1.20.1 {
    /*private static final ResourceLocation UID = new ResourceLocation(PolyglotTooltip.MODID, "jade_block_name");
    private static final ResourceLocation SECONDARY_NAME_TAG = new ResourceLocation(PolyglotTooltip.MODID, "jade_secondary_name");
    *///?} else {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(PolyglotTooltip.MODID, "jade_block_name");
    private static final ResourceLocation SECONDARY_NAME_TAG = ResourceLocation.fromNamespaceAndPath(PolyglotTooltip.MODID, "jade_secondary_name");
    //?}

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

    private ResolvedNames resolveNames(BlockAccessor accessor) {
        if (accessor.getBlock() instanceof LiquidBlock) {
            FluidState fluidState = accessor.getBlockState().getFluidState();
            if (!fluidState.isEmpty()) {
                //? fabric {
                /*FluidVariant fluidVariant = FluidVariant.of(fluidState.getType());
                Component primary = FluidVariantAttributes.getName(fluidVariant);
                *///?} else {
                FluidStack fluidStack = new FluidStack(fluidState.getType(), 1);
                //? if <=1.18.2 {
                /*Component primary = fluidState.getType().getAttributes().getDisplayName(fluidStack);
                *///?} else {
                Component primary = fluidState.getType().getFluidType().getDescription(fluidStack);
                //?}
                //?}
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
