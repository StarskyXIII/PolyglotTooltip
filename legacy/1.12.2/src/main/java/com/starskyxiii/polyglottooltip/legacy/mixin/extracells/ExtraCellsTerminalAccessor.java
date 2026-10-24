package com.starskyxiii.polyglottooltip.legacy.mixin.extracells;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "extracells.gui.GuiTerminal", remap = false)
public interface ExtraCellsTerminalAccessor {
    @Invoker(value = "updateFluids", remap = false)
    void polyglot$updateFluids();
}
