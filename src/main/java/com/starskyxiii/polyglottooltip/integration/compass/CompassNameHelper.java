package com.starskyxiii.polyglottooltip.integration.compass;
//~ minecraft_116_packages minecraft_identifier minecraft_util_package minecraft_legacy_components minecraft_118_factories

import com.starskyxiii.polyglottooltip.minecraft.ComponentFactory;
import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class CompassNameHelper {

    private CompassNameHelper() {
    }

    public static String appendBiomeSecondaryNames(ResourceLocation biomeKey, String primaryText) {
        return appendTranslatedSecondaryNames("biome", biomeKey, primaryText, CompassNameHelper::formatBiomeName);
    }

    public static String appendStructureSecondaryNames(ResourceLocation structureKey, String primaryText) {
        return appendTranslatedSecondaryNames("structure", structureKey, primaryText, name -> name);
    }

    private static String appendTranslatedSecondaryNames(String category,
                                                         ResourceLocation key,
                                                         String primaryText,
                                                         java.util.function.UnaryOperator<String> formatter) {
        if (!SecondaryTooltipUtil.shouldShowSecondaryLanguage() || key == null || primaryText == null
                || primaryText.codePoints().allMatch(Character::isWhitespace)) {
            return primaryText;
        }

        Component translatedName = ComponentFactory.translatable(Util.makeDescriptionId(category, key));
        List<String> rawSecondaryNames = LanguageCache.getInstance().resolveComponentsForAll(translatedName);
        Set<String> secondaryNames = new LinkedHashSet<>();
        for (String rawSecondaryName : rawSecondaryNames) {
            if (rawSecondaryName == null || rawSecondaryName.codePoints().allMatch(Character::isWhitespace)) {
                continue;
            }

            String formatted = formatter.apply(rawSecondaryName);
            if (!formatted.codePoints().allMatch(Character::isWhitespace) && !formatted.equals(primaryText)) {
                secondaryNames.add(formatted);
            }
        }

        if (secondaryNames.isEmpty()) {
            return primaryText;
        }

        return primaryText + " (" + String.join(", ", secondaryNames) + ")";
    }

    private static String formatBiomeName(String name) {
        StringBuilder formatted = new StringBuilder(name.length() + 8);
        char previous = ' ';
        for (int i = 0; i < name.length(); i++) {
            char current = name.charAt(i);
            if (Character.isUpperCase(current) && Character.isLowerCase(previous) && Character.isAlphabetic(previous)) {
                formatted.append(' ');
            }
            formatted.append(current);
            previous = current;
        }
        return formatted.toString();
    }
}
