package com.starskyxiii.polyglottooltip.integration.ae2;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.core.AEConfig;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.util.Platform;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import net.minecraft.ChatFormatting;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public class Ae2TooltipSearchPredicate implements Predicate<GridInventoryEntry> {

    private final String tooltip;
    private final Map<AEKey, String> tooltipCache;

    public Ae2TooltipSearchPredicate(String tooltip, Map<AEKey, String> tooltipCache) {
        this.tooltip = normalize(tooltip.toLowerCase());
        this.tooltipCache = tooltipCache;
    }

    @Override
    public boolean test(GridInventoryEntry gridInventoryEntry) {
        AEKey entryInfo = Objects.requireNonNull(gridInventoryEntry.getWhat());
        return ChineseScriptSearchMatcher.containsMatch(tooltip, getTooltipText(entryInfo));
    }

    private String getTooltipText(AEKey what) {
        return tooltipCache.computeIfAbsent(what, key -> {
            var lines = AEKeyRendering.getTooltip(key);

            StringBuilder tooltipText = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                var line = lines.get(i);

                if (i > 0 && i >= lines.size() - 1 && !AEConfig.instance().isSearchModNameInTooltips()) {
                    String text = line.getString();
                    boolean hadFormatting;
                    if (text.indexOf(ChatFormatting.PREFIX_CODE) != -1) {
                        text = ChatFormatting.stripFormatting(text);
                        hadFormatting = true;
                    } else {
                        hadFormatting = !line.getStyle().isEmpty();
                    }

                    if (!hadFormatting || !Objects.equals(text, Platform.getModName(what.getModId()))) {
                        tooltipText.append('\n').append(text);
                    }
                } else {
                    if (i > 0) {
                        tooltipText.append('\n');
                    }
                    line.visit(text -> {
                        if (text.indexOf(ChatFormatting.PREFIX_CODE) != -1) {
                            text = ChatFormatting.stripFormatting(text);
                        }
                        tooltipText.append(text);
                        return Optional.empty();
                    });
                }
            }

            return normalize(tooltipText.toString());
        });
    }

    private static String normalize(String input) {
        return input.toLowerCase().replace(" ", "");
    }
}
