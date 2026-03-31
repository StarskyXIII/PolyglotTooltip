package com.starskyxiii.polyglottooltip.i18n;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.Language;
import net.minecraft.client.resources.LanguageManager;

import com.starskyxiii.polyglottooltip.PolyglotTooltip;

/**
 * Language switching utility for prebuild operations.
 *
 * Two paths:
 *
 * FAST — calls LanguageManager.onResourceManagerReload() directly after setting
 * the current language. Reloads locale data + merges FML LanguageRegistry +
 * updates StringTranslate. Skips textures, sounds, models. Much faster for
 * back-to-back language switches during a full-registry build.
 *
 * FULL — calls mc.refreshResources(), identical to the in-game Language settings button.
 * Correct but slow (reloads everything). Used as fallback if fast path fails.
 *
 * Both paths call LanguageCache.clear() so cached Locale objects are discarded
 * and re-loaded from the new language's resource files on next use.
 *
 * IMPORTANT: Must only be called from explicit command paths, never from tooltip
 * or render paths.
 */
public final class LanguageSwitcher {

    public enum SwitchResult {
        FAST,
        FULL,
        FAILED
    }

    private LanguageSwitcher() {}

    /**
     * Switches to the given language code.
     *
     * @param mc         Minecraft instance
     * @param languageCode target language (e.g. "zh_CN")
     * @param useFast    true to attempt the fast path first
     * @return which path was used
     */
    public static SwitchResult switchTo(Minecraft mc, String languageCode, boolean useFast) {
        if (mc == null || languageCode == null || languageCode.trim().isEmpty()) {
            return SwitchResult.FAILED;
        }
        String code = languageCode.trim();

        if (useFast) {
            boolean ok = tryFast(mc, code);
            if (ok) return SwitchResult.FAST;
            PolyglotTooltip.LOG.warn(
                "[PolyglotTooltips] Fast language switch failed for '{}'; falling back to full reload.", code);
            return tryFull(mc, code) ? SwitchResult.FULL : SwitchResult.FAILED;
        } else {
            return tryFull(mc, code) ? SwitchResult.FULL : SwitchResult.FAILED;
        }
    }

    // -------------------------------------------------------------------------
    // Fast path
    // -------------------------------------------------------------------------

    private static boolean tryFast(Minecraft mc, String code) {
        try {
            LanguageManager lm = mc.getLanguageManager();
            if (lm == null) return false;

            net.minecraft.client.resources.IResourceManager rm = mc.getResourceManager();
            if (rm == null) return false;

            Language target = findLanguage(lm, code);
            if (target == null) {
                PolyglotTooltip.LOG.warn(
                    "[PolyglotTooltips] Language '{}' not found in LanguageManager; fast switch aborted.", code);
                return false;
            }

            lm.setCurrentLanguage(target);
            mc.gameSettings.language = code;

            // Reloads locale data (en_US + target) and merges FML LanguageRegistry.
            // Updates StringTranslate without triggering texture/sound/model reloads.
            lm.onResourceManagerReload(rm);

            if (mc.fontRenderer != null) {
                mc.fontRenderer.setUnicodeFlag(lm.isCurrentLocaleUnicode() || mc.gameSettings.forceUnicodeFont);
                mc.fontRenderer.setBidiFlag(lm.isCurrentLanguageBidirectional());
            }

            LanguageCache.clear();
            return true;
        } catch (Throwable t) {
            PolyglotTooltip.LOG.warn("[PolyglotTooltips] Fast language switch threw an exception.", t);
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Full path
    // -------------------------------------------------------------------------

    private static boolean tryFull(Minecraft mc, String code) {
        try {
            LanguageManager lm = mc.getLanguageManager();
            if (lm == null) {
                PolyglotTooltip.LOG.warn(
                    "[PolyglotTooltips] LanguageManager is null; full language switch for '{}' aborted.", code);
                return false;
            }

            Language target = findLanguage(lm, code);
            if (target != null) {
                lm.setCurrentLanguage(target);
            } else {
                PolyglotTooltip.LOG.warn(
                    "[PolyglotTooltips] Language '{}' not found; using GameSettings fallback.", code);
            }
            mc.gameSettings.language = code;
            mc.refreshResources();
            if (mc.fontRenderer != null) {
                mc.fontRenderer.setUnicodeFlag(lm.isCurrentLocaleUnicode() || mc.gameSettings.forceUnicodeFont);
                mc.fontRenderer.setBidiFlag(lm.isCurrentLanguageBidirectional());
            }
            LanguageCache.clear();
            return true;
        } catch (Throwable t) {
            PolyglotTooltip.LOG.warn(
                "[PolyglotTooltips] Full language switch threw an exception for '{}'.", code, t);
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Language findLanguage(LanguageManager lm, String code) {
        if (lm == null || code == null) return null;
        for (Object obj : lm.getLanguages()) {
            if (obj instanceof Language) {
                Language lang = (Language) obj;
                if (code.equalsIgnoreCase(lang.getLanguageCode())) return lang;
            }
        }
        return null;
    }
}
