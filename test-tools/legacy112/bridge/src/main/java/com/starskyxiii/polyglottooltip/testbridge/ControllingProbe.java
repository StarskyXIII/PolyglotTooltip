package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.fml.client.config.GuiCheckBox;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ControllingProbe {
    private static final String TYPE = "us.getfluxed.controlsearch.client.gui.GuiNewControls";
    private static final Map<KeyBinding, Binding> saved = new LinkedHashMap<>();
    private ControllingProbe() { }
    public static JsonObject open() throws Exception {
        Minecraft client = Minecraft.func_71410_x(); client.func_147108_a((GuiScreen) NativeAccess.create(TYPE, null, client.field_71474_y)); return observe();
    }
    public static JsonObject ui(JsonObject params) throws Exception {
        GuiScreen screen = current();
        if (params.has("binding")) {
            String key = params.get("binding").getAsString(); KeyBinding binding = null;
            for (KeyBinding value : Minecraft.func_71410_x().field_71474_y.field_74324_K) if (value.func_151464_g().equals(key)) binding = value;
            if (binding == null) throw new IllegalArgumentException("Unknown native binding " + key);
            if (!saved.containsKey(binding)) saved.put(binding, new Binding(binding));
            binding.setKeyModifierAndCode(KeyModifier.valueOf(params.get("modifier").getAsString()), params.get("code").getAsInt()); KeyBinding.func_74508_b(); NativeAccess.call(screen, "refreshKeys");
        }
        if (params.has("restore") && params.get("restore").getAsBoolean()) {
            for (Map.Entry<KeyBinding, Binding> entry : saved.entrySet()) entry.getKey().setKeyModifierAndCode(entry.getValue().modifier, entry.getValue().code);
            saved.clear(); KeyBinding.func_74508_b(); NativeAccess.call(screen, "refreshKeys");
        }
        for (String mode : new String[]{"boxSearchCategory", "boxSearchKey", "buttonConflict", "buttonNone", "buttonToggleKeys", "buttonSorting"}) if (params.has(mode) && params.get(mode).getAsBoolean()) {
            GuiButton button = (GuiButton) NativeAccess.get(screen, mode); if (!button.field_146124_l || !button.field_146125_m) throw new IllegalStateException("Native controlling button disabled");
            int x = button.field_146128_h + button.field_146120_f / 2, y = button.field_146129_i + button.field_146121_g / 2;
            NativeAccess.call(screen, "func_73864_a", x, y, 0); NativeAccess.call(screen, "func_146286_b", x, y, 0);
        }
        if (params.has("query")) {
            GuiTextField field = (GuiTextField) NativeAccess.get(screen, "search"); field.func_146195_b(true); field.func_146190_e(0); field.func_146199_i(field.func_146179_b().length());
            NativeAccess.call(screen, "func_73869_a", '\0', 14); for (char c : params.get("query").getAsString().toCharArray()) NativeAccess.call(screen, "func_73869_a", c, 0);
        }
        return observe();
    }
    public static JsonObject observe() throws Exception {
        GuiScreen screen = current(); Object list = NativeAccess.get(screen, "field_146494_r"); JsonObject result = new JsonObject();
        result.addProperty("screen", System.identityHashCode(screen)); result.addProperty("list", System.identityHashCode(list));
        result.addProperty("query", ((GuiTextField) NativeAccess.get(screen, "search")).func_146179_b());
        result.addProperty("refreshPending", !((GuiTextField) NativeAccess.get(screen, "search")).func_146179_b().equals(NativeAccess.get(screen, "lastFilterText")));
        for (String key : new String[]{"conflicts", "none", "toggleFreeKeys", "sortingType", "lastFilterText"}) result.addProperty(key, String.valueOf(NativeAccess.get(screen, key)));
        result.addProperty("listRole", (Boolean) NativeAccess.get(screen, "toggleFreeKeys") ? "suppressed-key-entry-list" : "filtered-entries-including-offscreen");
        for (String key : new String[]{"boxSearchCategory", "boxSearchKey"}) result.addProperty(key, ((GuiCheckBox) NativeAccess.get(screen, key)).isChecked());
        result.add("visible", entries((List<?>) NativeAccess.call(list, "getListEntries"))); result.add("all", entries((List<?>) NativeAccess.call(list, "getListEntriesAll"))); return result;
    }
    private static JsonArray entries(List<?> entries) throws Exception {
        JsonArray result = new JsonArray();
        for (Object entry : entries) {
            JsonObject row = new JsonObject(); row.addProperty("identity", System.identityHashCode(entry)); row.addProperty("class", entry.getClass().getSimpleName());
            if (entry.getClass().getSimpleName().equals("KeyEntry")) {
                KeyBinding binding = (KeyBinding) NativeAccess.call(entry, "getKeybinding"); row.addProperty("key", binding.func_151464_g()); row.addProperty("category", binding.func_151466_e());
                row.addProperty("code", binding.func_151463_i()); row.addProperty("modifier", binding.getKeyModifier().name()); row.addProperty("label", (String) NativeAccess.get(entry, "keyDesc"));
            } else if (entry.getClass().getSimpleName().equals("CategoryEntry")) row.addProperty("label", (String) NativeAccess.get(entry, "labelText"));
            result.add(row);
        }
        return result;
    }
    private static GuiScreen current() { GuiScreen screen = Minecraft.func_71410_x().field_71462_r; if (screen == null || !screen.getClass().getName().equals(TYPE)) throw new IllegalStateException("No native controlling screen"); return screen; }
    private static final class Binding { final int code; final KeyModifier modifier; Binding(KeyBinding value) { code = value.func_151463_i(); modifier = value.getKeyModifier(); } }
}
