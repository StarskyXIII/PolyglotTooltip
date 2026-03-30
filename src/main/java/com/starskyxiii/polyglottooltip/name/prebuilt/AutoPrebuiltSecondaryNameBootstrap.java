package com.starskyxiii.polyglottooltip.name.prebuilt;

import com.starskyxiii.polyglottooltip.PolyglotTooltip;
import com.starskyxiii.polyglottooltip.config.Config;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;

/**
 * Defers the automatic ManaMetal prebuild until the client has reached a stable
 * tick with a visible screen or world. This avoids building too early during init.
 */
public final class AutoPrebuiltSecondaryNameBootstrap {

    private static final int REQUIRED_STABLE_TICKS = 5;

    private boolean completed;
    private boolean running;
    private int stableTicks;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (completed || running || event == null || event.phase != TickEvent.Phase.END) {
            return;
        }
        if (PrebuiltSecondaryNameCache.isEmpty()) {
            if (!isReadyForPrebuild()) {
                stableTicks = 0;
                return;
            }

            stableTicks++;
            if (stableTicks < REQUIRED_STABLE_TICKS) {
                return;
            }

            runPrebuild();
            return;
        }

        completed = true;
    }

    private static boolean isReadyForPrebuild() {
        if (Config.displayLanguages == null || Config.displayLanguages.isEmpty()) {
            return false;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.gameSettings == null) {
            return false;
        }

        return minecraft.currentScreen != null || minecraft.theWorld != null;
    }

    private void runPrebuild() {
        running = true;
        try {
            PolyglotTooltip.LOG.info(
                "[PolyglotTooltips] ManaMetal prebuilt secondary name cache is missing; building it on the first stable client tick.");
            int count = PrebuiltSecondaryNameBuilder.rebuild();
            PolyglotTooltip.LOG.info(
                "[PolyglotTooltips] Built {} ManaMetal prebuilt secondary name entries on the first stable client tick.",
                count);
        } catch (Exception e) {
            PolyglotTooltip.LOG.warn(
                "[PolyglotTooltips] Failed to build ManaMetal prebuilt secondary name cache on the first stable client tick: {}",
                e.getMessage());
        } finally {
            completed = true;
            running = false;
        }
    }
}
