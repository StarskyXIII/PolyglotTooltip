package com.starskyxiii.polyglottooltip.testbridge;

import java.util.ArrayList;
import java.util.List;

public final class TooltipProbe {
    static boolean capturing;
    static List<String> captured;
    static com.google.gson.JsonObject refined;
    static final List<String> events = new ArrayList<>();

    private TooltipProbe() { }

    public static void observe(List<String> lines) {
        if (capturing) captured = new ArrayList<>(lines);
    }

    public static void event(net.minecraftforge.client.event.RenderTooltipEvent.Pre event) {
        if (!capturing) return;
        if (!event.isCanceled()) events.add(event.getClass().getName());
        observe(event.getLines());
    }
}
