package com.starskyxiii.polyglottooltip.legacy.integration.jei;

import java.util.List;

import com.starskyxiii.polyglottooltip.legacy.integration.IngredientTooltips;
import com.starskyxiii.polyglottooltip.legacy.integration.Integration;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.minecraft.FluidSecondaryNames;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class JeiIntegration implements Integration {
    public static final String ID = "jei";
    public static final String MODID = "jei";

    private static volatile IntegrationGuard active;

    private final IntegrationGuard guard;
    private volatile boolean stale;

    public JeiIntegration(IntegrationGuard guard) {
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
        JeiSearch.attach(guard);
        active = guard;
        MinecraftForge.EVENT_BUS.register(this);
    }

    public static void fluidTooltip(FluidStack stack, List<String> lines) {
        IngredientTooltips.append(active, lines, () -> FluidSecondaryNames.names(stack));
    }

    @Override
    public void invalidate() {
        stale = true;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (stale) {
            stale = false;
            JeiSearch.refresh();
        }
        JeiSearch.indexPending();
    }
}
