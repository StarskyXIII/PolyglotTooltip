package com.starskyxiii.polyglottooltip.search;

import com.starskyxiii.polyglottooltip.LanguageCache;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Collects normalized search text for client-side UIs that cache plain strings.
 */
public final class SearchTextCollector {

    private final Set<String> entries = new LinkedHashSet<>();

    public SearchTextCollector addText(String value) {
        addNormalized(value);
        return this;
    }

    public SearchTextCollector addTextBlock(String value) {
        if (value == null || value.isBlank()) {
            return this;
        }

        for (String line : value.split("\\R")) {
            addNormalized(line);
        }
        return this;
    }

    public SearchTextCollector addAll(Iterable<String> values) {
        if (values == null) {
            return this;
        }

        for (String value : values) {
            addNormalized(value);
        }
        return this;
    }

    public SearchTextCollector addComponent(Component component) {
        if (component == null) {
            return this;
        }

        addNormalized(component.getString());
        return addAll(LanguageCache.getInstance().resolveComponentsForAll(component));
    }

    public SearchTextCollector addItemStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return this;
        }

        addNormalized(stack.getHoverName().getString());
        return addAll(LanguageCache.getInstance().resolveSearchNamesForAll(stack));
    }

    public String build() {
        return String.join("\n", entries);
    }

    private void addNormalized(String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        entries.addAll(ChineseScriptSearchMatcher.getSearchVariants(value));
    }
}
