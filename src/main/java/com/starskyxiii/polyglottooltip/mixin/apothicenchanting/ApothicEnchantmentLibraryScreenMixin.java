package com.starskyxiii.polyglottooltip.mixin.apothicenchanting;
//~ gui_graphics_extractor

import com.starskyxiii.polyglottooltip.integration.apothicenchanting.ApothicEnchantmentLibraryUtil;
//? if <=1.20.1 {
/*import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
*///?} else {
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
//?}
import net.minecraft.client.gui.components.EditBox;
//? if <=1.19.2 {
/*import com.mojang.blaze3d.vertex.PoseStack;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
//? if <=1.19.2 {
/*import org.spongepowered.asm.mixin.injection.Redirect;
*///?} else if <=1.20.1 {
/*import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?} else {
import org.spongepowered.asm.mixin.injection.Redirect;
//?}
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Pseudo
//? if <=1.19.2 {
/*@Mixin(targets = "shadows.apotheosis.ench.library.EnchLibraryScreen", remap = false)
*///?} else if <=1.20.1 {
/*@Mixin(targets = "dev.shadowsoffire.apotheosis.ench.library.EnchLibraryScreen", remap = false)
*///?} else {
@Mixin(targets = "dev.shadowsoffire.apothic_enchanting.library.EnchLibraryScreen", remap = false)
//?}
public abstract class ApothicEnchantmentLibraryScreenMixin {

    @Shadow
    protected EditBox filter;

    //? if <=1.20.1 {
    /*@Inject(method = "isAllowedBySearch", at = @At("RETURN"), cancellable = true, remap = false)
    private void polyglottooltip$matchSecondaryLanguageNames(Entry<Enchantment> entry,
                                                             CallbackInfoReturnable<Boolean> cir) {
        polyglottooltip$matchSecondaryLanguageName(
                ApothicEnchantmentLibraryUtil.getEnchantmentDisplayName(entry.getKey()), cir);
    }
    *///?} else {
    @Inject(method = "isAllowedBySearch", at = @At("RETURN"), cancellable = true, remap = false)
    private void polyglottooltip$matchSecondaryLanguageNames(Object2IntMap.Entry<Holder<Enchantment>> entry,
                                                             CallbackInfoReturnable<Boolean> cir) {
        polyglottooltip$matchSecondaryLanguageName(entry.getKey().value().description(), cir);
    }
    //?}

    private void polyglottooltip$matchSecondaryLanguageName(Component enchantmentName,
                                                            CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return;
        }

        String query = this.filter == null ? "" : this.filter.getValue();
        if (ApothicEnchantmentLibraryUtil.matchesSearch(query, enchantmentName)) {
            cir.setReturnValue(true);
        }
    }

    //? if <=1.19.2 {
    /*@Redirect(
            method = {"renderTooltip", "m_7025_"},
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;add(Ljava/lang/Object;)Z",
                    ordinal = 0
            ),
            remap = false
    )
    private boolean polyglottooltip$insertSecondaryNameIntoLegacyTooltip(List<FormattedText> list,
                                                                         Object element,
                                                                         PoseStack poseStack,
                                                                         int mouseX,
                                                                         int mouseY) {
        boolean added = list.add((FormattedText) element);
        ApothicEnchantmentLibraryUtil.getHoveredEnchantmentName(this, mouseX, mouseY)
                .map(enchantmentName -> ApothicEnchantmentLibraryUtil.insertSecondaryNameLines(list, enchantmentName, 160))
                .ifPresent(updated -> {
                    list.clear();
                    list.addAll(updated);
                });
        return added;
    }
    *///?} else if <=1.20.1 {
    /*private int polyglottooltip$tooltipMouseX;
    private int polyglottooltip$tooltipMouseY;

    @Inject(method = {"renderTooltip", "m_280072_"}, at = @At("HEAD"), remap = false)
    private void polyglottooltip$captureTooltipMousePosition(GuiGraphics gfx,
                                                             int mouseX,
                                                             int mouseY,
                                                             CallbackInfo ci) {
        this.polyglottooltip$tooltipMouseX = mouseX;
        this.polyglottooltip$tooltipMouseY = mouseY;
    }

    @ModifyArg(
            method = {"renderTooltip", "m_280072_"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;renderComponentTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/world/item/ItemStack;)V",
                    remap = false
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
    *///?} else {
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
    //?}
}
