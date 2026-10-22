package mezz.jei.search;

import java.util.Collection;

import mezz.jei.gui.ingredients.IIngredientListElement;

public interface IElementSearch {
    Collection<IIngredientListElement<?>> getAllIngredients();
}
