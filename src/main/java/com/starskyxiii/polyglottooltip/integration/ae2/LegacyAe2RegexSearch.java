package com.starskyxiii.polyglottooltip.integration.ae2;
//~ minecraft_legacy_components

import appeng.api.client.AEStackRendering;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.client.gui.me.search.RepoSearch;
import appeng.core.AEConfig;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.util.Platform;
import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import com.starskyxiii.polyglottooltip.core.ChineseScriptPolicy;
import com.starskyxiii.polyglottooltip.minecraft.MinecraftLanguageAdapter;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class LegacyAe2RegexSearch {
    private static final List<WeakReference<Map<?, ?>>> CACHES = new CopyOnWriteArrayList<>();
    private static final List<WeakReference<Object>> SCREENS = new CopyOnWriteArrayList<>();

    static {
        MinecraftLanguageAdapter.getInstance().registerDependentCacheInvalidator(
                LegacyAe2RegexSearch::clearRegisteredCaches);
    }

    private LegacyAe2RegexSearch() {
    }

    public static Predicate<GridInventoryEntry> wrapName(Pattern pattern,
                                                          Predicate<GridInventoryEntry> original) {
        return entry -> original.test(entry)
                || matchesVariants(pattern, resolvePrimaryName(entry))
                || matchesVariants(pattern, resolveSecondaryNames(entry));
    }

    public static Predicate<GridInventoryEntry> wrapTooltip(Pattern pattern, RepoSearch repoSearch,
                                                             Predicate<GridInventoryEntry> original) {
        return entry -> original.test(entry)
                || matchesVariants(pattern, resolvePrimaryTooltip(entry, repoSearch))
                || matchesVariants(pattern, resolveSecondaryTooltip(entry));
    }

    public static void registerCache(Map<?, ?> cache) {
        CACHES.add(new WeakReference<>(cache));
    }

    public static void registerPatternAccessScreen(Object screen) {
        SCREENS.add(new WeakReference<>(screen));
    }

    private static List<String> resolvePrimaryName(GridInventoryEntry entry) {
        AEKey key = entry.getWhat();
        return key == null ? List.of() : List.of(key.getDisplayName().getString());
    }

    private static List<String> resolveSecondaryNames(GridInventoryEntry entry) {
        AEKey key = entry.getWhat();
        if (key == null || !SecondaryTooltipUtil.shouldShowSecondaryLanguage()) {
            return List.of();
        }
        LanguageCache cache = LanguageCache.getInstance();
        if (key instanceof AEItemKey itemKey) {
            return cache.resolveSearchNamesForAll(itemKey.toStack());
        }
        return cache.resolveComponentsForAll(key.getDisplayName());
    }

    private static List<String> resolvePrimaryTooltip(GridInventoryEntry entry, RepoSearch repoSearch) {
        AEKey key = entry.getWhat();
        return key == null ? List.of() : List.of(repoSearch.getTooltipText(key));
    }

    private static List<String> resolveSecondaryTooltip(GridInventoryEntry entry) {
        AEKey key = entry.getWhat();
        if (key == null || !SecondaryTooltipUtil.shouldShowSecondaryLanguage()) {
            return List.of();
        }
        MutableComponent composite = buildSearchableTooltip(key);
        return LanguageCache.getInstance().resolveComponentsForAll(composite).stream()
                .map(LegacyAe2RegexSearch::stripFormatting)
                .toList();
    }

    private static MutableComponent buildSearchableTooltip(AEKey key) {
        List<Component> lines = AEStackRendering.getTooltip(key);
        MutableComponent composite = Component.empty();
        for (int index = 0; index < lines.size(); index++) {
            Component line = lines.get(index);
            if (index > 0 && index >= lines.size() - 1
                    && !AEConfig.instance().isSearchModNameInTooltips()) {
                String text = line.getString();
                boolean hadFormatting;
                if (text.indexOf(ChatFormatting.PREFIX_CODE) != -1) {
                    text = stripFormatting(text);
                    hadFormatting = true;
                } else {
                    hadFormatting = !line.getStyle().isEmpty();
                }
                if (!hadFormatting || !Objects.equals(text, Platform.getModName(key.getModId()))) {
                    composite.append("\n").append(line);
                }
            } else {
                if (index > 0) {
                    composite.append("\n");
                }
                composite.append(line);
            }
        }
        return composite;
    }

    private static String stripFormatting(String value) {
        String stripped = ChatFormatting.stripFormatting(value);
        return stripped == null ? value : stripped;
    }

    private static boolean matchesVariants(Pattern pattern, Iterable<String> candidates) {
        boolean enabled = ChineseScriptSearchMatcher.isEnabled();
        for (String candidate : candidates) {
            for (String variant : ChineseScriptPolicy.scriptVariants(candidate, enabled)) {
                if (pattern.matcher(variant).find()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void clearRegisteredCaches() {
        CACHES.removeIf(reference -> {
            Map<?, ?> cache = reference.get();
            if (cache == null) {
                return true;
            }
            cache.clear();
            return false;
        });
        SCREENS.removeIf(reference -> {
            Object screen = reference.get();
            if (screen == null) {
                return true;
            }
            try {
                Method refreshList = screen.getClass().getDeclaredMethod("refreshList");
                refreshList.setAccessible(true);
                refreshList.invoke(screen);
            } catch (ReflectiveOperationException ignored) {
            }
            return false;
        });
    }
}
