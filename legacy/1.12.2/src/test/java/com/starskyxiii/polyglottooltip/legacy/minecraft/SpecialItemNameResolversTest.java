package com.starskyxiii.polyglottooltip.legacy.minecraft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Test;

import net.minecraft.item.ItemStack;

public class SpecialItemNameResolversTest {
    private final List<String> calls = new ArrayList<>();

    @After
    public void clear() {
        SpecialItemNameResolvers.clear();
    }

    private SpecialItemNameResolver resolver(String id, boolean accepts, String name) {
        return new SpecialItemNameResolver() {
            @Override
            public boolean accepts(ItemStack stack) {
                return accepts;
            }

            @Override
            public String resolve(ItemStack stack, ConsistencyCheck.Result result, String languageCode,
                                  SecondaryDictionary dictionary) {
                calls.add(id + " " + languageCode + " " + result.key() + " " + result.expected() + " "
                        + result.primary());
                if (name == null && id.equals("broken")) {
                    throw new IllegalStateException("expected");
                }
                return name;
            }
        };
    }

    private static Map<String, SecondaryDictionary> dictionaries() {
        Map<String, SecondaryDictionary> dictionaries = new LinkedHashMap<>();
        dictionaries.put("en_us", SecondaryDictionary.load("en_us", (language, sink) -> {}));
        return dictionaries;
    }

    @Test
    public void fallsThroughToTheNextAcceptingResolverUntilOneNames() {
        SpecialItemNameResolvers.register(resolver("skipped", false, "never"));
        SpecialItemNameResolvers.register(resolver("broken", true, null));
        SpecialItemNameResolvers.register(resolver("empty", true, null));
        SpecialItemNameResolvers.register(resolver("named", true, "Cable Facade - Stone"));
        SpecialItemNameResolvers.register(resolver("late", true, "unused"));
        ConsistencyCheck.Result result = ConsistencyCheck.Result.inconsistent(
                "item.appliedenergistics2.facade.name", "线缆伪装板", "线缆伪装板 - 石头");
        Map<String, String> names = SpecialItemNameResolvers.resolve(null, result, dictionaries());
        assertEquals(Collections.singletonMap("en_us", "Cable Facade - Stone"), names);
        assertEquals(Arrays.asList(
                "broken en_us item.appliedenergistics2.facade.name 线缆伪装板 线缆伪装板 - 石头",
                "empty en_us item.appliedenergistics2.facade.name 线缆伪装板 线缆伪装板 - 石头",
                "named en_us item.appliedenergistics2.facade.name 线缆伪装板 线缆伪装板 - 石头"), calls);
        assertEquals(4, SpecialItemNameResolvers.candidates(null).size());
    }

    @Test
    public void noResolverGivesNoNames() {
        SpecialItemNameResolvers.register(resolver("empty", true, null));
        ConsistencyCheck.Result result = ConsistencyCheck.Result.inconsistent(null, null, "Name");
        assertTrue(SpecialItemNameResolvers.resolve(null, result, dictionaries()).isEmpty());
        assertNull(SpecialItemNameResolvers.resolution(null, result, dictionaries()).resolver());
    }

    @Test
    public void resolutionNamesTheResolverThatProducedTheNames() {
        SpecialItemNameResolver empty = resolver("empty", true, null);
        SpecialItemNameResolver named = resolver("named", true, "Cable Facade - Stone");
        SpecialItemNameResolvers.register(empty);
        SpecialItemNameResolvers.register(named);
        ConsistencyCheck.Result result = ConsistencyCheck.Result.inconsistent(null, null, "线缆伪装板 - 石头");
        assertSame(named, SpecialItemNameResolvers.resolution(null, result, dictionaries()).resolver());
    }
}
