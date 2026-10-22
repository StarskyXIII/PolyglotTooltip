package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.LoaderState;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public final class PackProbe {
    private static final Gson GSON = new Gson();
    private static ItemStack retainedItem;
    private static GuiChest itemScreen;

    private PackProbe() {}

    public static JsonObject item(JsonObject params) throws Exception {
        Minecraft client = Minecraft.func_71410_x();
        if (params.has("item")) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(params.get("item").getAsString()));
            if (item == null || client.field_71439_g == null) throw new IllegalStateException("Item or player unavailable");
            retainedItem = new ItemStack(item, params.has("count") ? params.get("count").getAsInt() : 1,
                    params.has("metadata") ? params.get("metadata").getAsInt() : 0);
            if (params.has("nbt")) retainedItem.func_77982_d(JsonToNBT.func_180713_a(params.get("nbt").getAsString()));
            InventoryBasic inventory = new InventoryBasic("Native pack item", true, 27);
            inventory.func_70299_a(0, retainedItem);
            itemScreen = new GuiChest(client.field_71439_g.field_71071_by, inventory);
            client.func_147108_a(itemScreen);
        }
        if (itemScreen == null || client.field_71462_r != itemScreen) throw new IllegalStateException("Retained item screen is not current");
        Slot slot = itemScreen.field_147002_h.func_75139_a(0);
        NBTTagCompound before = retainedItem.func_77955_b(new NBTTagCompound());
        JsonObject result = new JsonObject();
        result.addProperty("item", String.valueOf(retainedItem.func_77973_b().getRegistryName()));
        result.addProperty("metadata", retainedItem.func_77960_j());
        result.addProperty("count", retainedItem.func_190916_E());
        result.addProperty("nbt", String.valueOf(retainedItem.func_77978_p()));
        result.addProperty("nativeName", retainedItem.func_82833_r());
        result.addProperty("rawNativeName", net.minecraft.client.resources.I18n.func_135052_a(retainedItem.func_77973_b().func_77667_c(retainedItem) + ".name"));
        result.add("tooltip", GSON.toJsonTree(retainedItem.func_82840_a(client.field_71439_g, ITooltipFlag.TooltipFlags.NORMAL)));
        result.addProperty("serialized", before.toString());
        result.addProperty("unchanged", before.equals(retainedItem.func_77955_b(new NBTTagCompound())));
        result.addProperty("stackIdentity", System.identityHashCode(retainedItem));
        result.addProperty("screenIdentity", System.identityHashCode(itemScreen));
        result.addProperty("slotSame", slot.func_75211_c() == retainedItem);
        result.addProperty("screen", itemScreen.getClass().getName());
        result.addProperty("x", (Integer) NativeAccess.get(itemScreen, "field_147003_i") + slot.field_75223_e + 8);
        result.addProperty("y", (Integer) NativeAccess.get(itemScreen, "field_147009_r") + slot.field_75221_f + 8);
        return result;
    }

    public static JsonObject setup() throws Exception {
        Minecraft client = Minecraft.func_71410_x();
        JsonObject result = new JsonObject();
        result.add("before", settings());
        client.field_71474_y.field_82881_y = false;
        client.field_71474_y.field_74350_i = 60;
        client.field_71474_y.field_151451_c = 3;
        client.field_71474_y.func_74303_b();
        result.add("after", settings());
        return result;
    }

    private static JsonObject settings() {
        Minecraft client = Minecraft.func_71410_x();
        JsonObject result = new JsonObject();
        result.addProperty("pauseOnLostFocus", client.field_71474_y.field_82881_y);
        result.addProperty("maxFps", client.field_71474_y.field_74350_i);
        result.addProperty("renderDistance", client.field_71474_y.field_151451_c);
        return result;
    }

    public static JsonObject runtime() throws Exception {
        Minecraft client = Minecraft.func_71410_x();
        JsonObject result = new JsonObject();
        result.addProperty("gameDirectory", client.field_71412_D.getCanonicalPath());
        result.addProperty("pid", ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
        result.addProperty("fixtureNonce", System.getProperty("clientdevbridge.fixtureNonce", ""));
        result.addProperty("forge", ForgeVersion.getVersion());
        result.addProperty("loaderAvailable", Loader.instance().hasReachedState(LoaderState.AVAILABLE));
        result.addProperty("nativeMainMenu", client.field_71462_r instanceof GuiMainMenu);
        for (String key : new String[] {"java.version", "java.runtime.version", "java.vendor", "java.home",
                "java.class.path", "sun.java.command", "cleanroom.homeDir", "cleanroom.relauncher.parent"}) {
            result.addProperty(key, System.getProperty(key, ""));
        }
        result.add("jvmArguments", GSON.toJsonTree(ManagementFactory.getRuntimeMXBean().getInputArguments()));
        JsonArray sources = new JsonArray();
        for (URL source : Launch.classLoader.getSources()) {
            sources.add(source.toExternalForm());
        }
        result.add("classLoaderSources", sources);
        JsonObject origins = new JsonObject();
        for (String name : new String[] {"net.minecraft.item.ItemStack", "net.minecraftforge.fml.common.Loader",
                "net.minecraft.launchwrapper.Launch", "net.minecraft.launchwrapper.LaunchClassLoader",
                "org.spongepowered.asm.mixin.MixinEnvironment", "zone.rong.mixinbooter.IEarlyMixinLoader",
                "zone.rong.mixinbooter.ILateMixinLoader", "top.outlands.foundation.boot.Foundation"}) {
            JsonObject origin = new JsonObject();
            try {
                Class<?> type = Class.forName(name, false, PackProbe.class.getClassLoader());
                origin.addProperty("source", type.getProtectionDomain().getCodeSource().getLocation().toExternalForm());
                origin.addProperty("loader", type.getClassLoader().getClass().getName());
                if (name.endsWith("MixinEnvironment")) {
                    Object environment = type.getMethod("getCurrentEnvironment").invoke(null);
                    origin.addProperty("version", String.valueOf(type.getMethod("getVersion").invoke(environment)));
                }
            } catch (ClassNotFoundException missing) {
                origin.addProperty("absent", true);
            } catch (ReflectiveOperationException | LinkageError error) {
                origin.addProperty("error", error.toString());
            }
            origins.add(name, origin);
        }
        result.add("origins", origins);
        JsonArray selected = new JsonArray();
        for (ResourcePackRepository.Entry entry : client.func_110438_M().func_110613_c()) {
            selected.add(entry.func_110515_d());
        }
        result.add("resourcePacks", selected);
        result.add("settings", settings());
        JsonArray defaults = new JsonArray();
        for (Object pack : (List<?>) NativeAccess.get(client, "field_110449_ao")) {
            defaults.add(((IResourcePack) pack).func_130077_b());
        }
        result.add("defaultResourcePacks", defaults);
        JsonArray mods = new JsonArray();
        for (ModContainer mod : Loader.instance().getModList()) {
            JsonObject value = new JsonObject();
            value.addProperty("id", mod.getModId());
            value.addProperty("version", mod.getVersion());
            value.addProperty("source", mod.getSource() == null ? "" : mod.getSource().getCanonicalPath());
            Object instance = mod.getMod();
            Class<?> implementation = instance == null ? mod.getClass() : instance.getClass();
            value.addProperty("implementationClass", implementation.getName());
            if (implementation.getProtectionDomain().getCodeSource() != null) {
                value.addProperty("implementationSource", implementation.getProtectionDomain().getCodeSource().getLocation().toString());
            }
            mods.add(value);
        }
        result.add("mods", mods);
        if (Loader.isModLoaded("polyglottooltip")) {
            java.util.Properties catalog = new java.util.Properties();
            Class<?> selector = NativeAccess.type("com.starskyxiii.polyglottooltip.legacy.bootstrap.IntegrationProfileSelector");
            try (java.io.InputStream source = selector.getResourceAsStream("/polyglottooltip.integrations.properties")) {
                catalog.load(source);
            }
            JsonObject profiles = new JsonObject();
            for (String name : new java.util.TreeSet<String>(catalog.stringPropertyNames())) {
                String key = name.substring(0, name.lastIndexOf('.'));
                if (!profiles.has(key)) profiles.addProperty(key, (Boolean) NativeAccess.call(selector, "enabled", key));
            }
            result.add("profiles", profiles);
        }
        return result;
    }
}
