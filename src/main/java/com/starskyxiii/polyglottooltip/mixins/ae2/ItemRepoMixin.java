package com.starskyxiii.polyglottooltip.mixins.ae2;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.starskyxiii.polyglottooltip.SearchTextCollector;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.Platform;
import net.minecraft.item.ItemStack;

@Pseudo
@Mixin(targets = "appeng.client.me.ItemRepo", remap = false)
public abstract class ItemRepoMixin {

    @Redirect(
        method = "updateView",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/util/Platform;getItemDisplayName(Ljava/lang/Object;)Ljava/lang/String;",
            ordinal = 0,
            remap = false),
        require = 0,
        remap = false)
    private String polyglot$expandSearchableNames(Object stack) {
        ItemStack itemStack = extractItemStack(stack);
        if (itemStack == null) {
            return Platform.getItemDisplayName(stack);
        }

        List<String> searchableNames = SearchTextCollector.collectSearchableNames(itemStack);
        if (searchableNames.isEmpty()) {
            return Platform.getItemDisplayName(stack);
        }

        return String.join("\n", searchableNames);
    }

    private static ItemStack extractItemStack(Object stack) {
        if (stack instanceof ItemStack) {
            return (ItemStack) stack;
        }

        if (stack instanceof IAEItemStack) {
            return ((IAEItemStack) stack).getItemStack();
        }

        return null;
    }
}
