package com.starskyxiii.polyglottooltip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;

public final class SearchTextCollector {

    private static final int MAX_CACHE_ENTRIES = 4096;
    private static final Map<SearchCacheKey, List<String>> SEARCH_TEXT_CACHE =
        new LinkedHashMap<SearchCacheKey, List<String>>(256, 0.75F, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<SearchCacheKey, List<String>> eldest) {
                return size() > MAX_CACHE_ENTRIES;
            }
        };

    private SearchTextCollector() {}

    public static void clearCache() {
        synchronized (SEARCH_TEXT_CACHE) {
            SEARCH_TEXT_CACHE.clear();
        }
    }

    public static List<String> collectSearchableNames(ItemStack stack) {
        SearchCacheKey cacheKey = SearchCacheKey.of(stack);
        if (cacheKey == null) {
            return new ArrayList<String>();
        }

        synchronized (SEARCH_TEXT_CACHE) {
            List<String> cached = SEARCH_TEXT_CACHE.get(cacheKey);
            if (cached != null) {
                return cached;
            }
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
        synchronized (SEARCH_TEXT_CACHE) {
            List<String> cached = SEARCH_TEXT_CACHE.get(cacheKey);
            if (cached != null) {
                return cached;
            }

            SEARCH_TEXT_CACHE.put(cacheKey, resolved);
            return resolved;
        }
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
