package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.BiConsumer;

/**
 * DEBUG: replays, after load, the work the product adds around the JEI/HEI ingredient filter, on every element of the
 * live filter. {@code allIngredients} is HEI's {@code IElementSearch.getAllIngredients} (JEI 4.16: the element list).
 * {@code tooltipStrings} is the element's search tooltip as JEI/HEI computes it inside the filter build
 * (the product's tooltip hooks only check the skip flag there); {@code tooltipUnskipped} is the same tooltip read without
 * the flag, i.e. what the build would cost without the skip. {@code tokens} runs the product's own token indexer over all
 * elements into a no-op sink with a cold name cache. {@code itemNames} resolves each item ingredient once with the
 * product cache cleared before it, grouped by the resolver that produced the name, and reports how often the
 * product's LRU cache would hit when the elements are indexed in filter order.
 */
final class IndexCostProbe {
    private static final String PRODUCT = "com.starskyxiii.polyglottooltip.legacy.";

    private IndexCostProbe() { }

    static JsonObject run(JsonObject params) throws Exception {
        long listed = System.nanoTime();
        List<?> elements = elements();
        JsonObject result = new JsonObject();
        result.addProperty("allIngredientsMs", (System.nanoTime() - listed) / 1e6);
        result.addProperty("elements", elements.size());
        Object cache = NativeAccess.get(NativeAccess.type(PRODUCT + "minecraft.ItemNames"), "CACHE");
        int capacity = (Integer) NativeAccess.get(NativeAccess.type(PRODUCT + "minecraft.NameCache"), "CAPACITY");

        long started = System.nanoTime();
        int failures = 0;
        for (Object element : elements) {
            try { NativeAccess.call(element, "getTooltipStrings"); } catch (Throwable error) { failures++; }
        }
        result.addProperty("tooltipStringsMs", (System.nanoTime() - started) / 1e6);
        result.addProperty("tooltipStringsFailures", failures);

        started = System.nanoTime();
        int stacks = 0;
        for (Object element : elements) {
            Object ingredient = NativeAccess.call(element, "getIngredient");
            if (!(ingredient instanceof ItemStack)) continue;
            stacks++;
            try { ((ItemStack) ingredient).func_82840_a(Minecraft.func_71410_x().field_71439_g, ITooltipFlag.TooltipFlags.NORMAL); }
            catch (Throwable ignored) { }
        }
        result.addProperty("tooltipUnskippedMs", (System.nanoTime() - started) / 1e6);
        result.addProperty("itemStacks", stacks);

        Class<?> search = NativeAccess.type(PRODUCT + "integration.jei.JeiSearch");
        Method index = search.getMethod("indexElements", List.class, int.class, long.class, BiConsumer.class);
        int[] tokens = new int[1];
        BiConsumer<String, Object> sink = (token, element) -> tokens[0]++;
        NativeAccess.call(cache, "clear");
        started = System.nanoTime();
        int reached = (Integer) index.invoke(null, elements, 0, Long.MAX_VALUE, sink);
        result.addProperty("tokensColdMs", (System.nanoTime() - started) / 1e6);
        result.addProperty("tokensReached", reached);
        result.addProperty("tokens", tokens[0]);

        Method of = NativeAccess.type(PRODUCT + "minecraft.ItemNames").getMethod("of", ItemStack.class);
        Method source = NativeAccess.type(PRODUCT + "minecraft.SecondaryNames").getMethod("source");
        Map<String, long[]> bySource = new TreeMap<>();
        Map<List<Object>, Boolean> lru = new LinkedHashMap<List<Object>, Boolean>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<List<Object>, Boolean> eldest) { return size() > capacity; }
        };
        int calls = 0, hits = 0;
        long total = 0;
        for (Object element : elements) {
            Object ingredient = NativeAccess.call(element, "getIngredient");
            if (!(ingredient instanceof ItemStack)) continue;
            ItemStack stack = (ItemStack) ingredient;
            List<Object> key = new ArrayList<>();
            key.add(new Identity(stack.func_77973_b()));
            key.add(stack.func_77960_j());
            key.add(stack.func_77942_o() ? stack.func_77978_p().hashCode() : 0);
            if (lru.put(key, Boolean.TRUE) != null) hits++;
            calls++;
            NativeAccess.call(cache, "clear");
            long before = System.nanoTime();
            Object names = of.invoke(null, stack);
            long spent = System.nanoTime() - before;
            total += spent;
            long[] bucket = bySource.computeIfAbsent(String.valueOf(source.invoke(names)), ignored -> new long[2]);
            bucket[0]++;
            bucket[1] += spent;
        }
        result.addProperty("itemNamesCalls", calls);
        result.addProperty("itemNamesColdMs", total / 1e6);
        result.addProperty("itemNamesColdMeanMicros", calls == 0 ? 0 : total / 1e3 / calls);
        result.addProperty("lruHits", hits);
        result.addProperty("lruHitRate", calls == 0 ? 0 : (double) hits / calls);
        JsonObject sources = new JsonObject();
        for (Map.Entry<String, long[]> entry : bySource.entrySet()) {
            JsonObject bucket = new JsonObject();
            bucket.addProperty("calls", entry.getValue()[0]);
            bucket.addProperty("ms", entry.getValue()[1] / 1e6);
            bucket.addProperty("meanMicros", entry.getValue()[1] / 1e3 / entry.getValue()[0]);
            sources.add(entry.getKey(), bucket);
        }
        result.add("itemNamesBySource", sources);
        return result;
    }

    private static List<?> elements() throws Exception {
        Object filter = mezz.jei.Internal.getIngredientFilter();
        try {
            return new ArrayList<>((Collection<?>) NativeAccess.call(NativeAccess.get(filter, "elementSearch"), "getAllIngredients"));
        } catch (NoSuchFieldException jei) {
            return new ArrayList<>((Collection<?>) NativeAccess.get(filter, "elementList"));
        }
    }

    private static final class Identity {
        private final Object value;
        Identity(Object value) { this.value = value; }
        @Override public boolean equals(Object other) { return other instanceof Identity && ((Identity) other).value == value; }
        @Override public int hashCode() { return System.identityHashCode(value); }
        @Override public String toString() { return Objects.toString(value); }
    }
}
