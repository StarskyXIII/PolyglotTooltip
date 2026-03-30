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
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

import com.starskyxiii.polyglottooltip.config.Config;
import com.starskyxiii.polyglottooltip.name.DisplayNameResolver;
import com.starskyxiii.polyglottooltip.name.GregTechDisplayNameDebugBridge;
import com.starskyxiii.polyglottooltip.search.SearchTextCollector;
import com.starskyxiii.polyglottooltip.tooltip.SecondaryTooltipUtil;

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
        return "/polyglotdump [modid]";
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

        String modIdFilter;
        File outputFile;
        try {
            modIdFilter = parseModIdFilter(args);
            outputFile = dumpItems(modIdFilter);
        } catch (Exception exception) {
            PolyglotDumpChat.send(sender, EnumChatFormatting.RED, MESSAGE_DUMP_FAILED, exception.getMessage());
            return;
        }

        PolyglotDumpChat.send(sender, EnumChatFormatting.GREEN, MESSAGE_DUMP_COMPLETE, outputFile.getAbsolutePath());
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args == null || args.length != 1) {
            return null;
        }

        List<String> knownModIds = collectKnownModIds();
        return getListOfStringsMatchingLastWord(args, knownModIds.toArray(new String[knownModIds.size()]));
    }

    private File dumpItems(String modIdFilter) throws Exception {
        Minecraft minecraft = Minecraft.getMinecraft();
        File rootDirectory = minecraft != null ? minecraft.mcDataDir : new File(".");
        File dumpDirectory = new File(rootDirectory, DUMP_DIRECTORY_NAME);
        if (!dumpDirectory.exists() && !dumpDirectory.mkdirs()) {
            throw new IllegalStateException("Could not create dump directory: " + dumpDirectory.getAbsolutePath());
        }

        File outputFile = new File(
            dumpDirectory,
            buildDumpFileName(modIdFilter));

        List<ItemStack> stacks = collectItemStacks(modIdFilter);
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

    private String buildDumpFileName(String modIdFilter) {
        StringBuilder builder = new StringBuilder(DUMP_FILE_PREFIX);
        if (modIdFilter != null && !modIdFilter.isEmpty()) {
            builder.append(modIdFilter).append('-');
        }
        builder.append(TIMESTAMP_FORMAT.format(new Date())).append(DUMP_FILE_EXTENSION);
        return builder.toString();
    }

    private List<ItemStack> collectItemStacks(String modIdFilter) {
        LinkedHashMap<DumpStackKey, ItemStack> collected = new LinkedHashMap<DumpStackKey, ItemStack>();

        for (Object registryEntry : Item.itemRegistry) {
            if (!(registryEntry instanceof Item)) {
                continue;
            }

            Item item = (Item) registryEntry;
            if (!matchesModIdFilter(item, modIdFilter)) {
                continue;
            }
            collectItemStacks(item, collected);
        }

        return new ArrayList<ItemStack>(collected.values());
    }

    private String parseModIdFilter(String[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        if (args.length > 1) {
            throw new IllegalArgumentException("Usage: " + getCommandUsage(null));
        }

        String rawFilter = args[0] == null ? "" : args[0].trim();
        if (rawFilter.isEmpty()) {
            return null;
        }

        return rawFilter.toLowerCase(Locale.ROOT);
    }

    private boolean matchesModIdFilter(Item item, String modIdFilter) {
        if (item == null || modIdFilter == null || modIdFilter.isEmpty()) {
            return true;
        }

        String registryName = safeRegistryName(item);
        if (registryName.isEmpty()) {
            return false;
        }

        int separatorIndex = registryName.indexOf(':');
        if (separatorIndex <= 0) {
            return false;
        }

        String modId = registryName.substring(0, separatorIndex).toLowerCase(Locale.ROOT);
        return modIdFilter.equals(modId);
    }

    private List<String> collectKnownModIds() {
        Set<String> modIds = new TreeSet<String>();
        for (Object registryEntry : Item.itemRegistry) {
            if (!(registryEntry instanceof Item)) {
                continue;
            }

            String registryName = safeRegistryName((Item) registryEntry);
            int separatorIndex = registryName.indexOf(':');
            if (separatorIndex > 0) {
                modIds.add(registryName.substring(0, separatorIndex));
            }
        }
        return new ArrayList<String>(modIds);
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
        header.add("oredict_names");

        for (String languageCode : Config.displayLanguages) {
            header.add("resolved_" + sanitizeColumnName(languageCode));
            header.add("gregtech_debug_" + sanitizeColumnName(languageCode));
            header.add("material_debug_" + sanitizeColumnName(languageCode));
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
        row.add(join(safeResolveOreDictionaryNames(stack)));

        for (String languageCode : Config.displayLanguages) {
            row.add(safeResolveSecondaryName(stack, languageCode));
            row.add(safeResolveGregTechDebug(stack, languageCode));
            row.add(safeResolveGregTechMaterialDebug(stack, languageCode));
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

    private String safeResolveGregTechDebug(ItemStack stack, String languageCode) {
        try {
            String debug = GregTechDisplayNameDebugBridge.describe(stack, languageCode);
            return debug == null ? "" : debug;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String safeResolveGregTechMaterialDebug(ItemStack stack, String languageCode) {
        try {
            String debug = GregTechDisplayNameDebugBridge.describeMaterial(stack, languageCode);
            return debug == null ? "" : debug;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private List<String> safeResolveOreDictionaryNames(ItemStack stack) {
        try {
            return GregTechDisplayNameDebugBridge.collectOreDictionaryNames(stack);
        } catch (Throwable ignored) {
            return new ArrayList<String>();
        }
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

        return safeRegistryName(stack.getItem());
    }

    private String safeRegistryName(Item item) {
        if (item == null) {
            return "";
        }

        try {
            Object name = Item.itemRegistry.getNameForObject(item);
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
