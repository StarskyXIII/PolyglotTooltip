package com.starskyxiii.polyglottooltip.mixin.mekae2;
//~ minecraft_116_packages

import appeng.api.stacks.AEKey;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Injects into {@code me.ramidzkh.mekae2.ae2.AMChemicalStackRenderer#getTooltip}
 * to append the secondary-language chemical name in AE2 ME-Terminal tooltips when
 * Mekanism chemicals are stored in the network.
 *
 * <p>{@code MekanismKey} (the runtime type of {@code ingredient}) extends {@link AEKey},
 * which is already on the compile classpath. Casting to {@code AEKey} and calling
 * {@link AEKey#getDisplayName()} avoids any Mekanism API dependency while returning
 * exactly the same component as {@code getStack().getChemical().getTextComponent()}.
 */
@Pseudo
@Mixin(targets = "me.ramidzkh.mekae2.ae2.AMChemicalStackRenderer", remap = false)
public class AmChemicalStackRendererMixin {

    @Inject(
            //? if <=1.19.2 {
            /*method = "getTooltip(Lme/ramidzkh/mekae2/ae2/MekanismKey;)Ljava/util/List;",
            *///?} else {
            method = "getTooltip",
            //?}
            at = @At("RETURN"),
            remap = false
    )
    private void onGetTooltip(@Coerce Object ingredient,
                              CallbackInfoReturnable<List<Component>> cir) {
        if (!(ingredient instanceof AEKey key)) return;
        SecondaryTooltipUtil.insertSecondaryName(cir.getReturnValue(), key.getDisplayName());
    }
}
