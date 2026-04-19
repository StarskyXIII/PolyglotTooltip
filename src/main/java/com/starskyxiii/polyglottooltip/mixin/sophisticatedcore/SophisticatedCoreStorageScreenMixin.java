package com.starskyxiii.polyglottooltip.mixin.sophisticatedcore;

import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.search.WrappedSearchMatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase", remap = false)
public abstract class SophisticatedCoreStorageScreenMixin {

    @Shadow
    private Predicate<ItemStack> stackFilter;

    @Inject(method = "updateSearchFilter(Ljava/lang/String;)V", at = @At("TAIL"))
    private void polyglottooltip$useSecondaryLanguageItemSearch(String searchPhrase, CallbackInfo ci) {
        if (searchPhrase.trim().isEmpty()) {
            stackFilter = stack -> true;
            return;
        }

        String[] searchTerms = searchPhrase.trim().split(" ");
        List<Predicate<ItemStack>> filters = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();

        for (String searchTerm : searchTerms) {
            if (searchTerm.startsWith("@")) {
                String modName = searchTerm.substring(1).toLowerCase(Locale.ROOT);
                filters.add(stack -> modName.isEmpty()
                        || BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().contains(modName));
            } else if (searchTerm.startsWith("#")) {
                String tooltipKeyword = searchTerm.substring(1).toLowerCase(Locale.ROOT);
                filters.add(stack -> Screen.getTooltipFromItem(minecraft, stack).stream()
                        .anyMatch(line -> line.getString().toLowerCase(Locale.ROOT).contains(tooltipKeyword)));
            } else {
                filters.add(stack -> polyglottooltip$matchesItemName(stack, searchTerm));
            }
        }

        stackFilter = stack -> !stack.isEmpty() && filters.stream().allMatch(filter -> filter.test(stack));
    }

    private static boolean polyglottooltip$matchesItemName(ItemStack stack, String searchTerm) {
        String primaryName = stack.getHoverName().getString();
        String lowerCaseTerm = searchTerm.toLowerCase(Locale.ROOT);
        return WrappedSearchMatcher.matches(
                searchTerm,
                primaryName,
                () -> primaryName.toLowerCase(Locale.ROOT).contains(lowerCaseTerm),
                () -> LanguageCache.getInstance().resolveSearchNamesForAll(stack)
        );
    }
}
