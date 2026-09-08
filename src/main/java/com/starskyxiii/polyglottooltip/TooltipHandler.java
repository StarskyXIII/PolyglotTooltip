package com.starskyxiii.polyglottooltip;
//~ forge_neoforge_package

import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
//? if >1.20.1 {
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.StringDecomposer;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
//? if <=1.20.1 {
/*import net.minecraft.world.item.enchantment.EnchantmentHelper;
*///?} else {
import net.minecraft.world.item.enchantment.ItemEnchantments;
//?}
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
//? if <=1.20.1 {
/*import net.neoforged.fml.common.Mod;
*///?} else {
import net.neoforged.fml.common.EventBusSubscriber;
//?}
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
//? if >=26 {
/*import net.neoforged.neoforge.event.entity.player.FluidTooltipEvent;
*///?}
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles tooltip post-processing for item stacks by:
 * <ol>
 *   <li>Inserting one formatted secondary-language item name line per configured
 *       language directly below the primary item name.</li>
 *   <li>Appending secondary-language enchantment names to existing enchantment
 *       lines.</li>
 *   <li>Reordering generated tooltip components so secondary-name lines stay
 *       directly below the primary item name.</li>
 * </ol>
 */
//? if <=1.20.1 {
/*@Mod.EventBusSubscriber(modid = PolyglotTooltip.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
*///?} else {
@EventBusSubscriber(modid = PolyglotTooltip.MODID, value = Dist.CLIENT)
//?}
public class TooltipHandler {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemTooltipAppender.append(event.getItemStack(), event.getToolTip());
    }

    //? if >=26 {
    /*@SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFluidTooltip(FluidTooltipEvent event) {
        if (!SecondaryTooltipUtil.shouldShowSecondaryLanguage()) return;
        SecondaryTooltipUtil.insertSecondaryName(event.getToolTip(), event.getFluidStack().getHoverName());
    }
    *///?}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGatherTooltipComponents(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !SecondaryTooltipUtil.shouldShowSecondaryLanguage()) return;

        List<String> names = SecondaryTooltipUtil.getSecondaryNames(stack);
        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();

        // Keep generated secondary-name lines grouped directly under the title.
        List<Either<FormattedText, TooltipComponent>> secondaryLines = new ArrayList<>();
        for (String secondary : names) {
            int idx = findTooltipTextIndex(elements, secondary);
            if (idx >= 0) {
                secondaryLines.add(elements.remove(idx));
            }
        }

        int targetIndex = findInsertionIndexAfterTitleAttachments(elements);
        for (int i = secondaryLines.size() - 1; i >= 0; i--) {
            elements.add(targetIndex, secondaryLines.get(i));
        }
    }

    private static int findTooltipTextIndex(List<Either<FormattedText, TooltipComponent>> elements, String targetText) {
        for (int i = 0; i < elements.size(); i++) {
            Either<FormattedText, TooltipComponent> element = elements.get(i);
            if (element.left().isPresent()) {
                String text = StringDecomposer.getPlainText(element.left().get());
                if (targetText.equals(text)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int findInsertionIndexAfterTitleAttachments(List<Either<FormattedText, TooltipComponent>> elements) {
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i).left().isPresent()) {
                int insertAt = Math.min(i + 1, elements.size());
                while (insertAt < elements.size() && isTitleAttachedTooltip(elements.get(insertAt))) {
                    insertAt++;
                }
                return insertAt;
            }
        }
        return 0;
    }

    private static boolean isTitleAttachedTooltip(Either<FormattedText, TooltipComponent> element) {
        return element.right()
                .map(component -> "com.hollingsworth.arsnouveau.client.gui.SchoolTooltip".equals(component.getClass().getName()))
                .orElse(false);
    }
}
