package mezz.jei.search;

import mezz.jei.api.search.ISearchIndex;
import mezz.jei.gui.ingredients.IIngredientListElement;

public abstract class PrefixedSearchable {
    public abstract ISearchStorage<IIngredientListElement<?>> getSearchStorage();

    public abstract ISearchIndex<IIngredientListElement<?>> getSearchIndex();
}
