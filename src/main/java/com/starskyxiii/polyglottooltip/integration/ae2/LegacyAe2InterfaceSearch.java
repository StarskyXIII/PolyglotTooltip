package com.starskyxiii.polyglottooltip.integration.ae2;
//~ minecraft_116_packages

import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import com.starskyxiii.polyglottooltip.integration.jec.JecMatchBridge;
import com.starskyxiii.polyglottooltip.minecraft.MinecraftLanguageAdapter;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import net.minecraft.item.ItemStack;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;

public final class LegacyAe2InterfaceSearch {
    private static final List<WeakReference<Refreshable>> SCREENS = new CopyOnWriteArrayList<>();

    static {
        MinecraftLanguageAdapter.getInstance().registerDependentCacheInvalidator(
                LegacyAe2InterfaceSearch::refreshRegisteredScreens);
    }

    private LegacyAe2InterfaceSearch() {
    }

    public interface Refreshable {
        void polyglot$refreshSearch();
    }

    public static void register(Refreshable screen) {
        SCREENS.add(new WeakReference<>(screen));
    }

    public static boolean matchesOutput(String nativeText, CharSequence query, ItemStack output,
                                        BiPredicate<String, CharSequence> contains) {
        if (contains.test(nativeText, query) || matchesCandidate(nativeText, query, contains)) {
            return true;
        }
        if (!SecondaryTooltipUtil.shouldShowSecondaryLanguage() || output.isEmpty()) {
            return false;
        }
        for (String name : LanguageCache.getInstance().resolveSearchNamesForAll(output)) {
            if (matchesCandidate(name, query, contains)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesCandidate(String text, CharSequence query,
                                            BiPredicate<String, CharSequence> contains) {
        for (String variant : ChineseScriptSearchMatcher.getSearchVariants(text)) {
            if (contains.test(variant, query)
                    || JecMatchBridge.contains(variant, query) == JecMatchBridge.Result.MATCH) {
                return true;
            }
        }
        return false;
    }

    private static void refreshRegisteredScreens() {
        SCREENS.removeIf(reference -> {
            Refreshable screen = reference.get();
            if (screen == null) {
                return true;
            }
            screen.polyglot$refreshSearch();
            return false;
        });
    }
}
