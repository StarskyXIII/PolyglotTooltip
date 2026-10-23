package com.starskyxiii.polyglottooltip.legacy.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import org.junit.Test;

public class StorageIntegrationsTest {
    private final List<String> warnings = new ArrayList<>();

    private Integrations start(Predicate<String> loaded, Predicate<String> enabled) {
        List<Integrations.Entry> storage = new ArrayList<>();
        for (Integrations.Entry entry : Integrations.known()) {
            if (entry.id.equals("ae2") || entry.id.equals("rs")) {
                storage.add(entry);
            }
        }
        Integrations integrations = new Integrations(storage, loaded, enabled,
                (message, error) -> warnings.add(message));
        integrations.start();
        return integrations;
    }

    @Test
    public void registersAe2AndRsUnderTheirModids() {
        List<String> modids = new ArrayList<>();
        for (Integrations.Entry entry : Integrations.known()) {
            modids.add(entry.id + "=" + entry.modid);
        }
        assertTrue(modids.contains("ae2=appliedenergistics2"));
        assertTrue(modids.contains("rs=refinedstorage"));
        assertTrue(modids.indexOf("rs=refinedstorage") < modids.indexOf("generic-format=null"));
    }

    @Test
    public void startsBothWithoutTheirModClassesOnTheClasspath() {
        Integrations integrations = start(modid -> true, id -> true);
        assertEquals(Arrays.asList("ae2", "rs"), integrations.active());
        assertTrue(integrations.disabled().isEmpty());
        assertTrue(warnings.isEmpty());
    }

    @Test
    public void skipsIntegrationsWhoseModIsNotLoaded() {
        Integrations none = start(modid -> false, id -> true);
        assertTrue(none.active().isEmpty());
        assertTrue(none.disabled().isEmpty());
        Integrations rsOnly = start(modid -> modid.equals("refinedstorage"), id -> true);
        assertEquals(Collections.singletonList("rs"), rsOnly.active());
    }

    @Test
    public void turningOneOffLeavesTheOtherRunning() {
        Integrations ae2Off = start(modid -> true, id -> !id.equals("ae2"));
        assertEquals(Collections.singletonList("rs"), ae2Off.active());
        assertEquals(Collections.singletonList("ae2 (turned off in the config)"), ae2Off.disabled());

        Integrations rsOff = start(modid -> true, id -> !id.equals("rs"));
        assertEquals(Collections.singletonList("ae2"), rsOff.active());
        assertEquals(Collections.singletonList("rs (turned off in the config)"), rsOff.disabled());
        assertTrue(warnings.isEmpty());
    }
}
