package com.starskyxiii.polyglottooltip.integration.gto;

public final class GtoFluidTooltipContext {
    private static final String CONTAINER = "com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer";
    private static final String TANK_WIDGET = "com.gregtechceu.gtceu.api.gui.widget.TankWidget";
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private GtoFluidTooltipContext() {
    }

    public static boolean isTankWidgetTooltip() {
        return WALKER.walk(frames -> frames
                .filter(frame -> !frame.getClassName().equals(GtoFluidTooltipContext.class.getName())
                        && !frame.getClassName().equals(CONTAINER))
                .findFirst()
                .map(frame -> frame.getClassName().equals(TANK_WIDGET)
                        && frame.getMethodName().equals("drawInForeground"))
                .orElse(false));
    }
}
