package com.starskyxiii.polyglottooltip.mixin.occultism;

//? if neoforge && >=26 {
/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.starskyxiii.polyglottooltip.integration.SearchScreenRefresh;
import com.starskyxiii.polyglottooltip.integration.occultism.OccultismSearchUtil;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "com.klikli_dev.occultism.client.gui.storage.StorageControllerGuiBase", remap = false)
public abstract class OccultismModernStorageControllerGuiMixin implements SearchScreenRefresh.Refreshable {
    @Shadow
    protected abstract void resetDisplayCaches();

    @ModifyReturnValue(method = "itemMatchesSearch(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"))
    private boolean polyglot$matchSecondaryItemName(boolean original, ItemStack stack,
                                                  @Local(ordinal = 0) String searchText) {
        return original || OccultismSearchUtil.matchesItemSearch(searchText, stack);
    }

    @ModifyReturnValue(
            method = "machineMatchesSearch(Lcom/klikli_dev/occultism/api/common/data/MachineReference;)Z",
            at = @At("RETURN")
    )
    private boolean polyglot$matchSecondaryMachineName(boolean original, @Coerce Object machine,
                                                     @Local(ordinal = 0) String searchText) {
        return original || OccultismSearchUtil.matchesMachineSearch(searchText, machine);
    }

    @Override
    public void polyglot$refreshSearch() {
        resetDisplayCaches();
    }
}
*///?}
