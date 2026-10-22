package com.starskyxiii.polyglottooltip.testbridge;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class NameAuditEnchantmentTest {
    @Test public void productRowRouteLeavesTheNativeTooltipAndDisplayMethodsUnchanged() throws Exception {
        System.setProperty("clientdevbridge.nameAuditProductRows", "true");
        try {
            byte[] input;
            try (java.io.InputStream stream = getClass().getResourceAsStream("/net/minecraft/item/ItemStack.class")) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                for (int size; (size = stream.read(buffer)) >= 0;) bytes.write(buffer, 0, size);
                input = bytes.toByteArray();
            }
            org.objectweb.asm.tree.ClassNode before = classNode(input), after = classNode(NameAuditTransformer.observeNames(input));
            assertArrayEquals(methodBytes(before, "func_82840_a"), methodBytes(after, "func_82840_a"));
            assertArrayEquals(methodBytes(before, "func_82833_r"), methodBytes(after, "func_82833_r"));
            assertEquals(0, methodCalls(after, "titled"));
            assertEquals(0, methodCalls(after, "named"));
            java.nio.file.Path root = java.nio.file.Paths.get(System.getProperty("audit.producerClass")).getParent();
            org.objectweb.asm.tree.ClassNode scope = classNode(NameAuditTransformer.observeEnchantmentScope(
                    java.nio.file.Files.readAllBytes(root.resolve("minecraft/NativeEnchantmentScope.class"))));
            org.objectweb.asm.tree.ClassNode rows = classNode(NameAuditTransformer.observeAnnotatedRows(
                    java.nio.file.Files.readAllBytes(root.resolve("ObservedTooltipRows.class"))));
            byte[] nameSource = java.nio.file.Files.readAllBytes(root.resolve("minecraft/NativeNameScope.class"));
            org.objectweb.asm.tree.ClassNode names = classNode(NameAuditTransformer.observeNameScope(nameSource));
            assertEquals(1, methodCalls(names, "productNamed"));
            assertEquals(1, methodCalls(names, "nativeBody"));
            assertEquals(methodCalls(classNode(nameSource), "observe"), methodCalls(names, "observe"));
            assertEquals(methodCalls(classNode(nameSource), "onItemTooltip"), methodCalls(names, "onItemTooltip"));
            List<String> finishOrder = new ArrayList<>();
            for (org.objectweb.asm.tree.MethodNode method : names.methods) if (method.name.equals("finish")) {
                for (org.objectweb.asm.tree.AbstractInsnNode instruction : method.instructions.toArray()) if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode) {
                    String name = ((org.objectweb.asm.tree.MethodInsnNode) instruction).name;
                    if (java.util.Arrays.asList("onItemTooltip", "nativeBody", "append", "appendNames").contains(name)) finishOrder.add(name);
                }
            }
            assertEquals(java.util.Arrays.asList("onItemTooltip", "nativeBody", "append", "appendNames"), finishOrder);
            assertEquals(2, methodCalls(scope, "productRows"));
            assertEquals(1, methodCalls(rows, "productTitle"));
            for (org.objectweb.asm.tree.ClassNode type : new org.objectweb.asm.tree.ClassNode[]{after, scope, rows, names}) {
                for (org.objectweb.asm.tree.MethodNode method : type.methods) {
                    new org.objectweb.asm.tree.analysis.Analyzer<>(new org.objectweb.asm.tree.analysis.BasicInterpreter()).analyze(type.name, method);
                }
            }
        } finally { System.clearProperty("clientdevbridge.nameAuditProductRows"); }
    }

    @Test public void bodyObserverRejectsAmbiguousChangedAndReorderedNativeSites() throws Exception {
        java.nio.file.Path root = java.nio.file.Paths.get(System.getProperty("audit.producerClass")).getParent();
        byte[] source = java.nio.file.Files.readAllBytes(root.resolve("minecraft/NativeNameScope.class"));
        System.setProperty("clientdevbridge.nameAuditProductRows", "true");
        try {
            for (int variant = 0; variant < 3; variant++) {
                org.objectweb.asm.tree.ClassNode node = classNode(source);
                for (org.objectweb.asm.tree.MethodNode method : node.methods) if (method.name.equals("finish")) {
                    org.objectweb.asm.tree.MethodInsnNode event = null, append = null;
                    for (org.objectweb.asm.tree.AbstractInsnNode instruction : method.instructions.toArray()) if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode) {
                        org.objectweb.asm.tree.MethodInsnNode call = (org.objectweb.asm.tree.MethodInsnNode) instruction;
                        if (call.name.equals("onItemTooltip")) event = call;
                        if (call.name.equals("append")) append = call;
                    }
                    assertNotNull(event); assertNotNull(append);
                    if (variant == 0) method.instructions.insert(event, new org.objectweb.asm.tree.MethodInsnNode(event.getOpcode(), event.owner, event.name, event.desc, event.itf));
                    else if (variant == 1) event.desc = "()Ljava/lang/Object;";
                    else { method.instructions.remove(append); method.instructions.insertBefore(event, append); }
                }
                org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(0); node.accept(writer);
                try { NameAuditTransformer.observeNameScope(writer.toByteArray()); fail("accepted unsupported finish variant " + variant); }
                catch (IllegalStateException expected) { }
            }
        } finally { System.clearProperty("clientdevbridge.nameAuditProductRows"); }
    }

    private static byte[] methodBytes(org.objectweb.asm.tree.ClassNode type, String name) {
        org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(0);
        writer.visit(org.objectweb.asm.Opcodes.V1_8, org.objectweb.asm.Opcodes.ACC_PUBLIC, type.name, null, "java/lang/Object", null);
        for (org.objectweb.asm.tree.MethodNode method : type.methods) if (method.name.equals(name)) method.accept(writer);
        writer.visitEnd();
        return writer.toByteArray();
    }

    @Test public void failedNativeBulkRemovalCannotReviveAnInsertionAfterArrayGrowth() {
        NameAuditCapture.Frame frame = NameAuditCapture.begin(new Object());
        try {
            ArrayList<String> rows = NameAuditEnchantmentCapture.rows();
            frame.initialList = rows;
            rows.add("title");
            String origin = new String("origin");
            NameAuditEnchantmentCapture.naming(new Object(), 1, "source");
            NameAuditEnchantmentCapture.inserting(rows, origin);
            NameAuditEnchantmentCapture.inserted(rows.add(origin));
            rows.add("a"); rows.add("b");
            java.util.Collection<Object> values = new java.util.AbstractCollection<Object>() {
                private boolean first = true;
                @Override public int size() { return 1; }
                @Override public java.util.Iterator<Object> iterator() { throw new AssertionError(); }
                @Override public boolean contains(Object value) {
                    if (value == origin && first) {
                        first = false;
                        rows.add(origin);
                        for (int index = 1; index <= 5; index++) rows.add("v" + index);
                        return true;
                    }
                    if (value.equals("v5")) rows.add("tail");
                    return value.equals("a") || value.equals("b");
                }
            };
            try { rows.removeAll(values); }
            catch (RuntimeException expected) {}
            assertFalse(frame.enchantments.get(0).survives(rows));
        } finally { NameAuditCapture.end(); }
    }

    @Test public void productObservationPreservesNaturalGetterSitesAndVerifiableBytecode() throws Exception {
        java.nio.file.Path root = java.nio.file.Paths.get(System.getProperty("audit.producerClass")).getParent();
        byte[] input = java.nio.file.Files.readAllBytes(root.resolve("minecraft/NativeEnchantmentScope.class"));
        org.objectweb.asm.tree.ClassNode before = classNode(input), after = classNode(NameAuditTransformer.observeEnchantmentScope(input));
        assertEquals(methodCalls(before, "func_77316_c"), methodCalls(after, "func_77316_c"));
        assertEquals(methodCalls(before, "func_77316_c"), methodCalls(after, "naming"));
        assertEquals(2, methodCalls(after, "inserting"));
        assertEquals(2, methodCalls(after, "inserted"));
        byte[] rows = java.nio.file.Files.readAllBytes(root.resolve("ObservedTooltipRows.class"));
        org.objectweb.asm.tree.ClassNode annotated = classNode(NameAuditTransformer.observeAnnotatedRows(rows));
        assertEquals(2, methodCalls(annotated, "annotated"));
        for (org.objectweb.asm.tree.ClassNode type : new org.objectweb.asm.tree.ClassNode[]{after, annotated}) {
            for (org.objectweb.asm.tree.MethodNode method : type.methods) {
                new org.objectweb.asm.tree.analysis.Analyzer<>(new org.objectweb.asm.tree.analysis.BasicInterpreter()).analyze(type.name, method);
            }
        }
    }

    private static org.objectweb.asm.tree.ClassNode classNode(byte[] bytes) {
        org.objectweb.asm.tree.ClassNode result = new org.objectweb.asm.tree.ClassNode();
        new org.objectweb.asm.ClassReader(bytes).accept(result, 0);
        return result;
    }

    private static int methodCalls(org.objectweb.asm.tree.ClassNode type, String name) {
        int calls = 0;
        for (org.objectweb.asm.tree.MethodNode method : type.methods) for (org.objectweb.asm.tree.AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode && ((org.objectweb.asm.tree.MethodInsnNode) instruction).name.equals(name)) calls++;
        }
        return calls;
    }

    @Test public void nativeObserverKeepsTheExistingGetterAndInsertionExactlyOnce() throws Exception {
        byte[] input;
        try (java.io.InputStream stream = getClass().getResourceAsStream("/net/minecraft/item/ItemEnchantedBook.class")) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int size; (size = stream.read(buffer)) >= 0;) bytes.write(buffer, 0, size);
            input = bytes.toByteArray();
        }
        org.objectweb.asm.tree.ClassNode type = new org.objectweb.asm.tree.ClassNode();
        new org.objectweb.asm.ClassReader(NameAuditTransformer.observeEnchantments(input)).accept(type, 0);
        int getters = 0, naming = 0, inserting = 0, inserted = 0;
        for (org.objectweb.asm.tree.MethodNode method : type.methods) {
            for (org.objectweb.asm.tree.AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof org.objectweb.asm.tree.MethodInsnNode)) continue;
                String name = ((org.objectweb.asm.tree.MethodInsnNode) instruction).name;
                if (name.equals("func_77316_c")) getters++;
                if (name.equals("naming")) naming++;
                if (name.equals("inserting")) inserting++;
                if (name.equals("inserted")) inserted++;
            }
            new org.objectweb.asm.tree.analysis.Analyzer<>(new org.objectweb.asm.tree.analysis.BasicInterpreter()).analyze(type.name, method);
        }
        assertEquals(1, getters);
        assertEquals(getters, naming);
        assertEquals(getters, inserting);
        assertEquals(getters, inserted);
    }

    @Test public void onlySuccessfulNaturalInsertionsCanSurvive() {
        NameAuditCapture.Frame frame = NameAuditCapture.begin(new Object());
        try {
            ArrayList<String> rows = NameAuditEnchantmentCapture.rows();
            frame.initialList = rows;
            rows.add("title");
            Object enchantment = new Object();
            String line = new String("Curse");
            NameAuditEnchantmentCapture.naming(enchantment, 1, "source");
            NameAuditEnchantmentCapture.inserting(rows, line);
            NameAuditEnchantmentCapture.inserted(rows.add(line));
            rows.add("footer");
            assertFalse(rows.removeAll(java.util.Collections.emptyList()));
            assertFalse(rows.removeAll(java.util.Collections.singleton("absent")));
            assertEquals(1, frame.enchantments.size());
            NameAuditEnchantmentCapture.Receipt receipt = frame.enchantments.get(0);
            assertSame(enchantment, receipt.enchantment);
            assertTrue(receipt.survives(rows));
            rows.set(1, line);
            assertFalse(receipt.survives(rows));
            assertFalse(frame.failed);
        } finally { NameAuditCapture.end(); }
    }

    @Test public void missingInsertionsAndUntrackedViewsRemainUnresolved() {
        NameAuditCapture.Frame frame = NameAuditCapture.begin(new Object());
        try {
            ArrayList<String> rows = NameAuditEnchantmentCapture.rows();
            frame.initialList = rows;
            rows.add("title");
            List<String> view = rows.subList(0, 1);
            String line = "Protection";
            NameAuditEnchantmentCapture.naming(new Object(), 4, "source");
            assertTrue(frame.enchantments.isEmpty());
            NameAuditEnchantmentCapture.inserting(rows, line);
            NameAuditEnchantmentCapture.inserted(rows.add(line));
            assertFalse(frame.enchantments.get(0).survives(rows));
        } finally { NameAuditCapture.end(); }
    }

    @Test public void modernSequencedOperationsCannotRestoreSurvival() throws Exception {
        java.lang.reflect.Method remove;
        try { remove = List.class.getMethod("removeLast"); }
        catch (NoSuchMethodException unavailable) { return; }
        NameAuditCapture.Frame frame = NameAuditCapture.begin(new Object());
        try {
            ArrayList<String> rows = NameAuditEnchantmentCapture.rows();
            frame.initialList = rows;
            rows.add("title");
            String line = "Protection";
            NameAuditEnchantmentCapture.naming(new Object(), 4, "source");
            NameAuditEnchantmentCapture.inserting(rows, line);
            NameAuditEnchantmentCapture.inserted(rows.add(line));
            rows.add((String) remove.invoke(rows));
            assertFalse(frame.enchantments.get(0).survives(rows));
        } finally { NameAuditCapture.end(); }
    }

    @Test public void annotationsMustRetainTheExactNaturalInsertionBinding() {
        NameAuditCapture.Frame frame = NameAuditCapture.begin(new Object());
        try {
            List<String> rows = new ArrayList<>();
            frame.initialList = rows;
            rows.add("title");
            Object enchantment = new Object();
            String original = new String("Curse"), annotated = new String("Curse | target");
            NameAuditEnchantmentCapture.naming(enchantment, 1, "source");
            NameAuditEnchantmentCapture.inserting(rows, original);
            NameAuditEnchantmentCapture.inserted(rows.add(original));
            rows.set(1, annotated);
            NameAuditEnchantmentCapture.annotated(true, rows, new RowBinding(1, original, enchantment, 1), annotated);
            assertSame(annotated, frame.enchantments.get(0).annotatedLine);
            assertFalse(frame.failed);
            NameAuditEnchantmentCapture.annotated(true, rows, new RowBinding(1, original, new Object(), 1), annotated);
            assertTrue(frame.failed);
        } finally { NameAuditCapture.end(); }
    }

    @Test public void anAnnotationCanFollowItsExactNativeRowAfterAnUnrelatedInsertion() {
        NameAuditCapture.Frame frame = NameAuditCapture.begin(new Object());
        try {
            List<String> rows = new ArrayList<>();
            frame.initialList = rows;
            rows.add("title");
            Object enchantment = new Object();
            String original = new String("Curse"), annotated = new String("Curse | target");
            NameAuditEnchantmentCapture.naming(enchantment, 1, "source");
            NameAuditEnchantmentCapture.inserting(rows, original);
            NameAuditEnchantmentCapture.inserted(rows.add(original));
            rows.add(1, "visual stats spacer");
            rows.set(2, annotated);
            NameAuditEnchantmentCapture.annotated(true, rows, new RowBinding(2, original, enchantment, 1), annotated);
            assertSame(annotated, frame.enchantments.get(0).annotatedLine);
            assertFalse(frame.failed);
        } finally { NameAuditCapture.end(); }
    }

    static final class RowBinding {
        final int index;
        final String row;
        final EnchantmentBinding value;
        RowBinding(int index, String row, Object enchantment, int level) {
            this.index = index; this.row = row; this.value = new EnchantmentBinding(enchantment, level);
        }
    }

    static final class EnchantmentBinding {
        final Object enchantment;
        final int level;
        final String source = "native-producer";
        EnchantmentBinding(Object enchantment, int level) { this.enchantment = enchantment; this.level = level; }
    }
}
