package com.starskyxiii.polyglottooltip.legacy.minecraft;

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

    public static SpecialItemNameResolver find(ItemStack stack) {
        for (SpecialItemNameResolver resolver : RESOLVERS) {
            try {
                if (resolver.accepts(stack)) {
                    return resolver;
                }
            } catch (RuntimeException error) {
                failed(resolver, error);
                return null;
            }
        }
        return null;
    }

    public static Map<String, String> resolve(ItemStack stack, Map<String, SecondaryDictionary> dictionaries) {
        SpecialItemNameResolver resolver = find(stack);
        if (resolver == null) {
            return Collections.emptyMap();
        }
        try {
            Map<String, String> names = new LinkedHashMap<>();
            for (Map.Entry<String, SecondaryDictionary> entry : dictionaries.entrySet()) {
                String name = resolver.resolve(stack, entry.getKey(), entry.getValue());
                if (name != null) {
                    names.put(entry.getKey(), name);
                }
            }
            return names;
        } catch (RuntimeException error) {
            failed(resolver, error);
            return Collections.emptyMap();
        }
    }

    private static void failed(SpecialItemNameResolver resolver, RuntimeException error) {
        FailureLog.debug("resolver|" + resolver.getClass().getName() + "|" + error.getClass().getName(),
                "Special name resolver " + resolver.getClass().getName() + " failed", error);
    }
}
