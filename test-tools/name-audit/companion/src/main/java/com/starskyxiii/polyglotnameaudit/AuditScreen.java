package com.starskyxiii.polyglotnameaudit;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

public final class AuditScreen extends Screen {
    private static AuditScreen active;
    private static JsonArray drawing;
    private final String sampleId;
    private final ItemStack stack;
    private JsonArray lines = new JsonArray();
    private long frames;
    private long drawnAt;

    private AuditScreen(JsonObject sample) {
        super(Component.literal("Name audit"));
        sampleId = sample.get("id").getAsString();
        stack = AuditProbe.decode(sample.get("stack"));
    }

    public static JsonObject open(JsonObject sample) {
        active = new AuditScreen(sample);
        Minecraft.getInstance().setScreen(active);
        return state();
    }

    public static void capture(FormattedCharSequence text) {
        if (drawing == null) return;
        StringBuilder value = new StringBuilder();
        text.accept((index, style, codepoint) -> { value.appendCodePoint(codepoint); return true; });
        drawing.add(value.toString());
    }

    public static JsonObject state() {
        JsonObject out = new JsonObject();
        boolean visible = active != null && Minecraft.getInstance().screen == active;
        out.addProperty("visible", visible);
        if (visible) {
            out.addProperty("id", active.sampleId);
            out.addProperty("frames", active.frames);
            out.addProperty("ageMs", (System.nanoTime() - active.drawnAt) / 1_000_000);
            out.add("lines", active.lines.deepCopy());
        }
        return out;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF20242A);
        graphics.drawString(font, "Name audit / " + sampleId.substring(0, 12), 12, 12, 0xFFFFFFFF);
        graphics.renderItem(stack, 24, 52);
        JsonArray frame = new JsonArray();
        drawing = frame;
        try { graphics.renderTooltip(font, stack, 52, 52); }
        finally { drawing = null; }
        lines = frame;
        drawnAt = System.nanoTime();
        frames++;
    }
}
