package com.starskyxiii.polyglottooltip.mixin.figura;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.starskyxiii.polyglottooltip.compat.figura.FiguraEmojiContext;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemStack.class, priority = 900)
public abstract class ItemStackHoverNameContextMixin {
    @Inject(method = "getHoverName", at = @At("HEAD"))
    private void polyglottooltip$enterHoverNameContext(CallbackInfoReturnable<Component> cir) {
        FiguraEmojiContext.enterHoverName();
    }

    @ModifyReturnValue(method = "getHoverName", at = @At("RETURN"))
    private Component polyglottooltip$exitHoverNameContext(Component original) {
        FiguraEmojiContext.exitHoverName();
        return original;
    }
}
