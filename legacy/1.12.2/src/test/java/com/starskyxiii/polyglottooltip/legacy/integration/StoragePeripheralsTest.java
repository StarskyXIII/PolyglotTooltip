package com.starskyxiii.polyglottooltip.legacy.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.junit.Test;

import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryNames;
import com.starskyxiii.polyglottooltip.legacy.minecraft.TooltipLines;

public class StoragePeripheralsTest {
    private static final Map<String, String> MODIDS = new LinkedHashMap<>();

    static {
        MODIDS.put("extracells", "extracells");
        MODIDS.put("betterp2p", "betterp2p");
        MODIDS.put("lazyae2", "threng");
        MODIDS.put("thaumicenergistics", "thaumicenergistics");
        MODIDS.put("mekanismenergistics", "mekeng");
        MODIDS.put("notenoughenergistics", "neenergistics");
        MODIDS.put("ae2fc", "ae2fc");
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

    private static SecondaryNames names(String primary, String secondary) {
        return SecondaryNames.of(primary, Collections.singletonList("en_us"), Collections.singletonMap("en_us", secondary),
                false, "zh_tw");
    }

    private static IntegrationGuard guard() {
        return new IntegrationGuard("extracells", (message, error) -> {});
    }

    @Test
    public void registersEachPeripheralUnderItsModidBeforeTheGenericResolvers() {
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
    public void startsEveryPeripheralWithoutItsModClasses() {
        Integrations integrations = start(modid -> true, id -> true);
        assertEquals(new ArrayList<>(MODIDS.keySet()), integrations.active());
        assertTrue(integrations.disabled().isEmpty());
        assertTrue(warnings.isEmpty());
    }

    @Test
    public void skipsPeripheralsWhoseModIsNotLoaded() {
        Integrations none = start(modid -> false, id -> true);
        assertTrue(none.active().isEmpty());
        assertTrue(none.disabled().isEmpty());
        Integrations novaOnly = start(modid -> Arrays.asList("mekeng", "neenergistics", "threng").contains(modid), id -> true);
        assertEquals(Arrays.asList("lazyae2", "mekanismenergistics", "notenoughenergistics"), novaOnly.active());
    }

    @Test
    public void turningOnePeripheralOffLeavesTheOthersRunning() {
        Integrations integrations = start(modid -> true, id -> !id.equals("extracells"));
        assertFalse(integrations.active().contains("extracells"));
        assertEquals(MODIDS.size() - 1, integrations.active().size());
        assertEquals(Collections.singletonList("extracells (turned off in the config)"), integrations.disabled());
        assertTrue(warnings.isEmpty());
    }

    @Test
    public void insertsNamesAfterTheLineThatShowsTheName() {
        List<String> tank = new ArrayList<>(Arrays.asList("§7North", "水", "1250 mB"));
        IngredientTooltips.appendAfter(guard(), tank, "水", () -> names("水", "Water"));
        assertEquals(Arrays.asList("§7North", "水", "§7Water", "1250 mB"), tank);

        List<String> missing = new ArrayList<>(Arrays.asList("Empty"));
        IngredientTooltips.appendAfter(guard(), missing, "水", () -> names("水", "Water"));
        assertEquals(Collections.singletonList("Empty"), missing);
    }

    @Test
    public void copiesFixedSizeListsAndKeepsTheOriginal() {
        List<String> fixed = Arrays.asList("§f水", "12,345 mB");
        List<String> named = IngredientTooltips.withNames(guard(), fixed, "水", () -> names("水", "Water"));
        assertEquals(Arrays.asList("§f水", "§7Water", "12,345 mB"), named);
        assertEquals(Arrays.asList("§f水", "12,345 mB"), fixed);

        assertSame(fixed, IngredientTooltips.withNames(guard(), fixed, "水", () -> SecondaryNames.EMPTY));
        assertSame(fixed, IngredientTooltips.withNames(null, fixed, "水", () -> names("水", "Water")));
        assertSame(fixed, TooltipLines.withoutSecondaryLines(
                () -> IngredientTooltips.withNames(guard(), fixed, "水", () -> names("水", "Water"))));
    }

    @Test
    public void slotMessagesGetOneLinePerName() {
        assertEquals("氫氣\n§7Hydrogen", IngredientTooltips.message(guard(), "氫氣", () -> names("氫氣", "Hydrogen")));
        assertEquals("Hydrogen", IngredientTooltips.message(guard(), "Hydrogen", () -> names("Hydrogen", "Hydrogen")));
        assertEquals("氫氣", IngredientTooltips.message(null, "氫氣", () -> names("氫氣", "Hydrogen")));
    }

    @Test
    public void failingNamesLeaveTheTooltipAlone() {
        IntegrationGuard guard = guard();
        List<String> fixed = Arrays.asList("水", "1 mB");
        assertSame(fixed, IngredientTooltips.withNames(guard, fixed, "水", () -> {
            throw new IllegalStateException("boom");
        }));
        assertEquals("水", IngredientTooltips.message(guard, "水", () -> {
            throw new IllegalStateException("boom");
        }));
        assertEquals(2, guard.failures());
    }
}
