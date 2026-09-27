package com.starskyxiii.polyglottooltip.mixin.figura;
//~ minecraft_116_packages

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.starskyxiii.polyglottooltip.compat.figura.FiguraEmojiContext;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ItemStack.class, priority = 900)
public abstract class ItemStackHoverNameContextMixin {
    @WrapMethod(method = "getHoverName")
    private Component polyglottooltip$wrapHoverName(Operation<Component> original) {
        FiguraEmojiContext.enterHoverName();
        try {
            return original.call();
        } finally {
            FiguraEmojiContext.exitHoverName();
        }
    }
}
