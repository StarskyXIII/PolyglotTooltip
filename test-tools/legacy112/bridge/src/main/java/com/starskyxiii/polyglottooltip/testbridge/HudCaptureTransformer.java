package com.starskyxiii.polyglottooltip.testbridge;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class HudCaptureTransformer {
    private static final String PROBE = "com/starskyxiii/polyglottooltip/testbridge/HudProbe";
    private static final String TOP = "mcjty/theoneprobe/";
    private static final String WAILA = "mcp/mobius/waila/";
    private HudCaptureTransformer() { }

    public static byte[] transform(String name, byte[] bytes) {
        if (name.equals("mcjty.theoneprobe.network.PacketReturnInfo$Handler") || name.equals("mcjty.theoneprobe.network.PacketReturnEntityInfo$Handler")) return delivery(name, bytes);
        if (name.equals("mcp.mobius.waila.network.MessageReceiveData$Handler") || name.equals("mcp.mobius.waila.api.impl.DataAccessorCommon")) return wailaNetwork(name, bytes);
        if (!name.equals("mcjty.theoneprobe.rendering.OverlayRenderer") && !name.equals("mcjty.theoneprobe.rendering.RenderHelper")
                && !name.equals("mcjty.theoneprobe.apiimpl.client.ElementTextRender") && !name.equals("mcp.mobius.waila.overlay.OverlayRenderer")
                && !name.equals("mcp.mobius.waila.overlay.DisplayUtil")) return bytes;
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
        int changes = 0;
        boolean community = node.methods.stream().anyMatch(method -> method.name.equals("renderFadingOverlayIfNeeded"));
        for (MethodNode method : node.methods) {
            if (name.equals("mcjty.theoneprobe.rendering.OverlayRenderer")) {
                if (method.name.equals("renderHUD")) {
                    require(method.desc.equals("(Lmcjty/theoneprobe/api/ProbeMode;F)V") && (method.access & Opcodes.ACC_STATIC) != 0, "TOP HUD entry");
                    method.instructions.insert(call("topHud", "()V")); changes++;
                    for (AbstractInsnNode instruction : method.instructions.toArray()) if (instruction.getOpcode() == Opcodes.RETURN) method.instructions.insertBefore(instruction, call("topHudEnd", "()V"));
                } else if (method.name.equals("renderHUDBlock") || method.name.equals("renderHUDEntity")) {
                    InsnList code = new InsnList(); code.add(new VarInsnNode(Opcodes.ALOAD, 1)); code.add(call("topTarget", "(Lnet/minecraft/util/math/RayTraceResult;)V"));
                    method.instructions.insert(code); changes++;
                } else if (method.name.equals("registerProbeInfo")) {
                    InsnList code = new InsnList();
                    if (method.desc.startsWith("(I")) {
                        code.add(new VarInsnNode(Opcodes.ILOAD, 0)); code.add(new VarInsnNode(Opcodes.ALOAD, 1)); code.add(new VarInsnNode(Opcodes.ALOAD, 2));
                        code.add(call("registeredBlock", "(ILnet/minecraft/util/math/BlockPos;Ljava/lang/Object;)V"));
                    } else {
                        code.add(new VarInsnNode(Opcodes.ALOAD, 0)); code.add(new VarInsnNode(Opcodes.ALOAD, 1));
                        code.add(call("registeredEntity", "(Ljava/util/UUID;Ljava/lang/Object;)V"));
                    }
                    AbstractInsnNode last = lastReturn(method); method.instructions.insertBefore(last, code); changes++;
                } else if (method.name.equals("renderElements") && (community ? method.desc.endsWith(";Z)V") : method.desc.endsWith("IElement;)V"))) {
                    InsnList code = new InsnList(); code.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    code.add(community ? new VarInsnNode(Opcodes.ILOAD, 7) : new InsnNode(Opcodes.ICONST_1));
                    code.add(call("topBegin", "(Ljava/lang/Object;Z)V")); method.instructions.insert(code);
                    int widths = 0, heights = 0, draws = 0, fades = 0;
                    for (AbstractInsnNode instruction : method.instructions.toArray()) {
                        if (instruction.getOpcode() == Opcodes.RETURN) method.instructions.insertBefore(instruction, call("topEnd", "()V"));
                        if (!(instruction instanceof MethodInsnNode)) continue;
                        MethodInsnNode invoke = (MethodInsnNode) instruction;
                        boolean layout = invoke.owner.equals(TOP + (community ? "rendering/ProbeInfoColumnLayout" : "apiimpl/ProbeInfo"));
                        if (layout && invoke.name.equals("getWidth") && invoke.desc.equals("()I")) { observeInt(method, invoke, "topWidth"); widths++; }
                        if (layout && invoke.name.equals("getHeight") && invoke.desc.equals("()I")) { observeInt(method, invoke, "topHeight"); heights++; }
                        if (layout && invoke.name.equals("render") && invoke.desc.equals("(II)V")) {
                            InsnList before = new InsnList(); before.add(new InsnNode(Opcodes.DUP2)); before.add(call("topPosition", "(II)V"));
                            method.instructions.insertBefore(invoke, before); method.instructions.insert(invoke, call("topDrawn", "()V")); draws++;
                        }
                        if (invoke.owner.equals(node.name) && invoke.name.equals("updateOverlayFade") && invoke.desc.equals("(Z)F")) {
                            InsnList after = new InsnList(); after.add(new InsnNode(Opcodes.DUP)); after.add(call("topFade", "(F)V")); method.instructions.insert(invoke, after); fades++;
                        }
                        if (invoke.owner.equals(TOP + "rendering/ProbeInfoColumnLayout") && invoke.name.equals("create")) {
                            InsnList after = new InsnList(); after.add(new InsnNode(Opcodes.DUP)); after.add(call("topLayout", "(Ljava/lang/Object;)V")); method.instructions.insert(invoke, after);
                        }
                    }
                    require(widths == 1 && heights == 1 && draws == 1 && fades == (community ? 1 : 0), "TOP layout calls"); changes++;
                }
            } else if (name.equals("mcjty.theoneprobe.rendering.RenderHelper")) {
                if (method.name.equals("renderText") && method.desc.equals("(Lnet/minecraft/client/Minecraft;IILjava/lang/String;)I")) {
                    for (AbstractInsnNode instruction : method.instructions.toArray()) if (instruction.getOpcode() == Opcodes.IRETURN) {
                        InsnList code = new InsnList(); code.add(new InsnNode(Opcodes.DUP)); code.add(new VarInsnNode(Opcodes.ALOAD, 3));
                        code.add(new VarInsnNode(Opcodes.ILOAD, 1)); code.add(new VarInsnNode(Opcodes.ILOAD, 2)); code.add(call("topText", "(ILjava/lang/String;II)V"));
                        method.instructions.insertBefore(instruction, code);
                    }
                    changes++;
                } else if (method.name.equals("drawThickBeveledBox") && method.desc.equals("(IIIIIIII)V")) {
                    InsnList code = new InsnList(); for (int index = 0; index < 4; index++) code.add(new VarInsnNode(Opcodes.ILOAD, index));
                    code.add(call("topBox", "(IIII)V")); method.instructions.insert(code); changes++;
                }
            } else if (name.equals("mcjty.theoneprobe.apiimpl.client.ElementTextRender") && method.name.equals("getWidth")) {
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (!(instruction instanceof MethodInsnNode)) continue;
                    MethodInsnNode invoke = (MethodInsnNode) instruction;
                    if (!invoke.owner.equals("net/minecraft/client/gui/FontRenderer") || !invoke.name.equals("func_78256_a") || !invoke.desc.equals("(Ljava/lang/String;)I")) continue;
                    method.instructions.insertBefore(invoke, new InsnNode(Opcodes.DUP2));
                    method.instructions.insert(invoke, call("topMeasured", "(Ljava/lang/Object;Ljava/lang/String;I)I")); changes++;
                }
            } else if (name.equals("mcp.mobius.waila.overlay.OverlayRenderer") && method.name.equals("renderOverlay") && method.desc.equals("()V")) {
                for (AbstractInsnNode instruction : method.instructions.toArray()) if (instruction.getOpcode() == Opcodes.RETURN) method.instructions.insertBefore(instruction, call("wailaHudEnd", "()V"));
                changes++;
            } else if (name.equals("mcp.mobius.waila.overlay.OverlayRenderer") && method.name.equals("drawTooltipBox") && method.desc.equals("(IIIIIII)V")) {
                InsnList code = new InsnList(); for (int index = 0; index < 4; index++) code.add(new VarInsnNode(Opcodes.ILOAD, index));
                code.add(call("wailaBox", "(IIII)V")); method.instructions.insert(code); changes++;
            } else if (name.equals("mcp.mobius.waila.overlay.OverlayRenderer") && method.name.equals("renderOverlay") && method.desc.equals("(Lmcp/mobius/waila/overlay/Tooltip;)V")) {
                require((method.access & Opcodes.ACC_STATIC) != 0, "HWYLA renderer static");
                InsnList code = new InsnList(); code.add(new VarInsnNode(Opcodes.ALOAD, 0)); code.add(call("wailaBegin", "(Ljava/lang/Object;)V")); method.instructions.insert(code);
                int draws = 0;
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction.getOpcode() == Opcodes.RETURN) method.instructions.insertBefore(instruction, call("wailaEnd", "()V"));
                    if (!(instruction instanceof MethodInsnNode)) continue;
                    MethodInsnNode invoke = (MethodInsnNode) instruction;
                    if (invoke.owner.equals(WAILA + "overlay/Tooltip") && (invoke.name.equals("draw") || invoke.name.equals("draw2nd")) && invoke.desc.equals("()V")) {
                        method.instructions.insert(invoke, call("wailaDrawn", "()V")); draws++;
                    }
                }
                require(draws == 2, "HWYLA drawing passes"); changes++;
            } else if (name.equals("mcp.mobius.waila.overlay.DisplayUtil") && method.name.equals("drawString") && method.desc.equals("(Ljava/lang/String;IIIZ)V")) {
                for (AbstractInsnNode instruction : method.instructions.toArray()) if (instruction.getOpcode() == Opcodes.RETURN) {
                    InsnList code = new InsnList(); code.add(new VarInsnNode(Opcodes.ALOAD, 0)); code.add(new VarInsnNode(Opcodes.ILOAD, 1)); code.add(new VarInsnNode(Opcodes.ILOAD, 2));
                    code.add(call("wailaText", "(Ljava/lang/String;II)V")); method.instructions.insertBefore(instruction, code);
                }
                changes++;
            }
        }
        int expected = name.equals("mcjty.theoneprobe.rendering.OverlayRenderer") ? 6 : name.equals("mcjty.theoneprobe.rendering.RenderHelper") ? 2 : name.equals("mcp.mobius.waila.overlay.OverlayRenderer") ? 3 : 1;
        require(changes == expected, name + " observer count " + changes);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }

    private static byte[] delivery(String name, byte[] bytes) {
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); int changes = 0;
        boolean entity = name.contains("EntityInfo");
        String descriptor = entity ? "(Ljava/util/UUID;Lmcjty/theoneprobe/apiimpl/ProbeInfo;)V" : "(ILnet/minecraft/util/math/BlockPos;Lmcjty/theoneprobe/apiimpl/ProbeInfo;)V";
        for (MethodNode method : node.methods) if (method.name.equals("lambda$onMessage$0") && (method.access & Opcodes.ACC_STATIC) != 0) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) if (instruction instanceof MethodInsnNode) {
                MethodInsnNode invoke = (MethodInsnNode) instruction;
                if (invoke.getOpcode() == Opcodes.INVOKESTATIC && invoke.owner.equals(TOP + "rendering/OverlayRenderer") && invoke.name.equals("registerProbeInfo") && invoke.desc.equals(descriptor)) {
                    invoke.owner = "com/starskyxiii/polyglottooltip/testbridge/HudDelivery"; invoke.name = entity ? "entity" : "block";
                    invoke.desc = descriptor.replace("Lmcjty/theoneprobe/apiimpl/ProbeInfo;", "Ljava/lang/Object;"); changes++;
                }
            }
        }
        require(changes == 1, name + " delivery count " + changes);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }

    private static byte[] wailaNetwork(String name, byte[] bytes) {
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); int changes = 0;
        boolean receive = name.endsWith("$Handler");
        String descriptor = receive ? "(Lmcp/mobius/waila/network/MessageReceiveData;Lnet/minecraftforge/fml/common/network/simpleimpl/MessageContext;)Lnet/minecraftforge/fml/common/network/simpleimpl/IMessage;" : "(Lnet/minecraft/nbt/NBTTagCompound;)V";
        for (MethodNode method : node.methods) if (method.name.equals(receive ? "onMessage" : "setNBTData") && method.desc.equals(descriptor)) {
            require((method.access & Opcodes.ACC_STATIC) == 0, "HWYLA network instance");
            InsnList code = new InsnList(); code.add(new VarInsnNode(Opcodes.ALOAD, 1));
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/starskyxiii/polyglottooltip/testbridge/WailaNetworkTrace", receive ? "receive" : "assigned", receive ? "(Ljava/lang/Object;)V" : "(Lnet/minecraft/nbt/NBTTagCompound;)V", false));
            if (receive) method.instructions.insert(code); else method.instructions.insertBefore(lastReturn(method), code); changes++;
        }
        require(changes == 1, name + " packet observation count " + changes);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }

    private static MethodInsnNode call(String name, String descriptor) { return new MethodInsnNode(Opcodes.INVOKESTATIC, PROBE, name, descriptor, false); }
    private static void observeInt(MethodNode method, AbstractInsnNode invoke, String name) {
        InsnList code = new InsnList(); code.add(new InsnNode(Opcodes.DUP)); code.add(call(name, "(I)V")); method.instructions.insert(invoke, code);
    }
    private static AbstractInsnNode lastReturn(MethodNode method) {
        AbstractInsnNode last = method.instructions.getLast();
        while (last != null && last.getOpcode() != Opcodes.RETURN) last = last.getPrevious();
        require(last != null, "No normal return"); return last;
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
