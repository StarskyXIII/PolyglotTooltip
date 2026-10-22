package com.starskyxiii.polyglottooltip.testbridge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;

@Mod(modid = "polyglotdevbridge", name = "Polyglot DevBridge", version = "1.0.0", clientSideOnly = true,
        acceptedMinecraftVersions = "[1.12.2]", acceptableRemoteVersions = "*")
@Mod.EventBusSubscriber(modid = "polyglotdevbridge")
public final class BridgeMod {
    static { BridgeProperties.apply(); }
    @SubscribeEvent
    public static void items(net.minecraftforge.event.RegistryEvent.Register<net.minecraft.item.Item> event) {
        if (Boolean.getBoolean("clientdevbridge.enabled")) CoreProbe.registerFixtures(event.getRegistry());
    }

    @SubscribeEvent
    public static void models(net.minecraftforge.client.event.ModelRegistryEvent event) {
        if (!Boolean.getBoolean("clientdevbridge.enabled")) return;
        net.minecraft.util.ResourceLocation id = new net.minecraft.util.ResourceLocation("polyglotdevbridge", "emi_gate");
        net.minecraftforge.client.model.ModelLoader.setCustomModelResourceLocation(net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.getValue(id), 0,
                new net.minecraft.client.renderer.block.model.ModelResourceLocation(id, "inventory"));
    }

    @Mod.EventHandler
    public void initialize(FMLInitializationEvent event) throws Exception {
        if (!Boolean.getBoolean("clientdevbridge.enabled")) return;
        MinecraftForge.EVENT_BUS.register(this);
        // A bridge that cannot listen is reported, never allowed to crash the game it was attached to.
        try {
            BridgeServer server = new BridgeServer(Integer.getInteger("clientdevbridge.port", 25612));
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(server::close, "polyglot-bridge-shutdown"));
            LogManager.getLogger("PolyglotDevBridge").info("Listening on 127.0.0.1:{}", server.port());
        } catch (Throwable error) {
            LogManager.getLogger("PolyglotDevBridge").error("Bridge unavailable; the game continues without it", error);
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            BridgeDispatch.tick();
            ScreenProbe.tick();
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST, receiveCanceled = true)
    public void tooltip(net.minecraftforge.client.event.RenderTooltipEvent.Pre event) {
        TooltipProbe.event(event);
    }

    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST, receiveCanceled = true)
    public void drawNative(net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Pre event) {
        ScreenProbe.drawNative(event);
    }

    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST, receiveCanceled = true)
    public void draw(net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Pre event) {
        ScreenProbe.draw(event);
        EmiFrameProbe.before(event);
    }

    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
    public void capture(net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Post event) {
        ScreenProbe.capture(event);
        EmiFrameProbe.after(event);
    }

    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST)
    public void hudStart(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) { PerformanceProbe.frame(true); HudProbe.startFrame(); EmiFrameProbe.start(); }
    }

    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST)
    public void performanceStart(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) PerformanceProbe.tick(true);
    }

    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
    public void performanceEnd(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) PerformanceProbe.tick(false);
    }

    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST)
    public void hudBoundaryTick(TickEvent.ClientTickEvent event) { HudBoundaryProbe.maintain(); }

    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
    public void hudEnd(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END) { HudProbe.endFrame(); EmiFrameProbe.end(); PerformanceProbe.frame(false); }
    }

    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
    public void config(net.minecraftforge.fml.client.event.ConfigChangedEvent.OnConfigChangedEvent event) { CarbonTrace.changed(event); }

    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
    public void config(net.minecraftforge.fml.client.event.ConfigChangedEvent.PostConfigChangedEvent event) { CarbonTrace.changed(event); }

    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
    public void hudBoundary(net.minecraftforge.fml.common.eventhandler.Event event) { HudBoundaryProbe.event(event); }
}
