package com.starskyxiii.polyglottooltip.mixin.jei;

import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional JEI integration that appends the secondary-language fluid name to
 * JEI fluid tooltips.
 *
 * <p>Injecting immediately after JEI adds the primary display name keeps our
 * secondary lines directly beneath it, before any advanced tooltip lines JEI
 * may append later.
 */
@Mixin(targets = "mezz.jei.forge.platform.FluidHelper", remap = false)
public class JeiFluidHelperMixin {

    @Inject(
            method = "getTooltip",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/api/gui/builder/ITooltipBuilder;add(Lnet/minecraft/network/chat/FormattedText;)V",
                    shift = At.Shift.AFTER,
                    ordinal = 0
            ),
            remap = false
    )
    private void onAddDisplayName(ITooltipBuilder tooltip,
                                  FluidStack ingredient,
                                  TooltipFlag tooltipFlag,
                                  CallbackInfo ci) {
        for (Component line : SecondaryTooltipUtil.getSecondaryNameLines(ingredient.getDisplayName())) {
            tooltip.add(line);
        }
    }
}
