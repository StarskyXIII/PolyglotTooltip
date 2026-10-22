package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonParser;
import org.junit.Test;
import static org.junit.Assert.*;

public class NameAuditQueryTest {
    private NameAuditQuery query(String tail) {
        return new NameAuditQuery(new JsonParser().parse("{\"queryVersion\":2,\"namespaces\":[\"minecraft\"],\"items\":[\"minecraft:stone\"]" + tail + "}").getAsJsonObject());
    }

    @Test
    public void defaultLimitHasOneCanonicalIdentity() {
        NameAuditQuery defaultQuery = query("");
        assertEquals(50000, defaultQuery.sampleLimit);
        assertEquals(defaultQuery.sha256, query(",\"sampleLimit\":50000").sha256);
        assertNotEquals(defaultQuery.sha256, query(",\"sampleLimit\":150000").sha256);
        assertNotEquals(defaultQuery.sha256, query(",\"fixtureSet\":\"name-boundaries-v1\"").sha256);
    }

    @Test
    public void invalidLimitsCannotAliasAnAcceptedQuery() {
        for (String value : new String[]{"49999", "150001", "50000.1", "5e4", "\"50000\"", "null", "true", "-50000"}) {
            try { query(",\"sampleLimit\":" + value); fail(value); }
            catch (IllegalArgumentException expected) {}
        }
        try { query(",\"fixtureSet\":\"unknown\""); fail(); }
        catch (IllegalArgumentException expected) {}
    }

    @Test
    public void legacyQueriesRetainTheirOriginalDigest() throws Exception {
        String raw = "{\"namespaces\":[\"minecraft\"],\"items\":[\"minecraft:stone\"]}";
        NameAuditQuery legacy = new NameAuditQuery(new JsonParser().parse(raw).getAsJsonObject());
        StringBuilder digest = new StringBuilder();
        for (byte value : java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8))) digest.append(String.format("%02x", value & 255));
        assertEquals(digest.toString(), legacy.sha256);
        assertNotEquals(legacy.sha256, query("").sha256);
    }
}
