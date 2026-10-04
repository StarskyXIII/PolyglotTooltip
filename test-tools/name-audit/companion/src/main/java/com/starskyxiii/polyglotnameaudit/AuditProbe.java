package com.starskyxiii.polyglotnameaudit;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.*;
import net.neoforged.fml.ModList;
import org.cyclops.clientdevbridge.mcadapter.ClientThread;
import org.cyclops.clientdevbridge.protocol.Dispatcher;
import java.util.*;

public final class AuditProbe {
    private static final ThreadCapture<Component> created = new ThreadCapture<>();

    public static void register(Dispatcher dispatcher) {
        if (!Boolean.getBoolean("nameaudit.enabled")) return;
        dispatcher.register("audit.identity", p -> ClientThread.submit(AuditProbe::identity));
        dispatcher.register("audit.collect", p -> ClientThread.submit(AuditProbe::collect));
        dispatcher.register("audit.observe", p -> ClientThread.submit(() -> observe(p.getAsJsonArray("samples"))));
        dispatcher.register("audit.render", p -> ClientThread.submit(() -> AuditScreen.open(p.getAsJsonObject("sample"))));
        dispatcher.register("audit.renderState", p -> ClientThread.submit(AuditScreen::state));
    }

    public static JsonObject identity() {
        Minecraft mc = Minecraft.getInstance();
        JsonObject out = new JsonObject();
        out.addProperty("session", System.getProperty("nameaudit.session", ""));
        out.addProperty("pid", ProcessHandle.current().pid());
        out.addProperty("gameDir", mc.gameDirectory.toPath().toAbsolutePath().normalize().toString());
        out.addProperty("language", mc.options.languageCode);
        out.addProperty("ready", mc.level != null && mc.player != null && mc.getOverlay() == null);
        JsonObject mods = new JsonObject();
        ModList.get().getMods().forEach(info -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("version", info.getVersion().toString());
            entry.addProperty("path", info.getOwningFile().getFile().getFilePath().toAbsolutePath().normalize().toString());
            mods.add(info.getModId(), entry);
        });
        out.add("mods", mods);
        if (ModList.get().isLoaded("polyglottooltip")) {
            try {
                Class<?> config = Class.forName("com.starskyxiii.polyglottooltip.Config");
                JsonObject settings = new JsonObject();
                for (String field : List.of("DISPLAY_LANGUAGE", "ALWAYS_SHOW")) {
                    Object value = config.getField(field).get(null);
                    settings.add(field, new Gson().toJsonTree(value.getClass().getMethod("get").invoke(value)));
                }
                out.add("settings", settings);
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        return out;
    }

    static JsonElement encode(ItemStack stack) {
        return ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, Minecraft.getInstance().level.registryAccess()), stack).getOrThrow();
    }

    static ItemStack decode(JsonElement json) {
        return ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, Minecraft.getInstance().level.registryAccess()), json).getOrThrow();
    }

    private static boolean target(ItemStack stack) {
        return Set.of("reliquary", "malum").contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
    }

    private static void add(JsonArray samples, ItemStack original, String source) {
        if (original.isEmpty()) return;
        ItemStack stack = original.copyWithCount(1);
        JsonObject sample = new JsonObject();
        sample.add("stack", encode(stack));
        sample.addProperty("source", source);
        sample.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        samples.add(sample);
    }

    private static JsonObject collect() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) throw new IllegalStateException("World is not ready");
        CreativeModeTabs.tryRebuildTabContents(mc.level.enabledFeatures(), true, mc.level.registryAccess());
        JsonArray samples = new JsonArray();
        for (var tab : CreativeModeTabs.allTabs()) {
            String key = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab).toString();
            tab.getDisplayItems().stream().filter(AuditProbe::target).forEach(s -> add(samples, s, "creative:" + key));
            tab.getSearchTabDisplayItems().stream().filter(AuditProbe::target).forEach(s -> add(samples, s, "search:" + key));
        }
        BuiltInRegistries.ITEM.stream().map(Item::getDefaultInstance).filter(AuditProbe::target).forEach(s -> add(samples, s, "registry"));
        add(samples, new ItemStack(Items.STONE), "control:stone");
        ItemStack literal = new ItemStack(Items.STONE);
        literal.set(DataComponents.CUSTOM_NAME, Component.literal("Audit Custom Name"));
        add(samples, literal, "control:literal");
        ItemStack translated = new ItemStack(Items.STONE);
        translated.set(DataComponents.CUSTOM_NAME, Component.translatable("item.minecraft.diamond"));
        add(samples, translated, "control:translated");
        ItemStack missing = new ItemStack(Items.STONE);
        missing.set(DataComponents.CUSTOM_NAME, Component.translatable("nameaudit.deliberately_missing"));
        add(samples, missing, "control:missing");
        for (JsonElement entry : samples.deepCopy()) {
            JsonObject sample = entry.getAsJsonObject();
            if (sample.get("item").getAsString().equals("malum:geas") && sample.get("source").getAsString().startsWith("creative:")) {
                ItemStack custom = decode(sample.get("stack"));
                custom.set(DataComponents.CUSTOM_NAME, Component.literal("Audit Custom Geas"));
                add(samples, custom, "control:literal-geas");
                custom.set(DataComponents.CUSTOM_NAME, Component.translatable("item.minecraft.diamond"));
                add(samples, custom, "control:translated-geas");
                custom.remove(DataComponents.CUSTOM_NAME);
                custom.set(DataComponents.ITEM_NAME, Component.translatable("item.minecraft.emerald"));
                add(samples, custom, "control:patched-geas");
                break;
            }
        }
        JsonObject out = new JsonObject();
        out.add("samples", samples);
        out.addProperty("complete", true);
        return out;
    }

    public static void capture(Component line) {
        created.capture(line);
    }

    private static boolean literal(Component component) {
        return component.getContents() instanceof PlainTextContents && component.getSiblings().stream().allMatch(AuditProbe::literal);
    }

    private static void missing(Component component, Set<String> keys) {
        if (component.getContents() instanceof TranslatableContents value) {
            if (!I18n.exists(value.getKey()) && value.getFallback() == null) keys.add(value.getKey());
            for (Object arg : value.getArgs()) if (arg instanceof Component child) missing(child, keys);
        }
        component.getSiblings().forEach(child -> missing(child, keys));
    }

    private static JsonObject line(Component value) {
        JsonObject out = new JsonObject();
        out.addProperty("text", value.getString());
        out.add("component", JsonParser.parseString(Component.Serializer.toJson(value, Minecraft.getInstance().level.registryAccess())));
        return out;
    }

    private static JsonObject observe(JsonArray samples) {
        if (samples.size() == 0 || samples.size() > 64) throw new IllegalArgumentException("Batch size must be 1..64");
        Minecraft mc = Minecraft.getInstance();
        JsonArray rows = new JsonArray();
        for (JsonElement value : samples) {
            JsonObject sample = value.getAsJsonObject();
            JsonObject row = new JsonObject();
            row.add("id", sample.get("id"));
            try {
                ItemStack stack = decode(sample.get("stack"));
                row.add("before", encode(stack));
                Component custom = stack.get(DataComponents.CUSTOM_NAME);
                row.addProperty("literalCustomName", custom != null && literal(custom));
                row.add("hoverName", line(stack.getHoverName()));
                List<Component> observed = created.begin();
                List<Component> lines;
                try {
                    lines = stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.Default.NORMAL);
                } finally { created.end(); }
                JsonArray tooltip = new JsonArray();
                lines.forEach(entry -> tooltip.add(line(entry)));
                row.add("tooltip", tooltip);
                JsonArray secondary = new JsonArray();
                for (Component component : observed) {
                    JsonObject entry = line(component);
                    JsonArray indices = new JsonArray();
                    for (int i = 0; i < lines.size(); i++) if (lines.get(i) == component) indices.add(i);
                    entry.add("indices", indices);
                    secondary.add(entry);
                }
                row.add("created", secondary);
                Set<String> keys = new TreeSet<>();
                if (!lines.isEmpty()) missing(lines.getFirst(), keys);
                row.add("missingTitleKeys", new Gson().toJsonTree(keys));
                row.add("after", encode(stack));
            } catch (Exception exception) {
                row.addProperty("error", exception.toString());
            }
            rows.add(row);
        }
        JsonObject out = new JsonObject();
        out.add("rows", rows);
        out.addProperty("complete", true);
        return out;
    }
}
