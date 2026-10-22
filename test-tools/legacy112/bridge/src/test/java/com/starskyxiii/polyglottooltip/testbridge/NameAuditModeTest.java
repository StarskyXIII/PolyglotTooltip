package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonParser;
import org.junit.Test;
import static org.junit.Assert.*;

public class NameAuditModeTest {
    private String mode(String json) {
        return NameAuditMode.read(new JsonParser().parse(json).getAsJsonObject());
    }

    @Test
    public void explicitFlagsCannotAliasAnotherObservationMode() {
        assertEquals("NORMAL", mode("{}"));
        assertEquals("NORMAL", mode("{\"tooltipFlag\":\"NORMAL\"}"));
        assertEquals("ADVANCED", mode("{\"tooltipFlag\":\"ADVANCED\"}"));
        for (String value : new String[]{"null", "true", "1", "{}", "[]", "\"advanced\"", "\"\""}) {
            try { mode("{\"tooltipFlag\":" + value + "}"); fail(value); }
            catch (IllegalArgumentException expected) {}
        }
    }
}
