package com.starskyxiii.polyglottooltip.legacy.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Predicate;

import org.junit.Test;

public class ScreenIntegrationsTest {
    private static final Map<String, String> MODIDS = new LinkedHashMap<>();

    static {
        MODIDS.put("controlling", "controlling");
        MODIDS.put("projecte", "projecte");
        MODIDS.put("integratedterminals", "integratedterminals");
        MODIDS.put("modularui", "modularui");
        MODIDS.put("gtceu", "gregtech");
    }

    private final List<String> warnings = new ArrayList<>();

    private Integrations start(Predicate<String> loaded, Predicate<String> enabled) {
        List<Integrations.Entry> entries = new ArrayList<>();
        for (Integrations.Entry entry : Integrations.known()) {
            if (MODIDS.containsKey(entry.id)) {
                entries.add(entry);
            }
        }
        Integrations integrations = new Integrations(entries, loaded, enabled, (message, error) -> warnings.add(message));
        integrations.start();
        return integrations;
    }

    @Test
    public void registersEachIntegrationUnderItsModidBeforeTheGenericResolvers() {
        List<String> registered = new ArrayList<>();
        for (Integrations.Entry entry : Integrations.known()) {
            registered.add(entry.id + "=" + entry.modid);
        }
        for (Map.Entry<String, String> entry : MODIDS.entrySet()) {
            String row = entry.getKey() + "=" + entry.getValue();
            assertTrue(row, registered.contains(row));
            assertTrue(row, registered.indexOf(row) < registered.indexOf("generic-format=null"));
        }
    }

    @Test
    public void startsEveryIntegrationWithoutItsModClasses() {
        Integrations integrations = start(modid -> true, id -> true);
        assertEquals(new ArrayList<>(MODIDS.keySet()), integrations.active());
        assertTrue(integrations.disabled().isEmpty());
        integrations.invalidate();
        assertTrue(warnings.isEmpty());
    }

    @Test
    public void skipsIntegrationsWhoseModIsNotLoaded() {
        Integrations none = start(modid -> false, id -> true);
        assertTrue(none.active().isEmpty());
        assertTrue(none.disabled().isEmpty());
        Integrations expert = start(modid -> modid.equals("controlling"), id -> true);
        assertEquals(Collections.singletonList("controlling"), expert.active());
    }

    @Test
    public void turningOneIntegrationOffLeavesTheOthersRunning() {
        Integrations integrations = start(modid -> true, id -> !id.equals("projecte"));
        assertFalse(integrations.active().contains("projecte"));
        assertEquals(MODIDS.size() - 1, integrations.active().size());
        assertEquals(Collections.singletonList("projecte (turned off in the config)"), integrations.disabled());
        assertTrue(warnings.isEmpty());
    }

    @Test
    public void everyIntegrationHasAConfigLabel() {
        for (String language : Arrays.asList("en_us", "zh_tw")) {
            String lang;
            try (Scanner scanner = new Scanner(getClass().getResourceAsStream(
                    "/assets/polyglottooltip/lang/" + language + ".lang"), "UTF-8")) {
                lang = scanner.useDelimiter("\\A").next();
            }
            for (String id : MODIDS.keySet()) {
                assertTrue(language + " " + id, lang.contains("polyglottooltip.config.integrations." + id + "="));
                assertTrue(language + " " + id, lang.contains("polyglottooltip.config.integrations." + id + ".tooltip="));
            }
        }
    }
}
