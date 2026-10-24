package com.starskyxiii.polyglottooltip.legacy.mixin.ae2fc;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.starskyxiii.polyglottooltip.legacy.integration.ae2fc.Ae2FluidCraftingTooltips;

import net.minecraft.item.ItemStack;

@Pseudo
@Mixin(targets = "com.glodblock.github.client.GuiFluidPatternEncoder", remap = false)
public abstract class Ae2FluidCraftingEncoderMixin {
    @Inject(method = "func_191927_a(Lnet/minecraft/item/ItemStack;)Ljava/util/List;", at = @At("RETURN"),
            cancellable = true, require = 0)
    private void polyglot$appendNames(ItemStack stack, CallbackInfoReturnable<List<String>> info) {
        List<String> lines = info.getReturnValue();
        List<String> named = Ae2FluidCraftingTooltips.packet(lines, stack);
        if (named != lines) {
            info.setReturnValue(named);
        }
    }
}
