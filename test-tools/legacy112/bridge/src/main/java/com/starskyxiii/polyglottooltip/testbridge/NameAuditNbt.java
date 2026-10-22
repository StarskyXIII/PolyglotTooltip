package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Base64;

public final class NameAuditNbt {
    private NameAuditNbt() { }

    public static JsonObject read(byte[] bytes) throws IOException {
        if (bytes.length > 4194304) throw new IOException("Audit NBT exceeds 4 MiB");
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int type = input.readUnsignedByte();
            if (type != 10 || !input.readUTF().isEmpty()) throw new IOException("Expected unnamed compound root");
            JsonObject result = read(input, type, 0);
            if (input.read() != -1) throw new IOException("Trailing NBT bytes");
            return result;
        }
    }

    private static int length(DataInputStream input, int width) throws IOException {
        int size = input.readInt();
        if (size < 0 || size > 4194304 / width) throw new IOException("Invalid audit NBT length");
        return size;
    }

    private static JsonObject read(DataInputStream input, int type, int depth) throws IOException {
        if (depth > 64) throw new IOException("Audit NBT depth exceeds 64");
        JsonObject result = new JsonObject();
        result.addProperty("type", type);
        switch (type) {
            case 1: result.addProperty("value", Byte.toString(input.readByte())); break;
            case 2: result.addProperty("value", Short.toString(input.readShort())); break;
            case 3: result.addProperty("value", Integer.toString(input.readInt())); break;
            case 4: result.addProperty("value", Long.toString(input.readLong())); break;
            case 5: result.addProperty("value", String.format("%08x", input.readInt())); break;
            case 6: result.addProperty("value", String.format("%016x", input.readLong())); break;
            case 7: {
                byte[] values = new byte[length(input, 1)];
                input.readFully(values);
                result.addProperty("value", Base64.getEncoder().encodeToString(values));
                break;
            }
            case 8: result.addProperty("value", input.readUTF()); break;
            case 9: {
                int child = input.readUnsignedByte(), size = length(input, 1);
                if (child > 12 || (child == 0 && size != 0)) throw new IOException("Invalid list element type");
                result.addProperty("elementType", child);
                JsonArray values = new JsonArray();
                for (int index = 0; index < size; index++) values.add(read(input, child, depth + 1));
                result.add("value", values);
                break;
            }
            case 10: {
                JsonObject values = new JsonObject();
                for (int child; (child = input.readUnsignedByte()) != 0;) {
                    String key = input.readUTF();
                    if (values.has(key)) throw new IOException("Duplicate compound key");
                    values.add(key, read(input, child, depth + 1));
                }
                result.add("value", values);
                break;
            }
            case 11: {
                JsonArray values = new JsonArray();
                for (int remaining = length(input, 4); remaining > 0; remaining--) values.add(Integer.toString(input.readInt()));
                result.add("value", values);
                break;
            }
            case 12: {
                JsonArray values = new JsonArray();
                for (int remaining = length(input, 8); remaining > 0; remaining--) values.add(Long.toString(input.readLong()));
                result.add("value", values);
                break;
            }
            default: throw new IOException("Unsupported audit NBT type " + type);
        }
        return result;
    }
}
