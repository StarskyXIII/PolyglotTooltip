package com.starskyxiii.polyglottooltip.report;

/**
 * Quality classification for a collected display-name entry.
 *
 * Priority (highest to lowest during classification):
 * EMPTY       — null or blank after stripping formatting codes
 * RAW_KEY     — untranslated i18n key (item.* / tile.* / entity.* / block.*)
 * FORMAT_ONLY — consists only of formatting artifacts or is trivially short (≤1 char)
 * MIXED_LANGUAGE — non-CJK language but name contains both CJK chars and ≥2 Latin letters
 * CONTAINS_CJK   — non-CJK language but name contains CJK chars (no significant Latin mix)
 * GOOD        — passes all checks for the given language
 *
 * Note: MIXED_LANGUAGE and CONTAINS_CJK are only assigned for non-CJK languages.
 * For CJK languages (zh_CN, zh_TW, ja_JP, ko_KR) the presence of CJK chars is normal.
 */
public enum NameQuality {

    /** Name is usable: non-empty, not a raw key, no unexpected cross-language contamination. */
    GOOD,

    /** Empty or null after stripping Minecraft formatting codes and trimming. */
    EMPTY,

    /** Untranslated i18n key left as display name (item.*, tile.*, entity.*, block.*). */
    RAW_KEY,

    /**
     * Non-CJK language entry whose display name contains CJK characters AND
     * at least 2 Latin letters — indicates a mixed/partially-translated string.
     */
    MIXED_LANGUAGE,

    /**
     * Non-CJK language entry whose display name contains CJK characters but
     * does NOT have significant Latin content — the whole name appears to be
     * in the wrong language (e.g. a zh_CN name leaked into en_US).
     */
    CONTAINS_CJK,

    /**
     * Name is suspiciously short or consists only of non-letter characters
     * after all obvious content is stripped (≤1 printable character).
     */
    FORMAT_ONLY
}
