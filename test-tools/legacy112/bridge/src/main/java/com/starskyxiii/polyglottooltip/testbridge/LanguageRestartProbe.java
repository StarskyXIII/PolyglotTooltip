package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.Locale;
import net.minecraft.client.resources.SimpleReloadableResourceManager;

/**
 * Recreates two things that happen in real play, on demand, for the language preparation's restart path:
 * a runtime writer putting a value straight into the live client dictionary ({@code write-live}), and a mod adding a
 * reload listener after startup ({@code append-listener}), which makes the modelled injections stale on the same
 * native snapshot without any resource reload (as CraftTweaker rereading its translation actions does).
 * The appended listener does nothing; the written value stays until the next resource reload.
 */
final class LanguageRestartProbe {
    private LanguageRestartProbe() {}

    static JsonObject run(JsonObject params) throws Exception {
        String action = params.get("action").getAsString();
        Minecraft client = Minecraft.func_71410_x();
        JsonObject result = new JsonObject();
        result.addProperty("action", action);
        if ("write-live".equals(action)) {
            String key = params.get("key").getAsString(), value = params.get("value").getAsString();
            Locale locale = (Locale) field(client.func_135016_M(), "field_135049_a");
            @SuppressWarnings("unchecked") Map<String, String> map = (Map<String, String>) field(locale, "field_135032_a");
            result.addProperty("before", map.get(key));
            map.put(key, value);
            result.addProperty("after", map.get(key));
            return result;
        }
        if ("append-listener".equals(action)) {
            SimpleReloadableResourceManager manager = (SimpleReloadableResourceManager) client.func_110442_L();
            @SuppressWarnings("unchecked") List<IResourceManagerReloadListener> listeners =
                    (List<IResourceManagerReloadListener>) field(manager, "field_110546_b");
            int before = listeners.size();
            listeners.add(resources -> {});
            result.addProperty("before", before);
            result.addProperty("after", listeners.size());
            return result;
        }
        if ("published-withheld".equals(action)) {
            // Whether the tables the tooltips read (the published snapshot) withhold the key, primary and every secondary.
            String key = params.get("key").getAsString();
            Class<?> adapter = Class.forName("com.starskyxiii.polyglottooltip.legacy.minecraft.MinecraftLanguageAdapter", false,
                    LanguageRestartProbe.class.getClassLoader());
            java.lang.reflect.Method snapshot = adapter.getMethod("snapshot");
            Object published = snapshot.invoke(null);
            result.addProperty("primary", withheld(field(published, "primaryTable"), key));
            int secondary = 0, withholding = 0;
            for (Object table : (List<?>) field(published, "tables")) { secondary++; if (withheld(table, key)) withholding++; }
            result.addProperty("secondaryTables", secondary);
            result.addProperty("secondaryWithholding", withholding);
            result.addProperty("generation", String.valueOf(field(published, "generation")));
            return result;
        }
        throw new IllegalArgumentException("Unknown language restart action " + action);
    }

    private static boolean withheld(Object table, String key) throws ReflectiveOperationException {
        if (table == null) return false;
        java.lang.reflect.Method method = table.getClass().getDeclaredMethod("withheld");
        method.setAccessible(true);
        return ((java.util.Set<?>) method.invoke(table)).contains(key);
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
            catch (NoSuchFieldException next) { }
        }
        throw new NoSuchFieldException(name);
    }
}
