package com.starskyxiii.polyglottooltip.mixin.occultism;

import com.starskyxiii.polyglottooltip.integration.occultism.OccultismSearchUtil;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.klikli_dev.occultism.client.gui.storage.StorageControllerGuiBase", remap = false)
public abstract class OccultismStorageControllerGuiMixin {

    @Shadow
    protected EditBox searchBar;

    @Inject(method = "itemMatchesSearch", at = @At("RETURN"), cancellable = true)
    private void polyglot$itemMatchesSearch(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            cir.setReturnValue(OccultismSearchUtil.matchesItemSearch(this.searchBar.getValue(), stack));
        }
    }

    @Inject(
            method = "machineMatchesSearch(Lcom/klikli_dev/occultism/api/common/data/MachineReference;)Z",
            at = @At("RETURN"),
            cancellable = true
    )
    private void polyglot$machineMatchesSearch(@Coerce Object machine, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            cir.setReturnValue(OccultismSearchUtil.matchesMachineSearch(this.searchBar.getValue(), machine));
        }
    }
}
