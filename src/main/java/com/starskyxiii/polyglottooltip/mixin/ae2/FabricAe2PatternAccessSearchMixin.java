package com.starskyxiii.polyglottooltip.mixin.ae2;
//~ minecraft_116_packages

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.llamalad7.mixinextras.sugar.Local;
import com.starskyxiii.polyglottooltip.LanguageCache;
//? if fabric && >1.19.2 && <=1.20.1 {
/*import com.starskyxiii.polyglottooltip.integration.ae2.Ae2SearchRefresh;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Map;
*///?}
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Locale;

@Pseudo
@Mixin(targets = "appeng.client.gui.me.patternaccess.PatternAccessTermScreen", remap = false)
public class FabricAe2PatternAccessSearchMixin
        //? if fabric && >1.19.2 && <=1.20.1
        //implements Ae2SearchRefresh.Refreshable
{
    //? if fabric && >1.19.2 && <=1.20.1 {
    /*@Shadow @Final
    private Map<String, ?> cachedSearches;

    @Shadow
    private void refreshList() {
        throw new AssertionError();
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void polyglot$registerSearchRefresh(CallbackInfo ci) {
        Ae2SearchRefresh.registerScreen(this);
    }

    @Override
    public void polyglot$refreshSearch() {
        cachedSearches.clear();
        if ((Object) this == Minecraft.getInstance().screen) {
            refreshList();
        }
    }
    *///?}

    @Inject(
            method = "itemStackMatchesSearchTerm(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;)Z",
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 1
    )
    private void polyglot$matchDecodedPatternOutputs(ItemStack stack,
                                                     String searchTerm,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || stack == null || stack.isEmpty() || searchTerm == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        var pattern = PatternDetailsHelper.decodePattern(stack, minecraft.level, false);
        if (pattern != null && polyglot$matchesDecodedOutputs(pattern.getOutputs(), searchTerm)) {
            cir.setReturnValue(true);
        }
    }

    private static boolean polyglot$matchesDecodedOutputs(GenericStack[] outputs, String searchTerm) {
        if (outputs == null || searchTerm == null) {
            return false;
        }

        for (GenericStack output : outputs) {
            if (output != null && output.what() != null
                    && polyglot$matchesDecodedOutput(output.what(), searchTerm)) {
                return true;
            }
        }
        return false;
    }

    private static boolean polyglot$matchesDecodedOutput(AEKey key, String searchTerm) {
        String primaryName = key.getDisplayName().getString().toLowerCase(Locale.ROOT);
        if (primaryName.contains(searchTerm)
                || ChineseScriptSearchMatcher.containsMatch(searchTerm, primaryName)) {
            return true;
        }

        LanguageCache cache = LanguageCache.getInstance();
        List<String> secondaryNames = key instanceof AEItemKey itemKey
                ? cache.resolveSearchNamesForAll(itemKey.toStack())
                : cache.resolveComponentsForAll(key.getDisplayName());
        return ChineseScriptSearchMatcher.containsMatch(searchTerm, secondaryNames);
    }

    @Redirect(
            method = "itemStackMatchesSearchTerm(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;)Z",
            at = @At(value = "INVOKE", target = "Ljava/lang/String;contains(Ljava/lang/CharSequence;)Z"),
            remap = false,
            require = 0
    )
    private boolean polyglot$matchPatternOutputName(String primaryName,
                                                    CharSequence searchTerm,
                                                    @Local(ordinal = 0) AEItemKey candidate) {
        if (primaryName.contains(searchTerm)) {
            return true;
        }
        String query = searchTerm.toString();
        if (ChineseScriptSearchMatcher.containsMatch(query, primaryName)) {
            return true;
        }
        List<String> secondaryNames = LanguageCache.getInstance().resolveSearchNamesForAll(candidate.toStack());
        return ChineseScriptSearchMatcher.containsMatch(query, secondaryNames);
    }
}
