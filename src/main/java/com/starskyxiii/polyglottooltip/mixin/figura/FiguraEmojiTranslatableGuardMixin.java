package com.starskyxiii.polyglottooltip.mixin.figura;

import com.starskyxiii.polyglottooltip.compat.figura.FiguraEmojiContext;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "org.figuramc.figura.font.Emojis", remap = false)
public abstract class FiguraEmojiTranslatableGuardMixin {
    private static final int MAX_COMPONENT_DEPTH = 32;

    @Inject(method = "applyEmojis", at = @At("HEAD"), cancellable = true, remap = false)
    private static void polyglottooltip$preserveTranslatableHoverName(Component text,
                                                                      CallbackInfoReturnable<MutableComponent> cir) {
        if (FiguraEmojiContext.isInHoverName()
                && text != null
                && polyglottooltip$containsTranslatable(text, 0)) {
            cir.setReturnValue(text.copy());
        }
    }

    private static boolean polyglottooltip$containsTranslatable(Component component, int depth) {
        if (component == null) {
            return false;
        }
        if (depth > MAX_COMPONENT_DEPTH) {
            return true;
        }

        ComponentContents contents = component.getContents();
        if (contents instanceof TranslatableContents tc) {
            for (Object arg : tc.getArgs()) {
                if (arg instanceof Component argComponent
                        && polyglottooltip$containsTranslatable(argComponent, depth + 1)) {
                    return true;
                }
            }
            return true;
        }

        for (Component sibling : component.getSiblings()) {
            if (polyglottooltip$containsTranslatable(sibling, depth + 1)) {
                return true;
            }
        }
        return false;
    }
}
