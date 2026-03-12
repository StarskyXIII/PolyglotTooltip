package com.starskyxiii.polyglottooltip.mixin.emi;

import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import com.starskyxiii.polyglottooltip.integration.emi.EmiTagNameHelper;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(targets = "dev.emi.emi.api.stack.TagEmiIngredient", remap = false)
public abstract class EmiTagEmiIngredientMixin {

    @Shadow
    @Final
    private TagKey<?> key;

    @Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true, remap = false)
    private void onGetTooltip(CallbackInfoReturnable<List<ClientTooltipComponent>> cir) {
        List<Component> secondaryLines = SecondaryTooltipUtil.getSecondaryNameLines(EmiTagNameHelper.getTagName(key));
        if (secondaryLines.isEmpty()) {
            return;
        }

        List<ClientTooltipComponent> tooltip = new ArrayList<>(cir.getReturnValue());
        int insertAt = tooltip.isEmpty() ? 0 : 1;
        for (int i = secondaryLines.size() - 1; i >= 0; i--) {
            tooltip.add(insertAt, ClientTooltipComponent.create(secondaryLines.get(i).getVisualOrderText()));
        }
        cir.setReturnValue(tooltip);
    }
}
