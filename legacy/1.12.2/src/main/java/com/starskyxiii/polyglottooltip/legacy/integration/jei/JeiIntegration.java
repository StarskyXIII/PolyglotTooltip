package com.starskyxiii.polyglottooltip.legacy.integration.jei;

import com.starskyxiii.polyglottooltip.legacy.integration.Integration;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class JeiIntegration implements Integration {
    public static final String ID = "jei";
    public static final String MODID = "jei";

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
        MinecraftForge.EVENT_BUS.register(this);
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
