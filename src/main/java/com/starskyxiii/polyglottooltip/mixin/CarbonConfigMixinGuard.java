package com.starskyxiii.polyglottooltip.mixin;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

final class CarbonConfigMixinGuard {
    static final String ARRAY = "carbonconfiglib.gui.impl.forge.ForgeArray";
    static final String VALUE = "carbonconfiglib.gui.impl.forge.ForgeValue";
    private static final String ARRAY_OWNER = ARRAY.replace('.', '/');
    private static final String VALUE_OWNER = VALUE.replace('.', '/');
    private static final String LIST = "Ljava/util/List;";
    private static final String CONSUMER = "Ljava/util/function/Consumer;";
    private static final String SAVE = "(Ljava/lang/String;L" + VALUE_OWNER + ";)V";

    private CarbonConfigMixinGuard() {
    }

    static boolean needsSaveIndexFix(ClassNode array) {
        if (!arrayFields(array)) {
            return false;
        }
        MethodNode save = method(array, "save", SAVE);
        if (save == null || (save.access & Opcodes.ACC_PRIVATE) == 0) {
            return false;
        }
        List<AbstractInsnNode> code = instructions(save);
        if (code.size() != 16 && code.size() != 18) {
            return false;
        }
        if (!var(code.get(0), Opcodes.ALOAD, 0)
                || !field(code.get(1), Opcodes.GETFIELD, ARRAY_OWNER, "values", LIST)
                || !var(code.get(2), Opcodes.ALOAD, 2)
                || !call(code.get(3), Opcodes.INVOKEINTERFACE, "java/util/List", "indexOf", "(Ljava/lang/Object;)I")
                || !var(code.get(4), Opcodes.ISTORE, 3)
                || !var(code.get(5), Opcodes.ILOAD, 3)
                || code.get(6).getOpcode() != Opcodes.ICONST_M1
                || !(code.get(7) instanceof JumpInsnNode)
                || code.get(7).getOpcode() != Opcodes.IF_ICMPNE
                || nextInstruction(((JumpInsnNode) code.get(7)).label) != code.get(9)
                || code.get(8).getOpcode() != Opcodes.RETURN
                || !var(code.get(9), Opcodes.ALOAD, 0)
                || !field(code.get(10), Opcodes.GETFIELD, ARRAY_OWNER, "currentValues", LIST)
                || code.get(11).getOpcode() != Opcodes.ICONST_0
                || !var(code.get(12), Opcodes.ALOAD, 1)
                || !call(code.get(13), Opcodes.INVOKEINTERFACE, "java/util/List", "set", "(ILjava/lang/Object;)Ljava/lang/Object;")
                || code.get(14).getOpcode() != Opcodes.POP) {
            return false;
        }
        if (code.size() == 16) {
            return code.get(15).getOpcode() == Opcodes.RETURN;
        }
        return var(code.get(15), Opcodes.ALOAD, 0)
                && (call(code.get(16), Opcodes.INVOKESPECIAL, ARRAY_OWNER, "autosave", "()V")
                    || call(code.get(16), Opcodes.INVOKEVIRTUAL, ARRAY_OWNER, "autosave", "()V"))
                && method(array, "autosave", "()V") != null
                && code.get(17).getOpcode() == Opcodes.RETURN;
    }

    static boolean needsRemovalIndexFix(ClassNode array, ClassNode value) {
        if (!arrayFields(array) || value == null || !VALUE_OWNER.equals(value.name)
                || !hasField(value, "saved", CONSUMER, true)
                || !hasField(value, "current", "Ljava/lang/String;", false)
                || method(array, "save", SAVE) != null) {
            return false;
        }
        List<AbstractInsnNode> remove = instructions(method(array, "removeNode", "(I)V"));
        if (remove.size() != 11
                || !var(remove.get(0), Opcodes.ALOAD, 0)
                || !field(remove.get(1), Opcodes.GETFIELD, ARRAY_OWNER, "values", LIST)
                || !var(remove.get(2), Opcodes.ILOAD, 1)
                || !call(remove.get(3), Opcodes.INVOKEINTERFACE, "java/util/List", "remove", "(I)Ljava/lang/Object;")
                || remove.get(4).getOpcode() != Opcodes.POP
                || !var(remove.get(5), Opcodes.ALOAD, 0)
                || !field(remove.get(6), Opcodes.GETFIELD, ARRAY_OWNER, "currentValues", LIST)
                || !var(remove.get(7), Opcodes.ILOAD, 1)
                || !call(remove.get(8), Opcodes.INVOKEINTERFACE, "java/util/List", "remove", "(I)Ljava/lang/Object;")
                || remove.get(9).getOpcode() != Opcodes.POP
                || remove.get(10).getOpcode() != Opcodes.RETURN) {
            return false;
        }
        List<AbstractInsnNode> save = instructions(method(value, "save", "()V"));
        return save.size() == 6
                && var(save.get(0), Opcodes.ALOAD, 0)
                && field(save.get(1), Opcodes.GETFIELD, VALUE_OWNER, "saved", CONSUMER)
                && var(save.get(2), Opcodes.ALOAD, 0)
                && field(save.get(3), Opcodes.GETFIELD, VALUE_OWNER, "current", "Ljava/lang/String;")
                && call(save.get(4), Opcodes.INVOKEINTERFACE, "java/util/function/Consumer", "accept", "(Ljava/lang/Object;)V")
                && save.get(5).getOpcode() == Opcodes.RETURN
                && callbackWiring(array, value, "reload")
                && callbackWiring(array, value, "createNode");
    }

    private static boolean callbackWiring(ClassNode array, ClassNode value, String name) {
        List<AbstractInsnNode> code = instructions(method(array, name, "()V"));
        int callbacks = 0;
        for (int i = 0; i + 1 < code.size(); i++) {
            if (!(code.get(i) instanceof InvokeDynamicInsnNode)) {
                continue;
            }
            InvokeDynamicInsnNode factory = (InvokeDynamicInsnNode) code.get(i);
            if (!factory.desc.equals("(L" + ARRAY_OWNER + ";I)" + CONSUMER)) {
                continue;
            }
            if (!factory.name.equals("accept")
                    || !factory.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")
                    || !factory.bsm.getName().equals("metafactory")
                    || factory.bsm.getTag() != Opcodes.H_INVOKESTATIC
                    || factory.bsm.isInterface()
                    || factory.bsmArgs.length != 3
                    || !Type.getMethodType("(Ljava/lang/Object;)V").equals(factory.bsmArgs[0])
                    || !Type.getMethodType("(Ljava/lang/String;)V").equals(factory.bsmArgs[2])
                    || !(factory.bsmArgs[1] instanceof Handle)) {
                return false;
            }
            Handle implementation = (Handle) factory.bsmArgs[1];
            if (!implementation.getOwner().equals(ARRAY_OWNER)
                    || (implementation.getTag() != Opcodes.H_INVOKESPECIAL
                        && implementation.getTag() != Opcodes.H_INVOKEVIRTUAL)
                    || implementation.isInterface()
                    || !implementation.getName().startsWith("lambda$" + name + "$")
                    || !implementation.getDesc().equals("(ILjava/lang/String;)V")
                    || !indexCallback(method(array, implementation.getName(), implementation.getDesc()))
                    || !(code.get(i + 1) instanceof MethodInsnNode)) {
                return false;
            }
            MethodInsnNode constructor = (MethodInsnNode) code.get(i + 1);
            if (!call(constructor, Opcodes.INVOKESPECIAL, VALUE_OWNER, "<init>", constructor.desc)
                    || !constructor.desc.endsWith(CONSUMER + ")V")
                    || !savesConsumerArgument(method(value, "<init>", constructor.desc))) {
                return false;
            }
            callbacks++;
        }
        return callbacks == 1;
    }

    private static boolean indexCallback(MethodNode callback) {
        List<AbstractInsnNode> code = instructions(callback);
        if (code.size() != 7 && code.size() != 8) {
            return false;
        }
        int pop = code.size() - 2;
        return var(code.get(0), Opcodes.ALOAD, 0)
                && field(code.get(1), Opcodes.GETFIELD, ARRAY_OWNER, "currentValues", LIST)
                && var(code.get(2), Opcodes.ILOAD, 1)
                && var(code.get(3), Opcodes.ALOAD, 2)
                && call(code.get(4), Opcodes.INVOKEINTERFACE, "java/util/List", "set", "(ILjava/lang/Object;)Ljava/lang/Object;")
                && (code.size() == 7 || (code.get(5) instanceof TypeInsnNode
                    && code.get(5).getOpcode() == Opcodes.CHECKCAST
                    && ((TypeInsnNode) code.get(5)).desc.equals("java/lang/String")))
                && code.get(pop).getOpcode() == Opcodes.POP
                && code.get(pop + 1).getOpcode() == Opcodes.RETURN;
    }

    private static boolean savesConsumerArgument(MethodNode constructor) {
        if (constructor == null) {
            return false;
        }
        Type[] arguments = Type.getArgumentTypes(constructor.desc);
        int slot = 1;
        for (int i = 0; i < arguments.length - 1; i++) {
            slot += arguments[i].getSize();
        }
        List<AbstractInsnNode> code = instructions(constructor);
        int writes = 0;
        for (int i = 0; i < code.size(); i++) {
            if (field(code.get(i), Opcodes.PUTFIELD, VALUE_OWNER, "saved", CONSUMER)) {
                if (i < 2 || !var(code.get(i - 2), Opcodes.ALOAD, 0)
                        || !var(code.get(i - 1), Opcodes.ALOAD, slot)) {
                    return false;
                }
                writes++;
            }
        }
        return writes == 1;
    }

    private static boolean arrayFields(ClassNode array) {
        return array != null && ARRAY_OWNER.equals(array.name)
                && hasField(array, "values", LIST, false)
                && hasField(array, "currentValues", LIST, false);
    }

    private static boolean hasField(ClassNode owner, String name, String descriptor, boolean writable) {
        return owner.fields.stream().filter(field -> field.name.equals(name)
                && field.desc.equals(descriptor)
                && (field.access & (Opcodes.ACC_STATIC | (writable ? Opcodes.ACC_FINAL : 0))) == 0).count() == 1;
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        MethodNode found = null;
        for (MethodNode candidate : owner.methods) {
            if (candidate.name.equals(name) && candidate.desc.equals(descriptor)) {
                if (found != null || (candidate.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
                    return null;
                }
                found = candidate;
            }
        }
        return found;
    }

    private static List<AbstractInsnNode> instructions(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<>();
        if (method != null && method.tryCatchBlocks.isEmpty()) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction.getOpcode() >= 0) {
                    result.add(instruction);
                }
            }
        }
        return result;
    }

    private static AbstractInsnNode nextInstruction(AbstractInsnNode instruction) {
        while (instruction != null && instruction.getOpcode() < 0) {
            instruction = instruction.getNext();
        }
        return instruction;
    }

    private static boolean var(AbstractInsnNode instruction, int opcode, int slot) {
        return instruction instanceof VarInsnNode && instruction.getOpcode() == opcode
                && ((VarInsnNode) instruction).var == slot;
    }

    private static boolean field(AbstractInsnNode instruction, int opcode, String owner, String name, String descriptor) {
        if (!(instruction instanceof FieldInsnNode) || instruction.getOpcode() != opcode) {
            return false;
        }
        FieldInsnNode field = (FieldInsnNode) instruction;
        return field.owner.equals(owner) && field.name.equals(name) && field.desc.equals(descriptor);
    }

    private static boolean call(AbstractInsnNode instruction, int opcode, String owner, String name, String descriptor) {
        if (!(instruction instanceof MethodInsnNode) || instruction.getOpcode() != opcode) {
            return false;
        }
        MethodInsnNode method = (MethodInsnNode) instruction;
        return method.owner.equals(owner) && method.name.equals(name) && method.desc.equals(descriptor)
                && method.itf == (opcode == Opcodes.INVOKEINTERFACE);
    }
}
