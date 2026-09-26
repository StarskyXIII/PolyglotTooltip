package com.starskyxiii.polyglottooltip.mixin.industrialforegoing;

import com.starskyxiii.polyglottooltip.integration.industrialforegoing.IndustrialForegoingNameHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "com.buuz135.industrial.item.addon.ProcessingAddonItem", remap = false)
public abstract class LegacyIndustrialForegoingProcessingAddonItemMixin extends Item {

    @Shadow private int tier;

    protected LegacyIndustrialForegoingProcessingAddonItemMixin() {
        super(new Item.Properties());
    }

    @Override
    public Component getName(ItemStack stack) {
        return IndustrialForegoingNameHelper.createAddonName("item.industrialforegoing.processing", tier);
    }
}
