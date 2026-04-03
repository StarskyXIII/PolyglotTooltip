package com.starskyxiii.polyglottooltip;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;

import java.util.regex.Pattern;

public final class LegacyFormatStyleUtil {

    private static final Pattern LEGACY_FORMAT_PATTERN = Pattern.compile("(?i)(?:&[0-9A-FK-OR])*");

    private LegacyFormatStyleUtil() {
    }

    public static boolean isValidLegacyFormatString(Object value) {
        if (!(value instanceof String text)) {
            return false;
        }
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
        if (formatting == ChatFormatting.RESET) {
            return Style.EMPTY;
        }
        if (formatting.isColor()) {
            return Style.EMPTY.withColor(formatting);
        }
        return switch (formatting) {
            case BOLD -> style.withBold(true);
            case ITALIC -> style.withItalic(true);
            case UNDERLINE -> style.withUnderlined(true);
            case STRIKETHROUGH -> style.withStrikethrough(true);
            case OBFUSCATED -> style.withObfuscated(true);
            default -> style;
        };
    }
}
