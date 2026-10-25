package com.starskyxiii.polyglottooltip.legacy.integration.controlling;

import com.starskyxiii.polyglottooltip.legacy.integration.Integration;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class ControllingIntegration implements Integration {
    public static final String ID = "controlling";
    public static final String MODID = "controlling";

    static volatile IntegrationGuard active;

    private final IntegrationGuard guard;
    private volatile boolean stale;

    public ControllingIntegration(IntegrationGuard guard) {
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
        stale = true;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && stale) {
            stale = false;
            guard.run(ControllingSearch::refreshOpenScreen);
        }
    }
}
