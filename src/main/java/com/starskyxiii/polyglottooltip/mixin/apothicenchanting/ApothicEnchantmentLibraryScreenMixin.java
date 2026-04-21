package com.starskyxiii.polyglottooltip.mixin.apothicenchanting;

import com.starskyxiii.polyglottooltip.integration.apothicenchanting.ApothicEnchantmentLibraryUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Pseudo
@Mixin(targets = "dev.shadowsoffire.apothic_enchanting.library.EnchLibraryScreen", remap = false)
public abstract class ApothicEnchantmentLibraryScreenMixin {

    @Shadow
    protected EditBox filter;

    @Inject(method = "isAllowedBySearch", at = @At("RETURN"), cancellable = true, remap = false)
    private void polyglottooltip$matchSecondaryLanguageNames(Object2IntMap.Entry<Holder<Enchantment>> entry,
                                                             CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return;
        }

        String query = this.filter == null ? "" : this.filter.getValue();
        if (ApothicEnchantmentLibraryUtil.matchesSearch(query, entry.getKey().value().description())) {
            cir.setReturnValue(true);
        }
    }

    @Redirect(
            method = "renderTooltip",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;add(Ljava/lang/Object;)Z",
                    ordinal = 0
            ),
            remap = false
    )
    private boolean polyglottooltip$insertSecondaryNameIntoTooltip(List<FormattedText> list,
                                                                   Object element,
                                                                   GuiGraphics gfx,
                                                                   int mouseX,
                                                                   int mouseY) {
        boolean added = list.add((FormattedText) element);
        int wrapWidth = 160;
        ApothicEnchantmentLibraryUtil.getHoveredEnchantmentName(this, mouseX, mouseY)
                .map(enchantmentName -> ApothicEnchantmentLibraryUtil.insertSecondaryNameLines(list, enchantmentName, wrapWidth))
                .ifPresent(updated -> {
                    list.clear();
                    list.addAll(updated);
                });
        return added;
    }
}
