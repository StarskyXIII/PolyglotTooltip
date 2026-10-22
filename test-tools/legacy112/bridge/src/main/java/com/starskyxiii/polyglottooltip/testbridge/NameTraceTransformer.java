package com.starskyxiii.polyglottooltip.testbridge;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public final class NameTraceTransformer {
    private NameTraceTransformer() { }
    public static byte[] transform(String name, byte[] bytes) {
        boolean cell = name.equals("com.github.glodblock.eae.common.item.InfinityCell");
        boolean ae = name.equals("appeng.util.item.AEItemStack") || name.equals("appeng.fluids.util.AEFluidStack");
        boolean registry = name.equals("appeng.util.item.AEItemStackRegistry");
        boolean stack = name.equals("net.minecraft.item.ItemStack"), item = name.equals("net.minecraft.item.Item") || name.matches("net.minecraft.item.Item(Potion|SplashPotion|LingeringPotion|TippedArrow)");
        boolean nbt = name.startsWith("net.minecraft.nbt.NBTTag"), fluid = name.equals("net.minecraftforge.fluids.FluidStack");
        boolean fluidProvider = name.matches("net.minecraftforge.fluids.FluidRegistry\\$[12]");
        if (!cell && !ae && !registry && !stack && !item && !nbt && !fluid && !fluidProvider) return bytes;
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
        for (MethodNode method : node.methods) {
            if (!((cell && (method.name.equals("getRecord") || method.name.equals("func_77653_i")))
                    || (ae && (method.name.equals("fromNBT") || method.name.equals("fromItemStack") || method.name.equals("fromFluidStack")))
                    || (registry && method.name.equals("getRegisteredStack"))
                    || (stack && (method.name.equals("<init>") || method.name.equals("func_82833_r") || method.name.equals("func_77946_l") || method.name.equals("func_77955_b")))
                    || (item && method.name.equals("func_77653_i"))
                    || (fluid && (method.name.equals("getLocalizedName") || method.name.equals("copy") || method.name.equals("writeToNBT")))
                    || (fluidProvider && method.name.equals("getLocalizedName"))
                    || (nbt && (method.name.equals("hashCode") || method.name.equals("equals") || method.name.equals("toString") || method.name.equals("func_74737_b"))))) continue;
            String key = name + "." + method.name + method.desc;
            InsnList entry = new InsnList(); entry.add(new LdcInsnNode(key));
            entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/starskyxiii/polyglottooltip/testbridge/NameTrace", "record", "(Ljava/lang/String;)V", false));
            method.instructions.insert(entry); NameTrace.install(key);
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
}
