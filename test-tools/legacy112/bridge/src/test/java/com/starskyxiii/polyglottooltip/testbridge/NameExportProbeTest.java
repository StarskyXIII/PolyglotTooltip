package com.starskyxiii.polyglottooltip.testbridge;

import org.junit.Test;
import static org.junit.Assert.*;

public final class NameExportProbeTest {
    @Test public void escapesCellSeparatorsAndBackslashes() {
        assertEquals("a\\tb\\nc\\rd\\\\e", NameExportProbe.escape("a\tb\nc\rd\\e"));
        assertEquals("", NameExportProbe.escape(null));
        assertEquals("石头 §7Stone", NameExportProbe.escape("石头 §7Stone"));
    }

    @Test public void headerNamesFifteenColumns() {
        assertEquals(15, NameExportProbe.HEADER.split("\t", -1).length);
        assertTrue(NameExportProbe.HEADER.startsWith("index\tid\t"));
    }
}
