package com.starskyxiii.polyglottooltip;
//~ minecraft_116_packages minecraft_116_language_map

import com.starskyxiii.polyglottooltip.minecraft.ComponentLanguageResolver;
import com.starskyxiii.polyglottooltip.minecraft.ItemStackNameAdapter;
import com.starskyxiii.polyglottooltip.minecraft.ItemNameDecorator;
import com.starskyxiii.polyglottooltip.minecraft.ItemStackSnapshotKey;
import com.starskyxiii.polyglottooltip.minecraft.MinecraftLanguageAdapter;
import com.starskyxiii.polyglottooltip.minecraft.SpecialItemNameResolver;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class LanguageCache {
    @FunctionalInterface
    public interface SpecialNameResolver extends SpecialItemNameResolver {
        @Override
        Optional<String> resolve(ItemStack stack, Function<Component, Optional<String>> componentResolver);
    }

    private static final LanguageCache INSTANCE = new LanguageCache();
    private static final List<SpecialNameResolver> SPECIAL_NAME_RESOLVERS = new ArrayList<>();
    private static final List<ItemNameDecorator> NAME_DECORATORS = new ArrayList<>();

    private final ItemStackNameAdapter itemStackNames = new ItemStackNameAdapter();
    private final Map<ItemStackSnapshotKey, List<String>> displayNameCache = new ConcurrentHashMap<>();
    private final Map<ItemStackSnapshotKey, List<String>> searchNameCache = new ConcurrentHashMap<>();
    private volatile LanguageSnapshot languageSnapshot = new LanguageSnapshot(Collections.emptyList(), null);

    private LanguageCache() {
    }

    public static LanguageCache getInstance() {
        return INSTANCE;
    }

    public static void registerSpecialNameResolver(SpecialNameResolver resolver) {
        SPECIAL_NAME_RESOLVERS.add(resolver);
    }

    public static void registerNameDecorator(ItemNameDecorator decorator) {
        NAME_DECORATORS.add(decorator);
    }

    public void replaceLanguages(List<ClientLanguage> languages) {
        replaceLanguages(languages, null);
    }

    public void replaceLanguages(List<ClientLanguage> languages, ClientLanguage englishLanguage) {
        replaceLanguages(languages, englishLanguage, Collections.emptyList());
    }

    public void replaceLanguages(List<ClientLanguage> languages,
                                 ClientLanguage englishLanguage,
                                 List<Map<String, String>> legacyLanguageEntries) {
        List<LanguageEntry> entries = new ArrayList<>(languages.size());
        for (int index = 0; index < languages.size(); index++) {
            Map<String, String> legacyEntries = index < legacyLanguageEntries.size()
                    ? Collections.unmodifiableMap(new java.util.LinkedHashMap<>(legacyLanguageEntries.get(index)))
                    : Collections.emptyMap();
            entries.add(new LanguageEntry(languages.get(index), legacyEntries));
        }
        languageSnapshot = new LanguageSnapshot(Collections.unmodifiableList(entries), englishLanguage);
        displayNameCache.clear();
        searchNameCache.clear();
    }

    public List<String> resolveDisplayNamesForAll(ItemStack stack) {
        return displayNameCache.computeIfAbsent(
                ItemStackSnapshotKey.from(stack),
                key -> resolveDisplayNamesUncached(stack));
    }

    public List<String> resolveSearchNamesForAll(ItemStack stack) {
        return searchNameCache.computeIfAbsent(
                ItemStackSnapshotKey.from(stack),
                key -> resolveSearchNamesUncached(stack));
    }

    public String resolveCurrentDisplayName(ItemStack stack) {
        return itemStackNames.resolveCurrentDisplayName(stack, NAME_DECORATORS);
    }

    public List<String> resolveComponentsForAll(Component component) {
        LinkedHashSet<String> results = new LinkedHashSet<>();
        LanguageSnapshot snapshot = languageSnapshot;
        for (LanguageEntry entry : snapshot.entries()) {
            ComponentLanguageResolver.resolve(component, entry.language()).ifPresent(results::add);
        }
        return Collections.unmodifiableList(new ArrayList<>(results));
    }

    //? if <=1.16.5 {
    /*public List<String> resolveITextComponentsForAll(Component component) {
        return resolveComponentsForAll(component);
    }
    *///?}

    public Optional<String> resolveEnglishComponent(Component component) {
        ClientLanguage english = languageSnapshot.english();
        return english == null ? Optional.empty() : ComponentLanguageResolver.resolve(component, english);
    }

    public void reloadImmediate() {
        MinecraftLanguageAdapter.getInstance().reloadImmediate();
    }

    private List<String> resolveDisplayNamesUncached(ItemStack stack) {
        LinkedHashSet<String> results = new LinkedHashSet<>();
        LanguageSnapshot snapshot = languageSnapshot;
        for (LanguageEntry entry : snapshot.entries()) {
            Function<Component, Optional<String>> componentResolver = component -> {
                Optional<String> resolved = ComponentLanguageResolver.resolve(component, entry.language());
                return resolved.isPresent()
                        ? resolved
                        : ComponentLanguageResolver.resolve(component, entry.legacyEntries()::get);
            };
            itemStackNames.resolveDisplayName(stack, componentResolver, SPECIAL_NAME_RESOLVERS, NAME_DECORATORS)
                    .ifPresent(results::add);
        }
        return Collections.unmodifiableList(new ArrayList<>(results));
    }

    private List<String> resolveSearchNamesUncached(ItemStack stack) {
        LinkedHashSet<String> results = new LinkedHashSet<>(resolveDisplayNamesUncached(stack));
        for (Component enchantmentName : itemStackNames.getCraftingEnchantmentNames(stack)) {
            results.addAll(resolveComponentsForAll(enchantmentName));
        }
        return Collections.unmodifiableList(new ArrayList<>(results));
    }

    private static final class LanguageEntry {
        private final ClientLanguage language;
        private final Map<String, String> legacyEntries;

        private LanguageEntry(ClientLanguage language, Map<String, String> legacyEntries) {
            this.language = language;
            this.legacyEntries = legacyEntries;
        }

        private ClientLanguage language() { return language; }
        private Map<String, String> legacyEntries() { return legacyEntries; }
    }

    private static final class LanguageSnapshot {
        private final List<LanguageEntry> entries;
        private final ClientLanguage english;

        private LanguageSnapshot(List<LanguageEntry> entries, ClientLanguage english) {
            this.entries = entries;
            this.english = english;
        }

        private List<LanguageEntry> entries() { return entries; }
        private ClientLanguage english() { return english; }
    }
}
