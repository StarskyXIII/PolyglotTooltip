package com.starskyxiii.polyglottooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Caches secondary-language translations using {@link ClientLanguage#loadFrom},
 * which internally calls {@code ResourceManager.listResourceStacks} to merge
 * translations key-by-key across all resource packs — the same strategy
 * Minecraft's own LanguageManager uses.
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
    }

    // -------------------------------------------------------------------------
    // Public multi-language API
    // -------------------------------------------------------------------------

    /**
     * Returns one resolved display name per configured language, in config order.
     * Languages that have no translation for this item are omitted.
     */
    public List<String> resolveDisplayNamesForAll(ItemStack stack) {
        List<String> results = new ArrayList<>();
        for (ClientLanguage lang : loadedLanguages) {
            Function<Component, Optional<String>> resolver = comp -> resolveComponentWithLang(comp, lang);
            Optional<String> name = Optional.empty();
            for (SpecialNameResolver sr : SPECIAL_NAME_RESOLVERS) {
                name = sr.resolve(stack, resolver);
                if (name.isPresent()) break;
            }
            if (name.isEmpty()) {
                name = resolveComponentWithLang(stack.getHoverName(), lang);
            }
            name.ifPresent(results::add);
        }
        return results;
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
}
