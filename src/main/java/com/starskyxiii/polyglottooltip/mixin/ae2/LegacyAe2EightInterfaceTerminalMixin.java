package com.starskyxiii.polyglottooltip.mixin.ae2;
//~ minecraft_116_packages

import appeng.client.gui.widgets.AETextField;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.starskyxiii.polyglottooltip.integration.ae2.LegacyAe2InterfaceSearch;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Pseudo
@Mixin(targets = "appeng.client.gui.me.interfaceterminal.InterfaceTerminalScreen", remap = false)
public class LegacyAe2EightInterfaceTerminalMixin implements LegacyAe2InterfaceSearch.Refreshable {
    @Shadow @Final
    private Map<String, ?> cachedSearches;

    @Shadow
    private AETextField searchField;

    @Shadow
    private void refreshList() {
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 1)
    private void polyglot$registerSearch(CallbackInfo ci) {
        LegacyAe2InterfaceSearch.register(this);
    }

    @Override
    public void polyglot$refreshSearch() {
        cachedSearches.clear();
        if (searchField != null) {
            refreshList();
        }
    }

    @Group(name = "polyglot_pattern_output", min = 1, max = 1)
    @WrapOperation(
            method = "itemStackMatchesSearchTerm(Lnet/minecraft/item/ItemStack;Ljava/lang/String;)Z",
            at = @At(value = "INVOKE", target = "Ljava/lang/String;contains(Ljava/lang/CharSequence;)Z"),
            remap = false,
            require = 0
    )
    private boolean polyglot$matchOutput(String text, CharSequence query, Operation<Boolean> original,
                                         @Local(ordinal = 1) ItemStack output) {
        return LegacyAe2InterfaceSearch.matchesOutput(text, query, output,
                (candidate, needle) -> original.call(candidate, needle));
    }

    @Group(name = "polyglot_pattern_output", min = 1, max = 1)
    @WrapOperation(
            method = "itemStackMatchesSearchTerm(Lnet/minecraft/item/ItemStack;Ljava/lang/String;)Z",
            at = @At(value = "INVOKE", target = "Lme/towdium/jecharacters/utils/Match;contains(Ljava/lang/String;Ljava/lang/CharSequence;)Z"),
            remap = false,
            require = 0
    )
    private boolean polyglot$matchOutputJec(String text, CharSequence query, Operation<Boolean> original,
                                            @Local(ordinal = 1) ItemStack output) {
        return LegacyAe2InterfaceSearch.matchesOutput(text, query, output,
                (candidate, needle) -> original.call(candidate, needle));
    }
}
