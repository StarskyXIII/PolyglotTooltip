package com.starskyxiii.polyglottooltip.legacy.mixin.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.starskyxiii.polyglottooltip.legacy.integration.jei.HeiIndexes;
import com.starskyxiii.polyglottooltip.legacy.integration.jei.JeiSearch;
import com.starskyxiii.polyglottooltip.legacy.integration.jei.SearchIndex;

import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.search.ElementSearch;
import mezz.jei.search.IElementSearch;
import mezz.jei.search.PrefixInfo;
import net.minecraft.util.NonNullList;

@Pseudo
@Mixin(targets = "mezz.jei.ingredients.IngredientFilter", remap = false)
public abstract class HeiFilterMixin implements SearchIndex {
    @Shadow private IElementSearch elementSearch;
    @Shadow private boolean afterBlock;

    @Unique private int polyglot$generation;
    @Unique private IElementSearch polyglot$search;
    @Unique private List<Object> polyglot$pending;
    @Unique private int polyglot$indexed;
    @Unique private boolean polyglot$force;
    @Unique private boolean polyglot$rebuildAfterBlock;

    @Shadow
    public abstract void modesChanged();

    @Shadow
    private void notifyListenersOfChange() {}

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lmezz/jei/search/IElementSearch;addAll(Lnet/minecraft/util/NonNullList;)V"))
    private NonNullList<IIngredientListElement> polyglot$initial(NonNullList<IIngredientListElement> elements) {
        polyglot$pending = new ArrayList<>(elements);
        return elements;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void polyglot$track(CallbackInfo ci) {
        polyglot$generation = JeiSearch.track(this);
        polyglot$search = elementSearch;
        if (polyglot$pending == null) {
            polyglot$queueAll();
        }
    }

    @Inject(method = "addIngredients", at = @At("RETURN"))
    private void polyglot$added(NonNullList<IIngredientListElement> elements, CallbackInfo ci) {
        polyglot$pending.addAll(elements);
    }

    @Inject(method = "addIngredient", at = @At("RETURN"))
    private void polyglot$added(IIngredientListElement<?> element, CallbackInfo ci) {
        polyglot$pending.add(element);
    }

    @Inject(method = "modesChanged", at = @At("RETURN"))
    private void polyglot$searchChanged(CallbackInfo ci) {
        if (elementSearch != polyglot$search) {
            polyglot$queueAll();
        }
    }

    @Inject(method = "block", at = @At("RETURN"))
    private void polyglot$blocked(CallbackInfo ci) {
        if (polyglot$rebuildAfterBlock) {
            polyglot$rebuild();
        }
    }

    @Inject(method = "getIngredientListUncached", at = @At("HEAD"))
    private void polyglot$beforeSearch(String filterText, CallbackInfoReturnable<List<?>> cir) {
        JeiSearch.indexBeforeSearch(this);
    }

    @Redirect(method = "modesChanged",
            at = @At(value = "INVOKE", target = "Lmezz/jei/config/Config;doesSearchTreeNeedReload()Z"))
    private boolean polyglot$needsReload() {
        return polyglot$force || HeiConfigInvoker.polyglot$searchTreeNeedsReload();
    }

    @Override
    public int polyglot$generation() {
        return polyglot$generation;
    }

    @Override
    public void polyglot$indexPending(long budgetNanos) {
        if (polyglot$indexed >= polyglot$pending.size() || !(elementSearch instanceof ElementSearch)) {
            return;
        }
        BiConsumer<String, Object> sink = HeiIndexes.sink(
                ((HeiElementSearchAccessor) elementSearch).polyglot$searchables().get(PrefixInfo.NO_PREFIX));
        if (sink == null) {
            return;
        }
        polyglot$indexed = JeiSearch.indexElements(polyglot$pending, polyglot$indexed, budgetNanos, sink);
        if (polyglot$indexed == polyglot$pending.size()) {
            polyglot$pending = new ArrayList<>();
            polyglot$indexed = 0;
        }
    }

    @Override
    public void polyglot$rebuild() {
        if (!afterBlock) {
            polyglot$rebuildAfterBlock = true;
            return;
        }
        polyglot$rebuildAfterBlock = false;
        polyglot$generation = JeiSearch.track(this);
        polyglot$force = true;
        try {
            modesChanged();
        } finally {
            polyglot$force = false;
        }
        notifyListenersOfChange();
    }

    @Unique
    private void polyglot$queueAll() {
        polyglot$search = elementSearch;
        polyglot$pending = new ArrayList<>(elementSearch.getAllIngredients());
        polyglot$indexed = 0;
    }
}
