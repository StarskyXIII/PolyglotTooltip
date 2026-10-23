package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.Language;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.common.ForgeModContainer;
import net.minecraftforge.client.resource.VanillaResourceType;
import net.minecraftforge.fml.client.FMLClientHandler;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;

public final class BridgeDispatch {
    private static final ArrayBlockingQueue<Runnable> tasks = new ArrayBlockingQueue<>(128);
    private static final List<Delay> delays = new ArrayList<>();
    private static long ticks;
    private static final Gson gson = new Gson();

    private BridgeDispatch() { }

    public static CompletableFuture<JsonElement> call(String method, JsonObject params) {
        CompletableFuture<JsonElement> future = new CompletableFuture<>();
        if ("emi.release".equals(method)) {
            try { future.complete(EmiProviderProbe.release(params)); }
            catch (Throwable error) { future.completeExceptionally(error); }
            return future;
        }
        if (!tasks.offer(() -> {
            if (future.isDone()) return;
            try {
                if ("wait.ticks".equals(method)) {
                    int count = params.has("ticks") ? params.get("ticks").getAsInt() : 2;
                    if (count < 0 || count > 1200) throw new IllegalArgumentException("ticks must be 0..1200");
                    delays.add(new Delay(ticks + count, future));
                } else if ("world.command".equals(method)) {
                    command(params, future);
                } else if ("ae.world".equals(method)) {
                    AeWorldProbe.world(params, future);
                } else if ("addon.world".equals(method)) {
                    AddonWorldProbe.world(params, future);
                } else if ("addon.inherited.world".equals(method)) {
                    AddonInheritedProbe.world(params, future);
                } else if ("screen.render".equals(method)) {
                    ScreenProbe.request(params, future);
                } else if ("lifecycle.transition".equals(method)) {
                    LifecycleProbe.request(params, future);
                } else if ("emi.frame".equals(method)) {
                    EmiFrameProbe.request(params, future);
                } else if ("hud.capture".equals(method)) {
                    HudProbe.request(params, future);
                } else if ("hud.world".equals(method)) {
                    HudWorldProbe.world(params, future);
                } else if ("pe.world".equals(method)) {
                    ProjectEProbe.world(params, future);
                } else if ("hud.settings".equals(method)) {
                    HudSettings.apply(params, future);
                } else if ("it.world".equals(method)) {
                    IntegratedTerminalProbe.world(params, future);
                } else if ("rs.world".equals(method)) {
                    RefinedProbe.world(params, future);
                } else future.complete(dispatch(method, params));
            } catch (Throwable error) {
                future.completeExceptionally(error);
            }
        })) future.completeExceptionally(new IllegalStateException("Bridge queue full"));
        return future;
    }

    public static void tick() {
        ticks++;
        for (int index = 0; index < 16; index++) {
            Runnable task = tasks.poll();
            if (task == null) break;
            task.run();
        }
        for (int index = delays.size() - 1; index >= 0; index--) {
            Delay delay = delays.get(index);
            if (delay.tick <= ticks || delay.future.isDone()) {
                delay.future.complete(gson.toJsonTree(ticks));
                delays.remove(index);
            }
        }
    }

    static JsonElement dispatch(String method, JsonObject params) throws Exception {
        Minecraft client = Minecraft.func_71410_x();
        switch (method) {
            case "probe.consistency": return ConsistencyProbe.run(params);
            case "probe.coverage": return CoverageProbe.run(params);
            case "probe.coreItem": return CoverageProbe.item(params);
            case "probe.nameExport": return NameExportProbe.run(params);
            case "lifecycle.snapshot": return LifecycleProbe.snapshot();
            case "pack.runtime": return PackProbe.runtime();
            case "pack.setup": return PackProbe.setup();
            case "pack.item": return PackProbe.item(params);
            case "status": return status();
            case "probe.performance": return PerformanceProbe.run(params);
            case "probe.emiProfile": return EmiProbe.profile();
            case "emi": return EmiProbe.run(params);
            case "jemi": return JemiProbe.run(params);
            case "emi.arm": return EmiProviderProbe.arm(params);
            case "emi.provider": return EmiProviderProbe.state();
            case "ae.setup": return AeProbe.setup(params);
            case "ae.search": return AeProbe.search(params);
            case "ae.observe": return AeProbe.observe();
            case "ae.ui": return AeWorldProbe.ui(params);
            case "ae.interface": return AeInterfaceProbe.observe(params);
            case "ae.fluid.widgets": return AeFluidProbe.widgets();
            case "ae.fluid.hover": return AeFluidProbe.hover();
            case "mek.names": return MekProbe.names();
            case "addon.names": return AddonNameProbe.inspect(params);
            case "addon.widgets": return AddonTooltipProbe.widgets();
            case "addon.repo.setup": return AddonRepoProbe.setup();
            case "addon.repo.search": return AddonRepoProbe.search(params);
            case "addon.repo.observe": return AddonRepoProbe.observe();
            case "addon.repo.nameLeaf": return AddonRepoProbe.nameLeaf(params);
            case "addon.fc": return AddonCraftingProbe.inspect(params);
            case "addon.search.setup": return AddonSearchProbe.setup();
            case "addon.search.query": return AddonSearchProbe.query(params);
            case "addon.search.observe": return AddonSearchProbe.observe();
            case "addon.ui": return AddonWorldProbe.ui(params);
            case "addon.hover": return AddonWorldProbe.hover(params);
            case "addon.inherited.open": return AddonInheritedProbe.openWireless();
            case "addon.inherited.ui": return AddonInheritedProbe.ui(params);
            case "integration.names": return IntegrationNameProbe.inspect(params);
            case "rs.ui": return RefinedProbe.ui(params);
            case "integration.item.open": return IntegrationNameProbe.open(params);
            case "integration.widget.open": return IntegrationWidgetProbe.open(params);
            case "integration.widget.observe": return IntegrationWidgetProbe.observe(params);
            case "hud.observe": return HudProbe.observe();
            case "hud.delay": return HudDelivery.command(params);
            case "hud.boundary": return HudBoundaryProbe.command(params);
            case "pe.observe": return ProjectEProbe.observe();
            case "pe.ui": return ProjectEProbe.ui(params);
            case "it.observe": return IntegratedTerminalProbe.observe();
            case "it.ui": return IntegratedTerminalProbe.ui(params);
            case "it.predicate": return IntegratedTerminalProbe.predicate(params);
            case "carbon.open": return CarbonProbe.open(params);
            case "carbon.observe": return CarbonProbe.observe();
            case "carbon.action": return CarbonProbe.action(params);
            case "controlling.open": return ControllingProbe.open();
            case "controlling.observe": return ControllingProbe.observe();
            case "controlling.ui": return ControllingProbe.ui(params);
            case "jei.search": return RecipeProbe.search(params);
            case "jei.observe": return RecipeProbe.observe();
            case "jei.hide": return RecipeProbe.hide(params);
            case "jei.dynamic": return RecipeProbe.dynamic(params);
            case "jei.ui": return RecipeProbe.ui(params);
            case "jei.input": return RecipeProbe.input(params);
            case "screen.snapshot": return screen();
            case "screen.inventory":
                if (client.field_71439_g == null) throw new IllegalStateException("No player");
                client.func_147108_a(new GuiInventory(client.field_71439_g));
                return screen();
            case "screen.close": client.func_147108_a(null); return screen();
            case "input.type":
                String text = params.get("text").getAsString();
                if (text.length() > 4096) throw new IllegalArgumentException("Text too long");
                for (char character : text.toCharArray()) invokeScreen("func_73869_a", new Class<?>[]{char.class, int.class}, character, 0);
                return screen();
            case "input.key":
                int repeat = params.has("repeat") ? params.get("repeat").getAsInt() : 1;
                if (repeat < 1 || repeat > 4096) throw new IllegalArgumentException("Invalid repeat");
                for (int index = 0; index < repeat; index++) {
                    invokeScreen("func_73869_a", new Class<?>[]{char.class, int.class}, '\0', params.get("key").getAsInt());
                }
                return screen();
            case "input.click":
                GuiScreen clicked = client.field_71462_r;
                invokeScreen("func_73864_a", new Class<?>[]{int.class, int.class, int.class},
                        params.get("x").getAsInt(), params.get("y").getAsInt(), params.has("button") ? params.get("button").getAsInt() : 0);
                if (clicked == client.field_71462_r) {
                    invokeScreen("func_146286_b", new Class<?>[]{int.class, int.class, int.class},
                            params.get("x").getAsInt(), params.get("y").getAsInt(), params.has("button") ? params.get("button").getAsInt() : 0);
                }
                return screen();
            case "language.open": client.func_147108_a(new GuiLanguage(null, client.field_71474_y, client.func_135016_M())); return screen();
            case "language.set":
                String code = params.get("language").getAsString();
                Language language = client.func_135016_M().func_191960_a(code);
                if (language == null) throw new IllegalArgumentException("Unknown language " + code);
                client.func_135016_M().func_135045_a(language);
                client.field_71474_y.field_74363_ab = language.func_135034_a();
                FMLClientHandler.instance().refreshResources(VanillaResourceType.LANGUAGES);
                client.field_71466_p.func_78264_a(client.func_135016_M().func_135042_a() || client.field_71474_y.field_151455_aw);
                client.field_71466_p.func_78275_b(client.func_135016_M().func_135044_b());
                if (client.field_71462_r instanceof GuiLanguage) {
                    Field buttonsField = GuiScreen.class.getDeclaredField("field_146292_n");
                    buttonsField.setAccessible(true);
                    List<?> buttons = (List<?>) buttonsField.get(client.field_71462_r);
                    for (Object element : buttons) {
                        GuiButton button = (GuiButton) element;
                        if (button.field_146127_k == 6) button.field_146126_j = I18n.func_135052_a("gui.done");
                        else if (button.field_146127_k == 100) button.field_146126_j = client.field_71474_y.func_74297_c(GameSettings.Options.FORCE_UNICODE_FONT);
                    }
                }
                client.field_71474_y.func_74303_b();
                return status();
            case "resources.reload": client.func_110436_a(); return status();
            case "resources.select":
                ResourcePackRepository repository = client.func_110438_M();
                repository.func_110611_a();
                List<ResourcePackRepository.Entry> selected = new ArrayList<>();
                for (JsonElement name : params.getAsJsonArray("packs")) {
                    ResourcePackRepository.Entry found = null;
                    for (ResourcePackRepository.Entry entry : repository.func_110609_b()) {
                        if (entry.func_110515_d().equals(name.getAsString())) found = entry;
                    }
                    if (found == null) throw new IllegalArgumentException("Resource pack not found: " + name);
                    selected.add(found);
                }
                repository.func_148527_a(selected);
                client.func_110436_a();
                return status();
            case "client.command":
                if (client.field_71439_g == null) throw new IllegalStateException("No player");
                String clientCommand = params.get("command").getAsString();
                if (!clientCommand.startsWith("/") || clientCommand.length() > 256) throw new IllegalArgumentException("Invalid client command");
                if (params.has("capture") && params.get("capture").getAsBoolean()) return CoverageProbe.command(clientCommand);
                return gson.toJsonTree(net.minecraftforge.client.ClientCommandHandler.instance.func_71556_a(client.field_71439_g, clientCommand));
            case "world.create":
                String name = params.has("name") ? params.get("name").getAsString() : "pt112-fixture";
                if (!name.matches("pt112-[a-zA-Z0-9-]{1,64}")) throw new IllegalArgumentException("Invalid fixture name");
                client.func_71371_a(name, name, new WorldSettings(1122026L, GameType.CREATIVE, false, false, WorldType.field_77138_c));
                return status();
            case "screenshot":
                String file = params.get("name").getAsString();
                if (!file.matches("[a-zA-Z0-9_-]{1,100}\\.png")) throw new IllegalArgumentException("Invalid screenshot name");
                java.io.File output = new java.io.File(client.field_71412_D, "screenshots/" + file);
                if (output.exists()) throw new IllegalArgumentException("Screenshot already exists");
                ScreenShotHelper.func_148259_a(client.field_71412_D, file, client.field_71443_c,
                        client.field_71440_d, client.func_147110_a());
                if (!output.isFile() || output.length() == 0) throw new IllegalStateException("Screenshot was not saved at requested path");
                JsonObject shot = new JsonObject();
                shot.addProperty("path", output.getAbsolutePath());
                return shot;
            case "quit": client.func_71400_g(); return gson.toJsonTree(true);
            default: throw new IllegalArgumentException("Unknown method " + method);
        }
    }

    private static void command(JsonObject params, CompletableFuture<JsonElement> future) {
        MinecraftServer server = Minecraft.func_71410_x().func_71401_C();
        if (server == null) throw new IllegalStateException("No integrated server");
        String command = params.get("command").getAsString();
        if (command.length() > 32768) throw new IllegalArgumentException("Command too long");
        server.func_152344_a(() -> {
            if (future.isDone()) return;
            try {
                int count = server.func_71187_D().func_71556_a(server, command);
                future.complete(gson.toJsonTree(count));
            } catch (Throwable error) { future.completeExceptionally(error); }
        });
    }

    private static JsonObject status() {
        Minecraft client = Minecraft.func_71410_x();
        JsonObject result = new JsonObject();
        result.addProperty("minecraft", "1.12.2");
        result.addProperty("gameDirectory", client.field_71412_D.getAbsolutePath());
        result.addProperty("pid", java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
        result.addProperty("fixtureNonce", System.getProperty("clientdevbridge.fixtureNonce", ""));
        result.addProperty("java", System.getProperty("java.version"));
        result.addProperty("language", client.field_71474_y.field_74363_ab);
        result.addProperty("selectiveResourceReload", ForgeModContainer.selectiveResourceReloadEnabled);
        result.addProperty("forceUnicode", client.field_71474_y.field_151455_aw);
        result.addProperty("unicode", client.field_71466_p.func_82883_a());
        result.addProperty("bidi", client.field_71466_p.func_78260_a());
        result.addProperty("ticks", ticks);
        result.addProperty("world", client.field_71441_e != null);
        result.addProperty("player", client.field_71439_g != null);
        result.addProperty("screen", client.field_71462_r == null ? "" : client.field_71462_r.getClass().getName());
        try {
            Class<?> mixin = Class.forName("org.spongepowered.asm.mixin.MixinEnvironment");
            result.addProperty("mixinSource", mixin.getProtectionDomain().getCodeSource().getLocation().toString());
            Object environment = mixin.getMethod("getCurrentEnvironment").invoke(null);
            result.addProperty("mixinVersion", String.valueOf(mixin.getMethod("getVersion").invoke(environment)));
        } catch (ReflectiveOperationException ignored) { }
        JsonArray mods = new JsonArray();
        for (ModContainer mod : Loader.instance().getModList()) {
            JsonObject value = new JsonObject();
            value.addProperty("id", mod.getModId());
            value.addProperty("version", mod.getVersion());
            value.addProperty("file", mod.getSource() == null ? "" : mod.getSource().getName());
            mods.add(value);
        }
        result.add("mods", mods);
        if (client.field_71439_g != null) {
            result.addProperty("x", client.field_71439_g.field_70165_t);
            result.addProperty("y", client.field_71439_g.field_70163_u);
            result.addProperty("z", client.field_71439_g.field_70161_v);
        }
        return result;
    }

    private static JsonObject screen() throws Exception {
        GuiScreen screen = Minecraft.func_71410_x().field_71462_r;
        JsonObject result = new JsonObject();
        result.addProperty("class", screen == null ? "" : screen.getClass().getName());
        if (screen == null) return result;
        result.addProperty("width", screen.field_146294_l);
        result.addProperty("height", screen.field_146295_m);
        Field field = GuiScreen.class.getDeclaredField("field_146292_n");
        field.setAccessible(true);
        JsonArray buttons = new JsonArray();
        for (Object element : (List<?>) field.get(screen)) {
            GuiButton button = (GuiButton) element;
            JsonObject value = new JsonObject();
            value.addProperty("id", button.field_146127_k);
            value.addProperty("text", button.field_146126_j);
            value.addProperty("x", button.field_146128_h);
            value.addProperty("y", button.field_146129_i);
            value.addProperty("width", button.field_146120_f);
            value.addProperty("height", button.field_146121_g);
            value.addProperty("enabled", button.field_146124_l);
            buttons.add(value);
        }
        result.add("buttons", buttons);
        return result;
    }

    private static void invokeScreen(String name, Class<?>[] types, Object... args) throws Exception {
        GuiScreen screen = Minecraft.func_71410_x().field_71462_r;
        if (screen == null) throw new IllegalStateException("No screen");
        Method method = GuiScreen.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(screen, args);
    }

    private static final class Delay {
        private final long tick;
        private final CompletableFuture<JsonElement> future;
        private Delay(long tick, CompletableFuture<JsonElement> future) { this.tick = tick; this.future = future; }
    }
}
