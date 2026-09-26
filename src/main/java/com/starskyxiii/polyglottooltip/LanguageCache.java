package com.starskyxiii.polyglottooltip;

import com.starskyxiii.polyglottooltip.minecraft.ComponentLanguageResolver;
import com.starskyxiii.polyglottooltip.minecraft.ItemStackNameAdapter;
import com.starskyxiii.polyglottooltip.minecraft.ItemStackSnapshotKey;
import com.starskyxiii.polyglottooltip.minecraft.MinecraftLanguageAdapter;
import com.starskyxiii.polyglottooltip.minecraft.SpecialItemNameResolver;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
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

    private final ItemStackNameAdapter itemStackNames = new ItemStackNameAdapter();
    private final Map<ItemStackSnapshotKey, List<String>> displayNameCache = new ConcurrentHashMap<>();
    private final Map<ItemStackSnapshotKey, List<String>> searchNameCache = new ConcurrentHashMap<>();
    private volatile LanguageSnapshot languageSnapshot = new LanguageSnapshot(List.of(), null);

    private LanguageCache() {
    }

    public static LanguageCache getInstance() {
        return INSTANCE;
    }

    public static void registerSpecialNameResolver(SpecialNameResolver resolver) {
        SPECIAL_NAME_RESOLVERS.add(resolver);
    }

    public void replaceLanguages(List<ClientLanguage> languages) {
        replaceLanguages(languages, null);
    }

    public void replaceLanguages(List<ClientLanguage> languages, ClientLanguage englishLanguage) {
        replaceLanguages(languages, englishLanguage, List.of());
    }

    public void replaceLanguages(List<ClientLanguage> languages,
                                 ClientLanguage englishLanguage,
                                 List<Map<String, String>> legacyLanguageEntries) {
        List<LanguageEntry> entries = new ArrayList<>(languages.size());
        for (int index = 0; index < languages.size(); index++) {
            Map<String, String> legacyEntries = index < legacyLanguageEntries.size()
                    ? Map.copyOf(legacyLanguageEntries.get(index))
                    : Map.of();
            entries.add(new LanguageEntry(languages.get(index), legacyEntries));
        }
        languageSnapshot = new LanguageSnapshot(List.copyOf(entries), englishLanguage);
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
        return itemStackNames.resolveCurrentDisplayName(stack);
    }

    public List<String> resolveComponentsForAll(Component component) {
        LinkedHashSet<String> results = new LinkedHashSet<>();
        LanguageSnapshot snapshot = languageSnapshot;
        for (LanguageEntry entry : snapshot.entries()) {
            ComponentLanguageResolver.resolve(component, entry.language()).ifPresent(results::add);
        }
        return List.copyOf(results);
    }

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
            Function<Component, Optional<String>> componentResolver =
                    component -> ComponentLanguageResolver.resolve(component, entry.language())
                            .or(() -> ComponentLanguageResolver.resolve(component, entry.legacyEntries()::get));
            itemStackNames.resolveDisplayName(stack, componentResolver, SPECIAL_NAME_RESOLVERS)
                    .ifPresent(results::add);
        }
        return List.copyOf(results);
    }

    private List<String> resolveSearchNamesUncached(ItemStack stack) {
        LinkedHashSet<String> results = new LinkedHashSet<>(resolveDisplayNamesUncached(stack));
        for (Component enchantmentName : itemStackNames.getCraftingEnchantmentNames(stack)) {
            results.addAll(resolveComponentsForAll(enchantmentName));
        }
        return List.copyOf(results);
    }

    private record LanguageEntry(ClientLanguage language, Map<String, String> legacyEntries) {
    }

    private record LanguageSnapshot(List<LanguageEntry> entries, ClientLanguage english) {
    }
}
