package com.starskyxiii.polyglottooltip.search;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.PatternSyntaxException;

/**
 * Shared search matcher for Integrated Terminals item queries.
 */
public final class IntegratedTerminalSearchHelper {

    private IntegratedTerminalSearchHelper() {
    }

    public static boolean matches(ItemStack stack, Object searchMode, String query) {
        SearchQuery parsed = SearchQuery.fromMode(searchMode, query);
        return matches(stack, parsed);
    }

    public static boolean matches(ItemStack stack, String rawQuery) {
        SearchQuery parsed = SearchQuery.fromRaw(rawQuery);
        return matches(stack, parsed);
    }

    private static boolean matches(ItemStack stack, SearchQuery query) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (query.text().isEmpty()) {
            return true;
        }

        return switch (query.mode()) {
            case MOD -> matchesMod(stack, query.text());
            case TOOLTIP -> matchesTooltip(stack, query.text());
            case TAG -> matchesTags(stack, query.text());
            case DEFAULT -> matchesSearchText(stack, query.text());
        };
    }

    private static boolean matchesMod(ItemStack stack, String query) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            return false;
        }

        return matchesText(Optional.ofNullable(stack.getItem().getCreatorModId(minecraft.getConnection().registryAccess(), stack))
                .orElse("minecraft")
                .toLowerCase(Locale.ENGLISH), query);
    }

    private static boolean matchesTooltip(ItemStack stack, String query) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }

        return stack.getTooltipLines(
                        Item.TooltipContext.of(minecraft.player.registryAccess()),
                        minecraft.player,
                        TooltipFlag.Default.NORMAL
                ).stream()
                .map(component -> component.getString().toLowerCase(Locale.ENGLISH))
                .anyMatch(line -> matchesText(line, query));
    }

    private static boolean matchesTags(ItemStack stack, String query) {
        return stack.getItem().builtInRegistryHolder().tags()
                .filter(tag -> matchesText(tag.location().toString().toLowerCase(Locale.ENGLISH), query))
                .anyMatch(tag -> BuiltInRegistries.ITEM.get(tag).isPresent());
    }

    private static boolean matchesSearchText(ItemStack stack, String query) {
        return new SearchTextCollector()
                .addItemStack(stack)
                .build()
                .lines()
                .map(line -> line.toLowerCase(Locale.ENGLISH))
                .anyMatch(line -> matchesText(line, query));
    }

    private static boolean matchesText(String candidate, String query) {
        try {
            return candidate.matches(".*" + query + ".*");
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private enum SearchMode {
        DEFAULT,
        MOD,
        TOOLTIP,
        TAG
    }

    private record SearchQuery(SearchMode mode, String text) {

        private static SearchQuery fromMode(Object mode, String query) {
            return new SearchQuery(parseMode(mode), normalize(query));
        }

        private static SearchQuery fromRaw(String query) {
            String normalized = normalize(query);
            if (normalized.isEmpty()) {
                return new SearchQuery(SearchMode.DEFAULT, normalized);
            }

            return switch (normalized.charAt(0)) {
                case '@' -> new SearchQuery(SearchMode.MOD, normalized.substring(1));
                case '#' -> new SearchQuery(SearchMode.TOOLTIP, normalized.substring(1));
                case '$' -> new SearchQuery(SearchMode.TAG, normalized.substring(1));
                default -> new SearchQuery(SearchMode.DEFAULT, normalized);
            };
        }

        private static SearchMode parseMode(Object mode) {
            String name = mode instanceof Enum<?> enumValue ? enumValue.name() : String.valueOf(mode);
            return switch (name) {
                case "MOD" -> SearchMode.MOD;
                case "TOOLTIP" -> SearchMode.TOOLTIP;
                case "TAG" -> SearchMode.TAG;
                default -> SearchMode.DEFAULT;
            };
        }

        private static String normalize(String query) {
            return query == null ? "" : query.toLowerCase(Locale.ENGLISH);
        }
    }
}
