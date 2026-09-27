package com.starskyxiii.polyglottooltip.mixin.figura;
//~ minecraft_116_packages

//? if <=1.19.2 {
/*import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
*///?} else {
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
//?}
import com.starskyxiii.polyglottooltip.compat.figura.FiguraEmojiContext;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
//? if >1.19.2 {
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?}

@Mixin(value = ItemStack.class, priority = 900)
public abstract class ItemStackHoverNameContextMixin {
    //? if <=1.19.2 {
    /*@WrapMethod(method = "getHoverName")
    private Component polyglottooltip$wrapHoverName(Operation<Component> original) {
        FiguraEmojiContext.enterHoverName();
        try {
            return original.call();
        } finally {
            FiguraEmojiContext.exitHoverName();
        }
    }
    *///?} else {
    @Inject(method = "getHoverName", at = @At("HEAD"))
    private void polyglottooltip$enterHoverNameContext(CallbackInfoReturnable<Component> cir) {
        FiguraEmojiContext.enterHoverName();
    }

    @ModifyReturnValue(method = "getHoverName", at = @At("RETURN"))
    private Component polyglottooltip$exitHoverNameContext(Component original) {
        FiguraEmojiContext.exitHoverName();
        return original;
    }
    //?}
}
