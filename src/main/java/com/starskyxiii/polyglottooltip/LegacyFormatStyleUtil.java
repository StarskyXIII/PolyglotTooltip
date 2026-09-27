package com.starskyxiii.polyglottooltip;
//~ minecraft_116_packages

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;

import java.util.regex.Pattern;

public final class LegacyFormatStyleUtil {

    private static final Pattern LEGACY_FORMAT_PATTERN = Pattern.compile("(?i)(?:&[0-9A-FK-OR])*");

    private LegacyFormatStyleUtil() {
    }

    public static boolean isValidLegacyFormatString(Object value) {
        if (!(value instanceof String)) {
            return false;
        }
        String text = (String) value;
        return LEGACY_FORMAT_PATTERN.matcher(text.trim()).matches();
    }

    public static Style parse(String formatCodes) {
        String normalized = formatCodes == null ? "" : formatCodes.trim();
        Style style = Style.EMPTY;
        for (int i = 0; i + 1 < normalized.length(); i += 2) {
            ChatFormatting formatting = ChatFormatting.getByCode(Character.toLowerCase(normalized.charAt(i + 1)));
            if (formatting == null) {
                continue;
            }
            style = apply(style, formatting);
        }
        return style;
    }

    public static Style tooltipSecondaryNameStyle() {
        return parse(Config.TOOLTIP_SECONDARY_NAME_FORMAT.get());
    }

    public static Style jadeSecondaryNameStyle() {
        return parse(Config.JADE_SECONDARY_NAME_FORMAT.get());
    }

    private static Style apply(Style style, ChatFormatting formatting) {
        return style.applyLegacyFormat(formatting);
    }
}
