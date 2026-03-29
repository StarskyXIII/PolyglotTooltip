package com.starskyxiii.polyglottooltip;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

public final class Ae2UnofficialInterfaceSearchHelper {

    private static final Set<String> LOGGED_REFLECTION_FAILURES = ConcurrentHashMap.newKeySet();
    private static final Map<Class<?>, Field> SECTION_NAME_FIELDS = new ConcurrentHashMap<Class<?>, Field>();
    private static final Set<Class<?>> MISSING_SECTION_NAME_FIELDS = ConcurrentHashMap.newKeySet();
    private static final Map<Class<?>, Field> SECTION_ENTRIES_FIELDS = new ConcurrentHashMap<Class<?>, Field>();
    private static final Set<Class<?>> MISSING_SECTION_ENTRIES_FIELDS = ConcurrentHashMap.newKeySet();
    private static final Map<Class<?>, Field> ENTRY_DISPLAY_NAME_FIELDS = new ConcurrentHashMap<Class<?>, Field>();
    private static final Set<Class<?>> MISSING_ENTRY_DISPLAY_NAME_FIELDS = ConcurrentHashMap.newKeySet();
    private static final Map<Class<?>, Field> ENTRY_SELF_REP_FIELDS = new ConcurrentHashMap<Class<?>, Field>();
    private static final Set<Class<?>> MISSING_ENTRY_SELF_REP_FIELDS = ConcurrentHashMap.newKeySet();
    private static final Map<Class<?>, Field> ENTRY_DISP_REP_FIELDS = new ConcurrentHashMap<Class<?>, Field>();
    private static final Set<Class<?>> MISSING_ENTRY_DISP_REP_FIELDS = ConcurrentHashMap.newKeySet();

    private Ae2UnofficialInterfaceSearchHelper() {}

    public static Boolean tryMatchSectionSearchTerm(Object section, String searchTerm) {
        if (searchTerm == null || searchTerm.isEmpty()) {
            return Boolean.TRUE;
        }

        LinkedHashSet<String> candidates = collectSectionCandidates(section);
        if (candidates.isEmpty()) {
            return null;
        }

        String normalizedQuery = searchTerm.toLowerCase(Locale.ROOT);
        if (normalizedQuery.length() >= 2 && normalizedQuery.startsWith("\"") && normalizedQuery.endsWith("\"")) {
            String exactQuery = normalizedQuery.substring(1, normalizedQuery.length() - 1);
            for (String candidate : candidates) {
                if (candidate.contains(exactQuery)) {
                    return Boolean.TRUE;
                }
            }
            return Boolean.FALSE;
        }

        String[] terms = normalizedQuery.split("\\s+");
        for (String candidate : candidates) {
            boolean matches = true;
            for (String term : terms) {
                if (!candidate.contains(term)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return Boolean.TRUE;
            }
        }

        return Boolean.FALSE;
    }

    private static LinkedHashSet<String> collectSectionCandidates(Object section) {
        LinkedHashSet<String> candidates = new LinkedHashSet<String>();
        if (section == null) {
            return candidates;
        }

        addText(candidates, getFieldValueAsString(
            section,
            "name",
            SECTION_NAME_FIELDS,
            MISSING_SECTION_NAME_FIELDS));

        Object entriesObject = getFieldValue(
            section,
            "entries",
            SECTION_ENTRIES_FIELDS,
            MISSING_SECTION_ENTRIES_FIELDS);
        if (entriesObject instanceof List) {
            for (Object entry : (List<?>) entriesObject) {
                addText(candidates, getFieldValueAsString(
                    entry,
                    "dispName",
                    ENTRY_DISPLAY_NAME_FIELDS,
                    MISSING_ENTRY_DISPLAY_NAME_FIELDS));
                addItemNames(candidates, getFieldValue(
                    entry,
                    "selfRep",
                    ENTRY_SELF_REP_FIELDS,
                    MISSING_ENTRY_SELF_REP_FIELDS));
                addItemNames(candidates, getFieldValue(
                    entry,
                    "dispRep",
                    ENTRY_DISP_REP_FIELDS,
                    MISSING_ENTRY_DISP_REP_FIELDS));
            }
        }

        return candidates;
    }

    private static void addItemNames(Set<String> candidates, Object itemObject) {
        if (!(itemObject instanceof ItemStack)) {
            return;
        }

        for (String name : SearchTextCollector.collectSearchableNames((ItemStack) itemObject)) {
            addText(candidates, name);
        }
    }

    private static void addText(Set<String> candidates, String text) {
        if (text == null) {
            return;
        }

        String normalized = EnumChatFormatting.getTextWithoutFormattingCodes(text);
        if (normalized == null) {
            return;
        }

        normalized = normalized.trim();
        if (normalized.isEmpty()) {
            return;
        }

        for (String variant : ChineseScriptSearchMatcher.getSearchVariants(normalized)) {
            String lowered = variant == null ? "" : variant.trim().toLowerCase(Locale.ROOT);
            if (!lowered.isEmpty()) {
                candidates.add(lowered);
            }
        }
    }

    private static String getFieldValueAsString(
        Object target,
        String fieldName,
        Map<Class<?>, Field> cache,
        Set<Class<?>> missing) {
        Object value = getFieldValue(target, fieldName, cache, missing);
        return value instanceof String ? (String) value : null;
    }

    private static Object getFieldValue(
        Object target,
        String fieldName,
        Map<Class<?>, Field> cache,
        Set<Class<?>> missing) {
        if (target == null) {
            return null;
        }

        Field field = resolveField(target.getClass(), fieldName, cache, missing);
        if (field == null) {
            return null;
        }

        try {
            return field.get(target);
        } catch (Exception ignored) {
            logReflectionFailureOnce(
                "access:" + target.getClass().getName() + "#" + fieldName,
                "AE2 Unofficial Interface search could not read {}#{}; falling back to original search if needed.",
                target.getClass().getName(),
                fieldName);
            return null;
        }
    }

    private static Field resolveField(
        Class<?> owner,
        String fieldName,
        Map<Class<?>, Field> cache,
        Set<Class<?>> missing) {
        Field cached = cache.get(owner);
        if (cached != null) {
            return cached;
        }

        if (missing.contains(owner)) {
            return null;
        }

        try {
            Field field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
            cache.put(owner, field);
            return field;
        } catch (Exception ignored) {
            missing.add(owner);
            logReflectionFailureOnce(
                "missing:" + owner.getName() + "#" + fieldName,
                "AE2 Unofficial Interface search could not resolve {}#{}; falling back to original search if needed.",
                owner.getName(),
                fieldName);
            return null;
        }
    }

    private static void logReflectionFailureOnce(String key, String message, Object arg1, Object arg2) {
        if (LOGGED_REFLECTION_FAILURES.add(key)) {
            PolyglotTooltip.LOG.debug(message, arg1, arg2);
        }
    }
}
