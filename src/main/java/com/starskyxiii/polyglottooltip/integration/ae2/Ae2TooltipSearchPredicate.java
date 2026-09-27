package com.starskyxiii.polyglottooltip.integration.ae2;
//~ ae_key_rendering_package ae_key_rendering_legacy minecraft_116_packages

//? if <=1.19.2 {
/*import appeng.api.client.AEStackRendering;
*///?} else {
import appeng.api.client.AEKeyRendering;
//?}
import appeng.api.stacks.AEKey;
import appeng.core.AEConfig;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.util.Platform;
import com.starskyxiii.polyglottooltip.LanguageCache;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;
import com.starskyxiii.polyglottooltip.search.ChineseScriptSearchMatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
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
            return buildTooltipText(lines, Platform.getModName(key.getModId()),
                    AEConfig.instance().isSearchModNameInTooltips(),
                    SecondaryTooltipUtil.shouldShowSecondaryLanguage()
                            ? LanguageCache.getInstance()::resolveComponentsForAll : line -> List.of());
        });
    }

    static String buildTooltipText(List<Component> lines, String modName, boolean searchModName,
                                   Function<Component, List<String>> secondaryResolver) {
        StringBuilder tooltipText = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            String text = line.getString();
            String primary = ChatFormatting.stripFormatting(text);
            if (i > 0 && i == lines.size() - 1 && !searchModName
                    && (text.indexOf(ChatFormatting.PREFIX_CODE) != -1 || !line.getStyle().isEmpty())
                    && Objects.equals(primary, modName)) {
                continue;
            }
            appendLine(tooltipText, primary);
            for (String secondary : secondaryResolver.apply(line)) {
                String translated = ChatFormatting.stripFormatting(secondary);
                if (translated != null && !translated.isEmpty() && !Objects.equals(primary, translated)) {
                    appendLine(tooltipText, translated);
                }
            }
        }
        return normalize(tooltipText.toString());
    }

    private static void appendLine(StringBuilder target, String line) {
        if (!target.isEmpty()) {
            target.append('\n');
        }
        target.append(line);
    }

    private static String normalize(String input) {
        return input.toLowerCase().replace(" ", "");
    }
}
