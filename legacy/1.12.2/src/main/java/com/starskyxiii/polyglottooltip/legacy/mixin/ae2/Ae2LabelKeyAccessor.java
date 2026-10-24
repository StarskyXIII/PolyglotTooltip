package com.starskyxiii.polyglottooltip.legacy.mixin.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = {"appeng.client.me.ClientDCInternalInv", "appeng.client.me.ClientDCInternalFluidInv"}, remap = false)
public interface Ae2LabelKeyAccessor {
    @Accessor(value = "unlocalizedName", remap = false)
    String polyglot$unlocalizedName();

    @Invoker(value = "getName", remap = false)
    String polyglot$name();
}
