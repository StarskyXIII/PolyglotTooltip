package com.starskyxiii.polyglottooltip.mixin.ae2;
//~ minecraft_116_packages

import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.me.common.Repo;
import appeng.util.item.AEItemStack;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.core.ChineseScriptPolicy;
import com.starskyxiii.polyglottooltip.integration.ae2.LegacyAe2RepoRefresh;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Group;

import net.minecraft.network.chat.Component;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(targets = "appeng.client.gui.me.items.ItemRepo", remap = false)
public abstract class LegacyAe2ItemRepoMixin {
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void polyglottooltip$register(CallbackInfo ci) {
        LegacyAe2RepoRefresh.register((Repo<?>) (Object) this);
    }

    @Group(name = "polyglottooltip$itemMatcher", min = 1)
    @WrapOperation(
            method = "matchesSearch(Lappeng/client/gui/me/common/Repo$SearchMode;Ljava/util/regex/Pattern;Lappeng/api/storage/data/IAEItemStack;)Z",
            at = @At(value = "INVOKE", target = "Ljava/util/regex/Pattern;matcher(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;"),
            require = 0,
            remap = false
    )
    private Matcher polyglottooltip$matchNative(Pattern pattern, CharSequence nativeText, Operation<Matcher> original,
                                                 @Coerce Object mode, Pattern ignored, IAEItemStack stack) {
        return polyglottooltip$match(pattern, nativeText, original, mode, stack);
    }

    @Group(name = "polyglottooltip$itemMatcher", min = 1)
    @WrapOperation(
            method = "matchesSearch(Lappeng/client/gui/me/common/Repo$SearchMode;Ljava/util/regex/Pattern;Lappeng/api/storage/data/IAEItemStack;)Z",
            at = @At(value = "INVOKE", target = "Lme/towdium/jecharacters/utils/Match;matcher(Ljava/util/regex/Pattern;Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;"),
            require = 0,
            remap = false
    )
    private Matcher polyglottooltip$matchJec(Pattern pattern, CharSequence nativeText, Operation<Matcher> original,
                                              @Coerce Object mode, Pattern ignored, IAEItemStack stack) {
        return polyglottooltip$match(pattern, nativeText, original, mode, stack);
    }

    private Matcher polyglottooltip$match(Pattern pattern, CharSequence nativeText, Operation<Matcher> original,
                                           @Coerce Object mode, IAEItemStack stack) {
        Matcher nativeMatcher = original.call(pattern, nativeText);
        if ("MOD".equals(String.valueOf(mode))) {
            return nativeMatcher;
        }
        if (nativeMatcher.find()) {
            return Pattern.compile(".*", Pattern.DOTALL).matcher("");
        }
        boolean scriptEnabled = ChineseScriptSearchMatcher.isEnabled();
        for (String variant : ChineseScriptPolicy.scriptVariants(nativeText.toString(), scriptEnabled)) {
            if (!variant.contentEquals(nativeText) && original.call(pattern, variant).find()) {
                return Pattern.compile(".*", Pattern.DOTALL).matcher("");
            }
        }
        Set<String> secondary = new LinkedHashSet<>();
        AEItemStack itemStack = (AEItemStack) stack;
        Component displayName = itemStack.getDisplayName();
        if (displayName.getString().contentEquals(nativeText)) {
            secondary.addAll(LanguageCache.getInstance().resolveSearchNamesForAll(stack.getDefinition()));
        }
        if ("NAME_OR_TOOLTIP".equals(String.valueOf(mode))) {
            for (Component tooltipLine : itemStack.getToolTip()) {
                if (tooltipLine.getString().contentEquals(nativeText)) {
                    secondary.addAll(LanguageCache.getInstance().resolveComponentsForAll(tooltipLine));
                }
            }
        }
        for (String name : secondary) {
            for (String variant : ChineseScriptPolicy.scriptVariants(name, scriptEnabled)) {
                if (original.call(pattern, variant).find()) {
                    return Pattern.compile(".*", Pattern.DOTALL).matcher("");
                }
            }
        }
        return nativeMatcher;
    }
}
