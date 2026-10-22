package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import org.junit.Test;
import static org.junit.Assert.*;

public final class LanguageStateProbeTest {
    @Test public void doesNotInvokeUnknownMapCallbacks() {
        HashMap<String, String> unknown = new HashMap<String, String>() {
            @Override public int size() { throw new AssertionError("size"); }
            @Override public String get(Object key) { throw new AssertionError("get"); }
            @Override public boolean containsKey(Object key) { throw new AssertionError("containsKey"); }
        };
        assertFalse(LanguageStateProbe.entries(unknown, false).get("observed").getAsBoolean());
    }

    @Test public void preservesAccessOrderAndDistinguishesMissingFromNull() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>(8, 0.75F, true);
        map.put("tile.stone.stone.name", "Stone");
        map.put("fluid.water", null);
        map.put("unrelated", "value");
        ArrayList<String> before = new ArrayList<>(map.keySet());
        JsonObject result = LanguageStateProbe.entries(map, false).getAsJsonObject("keys");
        assertEquals(before, new ArrayList<>(map.keySet()));
        assertEquals("Stone", result.getAsJsonObject("tile.stone.stone.name").get("value").getAsString());
        assertTrue(result.getAsJsonObject("fluid.water").get("present").getAsBoolean());
        assertFalse(result.getAsJsonObject("fluid.water").get("string").getAsBoolean());
        assertFalse(result.getAsJsonObject("item.paper.name").get("present").getAsBoolean());
    }
}
