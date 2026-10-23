package com.starskyxiii.polyglottooltip.legacy.integration.ae2;

import com.starskyxiii.polyglottooltip.legacy.integration.Integration;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class Ae2Integration implements Integration {
    public static final String ID = "ae2";
    public static final String MODID = "appliedenergistics2";

    static volatile IntegrationGuard active;

    private final IntegrationGuard guard;
    private volatile boolean stale;

    public Ae2Integration(IntegrationGuard guard) {
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
        Ae2Search.TEXT.clear();
        stale = true;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && stale) {
            stale = false;
            guard.run(Ae2Search::refreshOpenTerminal);
        }
    }
}
