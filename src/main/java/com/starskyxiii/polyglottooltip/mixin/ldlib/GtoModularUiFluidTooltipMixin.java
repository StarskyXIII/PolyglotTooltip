package com.starskyxiii.polyglottooltip.mixin.ldlib;

import com.starskyxiii.polyglottooltip.integration.FluidTooltipHelper;
import com.starskyxiii.polyglottooltip.integration.gto.GtoFluidTooltipContext;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Pseudo
@Mixin(targets = "com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer", remap = false)
public class GtoModularUiFluidTooltipMixin {
    @ModifyVariable(method = "setHoverTooltip(Ljava/util/List;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/inventory/tooltip/TooltipComponent;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1, remap = false)
    private List<Component> polyglot$appendGtoFluidName(List<Component> current, List<Component> tooltip,
            ItemStack stack, Font font, TooltipComponent component) {
        if (stack != ItemStack.EMPTY || font != null || component != null
                || !GtoFluidTooltipContext.isTankWidgetTooltip()) return current;
        return FluidTooltipHelper.withSecondaryFluidName(current);
    }
}
