package com.starskyxiii.polyglottooltip.testbridge;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class BridgeCaptureTransformer implements IClassTransformer {
    @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (!Boolean.getBoolean("clientdevbridge.enabled") || bytes == null) return bytes;
        if (transformedName.equals("com.raoulvdberge.refinedstorage.util.RenderUtils")) return refinedRender(bytes);
        // clientdevbridge.nameTrace=false leaves ItemStack and the other name sources untouched: the product refuses the
        // traced shape of ItemStack.getDisplayName, so a run that measures the product's fallback path needs this off.
        boolean nameTrace = !"false".equals(System.getProperty("clientdevbridge.nameTrace", "true"));
        if (nameTrace && (transformedName.equals("com.github.glodblock.eae.common.item.InfinityCell") || transformedName.startsWith("appeng.util.item.AEItemStack")
                || transformedName.equals("appeng.fluids.util.AEFluidStack") || transformedName.equals("net.minecraft.item.ItemStack")
                || transformedName.equals("net.minecraft.item.Item") || transformedName.startsWith("net.minecraft.nbt.NBTTag")
                || transformedName.matches("net.minecraft.item.Item(Potion|SplashPotion|LingeringPotion|TippedArrow)")
                || transformedName.equals("net.minecraftforge.fluids.FluidStack") || transformedName.matches("net.minecraftforge.fluids.FluidRegistry\\$[12]"))) return NameTraceTransformer.transform(transformedName, bytes);
        if (transformedName.startsWith("mcjty.theoneprobe.") || transformedName.startsWith("mcp.mobius.waila.")) return HudCaptureTransformer.transform(transformedName, bytes);
        if (transformedName.equals("carbonconfiglib.gui.impl.forge.ForgeArray")) return CarbonCaptureTransformer.transform(bytes);
        if ("thaumicenergistics.client.gui.helpers.MERepo".equals(transformedName)) return thaumic(bytes);
        if (!"extracells.gui.widget.fluid.AbstractFluidWidget".equals(transformedName)) return bytes;
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); int captures = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("drawHoveringText") || !method.desc.equals("(Ljava/util/List;IILnet/minecraft/client/gui/FontRenderer;)V")) continue;
            InsnList instructions = new InsnList(); instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/starskyxiii/polyglottooltip/testbridge/TooltipProbe", "observe", "(Ljava/util/List;)V", false));
            method.instructions.insert(instructions); captures++;
        }
        if (captures != 1) throw new IllegalStateException("Unsupported ExtraCells native tooltip capture shape");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }

    private static byte[] thaumic(byte[] bytes) {
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
        String descriptor = "(Lappeng/api/storage/data/IAEStack;Ljava/util/regex/Pattern;)Z";
        if (node.methods.stream().filter(method -> method.name.equals("searchName") && method.desc.equals(descriptor)).count() != 1) throw new IllegalStateException("Unsupported ThE native name predicate");
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, "polyglotDevBridge$searchName", descriptor, null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.name, "searchName", descriptor, false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN)); node.methods.add(method);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }

    private static byte[] refinedRender(byte[] bytes) {
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); int captures = 0;
        String descriptor = "(Ljava/util/List;Ljava/util/List;ZLnet/minecraft/item/ItemStack;IIIILnet/minecraft/client/gui/FontRenderer;)V";
        for (MethodNode method : node.methods) {
            if (!method.name.equals("drawTooltipWithSmallText") || !method.desc.equals(descriptor)) continue;
            InsnList entry = new InsnList();
            entry.add(new VarInsnNode(Opcodes.ALOAD, 0)); entry.add(new VarInsnNode(Opcodes.ALOAD, 1));
            entry.add(new VarInsnNode(Opcodes.ILOAD, 2)); entry.add(new VarInsnNode(Opcodes.ALOAD, 3));
            for (int index = 4; index < 8; index++) entry.add(new VarInsnNode(Opcodes.ILOAD, index));
            entry.add(new VarInsnNode(Opcodes.ALOAD, 8));
            entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/starskyxiii/polyglottooltip/testbridge/RefinedProbe", "capture", descriptor, false));
            method.instructions.insert(entry); captures++;
        }
        if (captures != 1) throw new IllegalStateException("Unexpected native RS tooltip boundary");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
}
