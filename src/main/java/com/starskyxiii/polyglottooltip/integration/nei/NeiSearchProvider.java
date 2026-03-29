package com.starskyxiii.polyglottooltip.integration.nei;

import java.util.List;
import java.util.regex.Pattern;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import codechicken.nei.ItemList.NothingItemFilter;
import codechicken.nei.SearchField;
import codechicken.nei.SearchTokenParser.ISearchParserProvider;
import codechicken.nei.SearchTokenParser.SearchMode;
import codechicken.nei.api.ItemFilter;

import com.starskyxiii.polyglottooltip.search.SearchTextCollector;

public class NeiSearchProvider implements ISearchParserProvider {

    @Override
    public ItemFilter getFilter(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            return new NothingItemFilter();
        }

        Pattern pattern = SearchField.getPattern(searchText);
        if (pattern == null) {
            return new NothingItemFilter();
        }

        return new PolyglotNameFilter(pattern);
    }

    @Override
    public char getPrefix() {
        return '~';
    }

    @Override
    public EnumChatFormatting getHighlightedColor() {
        return EnumChatFormatting.GRAY;
    }

    @Override
    public SearchMode getSearchMode() {
        return SearchMode.ALWAYS;
    }

    private static final class PolyglotNameFilter implements ItemFilter {

        private final Pattern pattern;

        private PolyglotNameFilter(Pattern pattern) {
            this.pattern = pattern;
        }

        @Override
        public boolean matches(ItemStack item) {
            List<String> searchableNames = SearchTextCollector.collectSearchableNames(item);
            for (String searchableName : searchableNames) {
                if (this.pattern.matcher(searchableName).find()) {
                    return true;
                }
            }

            return false;
        }
    }
}
