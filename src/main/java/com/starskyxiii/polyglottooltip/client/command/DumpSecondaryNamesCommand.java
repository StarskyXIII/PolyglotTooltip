package com.starskyxiii.polyglottooltip.client.command;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

import com.starskyxiii.polyglottooltip.Config;
import com.starskyxiii.polyglottooltip.DisplayNameResolver;
import com.starskyxiii.polyglottooltip.SearchTextCollector;
import com.starskyxiii.polyglottooltip.SecondaryTooltipUtil;

public class DumpSecondaryNamesCommand extends CommandBase {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String DUMP_DIRECTORY_NAME = "polyglottooltip";
    private static final String DUMP_FILE_PREFIX = "secondary-name-dump-";
    private static final String DUMP_FILE_EXTENSION = ".tsv";
    private static final SimpleDateFormat TIMESTAMP_FORMAT =
        new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT);
    private static final String MESSAGE_DUMP_STARTED = "command.polyglottooltip.dump.started";
    private static final String MESSAGE_DUMP_FAILED = "command.polyglottooltip.dump.failed";
    private static final String MESSAGE_DUMP_COMPLETE = "command.polyglottooltip.dump.complete";

    @Override
    public String getCommandName() {
        return "polyglotdump";
    }

    @Override
    public List<String> getCommandAliases() {
        return Collections.singletonList("ptdump");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/polyglotdump";
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
        PolyglotDumpChat.send(sender, EnumChatFormatting.YELLOW, MESSAGE_DUMP_STARTED);

        File outputFile;
        try {
            outputFile = dumpItems();
        } catch (Exception exception) {
            PolyglotDumpChat.send(sender, EnumChatFormatting.RED, MESSAGE_DUMP_FAILED, exception.getMessage());
            return;
        }

        PolyglotDumpChat.send(sender, EnumChatFormatting.GREEN, MESSAGE_DUMP_COMPLETE, outputFile.getAbsolutePath());
    }

    private File dumpItems() throws Exception {
        Minecraft minecraft = Minecraft.getMinecraft();
        File rootDirectory = minecraft != null ? minecraft.mcDataDir : new File(".");
        File dumpDirectory = new File(rootDirectory, DUMP_DIRECTORY_NAME);
        if (!dumpDirectory.exists() && !dumpDirectory.mkdirs()) {
            throw new IllegalStateException("Could not create dump directory: " + dumpDirectory.getAbsolutePath());
        }

        File outputFile = new File(
            dumpDirectory,
            DUMP_FILE_PREFIX + TIMESTAMP_FORMAT.format(new Date()) + DUMP_FILE_EXTENSION);

        List<ItemStack> stacks = collectItemStacks();
        Writer writer = null;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), UTF8));
            writeHeader(writer);
            for (ItemStack stack : stacks) {
                writeRow(writer, stack);
            }
        } finally {
            if (writer != null) {
                writer.close();
            }
        }

        return outputFile;
    }

    private List<ItemStack> collectItemStacks() {
        LinkedHashMap<DumpStackKey, ItemStack> collected = new LinkedHashMap<DumpStackKey, ItemStack>();

        for (Object registryEntry : Item.itemRegistry) {
            if (!(registryEntry instanceof Item)) {
                continue;
            }

            Item item = (Item) registryEntry;
            collectItemStacks(item, collected);
        }

        return new ArrayList<ItemStack>(collected.values());
    }

    private void collectItemStacks(Item item, Map<DumpStackKey, ItemStack> collected) {
        if (item == null) {
            return;
        }

        List<ItemStack> variants = new ArrayList<ItemStack>();
        addSubItems(item, item.getCreativeTab(), variants);

        for (CreativeTabs tab : CreativeTabs.creativeTabArray) {
            addSubItems(item, tab, variants);
        }

        if (variants.isEmpty()) {
            variants.add(new ItemStack(item, 1, 0));
        }

        for (ItemStack stack : variants) {
            if (stack == null || stack.getItem() == null) {
                continue;
            }

            DumpStackKey key = DumpStackKey.of(stack);
            if (key != null && !collected.containsKey(key)) {
                collected.put(key, stack.copy());
            }
        }
    }

    private void addSubItems(Item item, CreativeTabs tab, List<ItemStack> target) {
        if (item == null || tab == null) {
            return;
        }

        try {
            item.getSubItems(item, tab, target);
        } catch (Throwable ignored) {
            // Ignore buggy item variants during dump collection.
        }
    }

    private void writeHeader(Writer writer) throws Exception {
        List<String> header = new ArrayList<String>();
        header.add("registry_name");
        header.add("item_class");
        header.add("damage");
        header.add("nbt");
        header.add("primary_display_name");
        header.add("resolved_secondary_names");
        header.add("would_insert_secondary_name");
        header.add("inserted_secondary_lines");
        header.add("searchable_names");

        for (String languageCode : Config.displayLanguages) {
            header.add("resolved_" + sanitizeColumnName(languageCode));
        }

        writeLine(writer, header);
    }

    private void writeRow(Writer writer, ItemStack stack) throws Exception {
        String registryName = safeRegistryName(stack);
        String itemClass = stack != null && stack.getItem() != null ? stack.getItem().getClass().getName() : "";
        String primaryDisplayName = safeDisplayName(stack);
        List<String> resolvedSecondaryNames = safeResolveSecondaryNames(stack);
        List<String> insertedSecondaryLines = resolveInsertedSecondaryLines(stack, primaryDisplayName);
        List<String> searchableNames = SearchTextCollector.collectSearchableNames(stack);

        List<String> row = new ArrayList<String>();
        row.add(registryName);
        row.add(itemClass);
        row.add(stack != null ? String.valueOf(stack.getItemDamage()) : "");
        row.add(safeNbt(stack));
        row.add(primaryDisplayName);
        row.add(join(resolvedSecondaryNames));
        row.add(Boolean.toString(!insertedSecondaryLines.isEmpty()));
        row.add(join(insertedSecondaryLines));
        row.add(join(searchableNames));

        for (String languageCode : Config.displayLanguages) {
            row.add(safeResolveSecondaryName(stack, languageCode));
        }

        writeLine(writer, row);
    }

    private List<String> safeResolveSecondaryNames(ItemStack stack) {
        try {
            return DisplayNameResolver.resolveSecondaryDisplayNames(stack);
        } catch (Throwable ignored) {
            return new ArrayList<String>();
        }
    }

    private String safeResolveSecondaryName(ItemStack stack, String languageCode) {
        try {
            String resolved = DisplayNameResolver.resolveSecondaryDisplayNameForLanguage(stack, languageCode);
            return resolved == null ? "" : resolved;
        } catch (Throwable ignored) {
            // Ignore failed per-language resolution in dump output.
        }

        return "";
    }

    private List<String> resolveInsertedSecondaryLines(ItemStack stack, String primaryDisplayName) {
        ArrayList<String> tooltip = new ArrayList<String>();
        tooltip.add(primaryDisplayName);

        try {
            SecondaryTooltipUtil.insertSecondaryNames(tooltip, stack);
        } catch (Throwable ignored) {
            return new ArrayList<String>();
        }

        if (tooltip.size() <= 1) {
            return new ArrayList<String>();
        }

        return new ArrayList<String>(tooltip.subList(1, tooltip.size()));
    }

    private String safeRegistryName(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return "";
        }

        try {
            Object name = Item.itemRegistry.getNameForObject(stack.getItem());
            return name == null ? "" : String.valueOf(name);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String safeDisplayName(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return "";
        }

        try {
            String displayName = stack.getDisplayName();
            if (displayName == null) {
                return "";
            }

            String stripped = EnumChatFormatting.getTextWithoutFormattingCodes(displayName);
            return stripped == null ? displayName : stripped;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String safeNbt(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return "";
        }

        NBTTagCompound tagCompound = stack.getTagCompound();
        return tagCompound == null ? "" : tagCompound.toString();
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }

        LinkedHashSet<String> deduped = new LinkedHashSet<String>();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                deduped.add(value.trim());
            }
        }

        StringBuilder builder = new StringBuilder();
        for (String value : deduped) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private void writeLine(Writer writer, List<String> columns) throws Exception {
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                writer.write('\t');
            }
            writer.write(escape(columns.get(i)));
        }
        writer.write(System.getProperty("line.separator"));
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n");
    }

    private String sanitizeColumnName(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "unknown";
        }

        return value.trim().replace('-', '_');
    }

    private static final class DumpStackKey {

        private final Object item;
        private final int damage;
        private final String tagSignature;
        private final int hashCode;

        private DumpStackKey(Object item, int damage, String tagSignature) {
            this.item = item;
            this.damage = damage;
            this.tagSignature = tagSignature;

            int result = System.identityHashCode(item);
            result = 31 * result + damage;
            result = 31 * result + (tagSignature != null ? tagSignature.hashCode() : 0);
            this.hashCode = result;
        }

        private static DumpStackKey of(ItemStack stack) {
            if (stack == null || stack.getItem() == null) {
                return null;
            }

            NBTTagCompound tagCompound = stack.getTagCompound();
            String tagSignature = tagCompound == null ? null : tagCompound.toString();
            return new DumpStackKey(stack.getItem(), stack.getItemDamage(), tagSignature);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof DumpStackKey)) {
                return false;
            }

            DumpStackKey other = (DumpStackKey) obj;
            if (item != other.item || damage != other.damage) {
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

    private static final class PolyglotDumpChat {

        private PolyglotDumpChat() {}

        private static void send(ICommandSender sender, EnumChatFormatting color, String translationKey, Object... args) {
            if (sender != null) {
                ChatComponentTranslation message = new ChatComponentTranslation(translationKey, args);
                if (color != null) {
                    message.getChatStyle().setColor(color);
                }
                sender.addChatMessage(message);
            }
        }
    }
}
