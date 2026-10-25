package com.starskyxiii.polyglottooltip.legacy.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import org.junit.Test;

public class NameIntegrationsTest {
    private static final List<String> IDS = Arrays.asList(
            "names-potion", "names-forestry", "names-ic2", "names-microblock", "names-extracells",
            "names-tconstruct", "names-chiselsandbits", "names-guideapi", "names-reliquary");

    private static final List<String> REFLECTIVE = Arrays.asList("microblockcbe", "tconstruct", "chiselsandbits");

    private final List<String> warnings = new ArrayList<>();

    private Integrations start(Predicate<String> loaded, Predicate<String> enabled) {
        List<Integrations.Entry> names = new ArrayList<>();
        for (Integrations.Entry entry : Integrations.known()) {
            if (IDS.contains(entry.id)) {
                names.add(entry);
            }
        }
        Integrations integrations = new Integrations(names, loaded, enabled,
                (message, error) -> warnings.add(message));
        integrations.start();
        return integrations;
    }

    @Test
    public void registersEachResolverUnderItsModidBeforeTheGenericResolvers() {
        List<String> modids = new ArrayList<>();
        for (Integrations.Entry entry : Integrations.known()) {
            modids.add(entry.id + "=" + entry.modid);
        }
        List<String> expected = Arrays.asList("names-potion=null", "names-forestry=forestry", "names-ic2=ic2",
                "names-microblock=microblockcbe", "names-extracells=extracells", "names-tconstruct=tconstruct",
                "names-ae2=appliedenergistics2", "names-chiselsandbits=chiselsandbits", "names-guideapi=guideapi",
                "names-reliquary=xreliquary");
        for (String entry : expected) {
            assertTrue(entry, modids.contains(entry));
            assertTrue(entry, modids.indexOf(entry) < modids.indexOf("generic-bare-key=null"));
            assertTrue(entry, modids.indexOf(entry) < modids.indexOf("generic-format=null"));
            assertTrue(entry, modids.indexOf(entry) < modids.indexOf("generic-composite=null"));
        }
        assertTrue(modids.indexOf("generic-bare-key=null") < modids.indexOf("generic-format=null"));
    }

    @Test
    public void onlyThePotionResolverStartsWithoutItsMod() {
        Integrations integrations = start(modid -> false, id -> true);
        assertEquals(Collections.singletonList("names-potion"), integrations.active());
        assertTrue(integrations.disabled().isEmpty());
        assertTrue(warnings.isEmpty());
    }

    @Test
    public void startsTheModResolversWithoutTheirModClassesOnTheClasspath() {
        Integrations integrations = start(modid -> !REFLECTIVE.contains(modid), id -> true);
        assertEquals(Arrays.asList("names-potion", "names-forestry", "names-ic2", "names-extracells", "names-guideapi",
                "names-reliquary"), integrations.active());
        assertTrue(warnings.isEmpty());
    }

    @Test
    public void microblockWithoutForgeMultipartClassesIsSkippedWithOneWarning() {
        Integrations integrations = start(modid -> modid.equals("microblockcbe"), id -> !id.equals("names-potion"));
        assertTrue(integrations.active().isEmpty());
        assertEquals(1, warnings.size());
        assertEquals(Arrays.asList("names-potion (turned off in the config)"),
                integrations.disabled().subList(0, 1));
        assertTrue(integrations.disabled().get(1).startsWith("names-microblock (could not be constructed"));
    }

    @Test
    public void tinkersAndChiselsAndBitsWithoutTheirClassesAreSkippedWithOneWarningEach() {
        Integrations integrations = start(modid -> modid.equals("tconstruct") || modid.equals("chiselsandbits"),
                id -> !id.equals("names-potion"));
        assertTrue(integrations.active().isEmpty());
        assertEquals(2, warnings.size());
        assertTrue(integrations.disabled().get(1).startsWith("names-tconstruct (could not be constructed"));
        assertTrue(integrations.disabled().get(2).startsWith("names-chiselsandbits (could not be constructed"));
    }

    @Test
    public void turningOneOffLeavesTheOthersRunning() {
        Integrations integrations = start(modid -> !REFLECTIVE.contains(modid), id -> !id.equals("names-ic2"));
        assertEquals(Arrays.asList("names-potion", "names-forestry", "names-extracells", "names-guideapi",
                "names-reliquary"), integrations.active());
        assertEquals(Collections.singletonList("names-ic2 (turned off in the config)"), integrations.disabled());
    }
}
