package com.starskyxiii.polyglottooltip.mixin.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import com.starskyxiii.polyglottooltip.integration.ae2.LegacyAe2RegexSearch;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Mixin(targets = "appeng.client.gui.me.interfaceterminal.InterfaceTerminalScreen", remap = false)
public class LegacyAe2InterfaceTerminalScreenMixin {
    private static final Optional<Method> POLYGLOT_JEC_CONTAINS = polyglot$findJecContains();

    @Shadow @Final
    private Map<String, ?> cachedSearches;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void polyglot$registerLanguageDependentCache(CallbackInfo ci) {
        LegacyAe2RegexSearch.registerCache(cachedSearches);
        LegacyAe2RegexSearch.registerPatternAccessScreen(this);
    }

    @Inject(
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
}
