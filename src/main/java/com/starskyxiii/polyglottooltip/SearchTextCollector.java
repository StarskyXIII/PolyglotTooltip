package com.starskyxiii.polyglottooltip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;

public final class SearchTextCollector {

    private static final Map<SearchCacheKey, List<String>> SEARCH_TEXT_CACHE =
        new ConcurrentHashMap<SearchCacheKey, List<String>>();

    private SearchTextCollector() {}

    public static void clearCache() {
        SEARCH_TEXT_CACHE.clear();
    }

    public static List<String> collectSearchableNames(ItemStack stack) {
        SearchCacheKey cacheKey = SearchCacheKey.of(stack);
        if (cacheKey == null) {
            return new ArrayList<String>();
        }

        List<String> cached = SEARCH_TEXT_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        LinkedHashSet<String> searchableNames = new LinkedHashSet<String>();

        if (stack.getItem() == null) {
            return new ArrayList<String>(searchableNames);
        }

        addName(searchableNames, stack.getDisplayName());

        for (String translatedName : DisplayNameResolver.resolveSecondaryDisplayNames(stack)) {
            addName(searchableNames, translatedName);
        }

        List<String> resolved = Collections.unmodifiableList(new ArrayList<String>(searchableNames));
        List<String> previous = SEARCH_TEXT_CACHE.putIfAbsent(cacheKey, resolved);
        return previous != null ? previous : resolved;
    }

    private static void addName(LinkedHashSet<String> searchableNames, String text) {
        if (text == null) {
            return;
        }

        String normalized = EnumChatFormatting.getTextWithoutFormattingCodes(text);
        if (normalized == null) {
            return;
        }

        normalized = normalized.trim();
        if (!normalized.isEmpty()) {
            searchableNames.addAll(ChineseScriptSearchMatcher.getSearchVariants(normalized));
        }
    }

    private static final class SearchCacheKey {

        private final Object item;
        private final int itemDamage;
        private final String tagSignature;
        private final int hashCode;

        private SearchCacheKey(Object item, int itemDamage, String tagSignature) {
            this.item = item;
            this.itemDamage = itemDamage;
            this.tagSignature = tagSignature;

            int result = System.identityHashCode(item);
            result = 31 * result + itemDamage;
            result = 31 * result + (tagSignature != null ? tagSignature.hashCode() : 0);
            this.hashCode = result;
        }

        private static SearchCacheKey of(ItemStack stack) {
            if (stack == null || stack.getItem() == null) {
                return null;
            }

            NBTTagCompound tagCompound = stack.getTagCompound();
            String tagSignature = tagCompound == null ? null : tagCompound.toString();
            return new SearchCacheKey(stack.getItem(), stack.getItemDamage(), tagSignature);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof SearchCacheKey)) {
                return false;
            }

            SearchCacheKey other = (SearchCacheKey) obj;
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
}
