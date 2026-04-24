package com.starskyxiii.polyglottooltip.mixin.apothicenchanting;

import com.starskyxiii.polyglottooltip.integration.apothicenchanting.ApothicEnchantmentLibraryUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Pseudo
@Mixin(targets = "dev.shadowsoffire.apotheosis.ench.library.EnchLibraryScreen", remap = false)
public abstract class ApothicEnchantmentLibraryScreenMixin {

    @Shadow
    protected EditBox filter;

    @Inject(method = "isAllowedBySearch", at = @At("RETURN"), cancellable = true, remap = false)
    private void polyglottooltip$matchSecondaryLanguageNames(Entry<Enchantment> entry,
                                                             CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return;
        }

        String query = this.filter == null ? "" : this.filter.getValue();
        if (ApothicEnchantmentLibraryUtil.matchesSearch(
                query,
                ApothicEnchantmentLibraryUtil.getEnchantmentDisplayName(entry.getKey())
        )) {
            cir.setReturnValue(true);
        }
    }

    private int polyglottooltip$tooltipMouseX;
    private int polyglottooltip$tooltipMouseY;

    @Inject(method = {"renderTooltip", "m_280072_"}, at = @At("HEAD"), remap = false)
    private void polyglottooltip$captureTooltipMousePosition(GuiGraphics gfx,
                                                             int mouseX,
                                                             int mouseY,
                                                             org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        this.polyglottooltip$tooltipMouseX = mouseX;
        this.polyglottooltip$tooltipMouseY = mouseY;
    }

    @ModifyArg(
            method = {"renderTooltip", "m_280072_"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;renderComponentTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/world/item/ItemStack;)V",
                    remap = true
            ),
            index = 1,
            remap = false
    )
    private List<FormattedText> polyglottooltip$insertSecondaryNameIntoTooltip(List<FormattedText> list) {
        int wrapWidth = 160;
        return ApothicEnchantmentLibraryUtil.getHoveredEnchantmentName(
                        this,
                        this.polyglottooltip$tooltipMouseX,
                        this.polyglottooltip$tooltipMouseY
                )
                .map(enchantmentName -> ApothicEnchantmentLibraryUtil.insertSecondaryNameLines(list, enchantmentName, wrapWidth))
                .orElse(list);
    }
}
