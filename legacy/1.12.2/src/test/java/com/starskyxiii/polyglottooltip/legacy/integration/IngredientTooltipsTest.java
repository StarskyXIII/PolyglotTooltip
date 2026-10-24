package com.starskyxiii.polyglottooltip.legacy.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.junit.Test;

import com.starskyxiii.polyglottooltip.legacy.integration.jei.JeiIntegration;
import com.starskyxiii.polyglottooltip.legacy.integration.mekanism.MekanismIntegration;
import com.starskyxiii.polyglottooltip.legacy.minecraft.FluidSecondaryNames;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryDictionary;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryNames;
import com.starskyxiii.polyglottooltip.legacy.minecraft.TooltipLines;

public class IngredientTooltipsTest {
    private final List<String> warnings = new ArrayList<>();

    private static Map<String, SecondaryDictionary> dictionaries() {
        Map<String, SecondaryDictionary> dictionaries = new LinkedHashMap<>();
        dictionaries.put("en_us", SecondaryDictionary.load("en_us", (code, sink) -> {
            sink.accept("gas.hydrogen", "Hydrogen");
            sink.accept("fluid.water", "Water");
        }));
        return dictionaries;
    }

    private static SecondaryNames names(String key, String actual, String expected) {
        return SecondaryNames.of(actual, Collections.singletonList("en_us"),
                FluidSecondaryNames.resolve(key, actual, expected, dictionaries()), false, "zh_tw");
    }

    private static List<String> tooltip(String name) {
        return new ArrayList<>(Arrays.asList(name, "1000 mB"));
    }

    @Test
    public void addsGasAndFluidNamesAfterTheNameLine() {
        IntegrationGuard guard = new IntegrationGuard("mekanism", (message, error) -> {});
        List<String> gas = tooltip("氫氣");
        IngredientTooltips.append(guard, gas, () -> names("gas.hydrogen", "氫氣", "氫氣"));
        assertEquals(Arrays.asList("氫氣", "§7Hydrogen", "1000 mB"), gas);

        List<String> fluid = tooltip("水");
        IngredientTooltips.append(guard, fluid, () -> names("fluid.water", "水", "水"));
        assertEquals(Arrays.asList("水", "§7Water", "1000 mB"), fluid);
    }

    @Test
    public void dropsNamesThatFailTheConsistencyCheckOrEqualTheShownName() {
        IntegrationGuard guard = new IntegrationGuard("mekanism", (message, error) -> {});
        List<String> renamed = tooltip("液態氫");
        IngredientTooltips.append(guard, renamed, () -> names("gas.hydrogen", "液態氫", "氫氣"));
        assertEquals(tooltip("液態氫"), renamed);

        List<String> english = tooltip("Hydrogen");
        IngredientTooltips.append(guard, english, () -> names("gas.hydrogen", "Hydrogen", "Hydrogen"));
        assertEquals(tooltip("Hydrogen"), english);
    }

    @Test
    public void addsNothingWithoutAnActiveGuardOrWhileSkipped() {
        List<String> missing = tooltip("氫氣");
        IngredientTooltips.append(null, missing, () -> names("gas.hydrogen", "氫氣", "氫氣"));
        assertEquals(tooltip("氫氣"), missing);

        IntegrationGuard tripped = new IntegrationGuard("mekanism", (message, error) -> warnings.add(message));
        for (int i = 0; i < IntegrationGuard.FAILURE_LIMIT; i++) {
            IngredientTooltips.append(tripped, tooltip("氫氣"), () -> {
                throw new IllegalStateException("boom");
            });
        }
        assertEquals(1, warnings.size());
        List<String> disabled = tooltip("氫氣");
        IngredientTooltips.append(tripped, disabled, () -> names("gas.hydrogen", "氫氣", "氫氣"));
        assertEquals(tooltip("氫氣"), disabled);

        IntegrationGuard guard = new IntegrationGuard("jei", (message, error) -> {});
        List<String> indexed = TooltipLines.withoutSecondaryLines(() -> {
            List<String> lines = tooltip("水");
            IngredientTooltips.append(guard, lines, () -> names("fluid.water", "水", "水"));
            return lines;
        });
        assertEquals(tooltip("水"), indexed);
    }

    @Test
    public void keepsTheTooltipWhenNamesThrow() {
        IntegrationGuard guard = new IntegrationGuard("jei", (message, error) -> {});
        List<String> lines = tooltip("水");
        IngredientTooltips.append(guard, lines, () -> {
            throw new IllegalStateException("boom");
        });
        assertEquals(tooltip("水"), lines);
        assertEquals(1, guard.failures());
    }

    private Integrations start(Predicate<String> loaded, Predicate<String> enabled) {
        List<Integrations.Entry> entries = new ArrayList<>();
        entries.add(new Integrations.Entry(JeiIntegration.ID, JeiIntegration.MODID, guard -> new Integration() {
            @Override
            public String id() {
                return JeiIntegration.ID;
            }

            @Override
            public String modid() {
                return JeiIntegration.MODID;
            }

            @Override
            public void start() {}
        }));
        for (Integrations.Entry entry : Integrations.known()) {
            if (MekanismIntegration.ID.equals(entry.id)) {
                entries.add(entry);
            }
        }
        Integrations integrations = new Integrations(entries, loaded, enabled, (message, error) -> warnings.add(message));
        integrations.start();
        return integrations;
    }

    @Test
    public void registersMekanismUnderItsModid() {
        Integrations integrations = start(modid -> modid.equals("mekanism"), id -> true);
        assertEquals(Collections.singletonList("mekanism"), integrations.active());
        assertTrue(warnings.isEmpty());
    }

    @Test
    public void skipsMekanismWhenItIsNotLoaded() {
        Integrations integrations = start(modid -> modid.equals("jei"), id -> true);
        assertEquals(Collections.singletonList("jei"), integrations.active());
        assertTrue(integrations.disabled().isEmpty());
    }

    @Test
    public void turningMekanismOffLeavesJeiRunning() {
        Integrations integrations = start(modid -> true, id -> !id.equals("mekanism"));
        assertEquals(Collections.singletonList("jei"), integrations.active());
        assertEquals(Collections.singletonList("mekanism (turned off in the config)"), integrations.disabled());
    }
}
