package com.starskyxiii.polyglottooltip.tooltip;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;

import com.starskyxiii.polyglottooltip.config.Config;
import com.starskyxiii.polyglottooltip.name.DisplayNameResolver;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;

public final class SecondaryTooltipUtil {

    private static final char SECTION_SIGN = '\u00A7';
    private static final int MAX_INSERTED_LINE_CACHE_ENTRIES = 512;
    private static final Map<InsertedLineCacheKey, List<String>> INSERTED_SECONDARY_LINE_CACHE =
        new LinkedHashMap<InsertedLineCacheKey, List<String>>(64, 0.75F, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<InsertedLineCacheKey, List<String>> eldest) {
                return size() > MAX_INSERTED_LINE_CACHE_ENTRIES;
            }
        };

    private SecondaryTooltipUtil() {}

    public static void clearInsertedLineCache() {
        synchronized (INSERTED_SECONDARY_LINE_CACHE) {
            INSERTED_SECONDARY_LINE_CACHE.clear();
        }
    }

    public static void insertSecondaryNames(List<String> tooltip, ItemStack stack) {
        insertSecondaryNames(tooltip, stack, Config.secondaryNameColor);
    }

    public static void insertSecondaryNames(List<String> tooltip, ItemStack stack, String configuredFormatting) {
        if (tooltip == null || stack == null || stack.getItem() == null || !shouldShowSecondaryLanguage()) {
            return;
        }

        String primaryName = EnumChatFormatting.getTextWithoutFormattingCodes(stack.getDisplayName());
        int originalSize = tooltip.size();
        insertSecondaryNames(
            tooltip,
            1,
            DisplayNameResolver.resolveSecondaryDisplayNames(stack),
            primaryName,
            resolveItemPrimaryFormatting(stack),
            configuredFormatting);
        cacheInsertedSecondaryLines(stack, tooltip, originalSize);
    }

    public static void prioritizeSecondaryNamesAfterPrimary(List<String> tooltip, ItemStack stack) {
        if (tooltip == null || tooltip.isEmpty() || stack == null || stack.getItem() == null || !shouldShowSecondaryLanguage()) {
            return;
        }

        List<String> desiredSecondaryLines = getCachedInsertedSecondaryLines(stack);
        if (desiredSecondaryLines.isEmpty()) {
            return;
        }

        Set<String> normalizedDesiredLines = new LinkedHashSet<String>();
        for (String secondaryLine : desiredSecondaryLines) {
            String normalized = normalizeName(secondaryLine);
            if (!normalized.isEmpty()) {
                normalizedDesiredLines.add(normalized);
            }
        }

        for (int i = tooltip.size() - 1; i >= 1; i--) {
            String normalized = normalizeName(tooltip.get(i));
            if (!normalized.isEmpty() && normalizedDesiredLines.contains(normalized)) {
                tooltip.remove(i);
            }
        }

        tooltip.addAll(1, desiredSecondaryLines);
    }

    public static void insertSecondaryNames(List<String> tooltip, Collection<String> secondaryNames, String primaryName) {
        insertSecondaryNames(
            tooltip,
            tooltip == null || tooltip.isEmpty() ? 0 : 1,
            secondaryNames,
            primaryName,
            "",
            Config.secondaryNameColor);
    }

    public static void insertSecondaryNames(List<String> tooltip, int insertIndex, Collection<String> secondaryNames,
            String primaryName) {
        insertSecondaryNames(tooltip, insertIndex, secondaryNames, primaryName, "", Config.secondaryNameColor);
    }

    public static void insertSecondaryNames(List<String> tooltip, int insertIndex, Collection<String> secondaryNames,
            String primaryName, String primaryFormattingFallback) {
        insertSecondaryNames(
            tooltip,
            insertIndex,
            secondaryNames,
            primaryName,
            primaryFormattingFallback,
            Config.secondaryNameColor);
    }

    public static void insertSecondaryNames(List<String> tooltip, int insertIndex, Collection<String> secondaryNames,
            String primaryName, String primaryFormattingFallback, String configuredFormatting) {
        if (tooltip == null || secondaryNames == null || secondaryNames.isEmpty() || !shouldShowSecondaryLanguage()) {
            return;
        }

        Set<String> existingNames = collectExistingNames(tooltip);
        String normalizedPrimaryName = normalizeName(primaryName);
        if (!normalizedPrimaryName.isEmpty()) {
            existingNames.add(normalizedPrimaryName);
        }

        List<String> filteredSecondaryNames = new ArrayList<String>();
        for (String translatedName : secondaryNames) {
            String normalizedTranslatedName = normalizeName(translatedName);
            if (!normalizedTranslatedName.isEmpty() && !existingNames.contains(normalizedTranslatedName)) {
                filteredSecondaryNames.add(translatedName);
                existingNames.add(normalizedTranslatedName);
            }
        }

        if (filteredSecondaryNames.isEmpty()) {
            return;
        }

        insertIndex = Math.max(0, Math.min(insertIndex, tooltip.size()));
        String primaryLine = resolvePrimaryLine(tooltip, insertIndex);
        for (int i = filteredSecondaryNames.size() - 1; i >= 0; i--) {
            tooltip.add(
                insertIndex,
                formatSecondaryLine(filteredSecondaryNames.get(i), primaryLine, primaryFormattingFallback, configuredFormatting));
        }
    }

    public static boolean shouldShowSecondaryLanguage() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.gameSettings == null) {
            return false;
        }

        if (Config.alwaysShow) {
            return true;
        }

        String currentLanguage = minecraft.gameSettings.language;
        for (String languageCode : Config.displayLanguages) {
            if (languageCode != null && !languageCode.equalsIgnoreCase(currentLanguage)) {
                return true;
            }
        }

        return false;
    }

    public static String formatSecondaryLine(String translatedName) {
        return formatSecondaryLine(translatedName, "", "", Config.secondaryNameColor);
    }

    public static String formatSecondaryLine(String translatedName, String primaryLine) {
        return formatSecondaryLine(translatedName, primaryLine, "", Config.secondaryNameColor);
    }

    public static String formatSecondaryLine(String translatedName, String primaryLine, String primaryFormattingFallback) {
        return formatSecondaryLine(translatedName, primaryLine, primaryFormattingFallback, Config.secondaryNameColor);
    }

    public static String formatSecondaryLine(
            String translatedName,
            String primaryLine,
            String primaryFormattingFallback,
            String configuredFormatting) {
        String resolvedFormatting = resolveSecondaryFormatting(primaryLine, primaryFormattingFallback, configuredFormatting);
        if (hasConfiguredSecondaryFormatting(configuredFormatting)) {
            translatedName = EnumChatFormatting.getTextWithoutFormattingCodes(translatedName);
            return resolvedFormatting + (translatedName == null ? "" : translatedName);
        }

        if (containsFormattingCodes(translatedName)) {
            return translatedName == null ? "" : translatedName;
        }

        return resolvedFormatting + (translatedName == null ? "" : translatedName);
    }

    public static Set<String> collectExistingNames(List<String> tooltip) {
        LinkedHashSet<String> existingNames = new LinkedHashSet<String>();
        for (String line : tooltip) {
            String normalized = normalizeName(line);
            if (!normalized.isEmpty()) {
                existingNames.add(normalized);
            }
        }
        return existingNames;
    }

    public static String normalizeName(String value) {
        if (value == null) {
            return "";
        }

        String normalized = EnumChatFormatting.getTextWithoutFormattingCodes(value);
        if (normalized == null) {
            normalized = value;
        }

        return normalized.trim();
    }

    private static String resolvePrimaryLine(List<String> tooltip, int insertIndex) {
        if (tooltip == null || tooltip.isEmpty()) {
            return "";
        }

        int preferredIndex = Math.max(0, Math.min(insertIndex - 1, tooltip.size() - 1));
        String preferredLine = tooltip.get(preferredIndex);
        if (preferredLine != null && !preferredLine.isEmpty()) {
            return preferredLine;
        }

        for (String line : tooltip) {
            if (line != null && !line.isEmpty()) {
                return line;
            }
        }

        return "";
    }

    private static String resolveSecondaryFormatting(
            String primaryLine,
            String primaryFormattingFallback,
            String configuredFormattingValue) {
        FormattingSpec inheritedFormatting = FormattingSpec.fromFormattingCodes(
            extractLeadingFormattingCodes(primaryLine),
            extractLeadingFormattingCodes(primaryFormattingFallback));
        FormattingSpec configuredFormatting = parseConfiguredFormatting(resolveConfiguredFormattingValue(configuredFormattingValue));

        if (configuredFormatting.hasFormatting()) {
            String colorFormatting = configuredFormatting.colorFormatting;
            if (colorFormatting.isEmpty()) {
                colorFormatting = inheritedFormatting.colorFormatting;
            }
            if (colorFormatting.isEmpty()) {
                colorFormatting = EnumChatFormatting.GRAY.toString();
            }

            String styleFormatting = mergeStyleFormatting(
                inheritedFormatting.styleFormatting,
                configuredFormatting.styleFormatting);
            return colorFormatting + styleFormatting;
        }

        String inheritedFormattingCodes = inheritedFormatting.toFormattingCodes();
        if (!inheritedFormattingCodes.isEmpty()) {
            return inheritedFormattingCodes;
        }

        return EnumChatFormatting.GRAY.toString();
    }

    private static boolean hasConfiguredSecondaryFormatting(String configuredFormattingValue) {
        return parseConfiguredFormatting(resolveConfiguredFormattingValue(configuredFormattingValue)).hasFormatting();
    }

    private static boolean containsFormattingCodes(String translatedName) {
        if (translatedName == null || translatedName.isEmpty()) {
            return false;
        }

        return translatedName.indexOf(SECTION_SIGN) >= 0;
    }

    private static String resolveConfiguredFormattingValue(String configuredFormattingValue) {
        if (configuredFormattingValue != null && !configuredFormattingValue.trim().isEmpty()) {
            return configuredFormattingValue;
        }

        return Config.secondaryNameColor;
    }

    private static FormattingSpec parseConfiguredFormatting(String configuredFormatting) {
        if (configuredFormatting == null) {
            return FormattingSpec.EMPTY;
        }

        String normalized = configuredFormatting.trim();
        if (normalized.isEmpty()) {
            return FormattingSpec.EMPTY;
        }

        FormattingSpec spec = parseFormattingCodeSequence(normalized);
        if (spec.hasFormatting()) {
            return spec;
        }

        String[] tokens = normalized.split("[,\\s|+/]+");
        FormattingSpec builder = new FormattingSpec();
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token == null || token.isEmpty()) {
                continue;
            }

            FormattingSpec tokenSpec = parseFormattingToken(token);
            if (!tokenSpec.hasFormatting()) {
                return FormattingSpec.EMPTY;
            }

            builder.apply(tokenSpec);
        }

        return builder.hasFormatting() ? builder : FormattingSpec.EMPTY;
    }

    private static FormattingSpec parseFormattingCodeSequence(String configuredFormatting) {
        if (configuredFormatting == null || configuredFormatting.isEmpty()) {
            return FormattingSpec.EMPTY;
        }

        FormattingSpec builder = new FormattingSpec();
        boolean matchedAny = false;
        for (int i = 0; i < configuredFormatting.length();) {
            char current = configuredFormatting.charAt(i);
            if ((current == SECTION_SIGN || current == '&') && i + 1 < configuredFormatting.length()) {
                EnumChatFormatting formatting = resolveFormatting(Character.toLowerCase(configuredFormatting.charAt(i + 1)));
                if (formatting == null) {
                    return FormattingSpec.EMPTY;
                }

                builder.apply(formatting);
                matchedAny = true;
                i += 2;
                continue;
            }

            if (Character.isWhitespace(current)
                || current == ','
                || current == '|'
                || current == '+'
                || current == '/') {
                i++;
                continue;
            }

            return FormattingSpec.EMPTY;
        }

        return matchedAny ? builder : FormattingSpec.EMPTY;
    }

    private static FormattingSpec parseFormattingToken(String token) {
        if (token == null) {
            return FormattingSpec.EMPTY;
        }

        String normalized = token.trim();
        if (normalized.isEmpty()) {
            return FormattingSpec.EMPTY;
        }

        normalized = normalized.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if ("grey".equals(normalized)) {
            normalized = "gray";
        } else if ("dark_grey".equals(normalized)) {
            normalized = "dark_gray";
        } else if ("underline".equals(normalized)) {
            normalized = "underlined";
        } else if ("strike".equals(normalized) || "strikethrough".equals(normalized)) {
            normalized = "strikethrough";
        } else if ("magic".equals(normalized)) {
            normalized = "obfuscated";
        }

        for (EnumChatFormatting formatting : EnumChatFormatting.values()) {
            if (formatting.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                FormattingSpec spec = new FormattingSpec();
                spec.apply(formatting);
                return spec;
            }
        }

        FormattingSpec codeSequence = parseCompactFormattingCodes(token.trim());
        if (codeSequence.hasFormatting()) {
            return codeSequence;
        }

        return FormattingSpec.EMPTY;
    }

    private static FormattingSpec parseCompactFormattingCodes(String token) {
        if (token == null || token.isEmpty()) {
            return FormattingSpec.EMPTY;
        }

        if (token.length() > 2) {
            return FormattingSpec.EMPTY;
        }

        FormattingSpec builder = new FormattingSpec();
        boolean matchedAny = false;
        for (int i = 0; i < token.length(); i++) {
            EnumChatFormatting formatting = resolveFormatting(Character.toLowerCase(token.charAt(i)));
            if (formatting == null) {
                return FormattingSpec.EMPTY;
            }

            builder.apply(formatting);
            matchedAny = true;
        }

        return matchedAny ? builder : FormattingSpec.EMPTY;
    }

    private static String extractLeadingFormattingCodes(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i + 1 < text.length();) {
            if (text.charAt(i) != SECTION_SIGN) {
                break;
            }

            builder.append(text.charAt(i)).append(text.charAt(i + 1));
            i += 2;
        }
        return builder.toString();
    }

    private static String mergeStyleFormatting(String inheritedStyleFormatting, String configuredStyleFormatting) {
        FormattingSpec merged = new FormattingSpec();
        merged.apply(FormattingSpec.fromFormattingCodes(inheritedStyleFormatting));
        merged.apply(FormattingSpec.fromFormattingCodes(configuredStyleFormatting));
        return merged.styleFormatting;
    }

    private static EnumChatFormatting resolveFormatting(char code) {
        for (EnumChatFormatting formatting : EnumChatFormatting.values()) {
            String formattingCode = formatting.toString();
            if (formattingCode.length() == 2
                && formattingCode.charAt(0) == SECTION_SIGN
                && formattingCode.charAt(1) == code) {
                return formatting;
            }
        }
        return null;
    }

    private static String resolveItemPrimaryFormatting(ItemStack stack) {
        if (stack == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        if (stack.getRarity() != null && stack.getRarity().rarityColor != null) {
            builder.append(stack.getRarity().rarityColor);
        }

        builder.append(extractLeadingFormattingCodes(stack.getDisplayName()));
        return builder.toString();
    }

    private static void cacheInsertedSecondaryLines(ItemStack stack, List<String> tooltip, int originalSize) {
        InsertedLineCacheKey cacheKey = InsertedLineCacheKey.of(stack);
        if (cacheKey == null) {
            return;
        }

        List<String> insertedLines = new ArrayList<String>();
        int insertedCount = tooltip == null ? 0 : tooltip.size() - originalSize;
        for (int i = 0; i < insertedCount && i + 1 < tooltip.size(); i++) {
            insertedLines.add(tooltip.get(i + 1));
        }

        List<String> cachedLines = Collections.unmodifiableList(insertedLines);
        synchronized (INSERTED_SECONDARY_LINE_CACHE) {
            INSERTED_SECONDARY_LINE_CACHE.put(cacheKey, cachedLines);
        }
    }

    private static List<String> getCachedInsertedSecondaryLines(ItemStack stack) {
        InsertedLineCacheKey cacheKey = InsertedLineCacheKey.of(stack);
        if (cacheKey == null) {
            return new ArrayList<String>();
        }

        synchronized (INSERTED_SECONDARY_LINE_CACHE) {
            List<String> cachedLines = INSERTED_SECONDARY_LINE_CACHE.get(cacheKey);
            return cachedLines == null ? new ArrayList<String>() : new ArrayList<String>(cachedLines);
        }
    }

    private static final class InsertedLineCacheKey {

        private final Object item;
        private final int itemDamage;
        private final String tagSignature;
        private final int hashCode;

        private InsertedLineCacheKey(Object item, int itemDamage, String tagSignature) {
            this.item = item;
            this.itemDamage = itemDamage;
            this.tagSignature = tagSignature;

            int result = System.identityHashCode(item);
            result = 31 * result + itemDamage;
            result = 31 * result + (tagSignature != null ? tagSignature.hashCode() : 0);
            this.hashCode = result;
        }

        private static InsertedLineCacheKey of(ItemStack stack) {
            if (stack == null || stack.getItem() == null) {
                return null;
            }

            NBTTagCompound tagCompound = stack.getTagCompound();
            String tagSignature = tagCompound == null ? null : tagCompound.toString();
            return new InsertedLineCacheKey(stack.getItem(), stack.getItemDamage(), tagSignature);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof InsertedLineCacheKey)) {
                return false;
            }

            InsertedLineCacheKey other = (InsertedLineCacheKey) obj;
            if (item != other.item || itemDamage != other.itemDamage) {
                return false;
            }

            if (tagSignature == null) {
                return other.tagSignature == null;
            }

            return tagSignature.equals(other.tagSignature);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private static final class FormattingSpec {

        private static final FormattingSpec EMPTY = new FormattingSpec();

        private String colorFormatting = "";
        private String styleFormatting = "";

        private void apply(FormattingSpec other) {
            if (other == null) {
                return;
            }

            if (!other.colorFormatting.isEmpty()) {
                this.colorFormatting = other.colorFormatting;
            }

            if (!other.styleFormatting.isEmpty()) {
                for (int i = 0; i + 1 < other.styleFormatting.length(); i += 2) {
                    appendStyleCode(other.styleFormatting.charAt(i), other.styleFormatting.charAt(i + 1));
                }
            }
        }

        private void apply(EnumChatFormatting formatting) {
            if (formatting == null) {
                return;
            }

            String formattingCode = formatting.toString();
            if (formattingCode.length() != 2 || formattingCode.charAt(0) != SECTION_SIGN) {
                return;
            }

            if (formatting == EnumChatFormatting.RESET) {
                colorFormatting = "";
                styleFormatting = "";
                return;
            }

            if (formatting.isColor()) {
                colorFormatting = formattingCode;
                styleFormatting = "";
                return;
            }

            appendStyleCode(formattingCode.charAt(0), formattingCode.charAt(1));
        }

        private void appendStyleCode(char prefix, char code) {
            String styleCode = new String(new char[]{prefix, code});
            if (styleFormatting.indexOf(styleCode) < 0) {
                styleFormatting += styleCode;
            }
        }

        private boolean hasFormatting() {
            return !colorFormatting.isEmpty() || !styleFormatting.isEmpty();
        }

        private String toFormattingCodes() {
            return colorFormatting + styleFormatting;
        }

        private static FormattingSpec fromFormattingCodes(String primaryFormatting) {
            FormattingSpec spec = new FormattingSpec();
            if (primaryFormatting == null || primaryFormatting.isEmpty()) {
                return spec;
            }

            for (int i = 0; i + 1 < primaryFormatting.length();) {
                if (primaryFormatting.charAt(i) != SECTION_SIGN) {
                    break;
                }

                EnumChatFormatting formatting = resolveFormatting(Character.toLowerCase(primaryFormatting.charAt(i + 1)));
                if (formatting != null) {
                    spec.apply(formatting);
                }

                i += 2;
            }

            return spec;
        }

        private static FormattingSpec fromFormattingCodes(String primaryFormatting, String fallbackFormatting) {
            FormattingSpec spec = fromFormattingCodes(primaryFormatting);
            FormattingSpec fallbackSpec = fromFormattingCodes(fallbackFormatting);
            if (spec.colorFormatting.isEmpty()) {
                spec.colorFormatting = fallbackSpec.colorFormatting;
            }
            spec.applyStylesOnly(fallbackSpec.styleFormatting);
            return spec;
        }

        private void applyStylesOnly(String styleFormatting) {
            if (styleFormatting == null || styleFormatting.isEmpty()) {
                return;
            }

            for (int i = 0; i + 1 < styleFormatting.length(); i += 2) {
                appendStyleCode(styleFormatting.charAt(i), styleFormatting.charAt(i + 1));
            }
        }
    }
}
