package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.PotionTypes;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.potion.PotionUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CandidateNameProbe {
    private static final Gson gson = new Gson();
    private static final Map<String, ItemStack> items = new LinkedHashMap<>();
    private static final String PRODUCT = "com.starskyxiii.polyglottooltip.legacy.";
    private CandidateNameProbe() { }

    public static JsonObject inspect(JsonObject params) throws Exception {
        if (items.isEmpty() || params.has("reset")) setup();
        if (params.has("mutate")) {
            ItemStack stack = require(params.get("mutate").getAsString());
            if (params.has("type")) stack.func_77978_p().func_74774_a("type", params.get("type").getAsByte());
            if (params.has("damage")) stack.func_77964_b(params.get("damage").getAsInt());
        }
        JsonObject result = new JsonObject(); JsonArray rows = new JsonArray();
        for (Map.Entry<String, ItemStack> entry : items.entrySet()) rows.add(item(entry.getKey(), entry.getValue()));
        result.add("items", rows); result.add("cache", cache(new JsonObject()));
        result.add("traceControl", traceControl());
        result.add("instrumented", gson.toJsonTree(NameTrace.installed()));
        if (Loader.isModLoaded("polyglottooltip")) {
            JsonObject profiles = new JsonObject();
            for (String name : new String[]{"reliquary-names", "reliquary-title-cache", "extendedae-infinity-uel567", "rs-1616-search-refresh", "mrs-232-search-refresh"})
                profiles.addProperty(name, (Boolean) NativeAccess.call(NativeAccess.type(PRODUCT + "bootstrap.IntegrationProfileSelector"), "enabled", name));
            result.add("profiles", profiles);
            Object context = NativeAccess.call(NativeAccess.type(PRODUCT + "minecraft.MinecraftLanguageAdapter"), "snapshot");
            result.addProperty("generation", (Long) NativeAccess.get(context, "generation"));
            result.addProperty("resourceEpoch", (Long) NativeAccess.get(context, "nativeNameEpoch"));
        }
        return result;
    }

    private static void setup() throws Exception {
        items.clear();
        if (Loader.isModLoaded("xreliquary")) {
            Item charm = registered("xreliquary:mob_charm"), fragment = registered("xreliquary:mob_charm_fragment");
            for (int variant = 0; variant < 14; variant++) {
                items.put("charm-" + variant, (ItemStack) NativeAccess.call(charm.getClass(), "getCharmStack", (byte) variant));
                items.put("fragment-" + variant, new ItemStack(fragment, 1, variant));
            }
            for (ItemStack stack : items.values()) {
                if (!stack.func_77942_o()) stack.func_77982_d(new NBTTagCompound());
                stack.func_77978_p().func_74778_a("fixtureKeep", "unrelated variant sentinel 314");
            }
            items.put("charm-missing", new ItemStack(charm));
            items.put("charm-unknown", (ItemStack) NativeAccess.call(charm.getClass(), "getCharmStack", (byte) 14));
            items.put("fragment-unknown", new ItemStack(fragment, 1, 14));
            ItemStack wrong = require("charm-0").func_77946_l(); wrong.func_77978_p().func_74778_a("type", "native byte coercion");
            items.put("charm-string-type", wrong);
            ItemStack damaged = require("charm-0").func_77946_l(); damaged.func_77964_b(7); items.put("charm-damaged", damaged);
            items.put("charm-custom", require("charm-0").func_77946_l().func_151001_c("Literal charm 42"));
        }
        if (Loader.isModLoaded("extendedae")) {
            Item cell = registered("extendedae:infinity_cell");
            if (NativeAccess.get(NativeAccess.type("com.github.glodblock.eae.common.EAEItemAndBlock"), "INFINITY_CELL") != cell) throw new IllegalStateException("Wrong Infinity singleton");
            items.put("cell-empty", new ItemStack(cell));
            items.put("cell-stone", cell(new ItemStack(Blocks.field_150348_b)));
            items.put("cell-granite", cell(new ItemStack(Blocks.field_150348_b, 1, 1)));
            items.put("cell-wool", cell(new ItemStack(Blocks.field_150325_L, 1, 14)));
            items.put("cell-potion", cell(PotionUtils.func_185188_a(new ItemStack(Items.field_151068_bn), PotionTypes.field_185223_F)));
            items.put("cell-water", cell(new FluidStack(FluidRegistry.WATER, 4321)));
            items.put("cell-lava", cell(new FluidStack(FluidRegistry.LAVA, 9876)));
            items.put("cell-inner-literal", cell(new ItemStack(Items.field_151045_i).func_151001_c("  §bLiteral %1$s 名字  ")));
            items.put("cell-inner-empty", cell(new ItemStack(Items.field_151045_i).func_151001_c("")));
            items.put("cell-outer-literal", require("cell-water").func_77946_l().func_151001_c("Literal outer cell 42"));
            items.put("cell-nested", cell(require("cell-stone").func_77946_l()));
            ItemStack lore = require("cell-water").func_77946_l();
            NBTTagCompound display = new NBTTagCompound(); NBTTagList lines = new NBTTagList();
            lines.func_74742_a(new NBTTagString("Fixture literal 314 mB; keep punctuation")); display.func_74782_a("Lore", lines);
            lore.func_77978_p().func_74782_a("display", display); items.put("cell-lore", lore);
        }
        if (Loader.isModLoaded("retro_sophisticated_backpacks")) items.put("backpack", new ItemStack(registered("retro_sophisticated_backpacks:backpack_leather")));
        if (Loader.isModLoaded("naturescompass")) items.put("compass", new ItemStack(registered("naturescompass:naturescompass")));
        if (Loader.isModLoaded("apotheosis")) {
            items.put("prismatic-web", new ItemStack(registered("apotheosis:prismatic_web")));
            Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation("apotheosis", "hell_infusion"));
            if (enchantment == null) throw new IllegalStateException("Missing registered enchantment");
            items.put("hell-infusion-book", ItemEnchantedBook.func_92111_a(new EnchantmentData(enchantment, 2)));
        }
    }

    private static Item registered(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (item == null || item == Items.field_190931_a) throw new IllegalStateException("Missing registered " + id);
        return item;
    }

    private static ItemStack cell(Object value) throws Exception {
        Object record = value instanceof ItemStack
                ? NativeAccess.call(NativeAccess.type("appeng.util.item.AEItemStack"), "fromItemStack", value)
                : NativeAccess.call(NativeAccess.type("appeng.fluids.util.AEFluidStack"), "fromFluidStack", value);
        return (ItemStack) NativeAccess.call(registered("extendedae:infinity_cell"), "getRecordCell", record);
    }

    public static JsonObject cache(JsonObject params) throws Exception {
        JsonObject result = new JsonObject();
        if (!Loader.isModLoaded("xreliquary")) return result;
        Class<?> helper = NativeAccess.type("xreliquary.util.LanguageHelper");
        if (params.has("warm")) {
            for (int n = 0; n < 14; n++) for (String family : new String[]{"item.mob_charm_", "item.mob_charm_fragment_"})
                NativeAccess.call(helper, "getLocalization", family + n + ".name", new Object[0]);
            for (String raw : new String[]{"item.mob_charm_0.tooltip", "item.mob_charm_14.name", "xreliquary.item.mob_charm_0.name"})
                NativeAccess.call(helper, "getLocalization", raw, new Object[0]);
        }
        Map<?, ?> values = (Map<?, ?>) NativeAccess.get(helper, "preprocesssed");
        result.addProperty("identity", System.identityHashCode(values));
        JsonObject selected = new JsonObject(), sentinels = new JsonObject();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (key.matches("item\\.mob_charm_(fragment_)?(?:[0-9]|1[0-3])\\.name")) selected.addProperty(key, String.valueOf(entry.getValue()));
            else sentinels.addProperty(key, String.valueOf(entry.getValue()));
        }
        result.add("selected", selected); result.add("sentinels", sentinels);
        return result;
    }

    public static JsonObject open(JsonObject params) throws Exception {
        if (items.isEmpty()) setup();
        String key = params.get("key").getAsString(); ItemStack stack = require(key);
        Minecraft client = Minecraft.func_71410_x();
        GuiChest screen;
        if (params.has("retained")) {
            if (!(client.field_71462_r instanceof GuiChest)) throw new IllegalStateException("Retained candidate screen closed");
            screen = (GuiChest) client.field_71462_r;
        } else {
            InventoryBasic inventory = new InventoryBasic("Native candidate fixture", true, 27); inventory.func_70299_a(0, stack);
            screen = new GuiChest(client.field_71439_g.field_71071_by, inventory); client.func_147108_a(screen);
        }
        JsonObject result = item(key, stack); Slot slot = screen.field_147002_h.func_75139_a(0);
        int left = (Integer) NativeAccess.get(screen, "field_147003_i"), top = (Integer) NativeAccess.get(screen, "field_147009_r");
        result.addProperty("x", left + slot.field_75223_e + 8); result.addProperty("y", top + slot.field_75221_f + 8);
        result.addProperty("slotSame", slot.func_75211_c() == stack); result.addProperty("screen", screen.getClass().getName());
        result.addProperty("screenIdentity", System.identityHashCode(screen)); return result;
    }

    private static ItemStack require(String key) { ItemStack stack = items.get(key); if (stack == null) throw new IllegalArgumentException(key); return stack; }

    public static JsonObject guards() throws Exception {
        if (!Loader.isModLoaded("polyglottooltip") || !Loader.isModLoaded("extendedae")) throw new IllegalStateException("Guard fixture needs selected product and cell");
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("item.extendedae:infinity_cell.name", "環[%s]"); entries.put("tile.stone.stone.name", "石頭");
        entries.put("tile.water.name", "水"); entries.put("cell.unknown", "未知");
        Object language = NativeAccess.create(PRODUCT + "minecraft.LanguageTable", "resolver-only", entries);
        JsonObject result = new JsonObject(); JsonArray cases = new JsonArray(); result.add("cases", cases);
        result.addProperty("mode", "resolver-only; supplied primary; pathological stacks never rendered or serialized");
        for (int count : new int[]{1, 7, 8, 16}) cases.add(guard("depth-" + count, nested(count), language, 0));
        cases.add(guard("entry-depth-8", nested(1), language, 8));
        NBTTagCompound literal = new NBTTagCompound(), display = new NBTTagCompound();
        display.func_74778_a("Name", " §a%1$s 字 "); literal.func_74782_a("display", display);
        cases.add(guard("unknown-leaf-literal", raw(record("minecraft:diamond", 0, literal)), language, 0));
        display.func_74778_a("Name", ""); cases.add(guard("empty-literal", raw(record("minecraft:diamond", 0, literal)), language, 0));
        cases.add(guard("unknown-leaf", raw(record("minecraft:diamond", 0, null)), language, 0));
        NBTTagCompound overflow = record("minecraft:stone", 0, null); overflow.func_74768_a("Count", 256);
        cases.add(guard("count-byte-overflow", raw(overflow), language, 0));
        overflow = record("minecraft:stone", 0, null); overflow.func_74768_a("Damage", 65536);
        cases.add(guard("damage-short-overflow", raw(overflow), language, 0));
        cases.add(guard("noncanonical-id", raw(record("stone", 0, null)), language, 0));
        NBTTagCompound root = new NBTTagCompound(); root.func_74778_a("t", "i");
        root.func_74782_a("r", record("extendedae:infinity_cell", 0, root));
        ItemStack cycle = new ItemStack(registered("extendedae:infinity_cell")); cycle.func_77982_d(root);
        cases.add(guard("cycle", cycle, language, 0));
        ItemStack large = nested(1); large.func_77978_p().func_74773_a("noise", new byte[65537]);
        cases.add(guard("scalar-limit", large, language, 0));
        ItemStack deep = nested(1); NBTTagCompound cursor = deep.func_77978_p();
        for (int n = 0; n < 65; n++) { NBTTagCompound next = new NBTTagCompound(); cursor.func_74782_a("noise", next); cursor = next; }
        cases.add(guard("nbt-depth-limit", deep, language, 0));
        for (String template : new String[]{"%1000000000s", "%s%s", "%2$s", "%1$s %s %1$s"}) {
            entries.put("item.extendedae:infinity_cell.name", template);
            Object table = NativeAccess.create(PRODUCT + "minecraft.LanguageTable", "resolver-only", entries);
            cases.add(guard("format-" + template, nested(1), table, 0));
        }
        entries.remove("item.extendedae:infinity_cell.name");
        cases.add(guard("missing-effective-wrapper", nested(1), NativeAccess.create(PRODUCT + "minecraft.LanguageTable", "resolver-only", entries), 0));
        return result;
    }

    private static NBTTagCompound record(String id, int damage, NBTTagCompound data) {
        NBTTagCompound result = new NBTTagCompound(); result.func_74778_a("id", id); result.func_74774_a("Count", (byte) 1);
        result.func_74777_a("Damage", (short) damage); result.func_74772_a("Cnt", 1);
        if (data != null) result.func_74782_a("tag", data); return result;
    }

    private static ItemStack raw(NBTTagCompound record) {
        NBTTagCompound tag = new NBTTagCompound(); tag.func_74778_a("t", "i"); tag.func_74782_a("r", record);
        ItemStack result = new ItemStack(registered("extendedae:infinity_cell")); result.func_77982_d(tag); return result;
    }

    private static ItemStack nested(int depth) {
        NBTTagCompound record = record("minecraft:stone", 0, null);
        ItemStack result = null;
        for (int n = 0; n < depth; n++) { result = raw(record); record = record("extendedae:infinity_cell", 0, result.func_77978_p()); }
        return result;
    }

    private static JsonObject guard(String key, ItemStack stack, Object language, int depth) throws Exception {
        JsonObject row = new JsonObject(); row.addProperty("key", key);
        NBTTagCompound tag = stack.func_77978_p(); int count = stack.func_190916_E();
        NameTrace.begin();
        try {
            row.add("value", gson.toJsonTree(NativeAccess.call(NativeAccess.type(PRODUCT + "minecraft.SecondaryNameResolver"), "resolveItemName", stack, language, depth)));
        } finally { row.add("directCalls", gson.toJsonTree(NameTrace.end())); }
        NameTrace.begin();
        try {
            row.add("aliases", gson.toJsonTree(NativeAccess.call(NativeAccess.type(PRODUCT + "minecraft.SecondaryNameResolver"), "resolveItemNames", stack, "already observed primary")));
        } finally { row.add("collectorCalls", gson.toJsonTree(NameTrace.end())); }
        row.addProperty("sameTag", stack.func_77978_p() == tag); row.addProperty("sameCount", stack.func_190916_E() == count);
        return row;
    }

    private static JsonObject item(String key, ItemStack stack) throws Exception {
        NBTTagCompound before = stack.func_77955_b(new NBTTagCompound()).func_74737_b();
        JsonObject row = new JsonObject();
        row.addProperty("key", key); row.addProperty("id", String.valueOf(stack.func_77973_b().getRegistryName()));
        row.addProperty("numericId", Item.func_150891_b(stack.func_77973_b()));
        row.addProperty("class", stack.func_77973_b().getClass().getName()); row.addProperty("identity", System.identityHashCode(stack));
        NameTrace.begin(); String primary;
        try { primary = stack.func_82833_r(); } finally { row.add("nativeCalls", gson.toJsonTree(NameTrace.end())); }
        row.addProperty("nativeName", primary); row.addProperty("rawKey", stack.func_77973_b().func_77667_c(stack));
        if (key.equals("hell-infusion-book")) row.addProperty("enchantmentName", ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation("apotheosis", "hell_infusion")).func_77316_c(2));
        if (String.valueOf(stack.func_77973_b().getRegistryName()).startsWith("xreliquary:"))
            row.addProperty("rawTranslation", net.minecraft.client.resources.I18n.func_135052_a("xreliquary." + stack.func_77973_b().func_77667_c(stack) + ".name"));
        row.add("tooltip", gson.toJsonTree(stack.func_82840_a(Minecraft.func_71410_x().field_71439_g, ITooltipFlag.TooltipFlags.NORMAL)));
        row.add("advanced", gson.toJsonTree(stack.func_82840_a(Minecraft.func_71410_x().field_71439_g, ITooltipFlag.TooltipFlags.ADVANCED)));
        if (Loader.isModLoaded("polyglottooltip")) {
            NameTrace.begin();
            try { row.add("aliases", gson.toJsonTree(NativeAccess.call(NativeAccess.type(PRODUCT + "minecraft.SecondaryNameResolver"), "resolveItemNames", stack, primary))); }
            finally { row.add("aliasCalls", gson.toJsonTree(NameTrace.end())); }
        }
        row.addProperty("unchanged", before.equals(stack.func_77955_b(new NBTTagCompound())));
        row.addProperty("count", stack.func_190916_E()); row.addProperty("damage", stack.func_77952_i());
        row.addProperty("nbt", String.valueOf(stack.func_77978_p())); return row;
    }

    private static com.google.gson.JsonElement traceControl() {
        ItemStack stack = new ItemStack(Items.field_151045_i);
        FluidStack fluid = new FluidStack(FluidRegistry.WATER, 1000);
        NBTTagCompound tag = new NBTTagCompound(); NBTTagList list = new NBTTagList();
        list.func_74742_a(new NBTTagString("trace sentinel")); tag.func_74782_a("list", list);
        NBTTagCompound other = tag.func_74737_b();
        Map<String, Integer> observed;
        NameTrace.begin();
        try {
            stack.func_82833_r(); stack.func_77946_l();
            NBTTagCompound serialized = stack.func_77955_b(new NBTTagCompound()); new ItemStack(serialized);
            Items.field_151045_i.func_77653_i(stack);
            fluid.getLocalizedName(); fluid.copy(); fluid.writeToNBT(new NBTTagCompound());
            FluidRegistry.WATER.getLocalizedName(fluid); FluidRegistry.LAVA.getLocalizedName(new FluidStack(FluidRegistry.LAVA, 1000));
            for (String id : new String[]{"potion", "splash_potion", "lingering_potion", "tipped_arrow"}) {
                Item provider = registered("minecraft:" + id); provider.func_77653_i(new ItemStack(provider));
            }
            tag.hashCode(); tag.equals(other); tag.toString(); tag.func_74737_b();
        } finally { observed = NameTrace.end(); }
        return gson.toJsonTree(observed);
    }
}
