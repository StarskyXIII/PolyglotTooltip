package com.starskyxiii.polyglottooltip.mixin.jade;

import com.starskyxiii.polyglottooltip.PolyglotTooltip;
import com.starskyxiii.polyglottooltip.integration.jade.PolyglotTooltipJadePlugin;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import snownee.jade.Jade;
import snownee.jade.impl.WailaClientRegistration;
import snownee.jade.impl.WailaCommonRegistration;

@Mixin(value = Jade.class, priority = 900)
public abstract class JadeGtoCoreCompatMixin {

    @Unique
    private static boolean polyglottooltip$gtoCompatRegistered;

    /**
     * GTOCore replaces Jade's annotation scan with a hardcoded plugin whitelist.
     * Hook Jade.loadComplete() itself so we run even after their CommonProxy overwrite.
     */
    @Inject(method = "loadComplete", at = @At("HEAD"), remap = false)
    private static void polyglottooltip$registerBeforeJadeFreeze(CallbackInfo ci) {
        if (polyglottooltip$gtoCompatRegistered || !ModList.get().isLoaded("gtocore")) {
            return;
        }
        polyglottooltip$gtoCompatRegistered = true;
        PolyglotTooltipJadePlugin plugin = new PolyglotTooltipJadePlugin();
        plugin.register(WailaCommonRegistration.INSTANCE);
        plugin.registerClient(WailaClientRegistration.INSTANCE);
        PolyglotTooltip.LOGGER.info("[PolyglotTooltip] Injected Jade compat registration for GTOCore");
    }
}
