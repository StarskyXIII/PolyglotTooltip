package com.starskyxiii.polyglottooltip;

import com.starskyxiii.polyglottooltip.integration.occultism.OccultismSearchUtil;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Caches secondary-language translations using {@link ClientLanguage#loadFrom},
 * which merges translations key-by-key across all resource packs — the same
 * strategy Minecraft's own {@code LanguageManager} uses.
 *
 * <p>Supports multiple simultaneously loaded languages; each configured language
 * code gets its own {@link ClientLanguage} instance so they can be queried
 * independently and inserted as separate tooltip lines.
 */
public class LanguageCache extends SimplePreparableReloadListener<List<ClientLanguage>> {

    /**
     * Optional-mod integrations register a resolver here so that
     * {@link #resolveDisplayNamesForAll} can handle special item naming without
     * {@link LanguageCache} importing any integration-specific class.
     *
     * <p>Each resolver receives the {@link ItemStack} and a reference to
     * the per-language component resolver so it can translate {@link Component}
     * objects. Return {@link Optional#empty()} to indicate "not handled".
     */
    @FunctionalInterface
    public interface SpecialNameResolver {
        Optional<String> resolve(ItemStack stack, Function<Component, Optional<String>> componentResolver);
    }

    private static final List<SpecialNameResolver> SPECIAL_NAME_RESOLVERS = new ArrayList<>();

    public static void registerSpecialNameResolver(SpecialNameResolver resolver) {
        SPECIAL_NAME_RESOLVERS.add(resolver);
    }

    private static final LanguageCache INSTANCE = new LanguageCache();

    private List<ClientLanguage> loadedLanguages = new ArrayList<>();

    // Per-stack secondary-name cache. Some mods reuse one Item for many visible names
    // and derive the final text from NBT, so Item-only caching is too coarse.
    // We intentionally still ignore custom hover names and only key off the stack data
    // that affects generated translations.
    private final Map<DisplayNameCacheKey, List<String>> displayNameCache = new HashMap<>();
    private final Map<DisplayNameCacheKey, List<String>> searchNameCache = new HashMap<>();

    public static LanguageCache getInstance() {
        return INSTANCE;
    }

    // -------------------------------------------------------------------------
    // SimplePreparableReloadListener — runs on the resource-reload executor
    // -------------------------------------------------------------------------

    /** Runs on the background thread during resource reload. */
    @Override
    protected List<ClientLanguage> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        List<? extends String> langs = Config.DISPLAY_LANGUAGE.get();
        List<ClientLanguage> result = new ArrayList<>();
        for (String lang : langs) {
            try {
                ClientLanguage loaded = ClientLanguage.loadFrom(resourceManager, List.of(lang), false);
                PolyglotTooltip.LOGGER.info("[PolyglotTooltip] Loaded secondary language: {}", lang);
                result.add(loaded);
            } catch (Exception e) {
                PolyglotTooltip.LOGGER.error("[PolyglotTooltip] Failed to load language '{}': {}", lang, e.getMessage());
            }
        }
        return result;
    }

    /** Runs on the main thread after prepare completes. */
    @Override
    protected void apply(List<ClientLanguage> languages, ResourceManager resourceManager, ProfilerFiller profiler) {
        this.loadedLanguages = languages;
        this.displayNameCache.clear();
        this.searchNameCache.clear();
        OccultismSearchUtil.clearTooltipCache();
        ChineseScriptSearchMatcher.clearCaches();
    }

    // -------------------------------------------------------------------------
    // Public multi-language API
    // -------------------------------------------------------------------------

    /**
     * Returns one resolved display name per configured language, in config order.
     * Languages that have no translation for this item are omitted.
     */
    public List<String> resolveDisplayNamesForAll(ItemStack stack) {
        return displayNameCache.computeIfAbsent(DisplayNameCacheKey.from(stack), key -> resolveDisplayNamesUncached(stack));
    }

    /**
     * Returns secondary-language search text for the stack, including the stack
     * display name plus any enchantment names shown in the tooltip.
     */
    public List<String> resolveSearchNamesForAll(ItemStack stack) {
        return searchNameCache.computeIfAbsent(DisplayNameCacheKey.from(stack), key -> resolveSearchNamesUncached(stack));
    }

    /**
     * Returns the stack display name in the currently active game language,
     * preferring integration-provided special naming rules when available.
     */
    public String resolveCurrentDisplayName(ItemStack stack) {
        return resolveSpecialDisplayName(stack, component -> Optional.of(component.getString()))
                .orElseGet(() -> stack.getHoverName().getString());
    }

    private List<String> resolveDisplayNamesUncached(ItemStack stack) {
        List<String> results = new ArrayList<>();
        for (ClientLanguage lang : loadedLanguages) {
            Function<Component, Optional<String>> resolver = comp -> resolveComponentWithLang(comp, lang);
            Optional<String> name = resolveSpecialDisplayName(stack, resolver);
            if (name.isEmpty()) {
                name = resolveComponentWithLang(stack.getHoverName(), lang);
            }
            name.ifPresent(results::add);
        }
        return results;
    }

    private List<String> resolveSearchNamesUncached(ItemStack stack) {
        LinkedHashSet<String> results = new LinkedHashSet<>(resolveDisplayNamesUncached(stack));

        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        if (!enchantments.isEmpty()) {
            for (var entry : enchantments.entrySet()) {
                Component fullName = Enchantment.getFullname(entry.getKey(), entry.getIntValue());
                results.addAll(resolveComponentsForAll(fullName));
            }
        }

        return List.copyOf(results);
    }

    /**
     * Returns one resolved component string per configured language, in config order.
     * Languages that have no translation for this component are omitted.
     */
    public List<String> resolveComponentsForAll(Component component) {
        List<String> results = new ArrayList<>();
        for (ClientLanguage lang : loadedLanguages) {
            resolveComponentWithLang(component, lang).ifPresent(results::add);
        }
        return results;
    }

    // -------------------------------------------------------------------------
    // Private per-language resolution
    // -------------------------------------------------------------------------

    private Optional<String> resolveSpecialDisplayName(ItemStack stack,
                                                       Function<Component, Optional<String>> componentResolver) {
        for (SpecialNameResolver resolver : SPECIAL_NAME_RESOLVERS) {
            Optional<String> name = resolver.resolve(stack, componentResolver);
            if (name.isPresent()) {
                return name;
            }
        }
        return Optional.empty();
    }

    /**
     * Recursively resolves a {@link Component} using the given language cache,
     * including any sibling components (e.g., the roman-numeral level suffix on
     * enchantment names: "Unbreaking" + " " + "V").
     *
     * <p>Non-translatable components (literal text) are returned as-is since they
     * are the same in every language. Translatable components with format args have
     * each arg resolved recursively.
     *
     * <p>Returns {@link Optional#empty()} if this component's translation key is
     * absent from the given language.
     */
    private Optional<String> resolveComponentWithLang(Component component, ClientLanguage lang) {
        if (lang == null) return Optional.empty();
        if (!(component.getContents() instanceof TranslatableContents tc)) {
            // Literal or other non-translatable content — same text in every language
            return Optional.of(component.getString());
        }

        String template = lang.getOrDefault(tc.getKey(), null);
        if (template == null) return Optional.empty();

        // Resolve main content (with %s format args if any)
        String mainPart;
        Object[] args = tc.getArgs();
        if (args.length == 0) {
            mainPart = template;
        } else {
            Object[] resolvedArgs = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                resolvedArgs[i] = args[i] instanceof Component c
                        ? resolveComponentWithLang(c, lang).orElse(c.getString())
                        : String.valueOf(args[i]);
            }
            try {
                mainPart = String.format(template, resolvedArgs);
            } catch (Exception e) {
                mainPart = template; // malformed format string — return raw template
            }
        }

        // Append sibling components (e.g., " " + "V" for enchantment level suffixes)
        if (component.getSiblings().isEmpty()) {
            return Optional.of(mainPart);
        }
        StringBuilder sb = new StringBuilder(mainPart);
        for (Component sibling : component.getSiblings()) {
            sb.append(resolveComponentWithLang(sibling, lang).orElse(sibling.getString()));
        }
        return Optional.of(sb.toString());
    }

    /**
     * Synchronously reloads translations on the calling thread.
     * Called when the mod config changes so the new language takes effect
     * immediately without requiring F3+T.
     */
    public void reloadImmediate() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        List<ClientLanguage> langs = prepare(mc.getResourceManager(), InactiveProfiler.INSTANCE);
        apply(langs, mc.getResourceManager(), InactiveProfiler.INSTANCE);
    }

    private record DisplayNameCacheKey(ItemStack stackSnapshot, int hash) {
        private static DisplayNameCacheKey from(ItemStack stack) {
            ItemStack snapshot = stack.copyWithCount(1);
            return new DisplayNameCacheKey(snapshot, ItemStack.hashItemAndComponents(snapshot));
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof DisplayNameCacheKey other)) return false;
            return ItemStack.isSameItemSameComponents(this.stackSnapshot, other.stackSnapshot);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
