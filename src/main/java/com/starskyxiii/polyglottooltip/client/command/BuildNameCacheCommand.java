package com.starskyxiii.polyglottooltip.client.command;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCache;
import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCacheBuilder;
import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCacheBuilder.BuildResult;
import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCacheIO;
import com.starskyxiii.polyglottooltip.name.prebuilt.PrebuiltSecondaryNameIndexKey;
import com.starskyxiii.polyglottooltip.report.BuildReportWriter;

/**
 * Client command: /polyglotbuild [all | <modid> | clear]
 * Alias: /ptbuild
 *
 * Examples:
 *   /polyglotbuild         — shows usage
 *   /polyglotbuild all     — scan all mods
 *   /polyglotbuild manametalmod — scan only items with registry prefix "manametalmod:"
 *   /polyglotbuild gregtech     — scan only items with registry prefix "gregtech:"
 *   /polyglotbuild clear        — clear in-memory cache (disk file is kept)
 *
 * Output files:
 *   .minecraft/polyglottooltip/cache/full-name-cache.tsv
 *   .minecraft/polyglottooltip/report/build-report.txt
 *   .minecraft/polyglottooltip/report/build-summary.tsv
 *   .minecraft/polyglottooltip/report/suspect-entries.tsv
 */
public class BuildNameCacheCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "polyglotbuild";
    }

    @Override
    public List<String> getCommandAliases() {
        return Collections.singletonList("ptbuild");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/polyglotbuild [all | <modid> | clear]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args == null || args.length == 0) {
            chat(sender, EnumChatFormatting.YELLOW, "Usage: /polyglotbuild [all | <modid> | clear]");
            chat(sender, EnumChatFormatting.GRAY,   "  all     - scan every mod");
            chat(sender, EnumChatFormatting.GRAY,   "  <modid> - scan specific mod (e.g. manametalmod, gregtech)");
            chat(sender, EnumChatFormatting.GRAY,   "  clear   - clear in-memory cache");
            return;
        }

        String arg = args[0].trim();

        if ("clear".equalsIgnoreCase(arg)) {
            FullNameCache.replace(Collections.<PrebuiltSecondaryNameIndexKey, Map<String, String>>emptyMap());
            chat(sender, EnumChatFormatting.GREEN, "[PolyglotTooltips] Full name cache cleared from memory.");
            return;
        }

        String filter = "all".equalsIgnoreCase(arg) ? "all" : arg;

        chat(sender, EnumChatFormatting.YELLOW,
            "[PolyglotTooltips] Starting full name cache build... filter='" + filter + "'  (check logs for progress)");

        try {
            BuildResult result = FullNameCacheBuilder.build(filter);

            chat(sender, EnumChatFormatting.GREEN, "[PolyglotTooltips] Build complete!");
            chat(sender, EnumChatFormatting.WHITE, "  " + result.toSummaryLine());

            for (String lang : result.collectedPerLang.keySet()) {
                int collected = result.collectedPerLang.get(lang);
                int empty = safeGet(result.emptyPerLang, lang);
                int rawKey = safeGet(result.rawKeyPerLang, lang);
                String mode = result.switchModePerLang.containsKey(lang)
                    ? result.switchModePerLang.get(lang) : "?";
                long swMs = result.switchMsPerLang.containsKey(lang)
                    ? result.switchMsPerLang.get(lang) : -1L;
                long rsMs = result.resolveMsPerLang.containsKey(lang)
                    ? result.resolveMsPerLang.get(lang) : -1L;
                chat(sender, EnumChatFormatting.AQUA,
                    String.format("  %s: collected=%d  empty=%d  rawKey=%d  switch=%dms  resolve=%dms  [%s]",
                        lang, collected, empty, rawKey, swMs, rsMs, mode));
            }
            chat(sender, EnumChatFormatting.GRAY,
                String.format("  timing: enum=%dms  expand=%dms  write=%dms",
                    result.enumMs, result.expandMs, result.writeMs));

            chat(sender, EnumChatFormatting.GRAY,
                "  cache  -> " + FullNameCacheIO.getCacheFile().getAbsolutePath());
            chat(sender, EnumChatFormatting.GRAY,
                "  report -> " + BuildReportWriter.getReportDir().getAbsolutePath());

        } catch (Exception e) {
            chat(sender, EnumChatFormatting.RED, "[PolyglotTooltips] Build failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args != null && args.length == 1) {
            return getListOfStringsMatchingLastWord(args, new String[]{"all", "clear"});
        }
        return null;
    }

    private static void chat(ICommandSender sender, EnumChatFormatting color, String message) {
        if (sender == null) return;
        ChatComponentText c = new ChatComponentText(message);
        c.getChatStyle().setColor(color);
        sender.addChatMessage(c);
    }

    private static int safeGet(Map<String, Integer> map, String key) {
        Integer v = map.get(key);
        return v == null ? 0 : v;
    }
}
