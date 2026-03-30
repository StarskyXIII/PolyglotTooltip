package com.starskyxiii.polyglottooltip.client.command;

import java.util.Collections;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.starskyxiii.polyglottooltip.name.prebuilt.PrebuiltSecondaryNameBuilder;

/**
 * Client command to rebuild the prebuilt ManaMetal secondary name cache.
 *
 * Usage:  /polyglotrebuildsecondary manametal
 * Alias:  /ptrebuildsecondary manametal
 *
 * The command switches game language per configured display language to call
 * getDisplayName() on each ManaMetal item, then restores the original language.
 * Language switching is ONLY allowed here — never on tooltip/render paths.
 */
public class RebuildSecondaryNameCacheCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "polyglotrebuildsecondary";
    }

    @Override
    public List<String> getCommandAliases() {
        return Collections.singletonList("ptrebuildsecondary");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/polyglotrebuildsecondary manametal";
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
        if (args == null || args.length == 0 || !"manametal".equalsIgnoreCase(args[0])) {
            chat(sender, EnumChatFormatting.YELLOW,
                "Usage: /polyglotrebuildsecondary manametal");
            return;
        }

        chat(sender, EnumChatFormatting.YELLOW,
            "[PolyglotTooltips] Rebuilding ManaMetal secondary name cache...");

        try {
            int count = PrebuiltSecondaryNameBuilder.rebuild();
            chat(sender, EnumChatFormatting.GREEN,
                "[PolyglotTooltips] Done! Cached " + count + " ManaMetal name entries. "
                + "Cache file: polyglottooltip/cache/manametal-secondary-names.tsv");
        } catch (Exception e) {
            chat(sender, EnumChatFormatting.RED,
                "[PolyglotTooltips] Rebuild failed: " + e.getMessage());
        }
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args != null && args.length == 1) {
            return getListOfStringsMatchingLastWord(args, new String[]{"manametal"});
        }
        return null;
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
