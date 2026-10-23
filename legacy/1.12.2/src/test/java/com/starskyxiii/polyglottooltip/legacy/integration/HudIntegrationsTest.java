package com.starskyxiii.polyglottooltip.legacy.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import org.junit.Test;

public class HudIntegrationsTest {
    private final List<String> warnings = new ArrayList<>();

    private Integrations start(Predicate<String> loaded, Predicate<String> enabled) {
        List<Integrations.Entry> gated = new ArrayList<>();
        for (Integrations.Entry entry : Integrations.known()) {
            if (entry.modid != null) {
                gated.add(entry);
            }
        }
        Integrations integrations = new Integrations(gated, loaded, enabled,
                (message, error) -> warnings.add(message));
        integrations.start();
        return integrations;
    }

    @Test
    public void registersTopAndHwylaUnderTheirModids() {
        List<String> modids = new ArrayList<>();
        for (Integrations.Entry entry : Integrations.known()) {
            modids.add(entry.id + "=" + entry.modid);
        }
        assertTrue(modids.contains("top=theoneprobe"));
        assertTrue(modids.contains("hwyla=waila"));
    }

    @Test
    public void startsBothWithoutTheirModClassesOnTheClasspath() {
        Integrations integrations = start(modid -> modid.equals("theoneprobe") || modid.equals("waila"), id -> true);
        assertEquals(Arrays.asList("top", "hwyla"), integrations.active());
        assertTrue(integrations.disabled().isEmpty());
        assertTrue(warnings.isEmpty());
    }

    @Test
    public void skipsIntegrationsWhoseModIsNotLoaded() {
        Integrations integrations = start(modid -> modid.equals("waila"), id -> true);
        assertEquals(Collections.singletonList("hwyla"), integrations.active());
        assertTrue(integrations.disabled().isEmpty());
    }

    @Test
    public void turningOneOffLeavesTheOtherRunning() {
        Integrations topOff = start(modid -> modid.equals("theoneprobe") || modid.equals("waila"), id -> !id.equals("top"));
        assertEquals(Collections.singletonList("hwyla"), topOff.active());
        assertEquals(Collections.singletonList("top (turned off in the config)"), topOff.disabled());

        Integrations hwylaOff = start(modid -> modid.equals("theoneprobe") || modid.equals("waila"), id -> !id.equals("hwyla"));
        assertEquals(Collections.singletonList("top"), hwylaOff.active());
        assertEquals(Collections.singletonList("hwyla (turned off in the config)"), hwylaOff.disabled());
    }
}
