package com.starskyxiii.polyglottooltip.mixin.mekanism;
//~ minecraft_116_packages

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import com.starskyxiii.polyglottooltip.integration.mekanism.MekanismTooltipHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(targets = "mekanism.common.util.StorageUtils", remap = false)
public class MekanismStorageUtilsMixin {

    //? if forge && <=1.20.1 {
    /*@ModifyArg(
            method = "addStoredChemical(Lnet/minecraft/world/item/ItemStack;Ljava/util/List;ZZLmekanism/api/text/ILangEntry;Ljava/util/function/Function;Lnet/minecraftforge/common/capabilities/Capability;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/function/Function;apply(Ljava/lang/Object;)Ljava/lang/Object;"),
            index = 0,
            remap = false
    )
    private static Object polyglot$captureChemical(Object chemical,
                                                    @Share("chemicalInTank") LocalRef<Object> chemicalRef) {
        chemicalRef.set(chemical);
        return chemical;
    }
    *///?}

    @Inject(
            //? if forge && <=1.20.1 {
            /*method = "addStoredChemical(Lnet/minecraft/world/item/ItemStack;Ljava/util/List;ZZLmekanism/api/text/ILangEntry;Ljava/util/function/Function;Lnet/minecraftforge/common/capabilities/Capability;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", ordinal = 0, shift = At.Shift.AFTER)
            *///?} else {
            method = "addStoredChemical",
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", shift = At.Shift.AFTER)
            //?}
    )
    private static void onAddStoredChemical(ItemStack stack,
                                            List<Component> tooltip,
                                            //? if forge && <=1.20.1 {
                                            /*boolean includeAttribute,
                                            boolean includeAmount,
                                            @org.spongepowered.asm.mixin.injection.Coerce Object langEntry,
                                            @org.spongepowered.asm.mixin.injection.Coerce Object attributeGetter,
                                            @org.spongepowered.asm.mixin.injection.Coerce Object capability,
                                            *///?}
                                            CallbackInfo ci,
                                            //? if forge && <=1.20.1 {
                                            /*@Share("chemicalInTank") LocalRef<Object> chemicalRef) {
                                            *///?} else {
                                            @Local(name = "chemicalInTank") Object chemicalInTank) {
                                            //?}
        //? if forge && <=1.20.1 {
        /*Object chemicalInTank = chemicalRef.get();
        *///?}
        MekanismTooltipHelper.getChemicalNameFromStack(chemicalInTank)
                .ifPresent(name -> SecondaryTooltipUtil.insertSecondaryName(tooltip, name));
    }
}
