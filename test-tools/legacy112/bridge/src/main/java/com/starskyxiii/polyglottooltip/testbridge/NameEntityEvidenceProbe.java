package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.Loader;
import java.util.LinkedHashMap;
import java.util.Map;

final class NameEntityEvidenceProbe {
    private static final Map<Integer, Object> evidence = new LinkedHashMap<>();
    private static int sequence;
    private NameEntityEvidenceProbe() {}

    static JsonObject inspect(JsonObject params) throws Exception {
        Entity entity = Minecraft.func_71410_x().field_71441_e.func_73045_a(params.get("entityId").getAsInt());
        JsonObject result = new JsonObject();
        result.addProperty("present", entity != null);
        if (entity != null) {
            result.addProperty("identity", System.identityHashCode(entity));
            result.addProperty("uuid", entity.func_110124_au().toString());
            result.addProperty("dead", entity.field_70128_L);
            result.addProperty("custom", entity.func_145818_k_());
            if (entity.getClass().getName().equals("com.setycz.chickens.entity.EntityChickensChicken")) {
                String type = (String) NativeAccess.call(entity, "getChickenTypeInternal");
                result.addProperty("type", type);
                result.addProperty("layProgress", (Integer) NativeAccess.call(entity, "getLayProgress"));
                result.addProperty("growingAge", ((net.minecraft.entity.EntityAgeable) entity).func_70874_b());
                result.addProperty("child", ((net.minecraft.entity.EntityAgeable) entity).func_70631_g_());
                Object entry = NativeAccess.call(NativeAccess.type("com.setycz.chickens.registry.ChickensRegistry"), "getByRegistryName", type);
                if (entry != null && entry.getClass().getName().equals("com.setycz.chickens.registry.ChickensRegistryItem")) {
                    String key = "entity." + NativeAccess.call(entry, "getEntityName") + ".name";
                    result.addProperty("key", key);
                    result.addProperty("nativeName", entity.func_145818_k_() ? entity.func_95999_t()
                            : net.minecraft.util.text.translation.I18n.func_74838_a(key));
                }
            }
        }
        if (!Loader.isModLoaded("polyglottooltip")) return result;
        Class<?> formatter = NativeAccess.type("com.starskyxiii.polyglottooltip.legacy.integration.hud.HudNameFormatter");
        if (params.has("capture") && params.get("capture").getAsBoolean()) {
            Object value = NativeAccess.call(formatter, "entityName", entity);
            result.addProperty("admitted", value != null);
            if (value != null) {
                int token = ++sequence;
                evidence.put(token, value);
                while (evidence.size() > 64) evidence.remove(evidence.keySet().iterator().next());
                result.addProperty("token", token);
                result.addProperty("valid", (Boolean) NativeAccess.call(value, "valid", entity));
                result.addProperty("key", (String) NativeAccess.get(value, "key"));
                result.addProperty("nativeName", (String) NativeAccess.get(value, "nativeName"));
                result.add("aliases", new Gson().toJsonTree(NativeAccess.call(formatter, "key", NativeAccess.get(value, "key"))));
            }
        }
        if (params.has("token")) {
            Object value = evidence.get(params.get("token").getAsInt());
            if (value == null) throw new IllegalArgumentException("Unknown entity evidence token");
            result.addProperty("retainedValid", (Boolean) NativeAccess.call(value, "valid", entity));
        }
        return result;
    }
}
