package com.starskyxiii.polyglottooltip.integration.ae2;

//? if >1.16.5 && (!fabric || <=1.20.1) {
import com.starskyxiii.polyglottooltip.minecraft.ComponentFactory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class Ae2TooltipSearchTextTest {
    @Test
    void translatedLinesKeepNativeDetailsAndBoundaries() {
        List<Component> lines = List.of(
                ComponentFactory.literal("Potion"),
                ComponentFactory.literal("Speed (03:00)"),
                ComponentFactory.literal("1,000 mB"),
                ComponentFactory.literal("Missing translation"),
                ComponentFactory.literal("Minecraft").withStyle(ChatFormatting.BLUE));
        String text = Ae2TooltipSearchPredicate.buildTooltipText(lines, "Minecraft", false,
                line -> switch (line.getString()) {
                    case "Potion" -> List.of("药水");
                    case "Speed (03:00)" -> List.of("速度 (03:00)");
                    default -> List.of(line.getString());
                });

        assertEquals("potion\n药水\nspeed(03:00)\n速度(03:00)\n1,000mb\nmissingtranslation", text);
        assertFalse(text.contains("药水速度"));
        assertFalse(text.contains("minecraft"));
    }

    @Test
    void identicalNativeLinesAreNotGloballyDeduplicated() {
        List<Component> lines = List.of(
                ComponentFactory.literal("Tank"), ComponentFactory.literal("Tank"),
                ComponentFactory.literal("Minecraft").withStyle(ChatFormatting.BLUE));
        assertEquals("tank\ntank\nminecraft",
                Ae2TooltipSearchPredicate.buildTooltipText(lines, "Minecraft", true,
                        line -> List.of(line.getString())));
    }
}
//?}
