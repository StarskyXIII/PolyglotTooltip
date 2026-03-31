package com.starskyxiii.polyglottooltip.name.prebuilt;

import java.util.ArrayList;
import java.util.List;

import com.starskyxiii.polyglottooltip.PolyglotTooltip;
import com.starskyxiii.polyglottooltip.config.Config;
import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCacheBuilder.BuildResult;
import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCacheBuilder.PendingBuild;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import net.minecraft.client.Minecraft;

/**
 * Automatically builds the full name cache in two phases, spread across loading
 * and the first few client ticks after the main menu appears.
 *
 * Phase 1 — FMLLoadCompleteEvent (loading screen still visible):
 *   Enumerates the item registry and expands all sub-item variants (~2.6s).
 *   All mods are fully initialized at this point; the cost is hidden by the
 *   existing loading screen.
 *
 * Phase 2 — ClientTickEvent, one language per tick (after main menu appears):
 *   Switches to each target language, resolves all items, switches back (~700ms/lang).
 *   Spreads the freeze across N short stutter events instead of one long block.
 *
 * Only triggers when:
 *   - Config.autoRebuildFullNameCache is true
 *   - FullNameCache.isEmpty() (no cache was loaded from disk at startup)
 */
public final class AutoFullNameCacheBootstrap {

    private static final int REQUIRED_STABLE_TICKS = 5;

    private enum State { IDLE, READY, RESOLVING, DONE }

    private State state = State.IDLE;
    private PendingBuild pending;
    private List<String> pendingLangs;
    private int langIndex = 0;
    private int stableTicks = 0;

    /**
     * Called from ClientProxy.loadComplete() (FMLLoadCompleteEvent).
     * Not a @SubscribeEvent — FML lifecycle events must go through @Mod.EventHandler.
     */
    public void onLoadComplete() {
        if (!Config.autoRebuildFullNameCache) {
            state = State.DONE;
            return;
        }
        if (!FullNameCache.isEmpty()) {
            PolyglotTooltip.LOG.info(
                "[PolyglotTooltips] Full name cache already loaded; skipping auto-build.");
            state = State.DONE;
            return;
        }
        if (Config.displayLanguages == null || Config.displayLanguages.isEmpty()) {
            PolyglotTooltip.LOG.warn(
                "[PolyglotTooltips] No display languages configured; skipping auto-build.");
            state = State.DONE;
            return;
        }

        PolyglotTooltip.LOG.info("[PolyglotTooltips] Auto-build: expanding items during load...");
        try {
            pending = FullNameCacheBuilder.startBuild("all");
            pendingLangs = new ArrayList<String>(Config.displayLanguages);
            state = State.READY;
            PolyglotTooltip.LOG.info(
                "[PolyglotTooltips] Auto-build: expand complete ({} stacks, {}ms). "
                + "Will resolve {} language(s) after main menu appears.",
                pending.expandedCount(), pending.expandMs, pendingLangs.size());
        } catch (Throwable t) {
            PolyglotTooltip.LOG.warn(
                "[PolyglotTooltips] Auto-build expand failed: {}", t.getMessage());
            state = State.DONE;
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (state == State.DONE || state == State.IDLE || event.phase != Phase.END) return;

        if (!isReady()) {
            stableTicks = 0;
            return;
        }
        if (++stableTicks < REQUIRED_STABLE_TICKS) return;

        // Process one language per tick to avoid a long freeze
        if (langIndex < pendingLangs.size()) {
            String lang = pendingLangs.get(langIndex++);
            PolyglotTooltip.LOG.info(
                "[PolyglotTooltips] Auto-build: resolving '{}' ({}/{})...",
                lang, langIndex, pendingLangs.size());
            try {
                FullNameCacheBuilder.resolveLanguage(pending, lang);
            } catch (Throwable t) {
                PolyglotTooltip.LOG.warn(
                    "[PolyglotTooltips] Auto-build: resolveLanguage failed for '{}': {}",
                    lang, t.getMessage());
                FullNameCacheBuilder.cancelBuild(pending);
                state = State.DONE;
            }
            state = State.RESOLVING;
            return;
        }

        // All languages resolved — write cache
        try {
            BuildResult result = FullNameCacheBuilder.finishBuild(pending);
            PolyglotTooltip.LOG.info(
                "[PolyglotTooltips] Auto-build complete. {}", result.toSummaryLine());
        } catch (Throwable t) {
            PolyglotTooltip.LOG.warn(
                "[PolyglotTooltips] Auto-build finishBuild failed: {}", t.getMessage());
            FullNameCacheBuilder.cancelBuild(pending);
        }
        state = State.DONE;
    }

    private static boolean isReady() {
        if (Config.displayLanguages == null || Config.displayLanguages.isEmpty()) return false;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null) return false;
        return mc.currentScreen != null || mc.theWorld != null;
    }
}
