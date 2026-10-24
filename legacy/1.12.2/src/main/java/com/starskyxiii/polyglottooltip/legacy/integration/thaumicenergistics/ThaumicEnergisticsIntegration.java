package com.starskyxiii.polyglottooltip.legacy.integration.thaumicenergistics;

import com.starskyxiii.polyglottooltip.legacy.integration.IngredientTooltips;
import com.starskyxiii.polyglottooltip.legacy.integration.Integration;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import thaumicenergistics.item.ItemDummyAspect;

public final class ThaumicEnergisticsIntegration implements Integration {
    public static final String ID = "thaumicenergistics";
    public static final String MODID = "thaumicenergistics";

    static volatile IntegrationGuard active;

    private final IntegrationGuard guard;

    public ThaumicEnergisticsIntegration(IntegrationGuard guard) {
        this.guard = guard;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String modid() {
        return MODID;
    }

    @Override
    public void start() {
        active = guard;
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void invalidate() {
        ThaumicEnergisticsSearch.TEXT.clear();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack != null && stack.func_77973_b() instanceof ItemDummyAspect) {
            IngredientTooltips.append(guard, event.getToolTip(),
                    () -> AspectNames.of(((ItemDummyAspect) stack.func_77973_b()).getAspect(stack)));
        }
    }
}
