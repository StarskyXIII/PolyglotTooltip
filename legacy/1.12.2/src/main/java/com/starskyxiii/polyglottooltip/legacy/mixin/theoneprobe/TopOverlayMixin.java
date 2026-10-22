package com.starskyxiii.polyglottooltip.legacy.mixin.theoneprobe;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.starskyxiii.polyglottooltip.legacy.integration.theoneprobe.TopHud;

import mcjty.theoneprobe.api.IElement;
import mcjty.theoneprobe.api.IOverlayStyle;
import mcjty.theoneprobe.api.ProbeMode;
import mcjty.theoneprobe.apiimpl.ProbeInfo;
import mcjty.theoneprobe.rendering.OverlayRenderer;
import net.minecraft.util.math.RayTraceResult;

@Pseudo
@Mixin(targets = "mcjty.theoneprobe.rendering.OverlayRenderer", remap = false)
public abstract class TopOverlayMixin {
    @Redirect(method = {"renderHUDBlock", "renderHUDEntity"},
            at = @At(value = "INVOKE", target = "Lmcjty/theoneprobe/rendering/OverlayRenderer;renderElements("
                    + "Lmcjty/theoneprobe/apiimpl/ProbeInfo;Lmcjty/theoneprobe/api/IOverlayStyle;DD"
                    + "Lmcjty/theoneprobe/api/IElement;)V"))
    private static void polyglot$render(ProbeInfo info, IOverlayStyle style, double width, double height,
                                        IElement extra, ProbeMode mode, RayTraceResult hit,
                                        double screenWidth, double screenHeight) {
        TopHud.render(info, hit, () -> OverlayRenderer.renderElements(info, style, width, height, extra));
    }
}
