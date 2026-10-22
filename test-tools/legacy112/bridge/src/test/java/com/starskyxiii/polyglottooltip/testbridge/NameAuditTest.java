package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonObject;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import org.junit.Test;
import org.objectweb.asm.*;
import static org.junit.Assert.*;

public class NameAuditTest {
    @Test public void compiledProducerRetainsTheObservedInsertionSite() throws Exception {
        byte[] input = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(System.getProperty("audit.producerClass")));
        String name = "com.starskyxiii.polyglottooltip.legacy.SecondaryTooltipRows";
        System.setProperty("clientdevbridge.enabled", "true");
        System.setProperty("clientdevbridge.nameAudit", "true");
        try {
            byte[] output = new NameAuditTransformer().transform(name, name, input);
            assertFalse(Arrays.equals(input, output));
            assertNotNull(NameAuditCapture.inputHash());
            assertNotEquals(NameAuditCapture.inputHash(), NameAuditCapture.outputHash());
        } finally {
            System.clearProperty("clientdevbridge.enabled");
            System.clearProperty("clientdevbridge.nameAudit");
        }
    }

    private interface Writer { void write(DataOutputStream out) throws IOException; }

    private byte[] nbt(Writer writer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(10); out.writeUTF(""); writer.write(out); out.writeByte(0);
        return bytes.toByteArray();
    }

    private void tag(DataOutputStream out, int type, String key) throws IOException { out.writeByte(type); out.writeUTF(key); }

    @Test public void typedNbtPreservesNumericBitsAndNestedOrder() throws Exception {
        byte[] bytes = nbt(out -> {
            tag(out, 1, "byte"); out.writeByte(-1);
            tag(out, 2, "short"); out.writeShort(1);
            tag(out, 3, "int"); out.writeInt(1);
            tag(out, 4, "long"); out.writeLong(Long.MAX_VALUE);
            tag(out, 5, "float"); out.writeInt(0x80000000);
            tag(out, 6, "double"); out.writeLong(0x7ff8000000000042L);
            tag(out, 7, "bytes"); out.writeInt(2); out.write(new byte[]{0, -1});
            tag(out, 8, "unicode"); out.writeUTF("\u0000\u4e2d\ud83d\udc31");
            tag(out, 9, "list"); out.writeByte(8); out.writeInt(2); out.writeUTF("b"); out.writeUTF("a");
            tag(out, 10, "ForgeCaps"); tag(out, 8, "a"); out.writeUTF("kept"); out.writeByte(0);
            tag(out, 11, "ints"); out.writeInt(1); out.writeInt(Integer.MIN_VALUE);
            tag(out, 12, "longs"); out.writeInt(1); out.writeLong(Long.MIN_VALUE);
        });
        JsonObject values = NameAuditNbt.read(bytes).getAsJsonObject("value");
        assertEquals(12, values.entrySet().size());
        assertEquals("9223372036854775807", values.getAsJsonObject("long").get("value").getAsString());
        assertEquals("80000000", values.getAsJsonObject("float").get("value").getAsString());
        assertEquals("7ff8000000000042", values.getAsJsonObject("double").get("value").getAsString());
        assertEquals("\u0000\u4e2d\ud83d\udc31", values.getAsJsonObject("unicode").get("value").getAsString());
        assertEquals("b", values.getAsJsonObject("list").getAsJsonArray("value").get(0).getAsJsonObject().get("value").getAsString());
        assertEquals(2, values.getAsJsonObject("short").get("type").getAsInt());
        assertEquals(3, values.getAsJsonObject("int").get("type").getAsInt());
        assertEquals("-9223372036854775808", values.getAsJsonObject("longs").getAsJsonArray("value").get(0).getAsString());
        for (int size = 0; size < bytes.length; size++) {
            try { NameAuditNbt.read(Arrays.copyOf(bytes, size)); fail("accepted truncated NBT at " + size); }
            catch (IOException expected) { }
        }
        try { NameAuditNbt.read(Arrays.copyOf(bytes, bytes.length + 1)); fail("accepted trailing byte"); }
        catch (IOException expected) { }
    }

    @Test public void malformedNbtIsExplicitFailure() throws Exception {
        List<byte[]> invalid = Arrays.asList(
                nbt(out -> { tag(out, 1, "x"); out.writeByte(1); tag(out, 1, "x"); out.writeByte(2); }),
                nbt(out -> { tag(out, 7, "x"); out.writeInt(-1); }),
                nbt(out -> { tag(out, 9, "x"); out.writeByte(0); out.writeInt(1); }),
                nbt(out -> { for (int i = 0; i < 66; i++) tag(out, 10, "nested"); }),
                new byte[4194305]);
        for (byte[] bytes : invalid) {
            try { NameAuditNbt.read(bytes); fail("accepted malformed NBT"); }
            catch (IOException expected) { }
        }
    }

    @Test public void observerKeepsOriginalListCallIdentityAndException() throws Exception {
        String name = "com/starskyxiii/polyglottooltip/legacy/SecondaryTooltipRows";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "insert", "(Ljava/util/List;Ljava/util/List;I)V", null, null);
        method.visitCode(); method.visitVarInsn(Opcodes.ALOAD, 0); method.visitVarInsn(Opcodes.ILOAD, 2);
        method.visitVarInsn(Opcodes.ALOAD, 1); method.visitInsn(Opcodes.ICONST_0);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "add", "(ILjava/lang/Object;)V", true);
        method.visitInsn(Opcodes.RETURN); method.visitMaxs(0, 0); method.visitEnd(); writer.visitEnd();
        byte[] input = writer.toByteArray();
        NameAuditTransformer transformer = new NameAuditTransformer();
        System.clearProperty("clientdevbridge.nameAudit");
        assertSame(input, transformer.transform(name, name.replace('/', '.'), input));
        System.setProperty("clientdevbridge.enabled", "true"); System.setProperty("clientdevbridge.nameAudit", "true");
        try {
            byte[] output = transformer.transform(name, name.replace('/', '.'), input);
            assertFalse(Arrays.equals(input, output));
            class FixtureLoader extends ClassLoader {
                Class<?> define(byte[] data) { return defineClass(name.replace('/', '.'), data, 0, data.length); }
            }
            Class<?> type = new FixtureLoader().define(output);
            List<String> target = new ArrayList<>(Arrays.asList("native", "body"));
            String inserted = new String("English");
            NameAuditCapture.Frame frame = NameAuditCapture.begin();
            try { type.getMethod("insert", List.class, List.class, int.class).invoke(null, target, Arrays.asList(inserted), 1); }
            finally { NameAuditCapture.end(); }
            assertEquals(Arrays.asList("native", "English", "body"), target);
            assertEquals(1, frame.insertions.size()); assertSame(target, frame.insertions.get(0).target);
            assertSame(inserted, frame.insertions.get(0).text); assertFalse(frame.failed);
            frame = NameAuditCapture.begin();
            try {
                type.getMethod("insert", List.class, List.class, int.class).invoke(null, Collections.emptyList(), Arrays.asList(inserted), 0);
                fail("swallowed native add exception");
            } catch (InvocationTargetException expected) { assertTrue(expected.getCause() instanceof UnsupportedOperationException); }
            finally { NameAuditCapture.end(); }
            assertEquals(0, frame.insertions.size());
        } finally { System.clearProperty("clientdevbridge.enabled"); System.clearProperty("clientdevbridge.nameAudit"); }
    }
}
