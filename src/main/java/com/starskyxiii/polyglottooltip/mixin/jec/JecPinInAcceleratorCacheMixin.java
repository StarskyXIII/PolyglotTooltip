package com.starskyxiii.polyglottooltip.mixin.jec;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.towdium.pinin.PinIn", remap = false)
public abstract class JecPinInAcceleratorCacheMixin {
    @Shadow
    @Final
    private ThreadLocal<?> acc;

    @Shadow
    private int modification;

    @Unique
    private final ThreadLocal<Integer> polyglot$observedModification = new ThreadLocal<>();

    @Inject(
            method = {
                    "contains(Ljava/lang/String;Ljava/lang/String;)Z",
                    "begins(Ljava/lang/String;Ljava/lang/String;)Z",
                    "matches(Ljava/lang/String;Ljava/lang/String;)Z"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private void invalidateAcceleratorAfterConfigChange(
            String candidate,
            String query,
            CallbackInfoReturnable<Boolean> cir
    ) {
        polyglot$invalidateIfChanged(polyglot$observedModification, modification, acc);
    }

    @Unique
    private static void polyglot$invalidateIfChanged(
            ThreadLocal<Integer> observedModification,
            int currentModification,
            ThreadLocal<?> acceleratorCache
    ) {
        Integer observed = observedModification.get();
        if (observed == null || observed.intValue() != currentModification) {
            acceleratorCache.remove();
            observedModification.set(currentModification);
        }
    }
}
