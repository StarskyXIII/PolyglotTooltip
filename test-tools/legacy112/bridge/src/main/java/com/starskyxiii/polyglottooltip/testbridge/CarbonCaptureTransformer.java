package com.starskyxiii.polyglottooltip.testbridge;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class CarbonCaptureTransformer {
    private static final String TRACE = "com/starskyxiii/polyglottooltip/testbridge/CarbonTrace";
    private CarbonCaptureTransformer() { }
    public static byte[] transform(byte[] bytes) {
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); int found = 0;
        for (MethodNode method : node.methods) if (method.name.equals("save") && method.desc.equals("(Ljava/lang/String;Lcarbonconfiglib/gui/impl/forge/ForgeValue;)V") && (method.access & Opcodes.ACC_STATIC) == 0) {
            InsnList begin = new InsnList(); begin.add(new VarInsnNode(Opcodes.ALOAD, 0)); begin.add(new VarInsnNode(Opcodes.ALOAD, 2)); begin.add(new VarInsnNode(Opcodes.ALOAD, 1));
            begin.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TRACE, "callback", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)V", false)); method.instructions.insert(begin);
            for (AbstractInsnNode instruction : method.instructions.toArray()) if (instruction.getOpcode() == Opcodes.RETURN) {
                InsnList end = new InsnList(); end.add(new VarInsnNode(Opcodes.ALOAD, 0)); end.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TRACE, "returned", "(Ljava/lang/Object;)V", false)); method.instructions.insertBefore(instruction, end);
            }
            found++;
        }
        if (found != 1) throw new IllegalStateException("Carbon native callback observation count " + found);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
}
