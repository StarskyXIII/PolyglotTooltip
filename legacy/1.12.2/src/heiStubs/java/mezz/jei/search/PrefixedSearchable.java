package mezz.jei.search;

import mezz.jei.gui.ingredients.IIngredientListElement;

public abstract class PrefixedSearchable {
    public abstract ISearchStorage<IIngredientListElement<?>> getSearchStorage();
}
