package com.starskyxiii.polyglottooltip;

import com.starskyxiii.polyglottooltip.compat.figura.FiguraEmojiContext;
import com.starskyxiii.polyglottooltip.integration.occultism.OccultismSearchUtil;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
public class LanguageCache implements PreparableReloadListener {

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
    private final Map<DisplayNameCacheKey, List<String>> displayNameCache = new HashMap<>();
    private final Map<DisplayNameCacheKey, List<String>> searchNameCache = new HashMap<>();

    public static LanguageCache getInstance() {
        return INSTANCE;
    }

    // -------------------------------------------------------------------------
    // PreparableReloadListener
    // -------------------------------------------------------------------------

    @Override
    public CompletableFuture<Void> reload(SharedState currentReload,
                                          Executor taskExecutor,
                                          PreparationBarrier preparationBarrier,
                                          Executor reloadExecutor) {
        ResourceManager resourceManager = currentReload.resourceManager();
        return CompletableFuture
                .supplyAsync(() -> loadLanguages(resourceManager), taskExecutor)
                .thenCompose(preparationBarrier::wait)
                .thenAcceptAsync(this::applyLanguages, reloadExecutor);
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
     * matching the actual tooltip title after other mods have post-processed it.
     */
    public String resolveCurrentDisplayName(ItemStack stack) {
        return stack.getHoverName().getString();
    }

    private List<String> resolveDisplayNamesUncached(ItemStack stack) {
        LinkedHashSet<String> results = new LinkedHashSet<>();
        for (ClientLanguage lang : loadedLanguages) {
            Function<Component, Optional<String>> resolver = comp -> resolveComponentWithLang(comp, lang);
            resolveDisplayName(stack, resolver).ifPresent(results::add);
        }
        return List.copyOf(results);
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
        LinkedHashSet<String> results = new LinkedHashSet<>();
        for (ClientLanguage lang : loadedLanguages) {
            resolveComponentWithLang(component, lang).ifPresent(results::add);
        }
        return List.copyOf(results);
    }

    // -------------------------------------------------------------------------
    // Private per-language resolution
    // -------------------------------------------------------------------------

    private Optional<String> resolveDisplayName(
            ItemStack stack,
            Function<Component, Optional<String>> componentResolver
    ) {
        Optional<Component> customName = resolveCustomNameComponent(stack);
        if (customName.isPresent()) {
            return customName.flatMap(componentResolver);
        }

        Optional<Component> explicitItemName = resolvePatchedItemNameComponent(stack);
        if (explicitItemName.isPresent()) {
            Optional<String> resolved = explicitItemName.flatMap(componentResolver);
            if (resolved.isPresent()) {
                return resolved;
            }
        }

        Optional<String> specialName = FiguraEmojiContext.supplyInHoverName(
                () -> resolveSpecialDisplayName(stack, componentResolver));
        if (specialName.isPresent()) {
            return specialName;
        }

        Optional<String> itemName = FiguraEmojiContext.supplyInHoverName(
                () -> componentResolver.apply(stack.getItem().getName(stack)));
        if (itemName.isPresent()) {
            return itemName;
        }

        Optional<Component> defaultItemName = resolveDefaultItemNameComponent(stack);
        if (defaultItemName.isPresent()) {
            Optional<String> resolved = defaultItemName.flatMap(componentResolver);
            if (resolved.isPresent()) {
                return resolved;
            }
        }

        return componentResolver.apply(stack.getHoverName());
    }

    private Optional<Component> resolveCustomNameComponent(ItemStack stack) {
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName != null) {
            return Optional.of(customName);
        }
        return Optional.empty();
    }

    private Optional<Component> resolvePatchedItemNameComponent(ItemStack stack) {
        Component itemName = stack.get(DataComponents.ITEM_NAME);
        if (itemName == null) {
            return Optional.empty();
        }

        Component defaultItemName = stack.getPrototype().get(DataComponents.ITEM_NAME);
        return Objects.equals(itemName, defaultItemName) ? Optional.empty() : Optional.of(itemName);
    }

    private Optional<Component> resolveDefaultItemNameComponent(ItemStack stack) {
        Component itemName = stack.get(DataComponents.ITEM_NAME);
        return itemName == null ? Optional.empty() : Optional.of(itemName);
    }

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
     * Recursively resolves a {@link Component} using the given language cache.
     */
    private Optional<String> resolveComponentWithLang(Component component, ClientLanguage lang) {
        if (lang == null) return Optional.empty();
        Optional<String> mainPart = resolveComponentContentsWithLang(component, lang);
        if (mainPart.isEmpty()) {
            return Optional.empty();
        }

        if (component.getSiblings().isEmpty()) {
            return mainPart;
        }
        StringBuilder sb = new StringBuilder(mainPart.get());
        for (Component sibling : component.getSiblings()) {
            sb.append(resolveComponentWithLang(sibling, lang).orElse(""));
        }
        return Optional.of(sb.toString());
    }

    private Optional<String> resolveComponentContentsWithLang(Component component, ClientLanguage lang) {
        ComponentContents contents = component.getContents();
        if (!(contents instanceof TranslatableContents tc)) {
            return Optional.of(resolvePlainContents(contents));
        }

        String template = lang.getOrDefault(tc.getKey(), null);
        if (template == null) {
            template = tc.getFallback();
        }
        if (template == null) return Optional.empty();

        Object[] args = tc.getArgs();
        if (args.length == 0) {
            return Optional.of(template);
        }

        Object[] resolvedArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof Component c) {
                resolvedArgs[i] = resolveComponentWithLang(c, lang).orElse("");
            } else {
                resolvedArgs[i] = String.valueOf(arg);
            }
        }
        try {
            return Optional.of(String.format(template, resolvedArgs));
        } catch (Exception e) {
            return Optional.of(template);
        }
    }

    private String resolvePlainContents(ComponentContents contents) {
        StringBuilder sb = new StringBuilder();
        contents.visit(text -> {
            sb.append(text);
            return Optional.empty();
        });
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Internal load / apply helpers (also used by reloadImmediate)
    // -------------------------------------------------------------------------

    private List<ClientLanguage> loadLanguages(ResourceManager resourceManager) {
        List<? extends String> langs = Config.DISPLAY_LANGUAGE.get();
        List<ClientLanguage> result = new ArrayList<>();
        for (String lang : langs) {
            try {
                ClientLanguage loaded = ClientLanguage.loadFrom(resourceManager, languageChain(lang), false);
                PolyglotTooltip.LOGGER.info("[PolyglotTooltip] Loaded secondary language: {}", lang);
                result.add(loaded);
            } catch (Exception e) {
                PolyglotTooltip.LOGGER.error("[PolyglotTooltip] Failed to load language '{}': {}", lang, e.getMessage());
            }
        }
        return result;
    }

    private void applyLanguages(List<ClientLanguage> languages) {
        this.loadedLanguages = languages;
        this.displayNameCache.clear();
        this.searchNameCache.clear();
        OccultismSearchUtil.clearTooltipCache();
        ChineseScriptSearchMatcher.clearCaches();
    }

    private static List<String> languageChain(String lang) {
        return "en_us".equalsIgnoreCase(lang) ? List.of("en_us") : List.of("en_us", lang);
    }

    /**
     * Synchronously reloads translations on the calling thread.
     * Called when the mod config changes so the new language takes effect
     * immediately without requiring F3+T.
     */
    public void reloadImmediate() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        List<ClientLanguage> langs = loadLanguages(mc.getResourceManager());
        applyLanguages(langs);
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
