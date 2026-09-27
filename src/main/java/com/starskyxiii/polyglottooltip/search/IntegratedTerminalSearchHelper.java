package com.starskyxiii.polyglottooltip.search;
//~ minecraft_116_packages minecraft_116_compound_names

import net.minecraft.client.Minecraft;
//? if <=1.16.5 {
/*import net.minecraft.util.registry.Registry;
*///?} else if <=1.19.2 {
/*import net.minecraft.core.Registry;
*///?} else {
import net.minecraft.core.registries.BuiltInRegistries;
//?}
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
        if (query.text.isEmpty()) {
            return true;
        }
        switch (query.mode) {
            case MOD:
                return matchesMod(stack, query.text);
            case TOOLTIP:
                return matchesTooltip(stack, query.text);
            case TAG:
                return matchesTags(stack, query.text);
            default:
                return matchesSearchText(stack, query.text);
        }
    }

    private static boolean matchesMod(ItemStack stack, String query) {
        //? if >=26 {
        /*var minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            return false;
        }
        return matchesText(Optional.ofNullable(stack.getItem().getCreatorModId(
                        minecraft.getConnection().registryAccess(), stack))
                .orElse("minecraft")
                .toLowerCase(Locale.ENGLISH), query);
        *///?} else {
        return matchesText(Optional.ofNullable(stack.getItem().getCreatorModId(stack))
                .orElse("minecraft")
                .toLowerCase(Locale.ENGLISH), query);
        //?}
    }

    private static boolean matchesTooltip(ItemStack stack, String query) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }

        return stack.getTooltipLines(
                //? if >1.20.1 {
                Item.TooltipContext.of(minecraft.player.registryAccess()),
                //?}
                minecraft.player,
                        //? if <=1.16.5 {
                        /*TooltipFlag.TooltipFlags.NORMAL
                        *///?} else {
                        TooltipFlag.Default.NORMAL
                        //?}
                ).stream()
                .map(component -> component.getString().toLowerCase(Locale.ENGLISH))
                .anyMatch(line -> matchesText(line, query));
    }

    private static boolean matchesTags(ItemStack stack, String query) {
        //? if <=1.16.5 {
        /*return stack.getItem().getTags().stream()
                .anyMatch(tag -> matchesText(tag.toString().toLowerCase(Locale.ENGLISH), query));
        *///?} else {
        return stack.getItem().builtInRegistryHolder().tags()
                .filter(tag -> matchesText(tag.location().toString().toLowerCase(Locale.ENGLISH), query))
                //? if >=26 {
                /*.anyMatch(tag -> BuiltInRegistries.ITEM.get(tag).isPresent());
                *///?} else if <=1.19.2 {
                /*.anyMatch(tag -> !Registry.ITEM.getTag(tag).isEmpty());
                *///?} else {
                .anyMatch(tag -> !BuiltInRegistries.ITEM.getTag(tag).isEmpty());
                //?}
        //?}
    }

    private static boolean matchesSearchText(ItemStack stack, String query) {
        String searchText = new SearchTextCollector()
                .addItemStack(stack)
                .build();
        //? if <=1.16.5 {
        /*return java.util.Arrays.stream(searchText.split("\\R"))
        *///?} else {
        return searchText.lines()
        //?}
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

    private static final class SearchQuery {
        private final SearchMode mode;
        private final String text;

        private SearchQuery(SearchMode mode, String text) {
            this.mode = mode;
            this.text = text;
        }

        private static SearchQuery fromMode(Object mode, String query) {
            return new SearchQuery(parseMode(mode), normalize(query));
        }

        private static SearchQuery fromRaw(String query) {
            String normalized = normalize(query);
            if (normalized.isEmpty()) {
                return new SearchQuery(SearchMode.DEFAULT, normalized);
            }

            switch (normalized.charAt(0)) {
                case '@':
                    return new SearchQuery(SearchMode.MOD, normalized.substring(1));
                case '#':
                    return new SearchQuery(SearchMode.TOOLTIP, normalized.substring(1));
                case '$':
                    return new SearchQuery(SearchMode.TAG, normalized.substring(1));
                default:
                    return new SearchQuery(SearchMode.DEFAULT, normalized);
            }
        }

        private static SearchMode parseMode(Object mode) {
            String name = mode instanceof Enum<?> ? ((Enum<?>) mode).name() : String.valueOf(mode);
            if ("MOD".equals(name)) return SearchMode.MOD;
            if ("TOOLTIP".equals(name)) return SearchMode.TOOLTIP;
            if ("TAG".equals(name)) return SearchMode.TAG;
            return SearchMode.DEFAULT;
        }

        private static String normalize(String query) {
            return query == null ? "" : query.toLowerCase(Locale.ENGLISH);
        }
    }
}
