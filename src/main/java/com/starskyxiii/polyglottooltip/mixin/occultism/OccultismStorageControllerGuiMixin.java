package com.starskyxiii.polyglottooltip.mixin.occultism;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.starskyxiii.polyglottooltip.integration.occultism.OccultismSearchUtil;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "com.klikli_dev.occultism.client.gui.storage.StorageControllerGuiBase", remap = false)
public abstract class OccultismStorageControllerGuiMixin {

    @Shadow
    protected EditBox searchBar;

    @ModifyReturnValue(method = "itemMatchesSearch", at = @At("RETURN"))
    private boolean polyglot$matchSecondaryItemName(boolean original, ItemStack stack) {
        return original || OccultismSearchUtil.matchesItemSearch(this.searchBar.getValue(), stack);
    }

    @ModifyReturnValue(
            method = "machineMatchesSearch(Lcom/klikli_dev/occultism/api/common/data/MachineReference;)Z",
            at = @At("RETURN")
    )
    private boolean polyglot$matchSecondaryMachineName(boolean original, @Coerce Object machine) {
        return original || OccultismSearchUtil.matchesMachineSearch(this.searchBar.getValue(), machine);
    }
}
