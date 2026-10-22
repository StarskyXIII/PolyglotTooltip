package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

final class NameAuditState {
    private int remaining = 8192;
    private int bytes = 65536;

    static JsonObject capture(Object stack) {
        JsonObject result = new JsonObject();
        try {
            if (stack == null || !stack.getClass().getName().equals("net.minecraft.item.ItemStack")) throw new IllegalArgumentException("Unknown stack type");
            NameAuditState reader = new NameAuditState();
            result.addProperty("count", (Integer) field(stack, "field_77994_a"));
            result.addProperty("damage", (Integer) field(stack, "field_77991_e"));
            result.addProperty("empty", (Boolean) field(stack, "field_190928_g"));
            result.add("tag", reader.nbt(field(stack, "field_77990_d"), 0));
            result.add("capNBT", reader.nbt(field(stack, "capNBT"), 0));
            Object capabilities = field(stack, "capabilities");
            result.addProperty("capabilityState", capabilities == null ? "absent" : "opaque");
            result.addProperty("complete", true);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            result.addProperty("complete", false);
            result.addProperty("error", error.getClass().getName() + ": " + error.getMessage());
        }
        return result;
    }

    JsonElement nbt(Object value, int depth) throws ReflectiveOperationException {
        if (--remaining < 0 || depth > 32) throw new IllegalArgumentException("State limit");
        if (value == null) return JsonNull.INSTANCE;
        String type = value.getClass().getName();
        String[] names = {"NBTTagEnd", "NBTTagByte", "NBTTagShort", "NBTTagInt", "NBTTagLong", "NBTTagFloat", "NBTTagDouble", "NBTTagByteArray", "NBTTagString", "NBTTagList", "NBTTagCompound", "NBTTagIntArray", "NBTTagLongArray"};
        String[] fields = {null, "field_74756_a", "field_74752_a", "field_74748_a", "field_74753_a", "field_74750_a", "field_74755_a", "field_74754_a", "field_74751_a", "field_74747_a", "field_74784_a", "field_74749_a", "field_193587_b"};
        int id = -1;
        for (int i = 0; i < names.length; i++) if (type.equals("net.minecraft.nbt." + names[i])) id = i;
        if (id < 1) throw new IllegalArgumentException("Unknown NBT type " + type);
        Object data = field(value, fields[id]);
        JsonObject result = new JsonObject();
        result.addProperty("type", id);
        if (id >= 1 && id <= 4) result.addProperty("value", String.valueOf(data));
        else if (id == 5) result.addProperty("value", String.format(java.util.Locale.ROOT, "%08x", Float.floatToRawIntBits((Float) data)));
        else if (id == 6) result.addProperty("value", String.format(java.util.Locale.ROOT, "%016x", Double.doubleToRawLongBits((Double) data)));
        else if (id == 8) { text((String) data); result.addProperty("value", (String) data); }
        else if (id == 7 || id == 11 || id == 12) {
            int length = Array.getLength(data);
            remaining -= length;
            if (remaining < 0) throw new IllegalArgumentException("Array limit");
            if (id == 7) result.addProperty("value", Base64.getEncoder().encodeToString((byte[]) data));
            else {
                JsonArray array = new JsonArray();
                for (int i = 0; i < length; i++) array.add(String.valueOf(Array.get(data, i)));
                result.add("value", array);
            }
        } else if (id == 9) {
            if (data.getClass() != ArrayList.class) throw new IllegalArgumentException("Unknown NBT list");
            ArrayList<?> list = (ArrayList<?>) data;
            if (list.size() > remaining) throw new IllegalArgumentException("List limit");
            result.addProperty("elementType", (Byte) field(value, "field_74746_b"));
            JsonArray array = new JsonArray();
            for (Object child : list) array.add(nbt(child, depth + 1));
            result.add("value", array);
        } else if (id == 10) {
            JsonObject map = new JsonObject();
            if (data.getClass() == HashMap.class) {
                Map<?, ?> source = (Map<?, ?>) data;
                if (source.size() > remaining) throw new IllegalArgumentException("Map limit");
                for (Map.Entry<?, ?> entry : source.entrySet()) entry(map, entry.getKey(), entry.getValue(), depth);
            } else if (data instanceof Map && !data.getClass().getName().equals("zone.rong.loliasm.api.datastructures.LoliTagMap")) {
                // StellarCore's NBTTagBackingMap (an Object2ObjectOpenHashMap) and other Map-backed compound storage.
                Map<?, ?> source = (Map<?, ?>) data;
                if (source.size() > remaining) throw new IllegalArgumentException("Map limit");
                for (Map.Entry<?, ?> entry : source.entrySet()) entry(map, entry.getKey(), entry.getValue(), depth);
            } else {
                if (!data.getClass().getName().equals("zone.rong.loliasm.api.datastructures.LoliTagMap")) throw new IllegalArgumentException("Unknown NBT map " + data.getClass().getName());
                Object inner = field(data, "map");
                if (!inner.getClass().getName().equals("zone.rong.loliasm.api.datastructures.canonical.AutoCanonizingArrayMap")) throw new IllegalArgumentException("Unknown NBT inner map " + inner.getClass().getName());
                Object[] keys = (Object[]) field(inner, "key"), values = (Object[]) field(inner, "value");
                int size = (Integer) field(inner, "size");
                if (size < 0 || size > remaining || size > keys.length || size > values.length) throw new IllegalArgumentException("Map limit");
                for (int i = 0; i < size; i++) entry(map, keys[i], values[i], depth);
            }
            result.add("value", map);
        }
        return result;
    }

    private void entry(JsonObject target, Object key, Object value, int depth) throws ReflectiveOperationException {
        if (!(key instanceof String) || target.has((String) key)) throw new IllegalArgumentException("Invalid NBT key");
        text((String) key);
        target.add((String) key, nbt(value, depth + 1));
    }

    private void text(String value) {
        bytes -= value.length() * 3;
        if (bytes < 0) throw new IllegalArgumentException("Text limit");
    }

    private static Object field(Object value, String name) throws ReflectiveOperationException {
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(value);
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
