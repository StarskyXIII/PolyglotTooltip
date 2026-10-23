package com.starskyxiii.polyglottooltip.legacy.mixin.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import appeng.client.me.ItemRepo;

@Pseudo
@Mixin(targets = "appeng.client.gui.implementations.GuiMEMonitorable", remap = false)
public interface Ae2TerminalAccessor {
    @Accessor(value = "repo", remap = false)
    ItemRepo polyglot$repo();

    @Invoker(value = "setScrollBar", remap = false)
    void polyglot$setScrollBar();
}
