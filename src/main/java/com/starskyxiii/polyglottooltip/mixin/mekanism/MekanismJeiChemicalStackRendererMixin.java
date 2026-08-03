package com.starskyxiii.polyglottooltip.mixin.mekanism;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import com.starskyxiii.polyglottooltip.integration.mekanism.MekanismTooltipHelper;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Keeps JEMI's Mekanism chemical amount line in the first two tooltip entries.
 *
 * <p>In Mekanism for Minecraft 1.20.1, JEI chemical tooltips are built in
 * {@code ChemicalStackRenderer.getTooltip()}, not in {@code ChemicalStack} itself,
 * so this is the correct injection point for that JEI integration. JEMI scans
 * only the first two lines for an mB amount, so secondary names must follow it.
 */
@Pseudo
@Mixin(targets = "mekanism.client.jei.ChemicalStackRenderer", remap = false)
public class MekanismJeiChemicalStackRendererMixin {

    private static final Pattern JEMI_MILLIBUCKET_AMOUNT = Pattern.compile("(^|\\s)([\\d,]+)\\s*mB$");

    @WrapOperation(
            method = "getTooltip",
            at = @At(
                    value = "INVOKE",
                    target = "Lmekanism/common/util/ChemicalUtil;addChemicalDataToTooltip(Ljava/util/List;Lmekanism/api/chemical/Chemical;Z)V"
            ),
            remap = false
    )
    private void wrapAddChemicalData(List<Component> tooltip,
                                     @Coerce Object chemical,
                                     boolean advanced,
                                     Operation<Void> original) {
        original.call(tooltip, chemical, advanced);
        MekanismTooltipHelper.getChemicalName(chemical)
                .ifPresent(name -> SecondaryTooltipUtil.insertSecondaryNameAfterFirstDetailIf(
                        tooltip,
                        name,
                        line -> JEMI_MILLIBUCKET_AMOUNT.matcher(line.getString()).find()
                ));
    }
}
