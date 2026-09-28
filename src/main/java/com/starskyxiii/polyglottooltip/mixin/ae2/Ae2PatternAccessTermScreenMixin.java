package com.starskyxiii.polyglottooltip.mixin.ae2;
//~ minecraft_116_packages

//? if <=1.19.2 {
/*import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import com.starskyxiii.polyglottooltip.integration.ae2.LegacyAe2RegexSearch;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.Minecraft;
*///?} else if forge && <=1.20.1 {
/*import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import com.starskyxiii.polyglottooltip.integration.ae2.Ae2SearchRefresh;
import com.starskyxiii.polyglottooltip.integration.jec.JecMatchBridge;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?} else {
//? if neoforge && >=1.21.1 && <=1.21.1 {
import com.starskyxiii.polyglottooltip.integration.ae2.Ae2SearchRefresh;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Map;
//?}
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.starskyxiii.polyglottooltip.integration.ae2.PatternAccessSearchUtil;
import net.minecraft.client.Minecraft;
//?}
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "appeng.client.gui.me.patternaccess.PatternAccessTermScreen", remap = false)
public class Ae2PatternAccessTermScreenMixin
        //? if (forge && >1.19.2 && <=1.20.1) || (neoforge && >=1.21.1 && <=1.21.1)
        implements Ae2SearchRefresh.Refreshable
{

    //? if <=1.19.2 {
    /*private static final Optional<Method> POLYGLOT_JEC_CONTAINS = polyglot$findJecContains();

    @Shadow @Final
    private Map<String, ?> cachedSearches;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void polyglot$registerLanguageDependentCache(CallbackInfo ci) {
        LegacyAe2RegexSearch.registerCache(cachedSearches);
        LegacyAe2RegexSearch.registerPatternAccessScreen(this);
    }
    *///?}

    //? if (forge && >1.19.2 && <=1.20.1) || (neoforge && >=1.21.1 && <=1.21.1) {
    @Shadow @Final
    private Map<String, ?> cachedSearches;

    //? if neoforge && >=1.21.1 && <=1.21.1 {
    @Shadow @Final
    private Map<ItemStack, String> patternSearchText;
    //?}

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
        //? if neoforge && >=1.21.1 && <=1.21.1 {
        patternSearchText.clear();
        //?}
        if ((Object) this == Minecraft.getInstance().screen) {
            refreshList();
        }
    }
    //?}

    //? if <=1.19.2 {
    /*@Inject(
            method = "itemStackMatchesSearchTerm(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;)Z",
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 1
    )
    private void polyglot$matchPatternOutputName(ItemStack encodedPattern,
                                                 String searchTerm,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || encodedPattern.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        IPatternDetails details;
        try {
            details = PatternDetailsHelper.decodePattern(encodedPattern, minecraft.level);
        } catch (RuntimeException ignored) {
            return;
        }
        if (details == null) {
            return;
        }
        for (GenericStack output : details.getOutputs()) {
            if (output == null || output.what() == null) {
                continue;
            }
            AEKey key = output.what();
            String primaryName = key.getDisplayName().getString();
            if (polyglot$hostContains(primaryName, searchTerm)
                    || ChineseScriptSearchMatcher.containsMatch(searchTerm, primaryName)) {
                cir.setReturnValue(true);
                return;
            }
            if (!SecondaryTooltipUtil.shouldShowSecondaryLanguage()) {
                continue;
            }
            Iterable<String> secondaryNames = key instanceof AEItemKey itemKey
                    ? LanguageCache.getInstance().resolveSearchNamesForAll(itemKey.toStack())
                    : LanguageCache.getInstance().resolveComponentsForAll(key.getDisplayName());
            for (String secondaryName : secondaryNames) {
                if (polyglot$hostContains(secondaryName, searchTerm)
                        || ChineseScriptSearchMatcher.containsMatch(searchTerm, secondaryName)) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }

    private static boolean polyglot$hostContains(String value, CharSequence query) {
        String normalizedValue = value.toLowerCase(Locale.ROOT);
        if (normalizedValue.contains(query)) {
            return true;
        }
        if (POLYGLOT_JEC_CONTAINS.isEmpty()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(POLYGLOT_JEC_CONTAINS.get().invoke(null, normalizedValue, query));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static Optional<Method> polyglot$findJecContains() {
        try {
            return Optional.of(Class.forName("me.towdium.jecharacters.utils.Match")
                    .getMethod("contains", String.class, CharSequence.class));
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }
    *///?} else if forge && <=1.20.1 {
    /*@Inject(
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
        var pattern = PatternDetailsHelper.decodePattern(stack, minecraft.level);
        if (pattern == null) {
            return;
        }
        for (GenericStack output : pattern.getOutputs()) {
            if (output == null || output.what() == null) {
                continue;
            }
            AEKey key = output.what();
            if (polyglot$matchesOutputName(key.getDisplayName().getString(), searchTerm)) {
                cir.setReturnValue(true);
                return;
            }
            if (!SecondaryTooltipUtil.shouldShowSecondaryLanguage()) {
                continue;
            }
            Iterable<String> names = key instanceof AEItemKey itemKey
                    ? LanguageCache.getInstance().resolveSearchNamesForAll(itemKey.toStack())
                    : LanguageCache.getInstance().resolveComponentsForAll(key.getDisplayName());
            for (String name : names) {
                if (polyglot$matchesOutputName(name, searchTerm)) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }

    private static boolean polyglot$matchesOutputName(String name, String query) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.contains(query)
                || ChineseScriptSearchMatcher.containsMatch(query, normalized)
                || JecMatchBridge.contains(normalized, query) == JecMatchBridge.Result.MATCH;
    }
    *///?} else {
    @ModifyReturnValue(method = "getPatternSearchText", at = @At("RETURN"))
    private String extendPatternSearchText(String original, ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        return PatternAccessSearchUtil.extendPatternSearchText(original, stack, minecraft.level);
    }
    //?}
}
