package com.starskyxiii.polyglottooltip.legacy.integration.jei;

import java.util.function.BiConsumer;

import mezz.jei.api.search.ISearchIndex;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.search.ISearchStorage;
import mezz.jei.search.PrefixedSearchable;

public final class HeiIndexes {
    private static final boolean STORAGE =
            HeiIndexes.class.getClassLoader().getResource("mezz/jei/search/ISearchStorage.class") != null;

    private HeiIndexes() {}

    public static BiConsumer<String, Object> sink(Object searchable) {
        if (!(searchable instanceof PrefixedSearchable)) {
            return null;
        }
        return STORAGE ? Storage.sink((PrefixedSearchable) searchable) : Index.sink((PrefixedSearchable) searchable);
    }

    private static final class Storage {
        static BiConsumer<String, Object> sink(PrefixedSearchable searchable) {
            ISearchStorage<IIngredientListElement<?>> storage = searchable.getSearchStorage();
            return (token, element) -> storage.put(token, (IIngredientListElement<?>) element);
        }
    }

    private static final class Index {
        static BiConsumer<String, Object> sink(PrefixedSearchable searchable) {
            ISearchIndex<IIngredientListElement<?>> index = searchable.getSearchIndex();
            return index == null ? null : (token, element) -> index.put(token, (IIngredientListElement<?>) element);
        }
    }
}
