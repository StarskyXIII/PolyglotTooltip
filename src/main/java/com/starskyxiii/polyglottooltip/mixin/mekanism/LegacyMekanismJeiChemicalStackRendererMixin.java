package com.starskyxiii.polyglottooltip.mixin.mekanism;

import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Pseudo
@Mixin(targets = "mekanism.client.jei.ChemicalStackRenderer", remap = false)
public class LegacyMekanismJeiChemicalStackRendererMixin {

    private static final Pattern MILLIBUCKET_AMOUNT = Pattern.compile("(^|\\s)([\\d,]+)\\s*mB$");

    @Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true, remap = false)
    private void polyglottooltip$moveSecondaryNameAfterAmount(@Coerce Object stack,
                                                              TooltipFlag tooltipFlag,
                                                              CallbackInfoReturnable<List<Component>> cir) {
        List<Component> tooltip = new ArrayList<>(cir.getReturnValue());
        if (tooltip.isEmpty()) {
            return;
        }
        SecondaryTooltipUtil.insertSecondaryNameAfterFirstDetailIf(
                tooltip,
                tooltip.get(0),
                line -> MILLIBUCKET_AMOUNT.matcher(line.getString()).find()
        );
        cir.setReturnValue(tooltip);
    }
}
