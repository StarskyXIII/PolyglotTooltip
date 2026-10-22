package com.starskyxiii.polyglottooltip.testbridge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import static org.junit.Assert.*;

public class NameAuditBodyTest {
    private static final String STACK = "net/minecraft/item/ItemStack";

    private static Object stack() throws Exception {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, STACK, null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        method.visitCode(); method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        method.visitInsn(Opcodes.RETURN); method.visitMaxs(0, 0); method.visitEnd();
        method = writer.visitMethod(Opcodes.ACC_PUBLIC, "getTooltip", "(Ljava/util/List;I)Ljava/util/List;", null, new String[]{"java/lang/Exception"});
        method.visitCode(); method.visitVarInsn(Opcodes.ALOAD, 0); method.visitVarInsn(Opcodes.ALOAD, 1); method.visitVarInsn(Opcodes.ILOAD, 2);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "com/starskyxiii/polyglottooltip/testbridge/NameAuditBodyTest", "runFixture", "(Ljava/lang/Object;Ljava/util/List;I)Ljava/util/List;", false);
        method.visitInsn(Opcodes.ARETURN); method.visitMaxs(0, 0); method.visitEnd(); writer.visitEnd();
        class FixtureLoader extends ClassLoader { Class<?> define(byte[] bytes) { return defineClass(STACK.replace('/', '.'), bytes, 0, bytes.length); } }
        return new FixtureLoader().define(writer.toByteArray()).newInstance();
    }

    public static List<?> runFixture(Object stack, List<?> rows, int mode) throws Exception {
        if (mode == 4) {
            List<String> nested = new ArrayList<>(Arrays.asList("Nested", "Body"));
            stack.getClass().getMethod("getTooltip", List.class, int.class).invoke(stack, nested, 0);
        }
        if (mode != 3) NameAuditCapture.titled(rows, "Title", stack);
        if (mode == 2) NameAuditCapture.nativeBody(stack, new ArrayList<Object>(rows));
        else NameAuditCapture.nativeBody(stack, rows);
        if (mode == 1) NameAuditCapture.nativeBody(stack, rows);
        return rows;
    }

    private static NameAuditCapture.Frame capture(Object stack, List<?> rows, int mode) throws Exception {
        NameAuditCapture.Frame frame = NameAuditCapture.begin(stack);
        try { assertSame(rows, stack.getClass().getMethod("getTooltip", List.class, int.class).invoke(stack, rows, mode)); }
        finally { NameAuditCapture.end(); }
        return frame;
    }

    @Test public void capturesOneImmutableOrderedSnapshotAndIgnoresNestedTooltips() throws Exception {
        Object stack = stack();
        for (int mode : new int[]{0, 4}) {
            List<String> rows = new ArrayList<>(Arrays.asList("Title", "§cNative", "same", "same", " lore "));
            NameAuditCapture.Frame frame = capture(stack, rows, mode);
            assertFalse(frame.failed); assertTrue(frame.nativeBodyCaptured); assertSame(rows, frame.nativeBodyList);
            assertArrayEquals(new String[]{"Title", "§cNative", "same", "same", " lore "}, frame.nativeBodyBeforeAppend);
            rows.set(1, "§cNative§7｜English"); rows.add(1, "Secondary"); rows.remove(3);
            assertArrayEquals(new String[]{"Title", "§cNative", "same", "same", " lore "}, frame.nativeBodyBeforeAppend);
        }
    }

    @Test public void duplicateWrongListAndMissingTitleMakeEvidenceUnavailable() throws Exception {
        Object stack = stack();
        for (int mode : new int[]{1, 2, 3}) {
            List<String> rows = new ArrayList<>(Arrays.asList("Title", "Body"));
            NameAuditCapture.Frame frame = capture(stack, rows, mode);
            assertTrue(frame.failed); assertEquals(Arrays.asList("Title", "Body"), rows);
            if (mode != 1) assertFalse(frame.nativeBodyCaptured);
        }
    }

    @Test public void copyRejectsUnexpectedListWithoutCallingItsReadMethods() throws Exception {
        class UnexpectedRows extends ArrayList<String> {
            int reads;
            @Override public int size() { reads++; return super.size(); }
            @Override public String get(int index) { reads++; return super.get(index); }
        }
        UnexpectedRows rows = new UnexpectedRows(); rows.add("Title"); rows.add("Body");
        NameAuditCapture.Frame frame = capture(stack(), rows, 0);
        assertTrue(frame.failed); assertFalse(frame.nativeBodyCaptured); assertEquals(0, rows.reads);
    }

    @Test public void copyBoundsRowsStringsAndTotalCharactersWithoutChangingTheList() throws Exception {
        Object stack = stack();
        List<List<?>> invalid = new ArrayList<>();
        invalid.add(new ArrayList<Object>(Arrays.asList("Title", null)));
        invalid.add(new ArrayList<Object>(Arrays.asList("Title", Integer.valueOf(1))));
        invalid.add(new ArrayList<String>(Arrays.asList("Title", new String(new char[8193]))));
        invalid.add(new ArrayList<String>(java.util.Collections.nCopies(1025, "row")));
        invalid.add(new ArrayList<String>(java.util.Collections.nCopies(33, new String(new char[8192]))));
        for (List<?> rows : invalid) {
            List<?> before = new ArrayList<Object>(rows);
            NameAuditCapture.Frame frame = capture(stack, rows, 0);
            assertTrue(frame.failed); assertFalse(frame.nativeBodyCaptured); assertEquals(before, rows);
        }
    }
}
