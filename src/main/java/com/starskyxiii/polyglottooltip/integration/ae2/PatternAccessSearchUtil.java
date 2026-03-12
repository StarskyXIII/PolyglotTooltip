package com.starskyxiii.polyglottooltip.integration.ae2;

import appeng.api.stacks.AEItemKey;
import appeng.api.crafting.PatternDetailsHelper;
import com.starskyxiii.polyglottooltip.search.SearchTextCollector;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PatternAccessSearchUtil {

    private PatternAccessSearchUtil() {
    }

    public static String buildProviderSearchText(Component name) {
        return new SearchTextCollector()
                .addComponent(name)
                .build();
    }

    public static String extendPatternSearchText(String original, ItemStack stack, Level level) {
        SearchTextCollector collector = new SearchTextCollector()
                .addTextBlock(original);

        if (level == null || stack == null || stack.isEmpty()) {
            return collector.build();
        }

        var pattern = PatternDetailsHelper.decodePattern(stack, level);
        if (pattern == null) {
            return collector.build();
        }

        for (var output : pattern.getOutputs()) {
            if (output == null || output.what() == null) {
                continue;
            }

            if (output.what() instanceof AEItemKey itemKey) {
                collector.addItemStack(itemKey.toStack());
            } else {
                collector.addComponent(output.what().getDisplayName());
            }
        }

        return collector.build();
    }

    public static List<String> buildPatternTokenList(String original, GenericStack stack) {
        SearchTextCollector collector = new SearchTextCollector()
                .addTextBlock(original);

        if (stack != null && stack.what() != null) {
            if (stack.what() instanceof AEItemKey itemKey) {
                collector.addItemStack(itemKey.toStack());
            } else {
                collector.addComponent(stack.what().getDisplayName());
            }
        }

        return tokenize(collector.build());
    }

    private static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalized = text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        List<String> tokens = new ArrayList<>();
        for (String token : normalized.split(" ")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
