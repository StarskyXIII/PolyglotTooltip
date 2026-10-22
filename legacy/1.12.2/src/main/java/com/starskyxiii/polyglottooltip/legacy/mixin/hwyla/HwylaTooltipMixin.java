package com.starskyxiii.polyglottooltip.legacy.mixin.hwyla;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.starskyxiii.polyglottooltip.legacy.integration.hwyla.HwylaHud;

import mcp.mobius.waila.api.event.WailaTooltipEvent;

@Pseudo
@Mixin(targets = "mcp.mobius.waila.overlay.Tooltip", remap = false)
public abstract class HwylaTooltipMixin {
    @Redirect(method = "<init>(Ljava/util/List;Z)V",
            at = @At(value = "INVOKE",
                    target = "Lmcp/mobius/waila/api/event/WailaTooltipEvent;getCurrentTip()Ljava/util/List;"))
    private List<String> polyglot$tip(WailaTooltipEvent event) {
        return HwylaHud.tip(event);
    }
}
