package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.GuiModList;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.GuiConfigEntries;
import net.minecraftforge.fml.common.ModContainer;
import java.lang.reflect.Field;
import java.util.List;

public final class ConfigScreenProbe {
    private ConfigScreenProbe() { }

    public static JsonObject mods(JsonObject params) throws Exception {
        Minecraft client = Minecraft.func_71410_x();
        String modid = params.get("modid").getAsString();
        GuiModList list = new GuiModList(client.field_71462_r);
        client.func_147108_a(list);
        Field field = GuiModList.class.getDeclaredField("mods");
        field.setAccessible(true);
        List<?> mods = (List<?>) field.get(list);
        for (int index = 0; index < mods.size(); index++) {
            if (((ModContainer) mods.get(index)).getModId().equals(modid)) {
                list.selectModIndex(index);
                JsonObject result = new JsonObject();
                result.addProperty("class", list.getClass().getName());
                result.addProperty("selected", index);
                return result;
            }
        }
        throw new IllegalArgumentException("Mod not in the list: " + modid);
    }

    public static JsonObject observe() {
        GuiConfig screen = current();
        JsonObject result = new JsonObject();
        result.addProperty("class", screen.getClass().getName());
        result.addProperty("modID", screen.modID);
        result.addProperty("title", screen.title);
        JsonArray entries = new JsonArray();
        for (GuiConfigEntries.IConfigEntry entry : screen.entryList.listEntries) {
            JsonObject value = new JsonObject();
            value.addProperty("name", entry.getName());
            value.addProperty("label", entry.getConfigElement().getLanguageKey());
            value.addProperty("type", entry.getClass().getSimpleName());
            Object current = entry.getCurrentValue();
            value.addProperty("value", current == null ? null : String.valueOf(current));
            if (entry.getCurrentValues() != null) {
                JsonArray values = new JsonArray();
                for (Object element : entry.getCurrentValues()) values.add(String.valueOf(element));
                value.add("values", values);
            }
            entries.add(value);
        }
        result.add("entries", entries);
        return result;
    }

    public static JsonObject set(JsonObject params) throws Exception {
        GuiConfig screen = current();
        String name = params.get("name").getAsString();
        for (GuiConfigEntries.IConfigEntry entry : screen.entryList.listEntries) {
            if (!entry.getName().equals(name)) continue;
            if (entry instanceof GuiConfigEntries.BooleanEntry) {
                Field value = GuiConfigEntries.BooleanEntry.class.getDeclaredField("currentValue");
                value.setAccessible(true);
                value.setBoolean(entry, params.get("value").getAsBoolean());
                return observe();
            }
            if (!(entry instanceof GuiConfigEntries.ArrayEntry)) throw new IllegalArgumentException("Not a list entry: " + name);
            JsonArray input = params.getAsJsonArray("values");
            Object[] values = new Object[input.size()];
            int index = 0;
            for (JsonElement element : input) values[index++] = element.getAsString();
            ((GuiConfigEntries.ArrayEntry) entry).setListFromChildScreen(values);
            return observe();
        }
        throw new IllegalArgumentException("No config entry " + name);
    }

    private static GuiConfig current() {
        GuiScreen screen = Minecraft.func_71410_x().field_71462_r;
        if (!(screen instanceof GuiConfig)) throw new IllegalStateException("No config screen: " + (screen == null ? "none" : screen.getClass().getName()));
        return (GuiConfig) screen;
    }
}
