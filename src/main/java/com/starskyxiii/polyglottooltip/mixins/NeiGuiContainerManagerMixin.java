package com.starskyxiii.polyglottooltip.mixins;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

@Pseudo
@Mixin(targets = "codechicken.nei.guihook.GuiContainerManager", remap = false)
public abstract class NeiGuiContainerManagerMixin {

    @Shadow(remap = false)
    public GuiContainer window;

    @ModifyVariable(
        method = "renderToolTips",
        at = @At(value = "INVOKE", target = "Lcodechicken/nei/NEIModContainer;isGTNHLibLoaded()Z", remap = false),
        ordinal = 0,
        require = 0,
        remap = false)
    private List<String> polyglot$prioritizeSecondaryNames(List<String> tooltip) {
        ItemStack stack = getStackMouseOver();
        if (stack == null || tooltip == null || tooltip.isEmpty()) {
            return tooltip;
        }

        SecondaryTooltipUtil.prioritizeSecondaryNamesAfterPrimary(tooltip, stack);
        return tooltip;
    }

    private ItemStack getStackMouseOver() {
        try {
            Class<?> managerClass = Class.forName("codechicken.nei.guihook.GuiContainerManager");
            Object value = managerClass.getMethod("getStackMouseOver", GuiContainer.class).invoke(null, window);
            return value instanceof ItemStack ? (ItemStack) value : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
