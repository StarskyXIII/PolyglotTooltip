package com.starskyxiii.polyglottooltip.mixin.projecte;

//? if neoforge && >=1.21.1 && <=1.21.1 {
import com.starskyxiii.polyglottooltip.integration.SearchScreenRefresh;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "moze_intel.projecte.gameObjs.gui.GUITransmutation", remap = false)
public abstract class ProjectESearchRefreshMixin implements SearchScreenRefresh.Refreshable {
    @Shadow
    private moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory inv;

    @Override
    public void polyglot$refreshSearch() {
        if (inv != null) {
            inv.updateClientTargets(false);
        }
    }
}
//?}

