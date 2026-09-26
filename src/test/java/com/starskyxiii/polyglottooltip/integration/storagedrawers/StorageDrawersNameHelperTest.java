package com.starskyxiii.polyglottooltip.integration.storagedrawers;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageDrawersNameHelperTest {

    @Test
    void prefersModernSecondaryTranslationOverLegacyAlias() {
        assertEquals(Optional.of("现代译名"), StorageDrawersNameHelper.preferTranslatedName(
                Optional.of("现代译名"), "日文抽屉", Optional.of("Oak Drawers 1x1"),
                Optional.of("基础抽屉")));
    }

    @Test
    void usesLegacyAliasWhenModernKeyFallsBackToPrimaryLanguage() {
        assertEquals(Optional.of("基础抽屉"), StorageDrawersNameHelper.preferTranslatedName(
                Optional.of("Oak Drawers 1x1"), "日文抽屉", Optional.of("Oak Drawers 1x1"),
                Optional.of("基础抽屉")));
    }

    @Test
    void retainsEnglishFallbackWhenLegacyAliasIsUnavailable() {
        assertEquals(Optional.of("Mangrove Trim"), StorageDrawersNameHelper.preferTranslatedName(
                Optional.of("Mangrove Trim"), "マングローブトリム", Optional.of("Mangrove Trim"),
                Optional.empty()));
    }
}
