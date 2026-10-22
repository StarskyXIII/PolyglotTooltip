package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class HudWorldProbe {
    private static final Map<String, UUID> entities = new LinkedHashMap<>();
    private static WorldServer owned;
    private HudWorldProbe() { }

    public static void world(JsonObject params, CompletableFuture<JsonElement> future) {
        Minecraft client = Minecraft.func_71410_x(); MinecraftServer server = client.func_71401_C();
        if (server == null || client.field_71439_g == null) throw new IllegalStateException("No integrated player");
        UUID id = client.field_71439_g.func_110124_au();
        net.minecraft.world.World clientWorld = client.field_71441_e;
        Entity clientPlayer = client.field_71439_g;
        server.func_152344_a(() -> {
            if (future.isDone()) return;
            try {
                if (client.func_71401_C() != server || client.field_71441_e != clientWorld || client.field_71439_g != clientPlayer) throw new IllegalStateException("HUD world context changed");
                EntityPlayerMP player = server.func_184103_al().func_177451_a(id);
                WorldServer world = server.func_71218_a(0);
                if (player == null || player.field_70170_p != world || !world.func_72912_H().func_76065_j().startsWith("pt112-")) throw new IllegalStateException("Not an owned HUD world");
                if (owned != world) { entities.clear(); owned = world; }
                if (params.has("prepare") && params.get("prepare").getAsBoolean()) prepare(world);
                String target = params.has("target") ? params.get("target").getAsString() : "stone";
                int offset = params.has("offset") ? params.get("offset").getAsInt() : 0;
                if (offset < 0 || offset > 12) throw new IllegalArgumentException("HUD offset out of arena");
                double x, y = 4.5, z = 0.5;
                JsonObject origin = new JsonObject();
                if (target.equals("sky")) { x = 40.5; y = 60; z = 3.5; }
                else if (entities.containsKey(target)) {
                    Entity entity = world.func_175733_a(entities.get(target));
                    if (entity == null) throw new IllegalStateException("HUD entity missing");
                    if (params.has("chickenType")) {
                        if (!entity.getClass().getName().equals("com.setycz.chickens.entity.EntityChickensChicken")) throw new IllegalArgumentException("Not a fixture chicken");
                        String type = params.get("chickenType").getAsString();
                        Object entry = NativeAccess.call(NativeAccess.type("com.setycz.chickens.registry.ChickensRegistry"), "getByRegistryName", type);
                        if (entry == null || !Boolean.TRUE.equals(NativeAccess.call(entry, "isEnabled"))) throw new IllegalArgumentException("Unknown or disabled fixture chicken type");
                        NativeAccess.call(entity, "setChickenType", type);
                        stabilizeChicken(entity);
                    }
                    if (params.has("customName")) entity.func_96094_a(params.get("customName").getAsString());
                    if (params.has("remove") && params.get("remove").getAsBoolean()) entity.func_70106_y();
                    x = entity.field_70165_t; y = entity.field_70163_u + entity.field_70131_O * 0.5; z = entity.field_70161_v;
                    origin.addProperty("uuid", entity.func_110124_au().toString()); origin.addProperty("id", entity.func_145782_y());
                    if (entity.getClass().getName().equals("com.setycz.chickens.entity.EntityChickensChicken")) {
                        origin.addProperty("chickenType", (String) NativeAccess.call(entity, "getChickenTypeInternal"));
                        origin.addProperty("chickenJockey", ((net.minecraft.entity.passive.EntityChicken) entity).func_152116_bZ());
                        origin.addProperty("layProgress", (Integer) NativeAccess.call(entity, "getLayProgress"));
                        origin.addProperty("growingAge", ((net.minecraft.entity.EntityAgeable) entity).func_70874_b());
                    }
                } else {
                    int blockX;
                    switch (target) {
                        case "stone": blockX = 40 + offset; break;
                        case "furnace": blockX = 54; break;
                        case "water": blockX = 56; break;
                        default: throw new IllegalArgumentException("Unknown HUD target " + target);
                    }
                    BlockPos position = new BlockPos(blockX, 4, 0); x = blockX + 0.5;
                    origin.addProperty("dimension", 0); origin.addProperty("x", blockX); origin.addProperty("y", 4); origin.addProperty("z", 0);
                    origin.addProperty("state", world.func_180495_p(position).toString());
                }
                double playerX = x, playerY = 4, playerZ = z + (entities.containsKey(target) ? 2 : 3);
                if (target.equals("sky")) playerZ = 3.5;
                double dx = x - playerX, dy = y - (playerY + player.func_70047_e()), dz = z - playerZ;
                float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
                player.func_184611_a(EnumHand.MAIN_HAND, ItemStack.field_190927_a); player.func_184611_a(EnumHand.OFF_HAND, ItemStack.field_190927_a);
                player.func_70095_a(false); player.field_71135_a.func_147364_a(playerX, playerY, playerZ, yaw, pitch);
                JsonObject result = new JsonObject(); result.addProperty("target", target); result.add("origin", origin);
                result.addProperty("x", playerX); result.addProperty("y", playerY); result.addProperty("z", playerZ);
                result.addProperty("yaw", yaw); result.addProperty("pitch", pitch);
                if (client.func_71401_C() != server || client.field_71441_e != clientWorld || client.field_71439_g != clientPlayer || future.isDone()) throw new IllegalStateException("HUD world context changed during update");
                future.complete(result);
            } catch (Throwable error) { future.completeExceptionally(error); }
        });
    }

    private static void prepare(WorldServer world) throws Exception {
        for (int x = 38; x <= 70; x++) for (int z = -2; z <= 6; z++) {
            world.func_180501_a(new BlockPos(x, 3, z), Blocks.field_150348_b.func_176223_P(), 3);
            for (int y = 4; y <= 7; y++) world.func_175698_g(new BlockPos(x, y, z));
        }
        for (int x = 40; x <= 52; x++) world.func_180501_a(new BlockPos(x, 4, 0), Blocks.field_150348_b.func_176223_P(), 3);
        world.func_180501_a(new BlockPos(54, 4, 0), Blocks.field_150460_al.func_176223_P(), 3);
        world.func_180501_a(new BlockPos(56, 4, 0), Blocks.field_150355_j.func_176223_P(), 3);
        for (UUID uuid : entities.values()) { Entity previous = world.func_175733_a(uuid); if (previous != null) previous.func_70106_y(); }
        entities.clear();
        spawn(world, "pig", new EntityPig(world), 59.5, false);
        spawn(world, "cow", new EntityCow(world), 61.5, false);
        spawn(world, "custom", new EntityPig(world), 63.5, true);
        if (net.minecraftforge.fml.common.Loader.isModLoaded("chickens")) {
            java.util.List<String> types = new java.util.ArrayList<>();
            for (Object entry : (java.util.Collection<?>) NativeAccess.call(NativeAccess.type("com.setycz.chickens.registry.ChickensRegistry"), "getItems")) {
                if (Boolean.TRUE.equals(NativeAccess.call(entry, "isEnabled"))) types.add(String.valueOf(NativeAccess.call(entry, "getRegistryName")));
            }
            java.util.Collections.sort(types);
            if (types.size() < 2) throw new IllegalStateException("Missing enabled fixture chicken types");
            for (int index = 0; index < 2; index++) {
                EntityLiving chicken = (EntityLiving) NativeAccess.create("com.setycz.chickens.entity.EntityChickensChicken", world);
                NativeAccess.call(chicken, "setChickenType", types.get(index));
                stabilizeChicken(chicken);
                spawn(world, index == 0 ? "chicken" : "chicken-other", chicken, 66.5 + index * 2, false);
            }
        }
    }

    private static void stabilizeChicken(Entity entity) throws Exception {
        ((net.minecraft.entity.EntityAgeable) entity).func_70873_a(0);
        ((net.minecraft.entity.passive.EntityChicken) entity).func_152117_i(true);
        NativeAccess.call(entity, "setTimeUntilNextEgg", 24000);
    }

    private static void spawn(WorldServer world, String key, EntityLiving entity, double x, boolean custom) {
        entity.func_94061_f(true); entity.func_184224_h(true); entity.func_70012_b(x, 4, 0.5, 0, 0);
        if (custom) entity.func_96094_a("PT named pig");
        if (!world.func_72838_d(entity)) throw new IllegalStateException("Could not spawn native HUD entity");
        entities.put(key, entity.func_110124_au());
    }

}
