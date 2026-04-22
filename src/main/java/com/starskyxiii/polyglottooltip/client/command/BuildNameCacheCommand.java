package com.starskyxiii.polyglottooltip.client.command;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.item.Item;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCache;
import com.starskyxiii.polyglottooltip.name.prebuilt.FullNameCacheBuilder;
import com.starskyxiii.polyglottooltip.name.prebuilt.ManualFullNameCacheBuildManager;

/**
 * Client command: /polyglotbuild [all | <modid> | clear | status | cancel]
 * Alias: /ptbuild
 *
 * Examples:
 *   /polyglotbuild
 *   /polyglotbuild all
 *   /polyglotbuild chromaticraft
 *   /polyglotbuild status
 *   /polyglotbuild cancel
 *   /polyglotbuild clear
 */
public class BuildNameCacheCommand extends CommandBase {

    private static final ManualFullNameCacheBuildManager BUILD_MANAGER =
        ManualFullNameCacheBuildManager.getInstance();

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
        return "/polyglotbuild [all | <modid> | clear | status | cancel]";
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
        if (args == null || args.length != 1) {
            showUsage(sender);
            return;
        }

        String arg = args[0] == null ? "" : args[0].trim();
        if (arg.isEmpty()) {
            showUsage(sender);
            return;
        }

        if ("status".equalsIgnoreCase(arg)) {
            BUILD_MANAGER.showStatus(sender);
            return;
        }

        if ("cancel".equalsIgnoreCase(arg)) {
            BUILD_MANAGER.cancelBuild(sender);
            return;
        }

        if ("clear".equalsIgnoreCase(arg)) {
            if (FullNameCacheBuilder.hasActiveBuild()) {
                chat(sender, EnumChatFormatting.RED,
                    "[PolyglotTooltips] Cannot clear the cache while a build is running. Use /polyglotbuild status first.");
                return;
            }

            FullNameCache.clearDataPreservingMetadata();
            chat(sender, EnumChatFormatting.GREEN, "[PolyglotTooltips] Full name cache cleared from memory.");
            return;
        }

        String filter = "all".equalsIgnoreCase(arg) ? "all" : arg;
        boolean mergeWithPreviousCache = !"all".equalsIgnoreCase(arg);
        BUILD_MANAGER.startBuild(sender, filter, mergeWithPreviousCache);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args == null || args.length != 1) {
            return null;
        }

        Set<String> modIds = new LinkedHashSet<String>();
        modIds.add("all");
        modIds.add("status");
        modIds.add("cancel");
        modIds.add("clear");

        for (Object obj : Item.itemRegistry) {
            if (!(obj instanceof Item)) {
                continue;
            }

            Object nameObj = Item.itemRegistry.getNameForObject((Item) obj);
            if (nameObj == null) {
                continue;
            }

            String regName = String.valueOf(nameObj);
            int colon = regName.indexOf(':');
            if (colon > 0) {
                modIds.add(regName.substring(0, colon));
            }
        }

        return getListOfStringsMatchingLastWord(args, modIds.toArray(new String[0]));
    }

    private static void showUsage(ICommandSender sender) {
        chat(sender, EnumChatFormatting.YELLOW, "Usage: /polyglotbuild [all | <modid> | clear | status | cancel]");
        chat(sender, EnumChatFormatting.GRAY,   "  all     - scan every mod (full rebuild, non-blocking)");
        chat(sender, EnumChatFormatting.GRAY,   "  <modid> - scan one mod and merge into existing cache");
        chat(sender, EnumChatFormatting.GRAY,   "           e.g. chromaticraft, manametalmod, gregtech");
        chat(sender, EnumChatFormatting.GRAY,   "  status  - show active build progress");
        chat(sender, EnumChatFormatting.GRAY,   "  cancel  - cancel the current manual build");
        chat(sender, EnumChatFormatting.GRAY,   "  clear   - clear in-memory cache");
    }

    private static void chat(ICommandSender sender, EnumChatFormatting color, String message) {
        if (sender == null) {
            return;
        }

        ChatComponentText component = new ChatComponentText(message);
        component.getChatStyle().setColor(color);
        sender.addChatMessage(component);
    }
}
