package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import net.minecraft.nbt.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class NameAuditStateTest {
    @Test public void fieldProjectionPreservesNativeTypesWithoutSerialization() throws Exception {
        NBTTagCompound value = new NBTTagCompound();
        value.func_74774_a("byte", (byte) -1);
        value.func_74777_a("short", (short) 2);
        value.func_74768_a("int", 3);
        value.func_74772_a("long", Long.MAX_VALUE);
        value.func_74776_a("float", -0.0f);
        value.func_74780_a("double", Double.NEGATIVE_INFINITY);
        value.func_74778_a("text", "中文\u0000");
        value.func_74773_a("bytes", new byte[]{-1, 0});
        value.func_74783_a("ints", new int[]{Integer.MIN_VALUE});
        NBTTagList list = new NBTTagList();
        list.func_74742_a(new NBTTagString("z"));
        list.func_74742_a(new NBTTagString("a"));
        value.func_74782_a("list", list);
        value.func_74782_a("nested", new NBTTagCompound());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CompressedStreamTools.func_74800_a(value, new DataOutputStream(output));
        assertEquals(NameAuditNbt.read(output.toByteArray()), new NameAuditState().nbt(value, 0));
    }

    @Test public void unknownNbtSubclassIsRejectedWithoutCallingItsMethods() throws Exception {
        NBTTagCompound unknown = new NBTTagCompound() {
            @Override public byte func_74732_a() { throw new AssertionError(); }
        };
        try { new NameAuditState().nbt(unknown, 0); fail(); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().startsWith("Unknown NBT type")); }
        assertFalse(NameAuditState.capture(new Object()).get("complete").getAsBoolean());
    }
}
