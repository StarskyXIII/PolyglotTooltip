package com.starskyxiii.polyglottooltip.integration.prettypipes;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class PrettyPipesSearchPolicyTest {
    private static final Function<String, Iterable<String>> UNUSED_NAMES = value -> {
        throw new AssertionError("Secondary names should not be resolved");
    };

    @Test
    void nativeAcceptanceShortCircuitsAllNameResolution() {
        AtomicInteger calls = new AtomicInteger();
        Predicate<String> removal = new PrettyPipesSearchPolicy(true).extend(value -> {
            calls.incrementAndGet();
            return false;
        }, "ingot", true, true, value -> {
            throw new AssertionError("Primary name should not be resolved");
        }, UNUSED_NAMES);
        assertFalse(removal.test("native"));
        assertEquals(1, calls.get());
    }

    @Test
    void evaluatesNativeOnceBeforeSecondaryFallback() {
        AtomicInteger calls = new AtomicInteger();
        Predicate<String> removal = new PrettyPipesSearchPolicy(false).extend(value -> {
            calls.incrementAndGet();
            return true;
        }, "IRON", true, true, Function.identity(), value -> Collections.singletonList("Iron Ingot"));
        assertFalse(removal.test("铁锭"));
        assertEquals(1, calls.get());
    }

    @Test
    void reservedQueriesAndEmptyInputKeepOriginalPredicate() {
        Predicate<String> original = value -> true;
        PrettyPipesSearchPolicy policy = new PrettyPipesSearchPolicy(true);
        for (String query : Arrays.asList(null, "", "@minecraft", "@", "#Iron", "#")) {
            assertSame(original, policy.extend(original, query, true, true, Function.identity(), UNUSED_NAMES));
        }
        assertSame(original, policy.extend(original, "@minecraft", false, true, Function.identity(), UNUSED_NAMES));
    }

    @Test
    void legacyHashIsLiteralNameSearch() {
        PrettyPipesSearchPolicy policy = new PrettyPipesSearchPolicy(false);
        Predicate<String> removal = policy.extend(value -> true, "#Iron", false, true,
                Function.identity(), value -> Collections.singletonList(value));
        assertFalse(removal.test("Custom #Iron Name"));
        assertTrue(removal.test("Iron Ingot"));
    }

    @Test
    void disabledSecondaryNeverResolvesNames() {
        Predicate<String> removal = new PrettyPipesSearchPolicy(false).extend(value -> true,
                "iron", true, false, Function.identity(), UNUSED_NAMES);
        assertTrue(removal.test("铁锭"));
    }

    @Test
    void primaryScriptMatchingIsIndependentOfSecondaryVisibility() {
        Predicate<String> enabled = new PrettyPipesSearchPolicy(true).extend(value -> true,
                "鐵錠", true, false, Function.identity(), UNUSED_NAMES);
        Predicate<String> disabled = new PrettyPipesSearchPolicy(false).extend(value -> true,
                "鐵錠", true, false, Function.identity(), UNUSED_NAMES);
        assertFalse(enabled.test("铁锭"));
        assertTrue(disabled.test("铁锭"));
    }

    @Test
    void secondaryScriptMatchingFollowsMode() {
        assertTrue(removes("鐵錠", "铁锭", false));
        assertFalse(removes("鐵錠", "铁锭", true));
        assertFalse(removes("铁锭", "鐵錠", true));
    }

    @Test
    void preservesWhitespaceAndWholeQuerySubstringSemantics() {
        assertFalse(removes(" ", "Iron Ingot", false));
        assertTrue(removes("  ", "Iron Ingot", false));
        assertTrue(removes(" Iron", "Iron Ingot", false));
        assertFalse(removes("Iron ", "Iron Ingot", false));
        assertTrue(removes("Ingot ", "Iron Ingot", false));
        assertTrue(removes("Ingot Iron", "Iron Ingot", false));
        assertTrue(removes("iron.*", "Iron Ingot", false));
        assertFalse(removes("iron.*", "Custom Iron.* Name", false));
        assertTrue(removes(" 鐵錠", "铁锭", true));
    }

    @Test
    void caseFoldingDoesNotDependOnDefaultLocale() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertFalse(removes("IRON", "Iron Ingot", false));
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test
    void boundedCandidateCacheStillResolvesEvictedNames() {
        PrettyPipesSearchPolicy policy = new PrettyPipesSearchPolicy(false);
        Predicate<String> removal = policy.extend(value -> true, "item", true, true,
                Function.identity(), Collections::singletonList);
        for (int index = 0; index < 2200; index++) {
            assertFalse(removal.test("item " + index));
        }
        assertEquals(2048, policy.cachedCandidateCount());
        assertFalse(removal.test("item 0"));
        assertEquals(2048, policy.cachedCandidateCount());
    }

    @Test
    void filteringPreservesIdentityOrderCountsAndBothEntryKinds() {
        Row stored = new Row("铁锭", "Iron Ingot", 32, false);
        Row miss = new Row("石头", "Stone", 64, false);
        Row craftable = new Row("铁锭", "Iron Ingot", 1, true);
        List<Row> rows = new ArrayList<>(Arrays.asList(stored, miss, craftable));
        rows.removeIf(new PrettyPipesSearchPolicy(false).extend(row -> true, "iron", true, true,
                row -> row.primary, row -> Collections.singletonList(row.secondary)));
        assertEquals(Arrays.asList(stored, craftable), rows);
        assertSame(stored, rows.get(0));
        assertSame(craftable, rows.get(1));
        assertEquals(32, stored.count);
        assertFalse(stored.craftable);
        assertEquals(1, craftable.count);
        assertTrue(craftable.craftable);
    }

    @Test
    void preservesMissingAndLiteralCustomNameCandidates() {
        assertTrue(removes("iron", "Audit Custom Name", true));
        assertFalse(removes("custom", "Audit Custom Name", false));
        Predicate<String> removal = new PrettyPipesSearchPolicy(true).extend(value -> true,
                "iron", true, true, value -> "", value -> Collections.emptyList());
        assertTrue(removal.test("missing"));
    }

    private static boolean removes(String query, String candidate, boolean script) {
        return new PrettyPipesSearchPolicy(script).extend(value -> true, query, true, true,
                Function.<String>identity(), value -> Collections.singletonList(candidate)).test("");
    }

    private static final class Row {
        final String primary;
        final String secondary;
        final int count;
        final boolean craftable;

        Row(String primary, String secondary, int count, boolean craftable) {
            this.primary = primary;
            this.secondary = secondary;
            this.count = count;
            this.craftable = craftable;
        }
    }
}
