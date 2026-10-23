package com.starskyxiii.polyglottooltip.legacy.mixin.jei;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.starskyxiii.polyglottooltip.legacy.integration.jei.JeiSearch;
import com.starskyxiii.polyglottooltip.legacy.integration.jei.SearchIndex;

import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.suffixtree.CombinedSearchTrees;
import mezz.jei.suffixtree.GeneralizedSuffixTree;
import net.minecraft.util.NonNullList;

@Pseudo
@Mixin(targets = "mezz.jei.ingredients.IngredientFilter", remap = false)
public abstract class JeiFilterMixin implements SearchIndex {
    @Shadow @Final private NonNullList<IIngredientListElement> elementList;
    @Shadow private CombinedSearchTrees combinedSearchTrees;

    @Unique private GeneralizedSuffixTree polyglot$tokens;
    @Unique private int polyglot$generation;
    @Unique private int polyglot$indexed;

    @Shadow
    public abstract void modesChanged();

    @Shadow
    private void notifyListenersOfChange() {}

    @Inject(method = "<init>", at = @At("RETURN"))
    private void polyglot$track(CallbackInfo ci) {
        polyglot$generation = JeiSearch.track(this);
        polyglot$tokens = new GeneralizedSuffixTree();
        combinedSearchTrees.addSearchTree(polyglot$tokens);
    }

    @Inject(method = "modesChanged", at = @At("RETURN"))
    private void polyglot$searchChanged(CallbackInfo ci) {
        combinedSearchTrees.addSearchTree(polyglot$tokens);
    }

    @Inject(method = "getIngredientListUncached", at = @At("HEAD"))
    private void polyglot$beforeSearch(String filterText, CallbackInfoReturnable<List<?>> cir) {
        JeiSearch.indexBeforeSearch(this);
    }

    @Override
    public int polyglot$generation() {
        return polyglot$generation;
    }

    @Override
    public void polyglot$indexPending(long budgetNanos) {
        if (polyglot$indexed < elementList.size()) {
            polyglot$indexed = JeiSearch.indexPositions(elementList, polyglot$indexed, budgetNanos, polyglot$tokens::put);
        }
    }

    @Override
    public void polyglot$rebuild() {
        polyglot$generation = JeiSearch.track(this);
        polyglot$tokens = new GeneralizedSuffixTree();
        polyglot$indexed = 0;
        modesChanged();
        notifyListenersOfChange();
    }
}
