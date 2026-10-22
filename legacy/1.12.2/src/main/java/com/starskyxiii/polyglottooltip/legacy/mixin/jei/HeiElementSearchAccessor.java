package com.starskyxiii.polyglottooltip.legacy.mixin.jei;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "mezz.jei.search.ElementSearch", remap = false)
public interface HeiElementSearchAccessor {
    @Accessor("prefixedSearchables")
    Map<?, ?> polyglot$searchables();
}
