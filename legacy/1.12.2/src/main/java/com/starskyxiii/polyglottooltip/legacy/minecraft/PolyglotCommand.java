package com.starskyxiii.polyglottooltip.legacy.minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.starskyxiii.polyglottooltip.legacy.Config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;

public final class PolyglotCommand extends CommandBase {
    private static final String PREFIX = "polyglottooltip.command.";

    @Override
    public String func_71517_b() {
        return "polyglot";
    }

    @Override
    public String func_71518_a(ICommandSender sender) {
        return PREFIX + "usage";
    }

    @Override
    public boolean func_184882_a(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public List<String> func_184883_a(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        return args.length == 1 ? func_71530_a(args, "why", "reload") : Collections.<String>emptyList();
    }

    @Override
    public void func_184881_a(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        String action = args.length == 1 ? args[0] : "";
        if ("why".equals(action)) {
            why(sender);
        } else if ("reload".equals(action)) {
            reload(sender);
        } else {
            throw new WrongUsageException(func_71518_a(sender));
        }
    }

    private static void reload(ICommandSender sender) {
        Config.reload();
        SecondaryLanguages.rebuild();
        List<String> sizes = new ArrayList<>();
        for (Map.Entry<String, SecondaryDictionary> entry : SecondaryLanguages.dictionaries().entrySet()) {
            sizes.add(entry.getKey() + " (" + entry.getValue().size() + ")");
        }
        send(sender, "reload", SecondaryLanguages.generation(), sizes.isEmpty() ? none() : String.join(", ", sizes));
    }

    @SuppressWarnings("deprecation")
    private static void why(ICommandSender sender) {
        EntityPlayerSP player = Minecraft.func_71410_x().field_71439_g;
        ItemStack stack = player == null ? ItemStack.field_190927_a : player.func_184614_ca();
        if (stack.func_190926_b()) {
            send(sender, "empty");
            return;
        }
        try {
            String key = ItemNameKeys.of(stack);
            String actual = TextFormatting.func_110646_a(stack.func_82833_r()).trim();
            send(sender, "key", key != null ? key
                    : new TextComponentTranslation(PREFIX + (stack.func_82837_s() ? "custom" : "noKey")));
            send(sender, "primary", actual);
            if (key != null) {
                String expected = TextFormatting.func_110646_a(I18n.func_74838_a(key)).trim();
                send(sender, "standard", expected);
                send(sender, "match", new TextComponentTranslation(PREFIX + (expected.equals(actual) ? "yes" : "no")));
                for (Map.Entry<String, SecondaryDictionary> entry : SecondaryLanguages.dictionaries().entrySet()) {
                    SecondaryDictionary dictionary = entry.getValue();
                    String value = dictionary.get(key);
                    String result = value == null ? "missing" : dictionary.hasOwnKey(key) ? "hit" : "fallback";
                    send(sender, result, entry.getKey(), value == null ? "" : value);
                }
            }
            SpecialItemNameResolver resolver = SpecialItemNameResolvers.find(stack);
            send(sender, "resolver", resolver == null ? none() : resolver.getClass().getName());
            send(sender, "integrations", none());
        } catch (RuntimeException error) {
            Object item = stack.func_77973_b();
            FailureLog.debug("why|" + FailureLog.failureKey(item, error), "Diagnosis failed for "
                    + (item == null ? "null" : item.getClass().getName()), error);
            send(sender, "failed", error.getClass().getName());
        }
    }

    private static TextComponentTranslation none() {
        return new TextComponentTranslation(PREFIX + "none");
    }

    private static void send(ICommandSender sender, String key, Object... args) {
        sender.func_145747_a(new TextComponentTranslation(PREFIX + key, args));
    }
}
