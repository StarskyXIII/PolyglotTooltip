package com.starskyxiii.polyglottooltip.mixin.jei;

import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Optional JEI integration — appends the secondary-language fluid name to every
 * fluid tooltip in JEI's ingredient panel.
 *
 * <p>Targets {@code mezz.jei.neoforge.platform.FluidHelper#getTooltip} which
 * is the NeoForge-platform method JEI calls to populate a fluid ingredient's
 * tooltip list. The {@code targets} string form (rather than a {@code value}
 * class reference) means this Mixin compiles and loads without JEI on the
 * classpath; when JEI is absent the Mixin system simply skips the injection.
 */
@Mixin(targets = "mezz.jei.neoforge.platform.FluidHelper", remap = false)
public class JeiFluidHelperMixin {

    @Inject(method = "getTooltip", at = @At("RETURN"))
    private void onGetTooltip(List<Component> tooltip,
                               FluidStack ingredient,
                               TooltipFlag tooltipFlag,
                               CallbackInfo ci) {
        SecondaryTooltipUtil.insertSecondaryName(tooltip, ingredient.getHoverName());
    }
}
