package com.starskyxiii.polyglottooltip.mixin.prettypipes;
//~ minecraft_116_packages

//? if forge || (neoforge && <26) {
import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import com.starskyxiii.polyglottooltip.integration.SearchScreenRefresh;
import com.starskyxiii.polyglottooltip.integration.prettypipes.PrettyPipesSearchPolicy;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;
import java.util.function.Predicate;

@Pseudo
@Mixin(targets = "de.ellpeck.prettypipes.terminal.containers.ItemTerminalGui", remap = false)
public abstract class PrettyPipesItemTerminalMixin implements SearchScreenRefresh.Refreshable {
    @Shadow public EditBox search;
    @Shadow private List<ItemStack> items;
    @Shadow private List<ItemStack> craftables;
    @Shadow public abstract void updateWidgets();

    @Unique private PrettyPipesSearchPolicy polyglot$searchPolicy;
    @Unique private boolean polyglot$scriptMatching;
    @Unique private long polyglot$generation;

    @ModifyArg(method = "updateWidgets", at = @At(value = "INVOKE",
            target = "Ljava/util/List;removeIf(Ljava/util/function/Predicate;)Z"), index = 0, require = 1)
    private Predicate<Pair<ItemStack, Boolean>> polyglot$extendSearch(Predicate<Pair<ItemStack, Boolean>> original) {
        boolean scriptMatching = ChineseScriptSearchMatcher.isEnabled();
        long generation = SearchScreenRefresh.generation();
        if (polyglot$searchPolicy == null || scriptMatching != polyglot$scriptMatching
                || generation != polyglot$generation) {
            polyglot$searchPolicy = new PrettyPipesSearchPolicy(scriptMatching);
            polyglot$scriptMatching = scriptMatching;
            polyglot$generation = generation;
        }
        //? if <=1.16.5 {
        /*boolean tooltipPrefix = false;
        *///?} else {
        boolean tooltipPrefix = true;
        //?}
        return polyglot$searchPolicy.extend(original, search.getValue(), tooltipPrefix,
                SecondaryTooltipUtil.shouldShowSecondaryLanguage(),
                pair -> pair.getLeft().getHoverName().getString(),
                pair -> LanguageCache.getInstance().resolveSearchNamesForAll(pair.getLeft()));
    }

    @Override
    public void polyglot$refreshSearch() {
        polyglot$searchPolicy = null;
        if (search != null && items != null && craftables != null) {
            updateWidgets();
        }
    }
}
//?}
