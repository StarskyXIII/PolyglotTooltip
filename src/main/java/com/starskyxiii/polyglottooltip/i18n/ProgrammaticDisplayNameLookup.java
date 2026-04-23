package com.starskyxiii.polyglottooltip.i18n;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

/**
 * Resolves {@link ItemStack#getDisplayName()} against a target language without
 * changing the player's visible game language.
 *
 * <p>Callers can either:
 * <ul>
 *   <li>use {@link #beginScope(String)} to amortize one translation-map swap
 *       across many display-name lookups in a build slice, or</li>
 *   <li>call {@link #getItemDisplayName(ItemStack, String)} directly for a
 *       one-off lookup.</li>
 * </ul>
 *
 * <p>Resolvers that intentionally need the player's real current-language
 * display name can use {@link #getLiveLanguageDisplayName(ItemStack)} even
 * while a background scope is active.
 */
public final class ProgrammaticDisplayNameLookup {

    private static final Object LOCK = new Object();
    private static final ThreadLocal<ActiveScope> ACTIVE_SCOPE = new ThreadLocal<ActiveScope>();
    private static final ThreadLocal<LiveDisplayNameHints> LIVE_DISPLAY_NAME_HINTS =
        new ThreadLocal<LiveDisplayNameHints>();

    private static Field stringTranslateInstanceField;
    private static Field stringTranslateLanguageListField;
    private static boolean reflectionInitialized;

    private ProgrammaticDisplayNameLookup() {}

    public static TranslationScope beginScope(String languageCode) {
        return beginScope(languageCode, null);
    }

    public static TranslationScope beginScope(String languageCode, RestoreSnapshot restoreSnapshot) {
        String normalizedLanguage = normalizeLanguage(languageCode);
        String currentLanguageCode = getCurrentLanguageCode();
        if (normalizedLanguage == null || sameLanguage(normalizedLanguage, currentLanguageCode)) {
            return TranslationScope.noop();
        }

        ActiveScope activeScope = ACTIVE_SCOPE.get();
        if (activeScope != null && sameLanguage(activeScope.languageCode, normalizedLanguage)) {
            activeScope.depth++;
            return new TranslationScope(activeScope, true);
        }

        Map<String, String> targetTranslations = LanguageCache.snapshotTranslations(normalizedLanguage);
        if (targetTranslations == null || targetTranslations.isEmpty()) {
            return TranslationScope.noop();
        }

        synchronized (LOCK) {
            Map<String, String> liveTranslations = getLiveTranslations();
            if (liveTranslations == null) {
                return TranslationScope.noop();
            }

            Map<String, String> originalTranslations = activeScope == null
                ? resolveRestoreTranslations(restoreSnapshot, currentLanguageCode, liveTranslations)
                : new LinkedHashMap<String, String>(liveTranslations);
            ActiveScope newScope = new ActiveScope(
                normalizedLanguage,
                liveTranslations,
                originalTranslations,
                targetTranslations,
                activeScope);
            swapTranslations(liveTranslations, newScope.targetTranslations);
            ACTIVE_SCOPE.set(newScope);
            return new TranslationScope(newScope, false);
        }
    }

    public static RestoreSnapshot newRestoreSnapshot(String languageCode) {
        return new RestoreSnapshot(normalizeLanguage(languageCode));
    }

    public static String getItemDisplayName(ItemStack stack, String languageCode) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }

        String normalizedLanguage = normalizeLanguage(languageCode);
        ActiveScope activeScope = ACTIVE_SCOPE.get();
        if (activeScope != null) {
            if (normalizedLanguage == null || sameLanguage(normalizedLanguage, activeScope.languageCode)) {
                return safeGetDisplayName(stack);
            }
            if (sameLanguage(normalizedLanguage, getCurrentLanguageCode())) {
                return getLiveLanguageDisplayName(stack);
            }
        }

        if (normalizedLanguage == null || sameLanguage(normalizedLanguage, getCurrentLanguageCode())) {
            return safeGetDisplayName(stack);
        }

        Map<String, String> targetTranslations = LanguageCache.snapshotTranslations(normalizedLanguage);
        if (targetTranslations == null || targetTranslations.isEmpty()) {
            return safeGetDisplayName(stack);
        }

        synchronized (LOCK) {
            Map<String, String> liveTranslations = getLiveTranslations();
            if (liveTranslations == null) {
                return safeGetDisplayName(stack);
            }

            Map<String, String> originalTranslations = new LinkedHashMap<String, String>(liveTranslations);
            try {
                swapTranslations(liveTranslations, targetTranslations);
                return safeGetDisplayName(stack);
            } finally {
                swapTranslations(liveTranslations, originalTranslations);
            }
        }
    }

    public static LiveDisplayNameHintScope beginLiveDisplayNameHintScope(Map<ItemStack, String> hints) {
        if (hints == null || hints.isEmpty()) {
            return LiveDisplayNameHintScope.noop();
        }

        LiveDisplayNameHints activeHints = LIVE_DISPLAY_NAME_HINTS.get();
        if (activeHints != null && activeHints.hints == hints) {
            activeHints.depth++;
            return new LiveDisplayNameHintScope(activeHints, true);
        }

        LiveDisplayNameHints newHints = new LiveDisplayNameHints(hints, activeHints);
        LIVE_DISPLAY_NAME_HINTS.set(newHints);
        return new LiveDisplayNameHintScope(newHints, false);
    }

    public static String getLiveLanguageDisplayName(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }

        String hintedName = findHintedLiveDisplayName(stack);
        if (hintedName != null) {
            return hintedName;
        }

        ActiveScope activeScope = ACTIVE_SCOPE.get();
        if (activeScope == null) {
            return safeGetDisplayName(stack);
        }

        synchronized (LOCK) {
            try {
                swapTranslations(activeScope.liveTranslations, activeScope.originalTranslations);
                return safeGetDisplayName(stack);
            } finally {
                swapTranslations(activeScope.liveTranslations, activeScope.targetTranslations);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> getLiveTranslations() {
        initializeReflection();
        if (stringTranslateInstanceField == null || stringTranslateLanguageListField == null) {
            return null;
        }

        try {
            Object stringTranslateInstance = stringTranslateInstanceField.get(null);
            if (stringTranslateInstance == null) {
                return null;
            }

            Object liveTranslations = stringTranslateLanguageListField.get(stringTranslateInstance);
            return liveTranslations instanceof Map ? (Map<String, String>) liveTranslations : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static synchronized void initializeReflection() {
        if (reflectionInitialized) {
            return;
        }

        reflectionInitialized = true;
        stringTranslateInstanceField = findField(
            net.minecraft.util.StringTranslate.class,
            "instance",
            "field_74817_a");
        stringTranslateLanguageListField = findField(
            net.minecraft.util.StringTranslate.class,
            "languageList",
            "field_74816_c");
    }

    private static Field findField(Class<?> owner, String... candidateNames) {
        if (owner == null || candidateNames == null) {
            return null;
        }

        for (String candidateName : candidateNames) {
            if (candidateName == null || candidateName.isEmpty()) {
                continue;
            }

            try {
                Field field = owner.getDeclaredField(candidateName);
                field.setAccessible(true);
                return field;
            } catch (Exception ignored) {
                // Try next candidate.
            }
        }

        return null;
    }

    private static String safeGetDisplayName(ItemStack stack) {
        try {
            return stack.getDisplayName();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String findHintedLiveDisplayName(ItemStack stack) {
        LiveDisplayNameHints activeHints = LIVE_DISPLAY_NAME_HINTS.get();
        if (activeHints == null || activeHints.hints == null || stack == null) {
            return null;
        }

        String hintedName = activeHints.hints.get(stack);
        if (hintedName == null || hintedName.trim().isEmpty()) {
            return null;
        }
        return hintedName;
    }

    private static void swapTranslations(Map<String, String> liveTranslations, Map<String, String> replacements) {
        if (liveTranslations == null) {
            return;
        }

        liveTranslations.clear();
        if (replacements != null && !replacements.isEmpty()) {
            liveTranslations.putAll(replacements);
        }
    }

    private static String normalizeLanguage(String languageCode) {
        if (languageCode == null) {
            return null;
        }

        String normalized = languageCode.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static boolean sameLanguage(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static Map<String, String> resolveRestoreTranslations(RestoreSnapshot snapshot,
            String currentLanguageCode, Map<String, String> liveTranslations) {
        if (snapshot == null || !snapshot.isForLanguage(currentLanguageCode)) {
            return new LinkedHashMap<String, String>(liveTranslations);
        }

        if (snapshot.matches(liveTranslations)) {
            return snapshot.translations;
        }

        Map<String, String> refreshed = new LinkedHashMap<String, String>(liveTranslations);
        snapshot.update(currentLanguageCode, refreshed);
        return refreshed;
    }

    private static String getCurrentLanguageCode() {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft != null && minecraft.gameSettings != null && minecraft.gameSettings.language != null) {
                String languageCode = minecraft.gameSettings.language.trim();
                if (!languageCode.isEmpty()) {
                    return languageCode;
                }
            }
        } catch (Exception ignored) {
        }

        return "en_US";
    }

    private static final class ActiveScope {
        private final String languageCode;
        private final Map<String, String> liveTranslations;
        private final Map<String, String> originalTranslations;
        private final Map<String, String> targetTranslations;
        private final ActiveScope previous;
        private int depth;

        private ActiveScope(String languageCode, Map<String, String> liveTranslations,
                Map<String, String> originalTranslations, Map<String, String> targetTranslations,
                ActiveScope previous) {
            this.languageCode = languageCode;
            this.liveTranslations = liveTranslations;
            this.originalTranslations = originalTranslations;
            this.targetTranslations = targetTranslations;
            this.previous = previous;
            this.depth = 1;
        }
    }

    private static final class LiveDisplayNameHints {
        private final Map<ItemStack, String> hints;
        private final LiveDisplayNameHints previous;
        private int depth;

        private LiveDisplayNameHints(Map<ItemStack, String> hints, LiveDisplayNameHints previous) {
            this.hints = hints;
            this.previous = previous;
            this.depth = 1;
        }
    }

    public static final class RestoreSnapshot {
        private String languageCode;
        private Map<String, String> translations;

        private RestoreSnapshot(String languageCode) {
            this.languageCode = languageCode;
        }

        private boolean isForLanguage(String currentLanguageCode) {
            return languageCode != null
                && currentLanguageCode != null
                && sameLanguage(languageCode, currentLanguageCode);
        }

        private boolean matches(Map<String, String> liveTranslations) {
            return translations != null
                && !translations.isEmpty()
                && translations.equals(liveTranslations);
        }

        private void update(String currentLanguageCode, Map<String, String> refreshedTranslations) {
            languageCode = normalizeLanguage(currentLanguageCode);
            translations = refreshedTranslations;
        }
    }

    public static final class TranslationScope implements AutoCloseable {
        private final ActiveScope scope;
        private final boolean nested;
        private final boolean noop;
        private boolean closed;

        private TranslationScope(ActiveScope scope, boolean nested) {
            this.scope = scope;
            this.nested = nested;
            this.noop = false;
        }

        private TranslationScope() {
            this.scope = null;
            this.nested = false;
            this.noop = true;
        }

        private static TranslationScope noop() {
            return new TranslationScope();
        }

        public boolean isActive() {
            return !noop && scope != null;
        }

        @Override
        public void close() {
            if (closed || noop || scope == null) {
                return;
            }
            closed = true;

            ActiveScope activeScope = ACTIVE_SCOPE.get();
            if (activeScope != scope) {
                return;
            }

            if (nested && scope.depth > 1) {
                scope.depth--;
                return;
            }

            synchronized (LOCK) {
                swapTranslations(scope.liveTranslations, scope.originalTranslations);
                if (scope.previous == null) {
                    ACTIVE_SCOPE.remove();
                    return;
                }

                ACTIVE_SCOPE.set(scope.previous);
            }
        }
    }

    public static final class LiveDisplayNameHintScope implements AutoCloseable {
        private final LiveDisplayNameHints hints;
        private final boolean nested;
        private final boolean noop;
        private boolean closed;

        private LiveDisplayNameHintScope(LiveDisplayNameHints hints, boolean nested) {
            this.hints = hints;
            this.nested = nested;
            this.noop = false;
        }

        private LiveDisplayNameHintScope() {
            this.hints = null;
            this.nested = false;
            this.noop = true;
        }

        private static LiveDisplayNameHintScope noop() {
            return new LiveDisplayNameHintScope();
        }

        @Override
        public void close() {
            if (closed || noop || hints == null) {
                return;
            }
            closed = true;

            LiveDisplayNameHints activeHints = LIVE_DISPLAY_NAME_HINTS.get();
            if (activeHints != hints) {
                return;
            }

            if (nested && hints.depth > 1) {
                hints.depth--;
                return;
            }

            if (hints.previous == null) {
                LIVE_DISPLAY_NAME_HINTS.remove();
                return;
            }

            LIVE_DISPLAY_NAME_HINTS.set(hints.previous);
        }
    }
}
