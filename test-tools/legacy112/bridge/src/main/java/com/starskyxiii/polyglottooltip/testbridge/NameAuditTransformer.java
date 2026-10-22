package com.starskyxiii.polyglottooltip.testbridge;

import java.security.MessageDigest;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public final class NameAuditTransformer implements IClassTransformer {
    @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (!Boolean.getBoolean("clientdevbridge.enabled") || !Boolean.getBoolean("clientdevbridge.nameAudit") || bytes == null) return bytes;
        if (!NameAuditFinalObserver.present() && net.minecraft.launchwrapper.Launch.classLoader != null && net.minecraft.launchwrapper.Launch.classLoader.getResource("polyglottooltip.names.properties") == null
                && ("net.minecraft.item.ItemStack".equals(transformedName) || "net.minecraft.item.ItemEnchantedBook".equals(transformedName)
                    || "austeretony.enchcontrol.common.core.ECHooks".equals(transformedName))) bytes = observeEnchantments(bytes);
        if ("net.minecraft.item.ItemStack".equals(transformedName)) return observeNames(bytes);
        if ("com.starskyxiii.polyglottooltip.legacy.minecraft.NativeNameScope".equals(transformedName)) return observeNameScope(bytes);
        if ("com.starskyxiii.polyglottooltip.legacy.minecraft.NativeEnchantmentScope".equals(transformedName)) return observeEnchantmentScope(bytes);
        if ("com.starskyxiii.polyglottooltip.legacy.ObservedTooltipRows".equals(transformedName)) return observeAnnotatedRows(bytes);
        if ("com.starskyxiii.polyglottooltip.legacy.minecraft.ItemNameObservation".equals(transformedName)) return observeProduct(bytes);
        if ("com.starskyxiii.polyglottooltip.legacy.bootstrap.NameMethodCode".equals(transformedName)) return observeMethodInputs(bytes);
        if (Boolean.getBoolean("clientdevbridge.hudAudit") && ("mcjty.theoneprobe.rendering.OverlayRenderer".equals(transformedName)
                || "mcjty.theoneprobe.rendering.RenderHelper".equals(transformedName)
                || "mcjty.theoneprobe.apiimpl.client.ElementTextRender".equals(transformedName)
                || "mcp.mobius.waila.overlay.OverlayRenderer".equals(transformedName)
                || "mcp.mobius.waila.overlay.DisplayUtil".equals(transformedName))) return HudCaptureTransformer.transform(transformedName, bytes);
        if (!"com.starskyxiii.polyglottooltip.legacy.SecondaryTooltipRows".equals(transformedName)) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int methods = 0, sites = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("insert") || !method.desc.equals("(Ljava/util/List;Ljava/util/List;I)V")) continue;
            if ((method.access & Opcodes.ACC_STATIC) == 0) throw new IllegalStateException("Audit producer must be static");
            methods++;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.getOpcode() != Opcodes.INVOKEINTERFACE || !call.owner.equals("java/util/List")
                        || !call.name.equals("add") || !call.desc.equals("(ILjava/lang/Object;)V")) continue;
                int local = method.maxLocals++;
                InsnList before = new InsnList();
                before.add(new InsnNode(Opcodes.DUP));
                before.add(new VarInsnNode(Opcodes.ASTORE, local));
                before.add(new VarInsnNode(Opcodes.ALOAD, 0));
                before.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/starskyxiii/polyglottooltip/testbridge/NameAuditCapture", "committing", "(Ljava/util/List;)V", false));
                method.instructions.insertBefore(call, before);
                InsnList after = new InsnList();
                after.add(new VarInsnNode(Opcodes.ALOAD, 0));
                after.add(new VarInsnNode(Opcodes.ALOAD, local));
                after.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/starskyxiii/polyglottooltip/testbridge/NameAuditCapture", "inserted", "(Ljava/util/List;Ljava/lang/Object;)V", false));
                method.instructions.insert(call, after);
                sites++;
            }
        }
        if (methods != 1 || sites != 1) throw new IllegalStateException("Unsupported audit producer shape: " + methods + "/" + sites);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        byte[] output = writer.toByteArray();
        NameAuditCapture.installed(hash(bytes), hash(output));
        return output;
    }

    static byte[] observeEnchantments(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        String observer = "com/starskyxiii/polyglottooltip/testbridge/NameAuditEnchantmentCapture";
        String source = hash(bytes);
        for (MethodNode method : node.methods) {
            boolean tooltip = method.name.equals("func_82840_a") || method.name.equals("getTooltip");
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (tooltip && call.owner.equals("com/google/common/collect/Lists") && call.name.equals("newArrayList") && call.desc.equals("()Ljava/util/ArrayList;")) {
                    call.owner = observer;
                    call.name = "rows";
                }
                if (!call.owner.equals("net/minecraft/enchantment/Enchantment") || !call.name.equals("func_77316_c")
                        || !call.desc.equals("(I)Ljava/lang/String;")) continue;
                AbstractInsnNode following = call.getNext();
                while (following != null && following.getOpcode() < 0) following = following.getNext();
                if (!(following instanceof MethodInsnNode)) continue;
                MethodInsnNode add = (MethodInsnNode) following;
                if (!add.owner.equals("java/util/List") || !add.name.equals("add") || !add.desc.equals("(Ljava/lang/Object;)Z")) continue;
                InsnList before = new InsnList();
                before.add(new InsnNode(Opcodes.DUP2));
                before.add(new LdcInsnNode(source));
                before.add(new MethodInsnNode(Opcodes.INVOKESTATIC, observer, "naming", "(Ljava/lang/Object;ILjava/lang/String;)V", false));
                method.instructions.insertBefore(call, before);
                InsnList inserting = new InsnList();
                inserting.add(new InsnNode(Opcodes.DUP2));
                inserting.add(new MethodInsnNode(Opcodes.INVOKESTATIC, observer, "inserting", "(Ljava/util/List;Ljava/lang/Object;)V", false));
                method.instructions.insertBefore(add, inserting);
                InsnList after = new InsnList();
                after.add(new InsnNode(Opcodes.DUP));
                after.add(new MethodInsnNode(Opcodes.INVOKESTATIC, observer, "inserted", "(Z)V", false));
                method.instructions.insert(add, after);
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    static byte[] observeEnchantmentScope(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        String observer = "com/starskyxiii/polyglottooltip/testbridge/NameAuditEnchantmentCapture";
        String source = hash(bytes);
        for (MethodNode method : node.methods) {
            if (Boolean.getBoolean("clientdevbridge.nameAuditProductRows") && method.name.equals("rows")
                    && method.desc.equals("(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/ITooltipFlag;)Ljava/util/ArrayList;")) {
                for (AbstractInsnNode instruction : method.instructions.toArray()) if (instruction.getOpcode() == Opcodes.ARETURN) {
                    InsnList capture = new InsnList();
                    capture.add(new InsnNode(Opcodes.DUP));
                    capture.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    capture.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/starskyxiii/polyglottooltip/testbridge/NameAuditCapture",
                            "productRows", "(Ljava/util/List;Ljava/lang/Object;)V", false));
                    method.instructions.insertBefore(instruction, capture);
                }
            }
            if (!method.name.equals("add") || !method.desc.equals("(Ljava/util/List;Lnet/minecraft/enchantment/Enchantment;ILjava/lang/String;)Z")) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.owner.equals("net/minecraft/enchantment/Enchantment") && call.name.equals("func_77316_c") && call.desc.equals("(I)Ljava/lang/String;")) {
                    InsnList before = new InsnList();
                    before.add(new InsnNode(Opcodes.DUP2));
                    before.add(new LdcInsnNode(source));
                    before.add(new MethodInsnNode(Opcodes.INVOKESTATIC, observer, "naming", "(Ljava/lang/Object;ILjava/lang/String;)V", false));
                    method.instructions.insertBefore(call, before);
                }
                if (!call.owner.equals("java/util/List") || !call.name.equals("add") || !call.desc.equals("(Ljava/lang/Object;)Z")) continue;
                InsnList before = new InsnList();
                before.add(new InsnNode(Opcodes.DUP2));
                before.add(new MethodInsnNode(Opcodes.INVOKESTATIC, observer, "inserting", "(Ljava/util/List;Ljava/lang/Object;)V", false));
                method.instructions.insertBefore(call, before);
                InsnList after = new InsnList();
                after.add(new InsnNode(Opcodes.DUP));
                after.add(new MethodInsnNode(Opcodes.INVOKESTATIC, observer, "inserted", "(Z)V", false));
                method.instructions.insert(call, after);
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    static byte[] observeAnnotatedRows(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (MethodNode method : node.methods) {
            if (Boolean.getBoolean("clientdevbridge.nameAuditProductRows") && method.name.equals("add")
                    && method.desc.equals("(Ljava/lang/String;)Z")) {
                InsnList title = new InsnList();
                title.add(new VarInsnNode(Opcodes.ALOAD, 0));
                title.add(new VarInsnNode(Opcodes.ALOAD, 1));
                title.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/starskyxiii/polyglottooltip/testbridge/NameAuditCapture",
                        "productTitle", "(Ljava/util/List;Ljava/lang/Object;)V", false));
                method.instructions.insert(title);
            }
            if (java.util.Arrays.asList("set", "remove", "clear", "removeRange", "removeAll", "retainAll", "removeIf", "replaceAll", "sort", "subList").contains(method.name)) {
                InsnList event = new InsnList();
                event.add(new LdcInsnNode("product:" + method.name + method.desc));
                event.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/starskyxiii/polyglottooltip/testbridge/NameAuditCapture", "rowEvent", "(Ljava/lang/String;)V", false));
                method.instructions.insert(event);
            }
            if (!method.name.equals("replaceOwned") || !method.desc.equals("(Lcom/starskyxiii/polyglottooltip/legacy/ObservedTooltipRows$Entry;Ljava/lang/String;)Z")) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction.getOpcode() != Opcodes.IRETURN) continue;
                InsnList before = new InsnList();
                before.add(new InsnNode(Opcodes.DUP));
                before.add(new VarInsnNode(Opcodes.ALOAD, 0));
                before.add(new VarInsnNode(Opcodes.ALOAD, 1));
                before.add(new VarInsnNode(Opcodes.ALOAD, 2));
                before.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/starskyxiii/polyglottooltip/testbridge/NameAuditEnchantmentCapture", "annotated", "(ZLjava/util/List;Ljava/lang/Object;Ljava/lang/String;)V", false));
                method.instructions.insertBefore(instruction, before);
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    static byte[] observeNames(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean productRows = Boolean.getBoolean("clientdevbridge.nameAuditProductRows");
        int methods = 0, names = 0, titles = 0;
        String observer = "com/starskyxiii/polyglottooltip/testbridge/NameAuditCapture";
        for (MethodNode method : node.methods) {
            if (method.desc.equals("()Ljava/lang/String;") && (method.name.equals("func_82833_r")
                    || method.name.equals("getDisplayName") || method.name.equals("r"))) {
                names++;
                if (productRows) continue;
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction.getOpcode() != Opcodes.ARETURN) continue;
                    InsnList before = new InsnList();
                    before.add(new InsnNode(Opcodes.DUP));
                    before.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    before.add(new InsnNode(Opcodes.SWAP));
                    before.add(new MethodInsnNode(Opcodes.INVOKESTATIC, observer, "named", "(Ljava/lang/Object;Ljava/lang/String;)V", false));
                    method.instructions.insertBefore(instruction, before);
                }
            }
            if (!(method.name.equals("func_82840_a") || method.name.equals("getTooltip") || method.name.equals("a"))
                    || !method.desc.endsWith(")Ljava/util/List;")
                    || org.objectweb.asm.Type.getArgumentTypes(method.desc).length != 2) continue;
            methods++;
            if (productRows) continue;
            boolean initial = true;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (initial && call.owner.equals("java/util/List") && call.name.equals("add")
                        && call.desc.equals("(Ljava/lang/Object;)Z") && call.getOpcode() == Opcodes.INVOKEINTERFACE) {
                    InsnList before = new InsnList();
                    before.add(new InsnNode(Opcodes.DUP2));
                    before.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    before.add(new MethodInsnNode(Opcodes.INVOKESTATIC, observer, "titled", "(Ljava/util/List;Ljava/lang/Object;Ljava/lang/Object;)V", false));
                    method.instructions.insertBefore(call, before);
                    initial = false;
                    titles++;
                }
            }
        }
        if (methods != 1 || names != 1 || titles != (productRows ? 0 : 1)) {
            throw new IllegalStateException("Unsupported audit name observation shape: " + methods + "/" + names + "/" + titles);
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        byte[] output = writer.toByteArray();
        NameAuditCapture.namesInstalled(hash(bytes), hash(output));
        return output;
    }

    static byte[] observeNameScope(byte[] bytes) {
        if (!Boolean.getBoolean("clientdevbridge.nameAuditProductRows")) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int sites = 0, finishes = 0;
        String eventDescriptor = "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;Ljava/util/List;Lnet/minecraft/client/util/ITooltipFlag;)Lnet/minecraftforge/event/entity/player/ItemTooltipEvent;";
        String finishDescriptor = "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;Ljava/util/List;Lnet/minecraft/client/util/ITooltipFlag;Lcom/llamalad7/mixinextras/sugar/ref/LocalRef;Lcom/llamalad7/mixinextras/sugar/ref/LocalRef;)Lnet/minecraftforge/event/entity/player/ItemTooltipEvent;";
        for (MethodNode method : node.methods) {
            if (method.name.equals("finish") && method.desc.equals(finishDescriptor)) {
                if ((method.access & Opcodes.ACC_STATIC) == 0) throw new IllegalStateException("Nonstatic product finish");
                MethodInsnNode event = null, enchantment = null, names = null;
                int eventIndex = -1, enchantmentIndex = -1, namesIndex = -1, index = 0;
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode) instruction;
                        if (call.owner.equals("net/minecraftforge/event/ForgeEventFactory") && call.name.equals("onItemTooltip")) {
                            if (event != null || call.getOpcode() != Opcodes.INVOKESTATIC || !call.desc.equals(eventDescriptor)) throw new IllegalStateException("Unsupported native tooltip event site");
                            event = call; eventIndex = index;
                        } else if (call.owner.equals("com/starskyxiii/polyglottooltip/legacy/minecraft/NativeEnchantmentScope") && call.name.equals("append")) {
                            if (enchantment != null || call.getOpcode() != Opcodes.INVOKESTATIC || !call.desc.equals("(Lnet/minecraft/item/ItemStack;Ljava/util/List;)V")) throw new IllegalStateException("Unsupported annotation append site");
                            enchantment = call; enchantmentIndex = index;
                        } else if (call.owner.equals("com/starskyxiii/polyglottooltip/legacy/ItemTooltipAppender") && call.name.equals("appendNames")) {
                            if (names != null || call.getOpcode() != Opcodes.INVOKESTATIC || !call.desc.equals("(Lnet/minecraft/item/ItemStack;Ljava/util/List;Ljava/lang/String;Lcom/starskyxiii/polyglottooltip/legacy/minecraft/ItemNameObservation;Lcom/starskyxiii/polyglottooltip/legacy/minecraft/NativeTableObservation;)V")) throw new IllegalStateException("Unsupported name append site");
                            names = call; namesIndex = index;
                        }
                    }
                    index++;
                }
                if (event == null || enchantment == null || names == null || eventIndex >= enchantmentIndex || enchantmentIndex >= namesIndex) throw new IllegalStateException("Unsupported product finish order");
                InsnList body = new InsnList();
                body.add(new VarInsnNode(Opcodes.ALOAD, 0));
                body.add(new VarInsnNode(Opcodes.ALOAD, 2));
                body.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/starskyxiii/polyglottooltip/testbridge/NameAuditCapture", "nativeBody", "(Ljava/lang/Object;Ljava/util/List;)V", false));
                method.instructions.insert(event, body);
                method.instructions.insert(phase("event-before"));
                method.instructions.insertBefore(names, phase("append-before"));
                finishes++;
            }
            if (!method.name.equals("capture") || !method.desc.equals("(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/ITooltipFlag;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;Lcom/llamalad7/mixinextras/sugar/ref/LocalRef;Lcom/llamalad7/mixinextras/sugar/ref/LocalRef;)Ljava/lang/String;")) continue;
            method.instructions.insert(phase("name-entry"));
            for (AbstractInsnNode instruction : method.instructions.toArray()) if (instruction.getOpcode() == Opcodes.ARETURN) {
                InsnList capture = new InsnList();
                capture.add(new InsnNode(Opcodes.DUP));
                capture.add(new VarInsnNode(Opcodes.ALOAD, 0));
                capture.add(new InsnNode(Opcodes.SWAP));
                capture.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/starskyxiii/polyglottooltip/testbridge/NameAuditCapture",
                        "productNamed", "(Ljava/lang/Object;Ljava/lang/String;)V", false));
                method.instructions.insertBefore(instruction, capture);
                sites++;
            }
        }
        if (sites != 1 || finishes != 1) throw new IllegalStateException("Unsupported product name capture shape: " + sites + "/" + finishes);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static InsnList phase(String name) {
        InsnList result = new InsnList();
        result.add(new VarInsnNode(Opcodes.ALOAD, 0));
        result.add(new LdcInsnNode(name));
        result.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/starskyxiii/polyglottooltip/testbridge/NameAuditCapture", "phase", "(Ljava/lang/Object;Ljava/lang/String;)V", false));
        return result;
    }

    private static byte[] observeMethodInputs(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (MethodNode method : node.methods) {
            if (!method.name.equals("methods") || !method.desc.equals("([B)Ljava/util/Map;")) continue;
            InsnList before = new InsnList();
            before.add(new VarInsnNode(Opcodes.ALOAD, 0));
            before.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/starskyxiii/polyglottooltip/testbridge/NameAuditCapture", "methodInput", "([B)V", false));
            method.instructions.insert(before);
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] observeProduct(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (MethodNode method : node.methods) {
            if (!method.name.equals("named") || !method.desc.equals("(Ljava/lang/String;)V")) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction.getOpcode() != Opcodes.RETURN) continue;
                InsnList before = new InsnList();
                before.add(new VarInsnNode(Opcodes.ALOAD, 0));
                before.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/starskyxiii/polyglottooltip/testbridge/NameAuditCapture", "productObservation", "(Ljava/lang/Object;)V", false));
                method.instructions.insertBefore(instruction, before);
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static String hash(byte[] bytes) {
        try {
            StringBuilder result = new StringBuilder();
            for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) result.append(String.format("%02x", value & 255));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
