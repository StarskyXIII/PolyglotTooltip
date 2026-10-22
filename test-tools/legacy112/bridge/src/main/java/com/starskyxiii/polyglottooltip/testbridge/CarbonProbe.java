package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.common.Loader;
import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CarbonProbe {
    private static final String[] defaults = {"zero", "duplicate", "middle", "duplicate", "last"};
    private static final Map<Integer, Object> controls = new LinkedHashMap<>(), rows = new LinkedHashMap<>(), retained = new LinkedHashMap<>();
    private static GuiScreen observed;
    private static Configuration configuration;
    private static Property property;
    private static File file;
    private static Object array;
    private static boolean modern;
    private static final Map<GuiScreen, Boolean> dialogs = new IdentityHashMap<>();
    private CarbonProbe() { }

    public static JsonObject open(JsonObject params) throws Exception {
        Minecraft client = Minecraft.func_71410_x(); String name = params.get("name").getAsString();
        if (!name.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid Carbon fixture name");
        File folder = new File(client.field_71412_D, "config/polyglot-devbridge").getCanonicalFile();
        file = new File(folder, "carbon-" + name + ".cfg").getCanonicalFile();
        if (!file.toPath().startsWith(client.field_71412_D.getCanonicalFile().toPath()) || !file.getParentFile().equals(folder)) throw new IllegalStateException("Carbon fixture path escaped");
        boolean existed = file.isFile();
        if (params.has("requireExisting") && params.get("requireExisting").getAsBoolean() && !existed) throw new IllegalStateException("Carbon restart fixture is missing");
        Files.createDirectories(folder.toPath());
        configuration = new Configuration(file); configuration.load();
        if (existed) validate(configuration);
        property = configuration.get("fixture", "rows", defaults);
        if (!existed) configuration.save();
        Object configs = NativeAccess.create("carbonconfiglib.gui.impl.forge.ForgeConfigs", Loader.instance().getIndexedModList().get("polyglotdevbridge"), Collections.singletonList(configuration));
        Class<?> factory;
        try { factory = NativeAccess.type("carbonconfiglib.gui.screens.ConfigScreenFactory"); modern = true; }
        catch (ClassNotFoundException absent) { factory = NativeAccess.type("carbonconfiglib.gui.screen.ConfigScreenFactory"); modern = false; }
        Object builder = NativeAccess.create(factory.getName(), configs);
        GuiScreen screen = (GuiScreen) NativeAccess.call(builder, "createConfigGui", (Object) null);
        array = null; retained.clear(); dialogs.clear(); CarbonTrace.reset(); client.func_147108_a(screen);
        JsonObject result = observe(); result.addProperty("existed", existed); return result;
    }
    public static JsonObject action(JsonObject params) throws Exception {
        GuiScreen screen = current();
        if (screen != observed) throw new IllegalStateException("Carbon screen changed since observation");
        String action = params.get("action").getAsString(); JsonObject input = null;
        if (action.equals("control")) {
            Object control = controls.get(params.get("identity").getAsInt());
            if (control == null) throw new IllegalArgumentException("Unknown current Carbon control"); click(control);
        } else if (action.equals("edit")) {
            Object row = rows.get(params.get("identity").getAsInt());
            if (row == null || !row.getClass().getSimpleName().equals("StringElement")) throw new IllegalArgumentException("Unknown current Carbon text row");
            GuiTextField field = (GuiTextField) NativeAccess.get(row, modern ? "text" : "edit");
            if (modern) {
                Object check = NativeAccess.get(row, "edit"); if (!(Boolean) NativeAccess.call(check, "selected")) click(check);
                if (!(Boolean) NativeAccess.call(field, "isActive")) throw new IllegalStateException("Carbon row edit did not enable field");
            }
            field.func_146195_b(true); field.func_146190_e(0); field.func_146199_i(field.func_146179_b().length()); field.func_146191_b(params.get("text").getAsString());
        } else if (action.equals("add")) {
            if (!modern) throw new IllegalArgumentException("Use the native old ArrayScreen add control");
            Object entry = null; for (Object value : rows.values()) if (value.getClass().getName().endsWith("ArrayElement$AddElement")) entry = value;
            if (entry == null) throw new IllegalStateException("No current native Carbon add row");
            GuiButton selector = (GuiButton) NativeAccess.get(entry, "selector");
            if (!selector.field_146125_m || !selector.field_146124_l) throw new IllegalStateException("Native Carbon add row is unavailable");
            if (!(Boolean) NativeAccess.call(entry, "mouseClick", selector.field_146128_h + selector.field_146120_f * 0.5, selector.field_146129_i + selector.field_146121_g * 0.5, 0)) throw new IllegalStateException("Native Carbon add click rejected");
        } else if (action.equals("drag")) {
            if (!modern || !screen.getClass().getSimpleName().equals("ConfigScreen")) throw new IllegalArgumentException("No modern Carbon list to drag");
            Object row = rows.get(params.get("identity").getAsInt()), target = rows.get(params.get("target").getAsInt()), list = null;
            if (row == null || target == null || !row.getClass().getSimpleName().equals("StringElement") || !target.getClass().getSimpleName().equals("StringElement")) throw new IllegalArgumentException("Unknown Carbon drag rows");
            for (String key : new String[]{"rowOne", "rowTwo", "rowThree"}) {
                Object state = NativeAccess.get(screen, key); List<?> nodes = (List<?>) NativeAccess.call(state, "getNodes");
                if (nodes.contains(row) && nodes.contains(target)) list = NativeAccess.call(state, "getOwner");
            }
            if (list == null) throw new IllegalStateException("Carbon rows do not share a native list");
            List<?> children = (List<?>) NativeAccess.call(list, "children"); int from = children.indexOf(row), to = children.indexOf(target);
            if (Math.abs(from - to) != 1) throw new IllegalArgumentException("Carbon drag requires adjacent visible rows");
            double x = (Integer) NativeAccess.call(list, "getRowLeft") + 2.0;
            double y = ((GuiTextField) NativeAccess.get(row, "text")).field_146210_g + 8.0, targetY = ((GuiTextField) NativeAccess.get(target, "text")).field_146210_g + 8.0;
            if (!(Boolean) NativeAccess.call(list, "isMouseOver", x, y) || !(Boolean) NativeAccess.call(list, "isMouseOver", x, targetY) || NativeAccess.call(list, "getHovered", x, y) != row || NativeAccess.call(list, "getHovered", x, targetY) != target) throw new IllegalStateException("Carbon drag geometry does not identify visible requested rows");
            try {
                NativeAccess.call(list, "mouseClick", x, y, 0);
                if (NativeAccess.get(list, "dragging") != row) throw new IllegalStateException("Native Carbon drag did not capture requested row");
                NativeAccess.call(list, "mouseDrag", x, targetY, 0, 0.0, targetY - y);
                input = new JsonObject(); input.addProperty("path", "CarbonList.mouseClick/mouseDrag/mouseRelease"); input.addProperty("row", System.identityHashCode(row)); input.addProperty("target", System.identityHashCode(target)); input.addProperty("from", from); input.addProperty("to", to); input.addProperty("x", x); input.addProperty("y", y); input.addProperty("targetY", targetY);
            } finally { NativeAccess.call(list, "mouseRelease", x, targetY, 0); }
            if (NativeAccess.get(list, "dragging") != null) throw new IllegalStateException("Native Carbon drag did not release");
            if (children.get(to) != row || children.get(from) != target || (Integer) NativeAccess.call(array, "indexOf", NativeAccess.get(row, "node")) != to || (Integer) NativeAccess.call(array, "indexOf", NativeAccess.get(target, "node")) != from) throw new IllegalStateException("Native Carbon GUI and node drag orders disagree");
        } else if (action.equals("retain")) {
            Object row = rows.get(params.get("identity").getAsInt()); if (row == null) throw new IllegalArgumentException("Unknown Carbon row");
            Object node = NativeAccess.get(row, modern ? "node" : "value"); retained.put(System.identityHashCode(node), node);
        } else if (action.equals("retainedSave")) {
            Object node = retained.get(params.get("identity").getAsInt());
            if (node == null || array == null || (Integer) NativeAccess.call(array, "indexOf", node) != -1) throw new IllegalArgumentException("Retained Carbon node is not detached"); NativeAccess.call(node, "save");
        } else if (action.equals("close")) NativeAccess.call(screen, "onClose");
        else if (action.equals("modelMove")) {
            if (array == null) throw new IllegalStateException("No observed Carbon array");
            String method = params.get("method").getAsString();
            if (method.equals("swap") && modern) NativeAccess.call(array, method, params.get("from").getAsInt(), params.get("to").getAsInt());
            else if (method.equals("moveUp") || method.equals("moveDown")) NativeAccess.call(array, method, params.get("from").getAsInt());
            else throw new IllegalArgumentException("Invalid native Carbon movement");
        } else throw new IllegalArgumentException("Unknown Carbon action");
        GuiScreen next = Minecraft.func_71410_x().field_71462_r;
        if (next instanceof net.minecraft.client.gui.GuiYesNo && next != screen) dialogs.put(next, true);
        if (next == null || next instanceof net.minecraft.client.gui.GuiMainMenu) { JsonObject result = new JsonObject(); result.addProperty("closed", true); result.add("callbacks", CarbonTrace.snapshot()); persistence(result); observed = null; return result; }
        JsonObject result = observe(); if (input != null) result.add("input", input); return result;
    }
    private static void click(Object value) throws Exception {
        if (!(value instanceof GuiButton)) throw new IllegalArgumentException("Not a native Carbon button");
        GuiButton button = (GuiButton) value;
        if (!button.field_146125_m || !button.field_146124_l || button.field_146120_f <= 0 || button.field_146121_g <= 0) throw new IllegalStateException("Carbon button not active/visible");
        double x = button.field_146128_h + button.field_146120_f * 0.5, y = button.field_146129_i + button.field_146121_g * 0.5;
        if (current() instanceof net.minecraft.client.gui.GuiYesNo) {
            GuiScreen screen = current(); NativeAccess.call(screen, "func_73864_a", (int) x, (int) y, 0);
            NativeAccess.call(screen, "func_146286_b", (int) x, (int) y, 0);
        } else if (!(Boolean) NativeAccess.call(value, "mouseClick", x, y, 0)) throw new IllegalStateException("Native Carbon button rejected click");
    }
    public static JsonObject observe() throws Exception {
        GuiScreen screen = current(); observed = screen; controls.clear(); rows.clear();
        JsonObject result = new JsonObject(); result.addProperty("screen", screen.getClass().getName()); result.addProperty("identity", System.identityHashCode(screen)); result.addProperty("modern", modern);
        JsonArray entries = new JsonArray();
        for (Object entry : entries(screen)) {
            rows.put(System.identityHashCode(entry), entry); JsonObject row = new JsonObject(); row.addProperty("identity", System.identityHashCode(entry)); row.addProperty("class", entry.getClass().getName());
            if (entry.getClass().getSimpleName().equals("StringElement")) {
                Object node = NativeAccess.get(entry, modern ? "node" : "value"); Object owner = NativeAccess.get(entry, "array");
                if (owner != null) { array = owner; row.addProperty("index", (Integer) NativeAccess.call(owner, "indexOf", node)); }
                row.addProperty("node", System.identityHashCode(node)); row.addProperty("value", (String) NativeAccess.call(node, "get"));
                GuiTextField text = (GuiTextField) NativeAccess.get(entry, modern ? "text" : "edit"); row.addProperty("text", text.func_146179_b());
                row.addProperty("x", text.field_146209_f); row.addProperty("y", text.field_146210_g);
            }
            if (entry.getClass().getSimpleName().equals("ArrayElement")) { Object value = NativeAccess.get(entry, "node"); if (value.getClass().getName().equals("carbonconfiglib.gui.impl.forge.ForgeArray")) array = value; }
            JsonArray buttons = new JsonArray();
            for (String key : new String[]{"button", "open", "textBox", "delete", "revert", "reset", "edit", "setReset", "setDefault", "moveUp", "moveDown"}) {
                try { Object value = NativeAccess.get(entry, key); if (value instanceof GuiButton) buttons.add(control((GuiButton) value, key)); }
                catch (NoSuchFieldException ignored) { }
            }
            if (!modern) try { for (Object child : (List<?>) NativeAccess.call(entry, "children")) if (child instanceof GuiButton && !controls.containsKey(System.identityHashCode(child))) buttons.add(control((GuiButton) child, "child")); } catch (NoSuchMethodException ignored) { }
            row.add("controls", buttons); entries.add(row);
        }
        result.add("entries", entries); JsonArray buttons = new JsonArray();
        List<?> children = screen instanceof net.minecraft.client.gui.GuiYesNo ? (List<?>) NativeAccess.get(screen, "field_146292_n") : (List<?>) NativeAccess.call(screen, "children");
        for (Object child : children) if (child instanceof GuiButton) buttons.add(control((GuiButton) child, "screen"));
        result.add("controls", buttons);
        if (modern && screen.getClass().getSimpleName().equals("ConfigScreen")) {
            for (String key : new String[]{"autoSave", "bulkEdit"}) {
                Object state = NativeAccess.get(screen, key); result.addProperty(key, (Boolean) NativeAccess.call(state, "getValue"));
                GuiButton found = null;
                for (Object child : children) if (child.getClass().getName().equals("carbonconfiglib.gui.base.widgets.CarbonCheckBox") && NativeAccess.call(child, "getState") == state) found = (GuiButton) child;
                if (found == null) throw new IllegalStateException("Native Carbon checkbox owner missing " + key);
                result.add(key + "Control", control(found, key));
            }
            result.add("saveControl", control((GuiButton) NativeAccess.get(screen, "save"), "save"));
            result.addProperty("root", System.identityHashCode(NativeAccess.get(screen, "rootElement")));
        }
        if (!modern && screen.getClass().getSimpleName().equals("ArrayScreen")) array = NativeAccess.get(screen, "array");
        if (array != null) {
            CarbonTrace.bind(array);
            JsonObject model = new JsonObject(); model.addProperty("identity", System.identityHashCode(array)); model.add("current", strings((List<?>) NativeAccess.get(array, "currentValues")));
            JsonArray values = new JsonArray(); for (Object value : (List<?>) NativeAccess.get(array, "values")) { JsonObject row = new JsonObject(); row.addProperty("identity", System.identityHashCode(value)); row.addProperty("value", (String) NativeAccess.call(value, "get")); values.add(row); }
            model.add("values", values); result.add("model", model);
        }
        result.add("callbacks", CarbonTrace.snapshot());
        JsonArray kept = new JsonArray(); for (Map.Entry<Integer, Object> entry : retained.entrySet()) { JsonObject row = new JsonObject(); row.addProperty("identity", entry.getKey()); row.addProperty("index", array == null ? -1 : (Integer) NativeAccess.call(array, "indexOf", entry.getValue())); kept.add(row); } result.add("retained", kept);
        persistence(result);
        return result;
    }
    private static void persistence(JsonObject result) throws Exception {
        if (file != null) {
            result.addProperty("file", file.getAbsolutePath()); result.add("property", strings(java.util.Arrays.asList(property.getStringList())));
            Configuration disk = new Configuration(file); disk.load(); validate(disk); result.add("disk", strings(java.util.Arrays.asList(disk.getCategory("fixture").get("rows").getStringList())));
            byte[] data = Files.readAllBytes(file.toPath()), digest = MessageDigest.getInstance("SHA-256").digest(data); StringBuilder hash = new StringBuilder(); for (byte value : digest) hash.append(String.format("%02x", value & 255)); result.addProperty("sha256", hash.toString());
        }
    }
    private static List<?> entries(GuiScreen screen) throws Exception {
        if (!modern) { try { return (List<?>) NativeAccess.get(screen, "allEntries"); } catch (NoSuchFieldException absent) { return Collections.emptyList(); } }
        if (screen.getClass().getSimpleName().equals("ConfigListScreen")) return (List<?>) NativeAccess.call(NativeAccess.call(NativeAccess.get(screen, "listState"), "getOwner"), "children");
        List<Object> entries = new ArrayList<>(); Map<Object, Boolean> seen = new IdentityHashMap<>();
        if (screen.getClass().getSimpleName().equals("ConfigScreen")) for (String name : new String[]{"rowOne", "rowTwo", "rowThree"}) for (Object entry : (List<?>) NativeAccess.call(NativeAccess.call(NativeAccess.get(screen, name), "getOwner"), "children")) if (seen.put(entry, true) == null) entries.add(entry);
        return entries;
    }
    private static JsonObject control(GuiButton value, String role) {
        int id = System.identityHashCode(value); Object prior = controls.put(id, value); if (prior != null && prior != value) throw new IllegalStateException("Carbon control identity collision");
        JsonObject result = new JsonObject(); result.addProperty("identity", id); result.addProperty("role", role); result.addProperty("text", value.field_146126_j);
        result.addProperty("x", value.field_146128_h); result.addProperty("y", value.field_146129_i); result.addProperty("width", value.field_146120_f); result.addProperty("height", value.field_146121_g); result.addProperty("enabled", value.field_146124_l); result.addProperty("visible", value.field_146125_m); return result;
    }
    private static JsonArray strings(List<?> values) { JsonArray result = new JsonArray(); for (Object value : values) result.add(String.valueOf(value)); return result; }
    private static void validate(Configuration config) { if (!config.hasKey("fixture", "rows") || !config.getCategory("fixture").get("rows").isList() || config.getCategory("fixture").get("rows").getType() != Property.Type.STRING) throw new IllegalStateException("Persisted Carbon rows are missing or not a string list"); }
    private static GuiScreen current() { GuiScreen screen = Minecraft.func_71410_x().field_71462_r; if (screen == null || !screen.getClass().getName().startsWith("carbonconfiglib.gui.") && !dialogs.containsKey(screen)) throw new IllegalStateException("No owned native Carbon screen"); return screen; }
}
