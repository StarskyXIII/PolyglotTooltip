package com.starskyxiii.polyglottooltip.testbridge;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import static org.junit.Assert.*;

public class NameAuditObservationTest {
    private static final String NAME = "net/minecraft/item/ItemStack";

    private byte[] fixture() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, NAME, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "calls", "I", null, null).visitEnd();
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        method.visitCode(); method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        method.visitInsn(Opcodes.RETURN); method.visitMaxs(0, 0); method.visitEnd();
        method = writer.visitMethod(Opcodes.ACC_PUBLIC, "getDisplayName", "()Ljava/lang/String;", null, null);
        method.visitCode(); method.visitFieldInsn(Opcodes.GETSTATIC, NAME, "calls", "I");
        method.visitInsn(Opcodes.ICONST_1); method.visitInsn(Opcodes.IADD);
        method.visitFieldInsn(Opcodes.PUTSTATIC, NAME, "calls", "I");
        method.visitLdcInsn("Literal (#12)"); method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0); method.visitEnd();
        method = writer.visitMethod(Opcodes.ACC_PUBLIC, "getTooltip", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;", null, null);
        method.visitCode(); method.visitVarInsn(Opcodes.ALOAD, 2);
        Label simple = new Label(); method.visitJumpInsn(Opcodes.IFNULL, simple);
        method.visitVarInsn(Opcodes.ALOAD, 0); method.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
        method.visitInsn(Opcodes.DUP); method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, NAME, "getTooltip", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;", false);
        method.visitInsn(Opcodes.POP); method.visitLabel(simple);
        method.visitVarInsn(Opcodes.ALOAD, 1); method.visitTypeInsn(Opcodes.CHECKCAST, "java/util/List");
        method.visitVarInsn(Opcodes.ASTORE, 3); method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, NAME, "getDisplayName", "()Ljava/lang/String;", false);
        method.visitVarInsn(Opcodes.ASTORE, 4); method.visitVarInsn(Opcodes.ALOAD, 3);
        method.visitVarInsn(Opcodes.ALOAD, 4); method.visitLdcInsn(" (#1234/1)");
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
        method.visitInsn(Opcodes.POP); method.visitVarInsn(Opcodes.ALOAD, 3); method.visitLdcInsn("Body");
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
        method.visitInsn(Opcodes.POP); method.visitVarInsn(Opcodes.ALOAD, 3); method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0); method.visitEnd(); writer.visitEnd();
        return writer.toByteArray();
    }

    @Test
    public void capturesNaturalNameAndInitialTitleWithoutRepeatingCallsOrLeakingNestedFrames() throws Exception {
        System.setProperty("clientdevbridge.enabled", "true");
        System.setProperty("clientdevbridge.nameAudit", "true");
        try {
            byte[] output = new NameAuditTransformer().transform(NAME, NAME.replace('/', '.'), fixture());
            class FixtureLoader extends ClassLoader {
                Class<?> define(byte[] bytes) { return defineClass(NAME.replace('/', '.'), bytes, 0, bytes.length); }
            }
            Class<?> type = new FixtureLoader().define(output);
            Object stack = type.newInstance();
            java.lang.reflect.Method tooltip = type.getMethod("getTooltip", Object.class, Object.class);
            for (Object nested : new Object[]{null, Boolean.TRUE}) {
                List<String> lines = new ArrayList<>();
                int before = type.getField("calls").getInt(null);
                NameAuditCapture.Frame frame = NameAuditCapture.begin(stack);
                try { assertSame(lines, tooltip.invoke(stack, lines, nested)); }
                finally { NameAuditCapture.end(); }
                assertEquals(nested == null ? 1 : 2, type.getField("calls").getInt(null) - before);
                assertEquals(Arrays.asList("Literal (#12) (#1234/1)", "Body"), lines);
                assertEquals("Literal (#12)", frame.nativeName);
                assertEquals(lines.get(0), frame.initialTitle);
                assertSame(lines, frame.initialList);
                assertTrue(frame.nativeNameCaptured); assertTrue(frame.initialTitleCaptured);
                assertFalse(frame.failed);
            }
            NameAuditCapture.Frame failed = NameAuditCapture.begin(stack);
            try { tooltip.invoke(stack, Collections.emptyList(), null); fail(); }
            catch (InvocationTargetException expected) { assertTrue(expected.getCause() instanceof UnsupportedOperationException); }
            finally { NameAuditCapture.end(); }
            assertTrue(failed.nativeNameCaptured);
            NameAuditCapture.Frame fresh = NameAuditCapture.begin(stack);
            try { assertFalse(fresh.nativeNameCaptured); assertNull(fresh.initialList); }
            finally { NameAuditCapture.end(); }
            assertNotNull(NameAuditCapture.nameInputHash());
            assertNotEquals(NameAuditCapture.nameInputHash(), NameAuditCapture.nameOutputHash());
        } finally {
            NameAuditCapture.end();
            System.clearProperty("clientdevbridge.enabled");
            System.clearProperty("clientdevbridge.nameAudit");
        }
    }
}
