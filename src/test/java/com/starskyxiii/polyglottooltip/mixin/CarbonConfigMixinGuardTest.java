package com.starskyxiii.polyglottooltip.mixin;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarbonConfigMixinGuardTest {
    private static final String ARRAY = CarbonConfigMixinGuard.ARRAY.replace('.', '/');
    private static final String VALUE = CarbonConfigMixinGuard.VALUE.replace('.', '/');
    private static final String PREFIX = "com.starskyxiii.polyglottooltip.mixin.carbonconfig.";
    private static final String LIST = "Ljava/util/List;";
    private static final String CONSUMER = "Ljava/util/function/Consumer;";
    private static final String SAVE = "(Ljava/lang/String;L" + VALUE + ";)V";

    @Test
    void selectsEveryPublishedSaveTail() {
        for (int tail : new int[] {0, Opcodes.INVOKESPECIAL, Opcodes.INVOKEVIRTUAL}) {
            ClassNode array = current(tail);
            assertTrue(CarbonConfigMixinGuard.needsSaveIndexFix(array));
            assertFalse(CarbonConfigMixinGuard.needsRemovalIndexFix(array, legacyValue()));
        }
    }

    @Test
    void skipsCorrectedAndChangedSaveBodies() {
        for (Consumer<ClassNode> mutation : Arrays.<Consumer<ClassNode>>asList(
                node -> save(node).instructions.set(save(node).instructions.get(12), new VarInsnNode(Opcodes.ILOAD, 3)),
                node -> save(node).desc = "(Ljava/lang/Object;L" + VALUE + ";)V",
                node -> save(node).access = Opcodes.ACC_STATIC,
                node -> save(node).access = Opcodes.ACC_PUBLIC,
                node -> ((VarInsnNode) save(node).instructions.get(4)).var = 4,
                node -> ((FieldInsnNode) save(node).instructions.get(1)).owner = "other/Array",
                node -> ((FieldInsnNode) save(node).instructions.get(11)).name = "defaults",
                node -> ((MethodInsnNode) save(node).instructions.get(14)).name = "add",
                node -> ((MethodInsnNode) save(node).instructions.get(14)).itf = false,
                node -> ((JumpInsnNode) save(node).instructions.get(7)).label = new LabelNode(),
                node -> save(node).instructions.insert(new InsnNode(Opcodes.NOP)),
                node -> node.fields.get(0).desc = "Ljava/util/ArrayList;",
                node -> node.fields.get(0).access = Opcodes.ACC_STATIC,
                node -> node.name = "other/Array",
                node -> node.methods.add(save(node)))) {
            ClassNode array = current(0);
            mutation.accept(array);
            assertFalse(CarbonConfigMixinGuard.needsSaveIndexFix(array));
        }
    }

    @Test
    void selectsBothHistoricalCallbackVariants() {
        for (boolean cast : new boolean[] {false, true}) {
            ClassNode array = legacyArray(cast);
            assertTrue(CarbonConfigMixinGuard.needsRemovalIndexFix(array, legacyValue()));
            assertFalse(CarbonConfigMixinGuard.needsSaveIndexFix(array));
        }
    }

    @Test
    void skipsChangedHistoricalRemovalAndCallbackContracts() {
        for (Consumer<ClassNode> mutation : Arrays.<Consumer<ClassNode>>asList(
                node -> node.methods.get(0).instructions.insert(new InsnNode(Opcodes.NOP)),
                node -> ((FieldInsnNode) node.methods.get(0).instructions.get(6)).name = "defaults",
                node -> node.methods.get(0).desc = "(J)V",
                node -> node.methods.get(1).instructions.set(node.methods.get(1).instructions.get(2), new InsnNode(Opcodes.ICONST_0)),
                node -> node.methods.get(3).instructions.insert(new InsnNode(Opcodes.NOP)),
                node -> ((InvokeDynamicInsnNode) node.methods.get(2).instructions.get(0)).desc = "()" + CONSUMER,
                node -> ((InvokeDynamicInsnNode) node.methods.get(2).instructions.get(0)).bsmArgs[2] = Type.getMethodType("(I)V"),
                node -> ((InvokeDynamicInsnNode) node.methods.get(2).instructions.get(0)).bsmArgs[1] = new Handle(
                        Opcodes.H_INVOKESTATIC, ARRAY, "lambda$reload$1", "(ILjava/lang/String;)V", false),
                node -> ((MethodInsnNode) node.methods.get(2).instructions.get(1)).owner = "other/Value",
                node -> node.fields.get(1).access = Opcodes.ACC_STATIC)) {
            ClassNode array = legacyArray(false);
            mutation.accept(array);
            assertFalse(CarbonConfigMixinGuard.needsRemovalIndexFix(array, legacyValue()));
        }
    }

    @Test
    void skipsChangedHistoricalValueContracts() {
        for (Consumer<ClassNode> mutation : Arrays.<Consumer<ClassNode>>asList(
                node -> node.fields.get(0).desc = "Ljava/util/function/BiConsumer;",
                node -> node.fields.get(0).access = Opcodes.ACC_FINAL,
                node -> node.methods.get(0).instructions.insert(new InsnNode(Opcodes.NOP)),
                node -> ((FieldInsnNode) node.methods.get(0).instructions.get(3)).name = "defaultValue",
                node -> ((VarInsnNode) node.methods.get(1).instructions.get(1)).var = 0,
                node -> node.name = "other/Value")) {
            ClassNode value = legacyValue();
            mutation.accept(value);
            assertFalse(CarbonConfigMixinGuard.needsRemovalIndexFix(legacyArray(false), value));
        }
    }

    @Test
    void historicalMixinsShareTheSameGateAndRejectMismatchedTargets() {
        ClassNode array = legacyArray(false);
        ClassNode value = legacyValue();
        OptionalIntegrationMixinPlugin plugin = new OptionalIntegrationMixinPlugin(name ->
                name.equals(CarbonConfigMixinGuard.ARRAY) ? array : value);
        assertTrue(plugin.shouldApplyMixin(CarbonConfigMixinGuard.ARRAY, PREFIX + "LegacyCarbonForgeArrayRemoveMixin"));
        assertTrue(plugin.shouldApplyMixin(CarbonConfigMixinGuard.VALUE, PREFIX + "LegacyCarbonForgeValueAccessor"));
        assertFalse(plugin.shouldApplyMixin(CarbonConfigMixinGuard.VALUE, PREFIX + "LegacyCarbonForgeArrayRemoveMixin"));
        assertFalse(plugin.shouldApplyMixin(CarbonConfigMixinGuard.ARRAY, PREFIX + "LegacyCarbonForgeValueAccessor"));
        value.fields.clear();
        assertFalse(plugin.shouldApplyMixin(CarbonConfigMixinGuard.ARRAY, PREFIX + "LegacyCarbonForgeArrayRemoveMixin"));
        assertFalse(plugin.shouldApplyMixin(CarbonConfigMixinGuard.VALUE, PREFIX + "LegacyCarbonForgeValueAccessor"));
    }

    @Test
    void skipsMissingUnreadableAndMalformedOptionalClasses() {
        for (OptionalIntegrationMixinPlugin plugin : Arrays.asList(
                new OptionalIntegrationMixinPlugin(name -> null),
                new OptionalIntegrationMixinPlugin(name -> { throw new ClassNotFoundException(name); }),
                new OptionalIntegrationMixinPlugin(name -> { throw new IOException(name); }),
                new OptionalIntegrationMixinPlugin(name -> OptionalIntegrationMixinPlugin.readClassMetadata(
                        name, new ByteArrayInputStream(new byte[] {0, 1, 2}))))) {
            assertFalse(plugin.shouldApplyMixin(CarbonConfigMixinGuard.ARRAY, PREFIX + "CarbonForgeArraySaveMixin"));
            assertFalse(plugin.shouldApplyMixin(CarbonConfigMixinGuard.ARRAY, PREFIX + "LegacyCarbonForgeArrayRemoveMixin"));
            assertFalse(plugin.shouldApplyMixin(CarbonConfigMixinGuard.VALUE, PREFIX + "LegacyCarbonForgeValueAccessor"));
        }
    }

    @Test
    void readsOptionalTargetCodeWithoutDefiningCarbonClasses() throws Exception {
        ClassNode original = current(Opcodes.INVOKEVIRTUAL);
        original.version = Opcodes.V1_8;
        original.superName = "java/lang/Object";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        original.accept(writer);
        ClassNode loaded = OptionalIntegrationMixinPlugin.readClassMetadata(CarbonConfigMixinGuard.ARRAY,
                new ByteArrayInputStream(writer.toByteArray()));
        assertTrue(CarbonConfigMixinGuard.needsSaveIndexFix(loaded));
        ClassNode metadataOnly = OptionalIntegrationMixinPlugin.readClassMetadata("optional.OtherClass",
                new ByteArrayInputStream(writer.toByteArray()));
        assertFalse(CarbonConfigMixinGuard.needsSaveIndexFix(metadataOnly));
    }

    private static ClassNode current(int tail) {
        ClassNode node = arrayFields();
        MethodNode save = new MethodNode(Opcodes.ACC_PRIVATE, "save", SAVE, null, null);
        LabelNode found = new LabelNode();
        add(save, new VarInsnNode(Opcodes.ALOAD, 0), field(ARRAY, "values", LIST),
                new VarInsnNode(Opcodes.ALOAD, 2), call("java/util/List", "indexOf", "(Ljava/lang/Object;)I"),
                new VarInsnNode(Opcodes.ISTORE, 3), new VarInsnNode(Opcodes.ILOAD, 3), new InsnNode(Opcodes.ICONST_M1),
                new JumpInsnNode(Opcodes.IF_ICMPNE, found), new InsnNode(Opcodes.RETURN), found,
                new VarInsnNode(Opcodes.ALOAD, 0), field(ARRAY, "currentValues", LIST), new InsnNode(Opcodes.ICONST_0),
                new VarInsnNode(Opcodes.ALOAD, 1), call("java/util/List", "set", "(ILjava/lang/Object;)Ljava/lang/Object;"),
                new InsnNode(Opcodes.POP));
        if (tail != 0) {
            add(save, new VarInsnNode(Opcodes.ALOAD, 0), new MethodInsnNode(tail, ARRAY, "autosave", "()V", false));
            MethodNode autosave = new MethodNode(Opcodes.ACC_PRIVATE, "autosave", "()V", null, null);
            add(autosave, new InsnNode(Opcodes.RETURN));
            node.methods.add(autosave);
        }
        add(save, new InsnNode(Opcodes.RETURN));
        node.methods.add(save);
        return node;
    }

    private static ClassNode legacyArray(boolean cast) {
        ClassNode node = arrayFields();
        MethodNode remove = new MethodNode(Opcodes.ACC_PUBLIC, "removeNode", "(I)V", null, null);
        for (String name : Arrays.asList("values", "currentValues")) {
            add(remove, new VarInsnNode(Opcodes.ALOAD, 0), field(ARRAY, name, LIST),
                    new VarInsnNode(Opcodes.ILOAD, 1), call("java/util/List", "remove", "(I)Ljava/lang/Object;"),
                    new InsnNode(Opcodes.POP));
        }
        add(remove, new InsnNode(Opcodes.RETURN));
        node.methods.add(remove);
        for (String name : Arrays.asList("reload", "createNode")) {
            String lambdaName = "lambda$" + name + "$1";
            MethodNode callback = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC,
                    lambdaName, "(ILjava/lang/String;)V", null, null);
            add(callback, new VarInsnNode(Opcodes.ALOAD, 0), field(ARRAY, "currentValues", LIST),
                    new VarInsnNode(Opcodes.ILOAD, 1), new VarInsnNode(Opcodes.ALOAD, 2),
                    call("java/util/List", "set", "(ILjava/lang/Object;)Ljava/lang/Object;"));
            if (cast) {
                add(callback, new org.objectweb.asm.tree.TypeInsnNode(Opcodes.CHECKCAST, "java/lang/String"));
            }
            add(callback, new InsnNode(Opcodes.POP), new InsnNode(Opcodes.RETURN));
            node.methods.add(callback);
            MethodNode create = new MethodNode(Opcodes.ACC_PUBLIC, name, "()V", null, null);
            add(create, new InvokeDynamicInsnNode("accept", "(L" + ARRAY + ";I)" + CONSUMER,
                    new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                                    + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
                                    + "Ljava/lang/invoke/CallSite;", false),
                    Type.getMethodType("(Ljava/lang/Object;)V"),
                    new Handle(Opcodes.H_INVOKESPECIAL, ARRAY, lambdaName, "(ILjava/lang/String;)V", false),
                    Type.getMethodType("(Ljava/lang/String;)V")),
                    new MethodInsnNode(Opcodes.INVOKESPECIAL, VALUE, "<init>", "(" + CONSUMER + ")V", false),
                    new InsnNode(Opcodes.RETURN));
            node.methods.add(create);
        }
        return node;
    }

    private static ClassNode legacyValue() {
        ClassNode node = new ClassNode();
        node.name = VALUE;
        node.fields.add(new FieldNode(0, "saved", CONSUMER, null, null));
        node.fields.add(new FieldNode(0, "current", "Ljava/lang/String;", null, null));
        MethodNode save = new MethodNode(Opcodes.ACC_PUBLIC, "save", "()V", null, null);
        add(save, new VarInsnNode(Opcodes.ALOAD, 0), field(VALUE, "saved", CONSUMER),
                new VarInsnNode(Opcodes.ALOAD, 0), field(VALUE, "current", "Ljava/lang/String;"),
                call("java/util/function/Consumer", "accept", "(Ljava/lang/Object;)V"), new InsnNode(Opcodes.RETURN));
        node.methods.add(save);
        MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "(" + CONSUMER + ")V", null, null);
        add(constructor, new VarInsnNode(Opcodes.ALOAD, 0), new VarInsnNode(Opcodes.ALOAD, 1),
                new FieldInsnNode(Opcodes.PUTFIELD, VALUE, "saved", CONSUMER), new InsnNode(Opcodes.RETURN));
        node.methods.add(constructor);
        return node;
    }

    private static ClassNode arrayFields() {
        ClassNode node = new ClassNode();
        node.name = ARRAY;
        node.fields.add(new FieldNode(0, "values", LIST, null, null));
        node.fields.add(new FieldNode(0, "currentValues", LIST, null, null));
        return node;
    }

    private static MethodNode save(ClassNode node) {
        return node.methods.stream().filter(method -> method.name.equals("save")).findFirst().get();
    }

    private static FieldInsnNode field(String owner, String name, String descriptor) {
        return new FieldInsnNode(Opcodes.GETFIELD, owner, name, descriptor);
    }

    private static MethodInsnNode call(String owner, String name, String descriptor) {
        return new MethodInsnNode(Opcodes.INVOKEINTERFACE, owner, name, descriptor, true);
    }

    private static void add(MethodNode method, AbstractInsnNode... instructions) {
        for (AbstractInsnNode instruction : instructions) {
            method.instructions.add(instruction);
        }
    }
}
