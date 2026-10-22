package com.starskyxiii.polyglottooltip.legacy.minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.item.ItemStack;

public final class SpecialItemNameResolvers {
    private static final List<SpecialItemNameResolver> RESOLVERS = new CopyOnWriteArrayList<>();

    private SpecialItemNameResolvers() {}

    public static void register(SpecialItemNameResolver resolver) {
        if (resolver != null) {
            RESOLVERS.add(resolver);
        }
    }

    static void clear() {
        RESOLVERS.clear();
    }

    public static List<SpecialItemNameResolver> candidates(ItemStack stack) {
        List<SpecialItemNameResolver> accepted = new ArrayList<>();
        for (SpecialItemNameResolver resolver : RESOLVERS) {
            if (accepts(resolver, stack)) {
                accepted.add(resolver);
            }
        }
        return accepted;
    }

    public static Map<String, String> resolve(ItemStack stack, ConsistencyCheck.Result result,
                                              Map<String, SecondaryDictionary> dictionaries) {
        for (SpecialItemNameResolver resolver : RESOLVERS) {
            if (!accepts(resolver, stack)) {
                continue;
            }
            try {
                Map<String, String> names = new LinkedHashMap<>();
                for (Map.Entry<String, SecondaryDictionary> entry : dictionaries.entrySet()) {
                    String name = resolver.resolve(stack, result, entry.getKey(), entry.getValue());
                    if (name != null) {
                        names.put(entry.getKey(), name);
                    }
                }
                if (!names.isEmpty()) {
                    return names;
                }
            } catch (RuntimeException error) {
                failed(resolver, error);
            }
        }
        return Collections.emptyMap();
    }

    private static boolean accepts(SpecialItemNameResolver resolver, ItemStack stack) {
        try {
            return resolver.accepts(stack);
        } catch (RuntimeException error) {
            failed(resolver, error);
            return false;
        }
    }

    private static void failed(SpecialItemNameResolver resolver, RuntimeException error) {
        FailureLog.debug("resolver|" + resolver.getClass().getName() + "|" + error.getClass().getName(),
                "Special name resolver " + resolver.getClass().getName() + " failed", error);
    }
}
