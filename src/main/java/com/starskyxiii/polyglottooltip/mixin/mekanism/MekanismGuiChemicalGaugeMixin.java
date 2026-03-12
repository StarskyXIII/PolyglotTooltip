package com.starskyxiii.polyglottooltip.mixin.mekanism;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.starskyxiii.polyglottooltip.integration.mekanism.MekanismTooltipHelper;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.List;

@Pseudo
@Mixin(targets = "mekanism.client.gui.element.gauge.GuiChemicalGauge", remap = false)
public class MekanismGuiChemicalGaugeMixin {

    @WrapOperation(
            method = "getTooltipText",
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
        MekanismTooltipHelper.getChemicalName(chemical)
                .ifPresent(name -> MekanismTooltipHelper.addSecondaryName(tooltip, name));
        original.call(tooltip, chemical, advanced);
    }
}
