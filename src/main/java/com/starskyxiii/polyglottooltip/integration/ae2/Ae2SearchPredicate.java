package com.starskyxiii.polyglottooltip.integration.ae2;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.menu.me.common.GridInventoryEntry;
import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.search.WrappedSearchMatcher;

import java.util.List;
import java.util.function.Predicate;

/**
 * AE2 name-search predicate that matches the current display name plus any
 * configured secondary-language names.
 *
 * <p>The original {@code NameSearchPredicate} is still used as a delegate so
 * behavior added by other mods remains intact after our extra matching layer.
 */
public class Ae2SearchPredicate implements Predicate<GridInventoryEntry> {

    private final String term;
    private final Predicate<GridInventoryEntry> delegate;

    public Ae2SearchPredicate(String term, Predicate<GridInventoryEntry> delegate) {
        this.term = term;
        this.delegate = delegate;
    }

    @Override
    public boolean test(GridInventoryEntry entry) {
        AEKey key = entry.getWhat();
        if (key == null) return false;

        return WrappedSearchMatcher.matches(
                term,
                key.getDisplayName().getString(),
                () -> delegate.test(entry),
                () -> resolveSecondaryNames(key)
        );
    }

    private List<String> resolveSecondaryNames(AEKey key) {
        LanguageCache cache = LanguageCache.getInstance();
        if (key instanceof AEItemKey itemKey) {
            return cache.resolveDisplayNamesForAll(itemKey.toStack());
        }
        return cache.resolveComponentsForAll(key.getDisplayName());
    }
}
