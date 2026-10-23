package com.starskyxiii.polyglottooltip.legacy.minecraft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

public class TooltipLinesTest {
    private static final TooltipLines LINES = new TooltipLines(stack -> SecondaryNames.of("鐵錠",
            Collections.singletonList("en_us"), Collections.singletonMap("en_us", "Iron Ingot"), false, "zh_tw"));

    private static List<String> tooltip() {
        List<String> lines = new ArrayList<>(Arrays.asList("鐵錠", "Minecraft"));
        LINES.onTooltip(new ItemTooltipEvent(null, null, lines, ITooltipFlag.TooltipFlags.NORMAL));
        return lines;
    }

    @Test
    public void insertsSecondaryNamesAfterTheNameLine() {
        List<String> lines = tooltip();
        assertEquals(3, lines.size());
        assertTrue(lines.get(1).endsWith("Iron Ingot"));
    }

    @Test
    public void addsNothingWhileSecondaryLinesAreSkipped() {
        List<String> lines = TooltipLines.withoutSecondaryLines(TooltipLinesTest::tooltip);
        assertEquals(Arrays.asList("鐵錠", "Minecraft"), lines);
        assertFalse(TooltipLines.skipped());
        assertEquals(3, tooltip().size());
    }

    @Test
    public void nestedSkipsRestoreTheOuterState() {
        TooltipLines.withoutSecondaryLines(() -> {
            TooltipLines.withoutSecondaryLines(() -> null);
            assertTrue(TooltipLines.skipped());
            return null;
        });
        assertFalse(TooltipLines.skipped());
    }

    @Test
    public void clearsTheSkipWhenTheActionThrows() {
        try {
            TooltipLines.withoutSecondaryLines(() -> {
                throw new IllegalStateException("tooltip failed");
            });
            fail();
        } catch (IllegalStateException expected) {
            assertEquals("tooltip failed", expected.getMessage());
        }
        assertFalse(TooltipLines.skipped());
        assertEquals(3, tooltip().size());
    }

    @Test
    public void skipsOnlyTheCallingThread() throws InterruptedException {
        List<List<String>> other = new ArrayList<>();
        TooltipLines.withoutSecondaryLines(() -> {
            Thread thread = new Thread(() -> other.add(tooltip()));
            thread.start();
            try {
                thread.join();
            } catch (InterruptedException error) {
                throw new IllegalStateException(error);
            }
            return null;
        });
        assertEquals(3, other.get(0).size());
    }
}
