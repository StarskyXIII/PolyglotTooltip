package com.starskyxiii.polyglottooltip.integration.ae2;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public final class ForgeAe2RepoSearchLayout {
    private static final String REPO = "appeng/client/gui/me/search/RepoSearch";
    private static final String SEARCH_PACKAGE = "appeng/client/gui/me/search/";
    private static final String PARSER = "(Ljava/lang/String;)Ljava/util/List;";
    private static final String ARRAY_LIST = "java/util/ArrayList";
    private static final String ADD = "(Ljava/lang/Object;)Z";
    private static final String GTO_MIXIN_PACKAGE = "com.gtocore.mixin";
    private static final String GTO_MIXIN = "ae2.search.RepoSearchMixin";
    private static final String STRING_CONSTRUCTOR = "(Ljava/lang/String;)V";
    private static final List<String> CONSTRUCTIONS = List.of(ARRAY_LIST, SEARCH_PACKAGE + "ModSearchPredicate",
            SEARCH_PACKAGE + "TooltipsSearchPredicate", SEARCH_PACKAGE + "TagSearchPredicate",
            SEARCH_PACKAGE + "ItemIdSearchPredicate", SEARCH_PACKAGE + "NameSearchPredicate");
    private static volatile String gtoNamePredicate;

    private ForgeAe2RepoSearchLayout() {
    }

    public static boolean prepare(String target, Function<String, InputStream> resources) {
        if (!REPO.replace('/', '.').equals(target)) {
            return false;
        }
        try {
            if (!matchesParser(readClass(resources, REPO))) {
                return false;
            }
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
        try {
            gtoNamePredicate = readGtoPredicate(resources);
        } catch (IOException | RuntimeException ignored) {
            gtoNamePredicate = null;
        }
        return true;
    }

    public static boolean isGtoNamePredicate(String className) {
        return className.equals(gtoNamePredicate);
    }

    private static boolean matchesParser(ClassNode repo) {
        if (repo == null || !REPO.equals(repo.name)) {
            return false;
        }
        MethodNode parser = findMethod(repo, "getPredicates", PARSER);
        if (parser == null || (parser.access & Opcodes.ACC_STATIC) != 0 || parser.localVariables == null) {
            return false;
        }
        List<AbstractInsnNode> code = executableInstructions(parser);
        List<AbstractInsnNode> returns = code.stream().filter(insn -> insn.getOpcode() == Opcodes.ARETURN).toList();
        if (returns.size() != 1 || code.size() < 2 || returns.get(0) != code.get(code.size() - 1)
                || !isVariable(code.get(code.size() - 2), Opcodes.ALOAD, 3)) {
            return false;
        }
        int returnIndex = parser.instructions.indexOf(returns.get(0));
        long activeTerms = parser.localVariables.stream()
                .filter(local -> local.index == 2 && parser.instructions.indexOf(local.start) <= returnIndex
                        && parser.instructions.indexOf(local.end) > returnIndex
                        && "terms".equals(local.name) && "[Ljava/lang/String;".equals(local.desc)).count();
        long writesToTerms = code.stream().filter(insn -> isVariable(insn, Opcodes.ASTORE, 2)).count();
        long adds = code.stream().filter(insn -> calls(insn, Opcodes.INVOKEVIRTUAL, ARRAY_LIST, "add", ADD)).count();
        List<String> constructors = code.stream().filter(insn -> insn.getOpcode() == Opcodes.NEW)
                .map(insn -> ((TypeInsnNode) insn).desc).toList();
        List<String> prefixes = new ArrayList<>();
        for (int i = 1; i < code.size(); i++) {
            if (calls(code.get(i), Opcodes.INVOKEVIRTUAL, "java/lang/String", "startsWith", "(Ljava/lang/String;)Z")
                    && code.get(i - 1) instanceof LdcInsnNode literal && literal.cst instanceof String prefix) {
                prefixes.add(prefix);
            }
        }
        return activeTerms == 1 && writesToTerms == 1 && adds == 5 && constructors.equals(CONSTRUCTIONS)
                && prefixes.equals(List.of("@", "#", "$", "*"));
    }

    private static String readGtoPredicate(Function<String, InputStream> resources) throws IOException {
        try (InputStream stream = resources.apply("gtocore.mixins.json")) {
            if (stream == null) {
                return null;
            }
            JsonObject config = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            if (!GTO_MIXIN_PACKAGE.equals(config.get("package").getAsString()) || !config.has("client")) {
                return null;
            }
            boolean registered = config.getAsJsonArray("client").asList().stream()
                    .anyMatch(entry -> GTO_MIXIN.equals(entry.getAsString()));
            if (!registered) {
                return null;
            }
        }
        ClassNode mixin = readClass(resources, GTO_MIXIN_PACKAGE.replace('.', '/') + '/' + GTO_MIXIN.replace('.', '/'));
        if (mixin == null || !List.of(Type.getObjectType(REPO)).equals(annotationValue(
                annotation(mixin.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;"), "value"))) {
            return null;
        }
        MethodNode redirect = findMethod(mixin, "redirectAddNamePredicate",
                "(Ljava/util/ArrayList;Ljava/lang/Object;Ljava/lang/String;)Z");
        if (redirect == null || !matchesNameRedirect(redirect)) {
            return null;
        }
        List<AbstractInsnNode> code = executableInstructions(redirect);
        if (code.size() != 7 || !isVariable(code.get(0), Opcodes.ALOAD, 1)
                || code.get(1).getOpcode() != Opcodes.NEW || code.get(2).getOpcode() != Opcodes.DUP
                || !isVariable(code.get(3), Opcodes.ALOAD, 3) || code.get(6).getOpcode() != Opcodes.IRETURN) {
            return null;
        }
        String predicate = ((TypeInsnNode) code.get(1)).desc;
        if (!predicate.startsWith("com/gtocore/")
                || !calls(code.get(4), Opcodes.INVOKESPECIAL, predicate, "<init>", STRING_CONSTRUCTOR)
                || !calls(code.get(5), Opcodes.INVOKEVIRTUAL, ARRAY_LIST, "add", ADD)) {
            return null;
        }
        ClassNode type = readClass(resources, predicate);
        if (type == null || !predicate.equals(type.name) || !type.interfaces.contains("java/util/function/Predicate")
                || findMethod(type, "<init>", STRING_CONSTRUCTOR) == null) {
            return null;
        }
        return predicate.replace('/', '.');
    }

    private static boolean matchesNameRedirect(MethodNode method) {
        AnnotationNode redirect = annotation(method.visibleAnnotations, "Lorg/spongepowered/asm/mixin/injection/Redirect;");
        if (!List.of("getPredicates").equals(annotationValue(redirect, "method"))
                || annotationValue(redirect, "slice") != null
                || !(annotationValue(redirect, "at") instanceof AnnotationNode at)) {
            return false;
        }
        List<AnnotationNode>[] parameters = method.invisibleParameterAnnotations;
        AnnotationNode local = parameters != null && parameters.length == 3
                ? annotation(parameters[2], "Lcom/llamalad7/mixinextras/sugar/Local;") : null;
        return "INVOKE".equals(annotationValue(at, "value")) && Integer.valueOf(4).equals(annotationValue(at, "ordinal"))
                && ("L" + ARRAY_LIST + ";add" + ADD).equals(annotationValue(at, "target"))
                && List.of("part").equals(annotationValue(local, "name"));
    }

    private static ClassNode readClass(Function<String, InputStream> resources, String name) throws IOException {
        try (InputStream stream = resources.apply(name + ".class")) {
            if (stream == null) {
                return null;
            }
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static List<AbstractInsnNode> executableInstructions(MethodNode method) {
        return Arrays.stream(method.instructions.toArray()).filter(insn -> insn.getOpcode() >= 0).toList();
    }

    private static MethodNode findMethod(ClassNode type, String name, String desc) {
        return type.methods.stream().filter(method -> name.equals(method.name) && desc.equals(method.desc))
                .findFirst().orElse(null);
    }

    private static boolean calls(AbstractInsnNode insn, int opcode, String owner, String name, String desc) {
        return insn instanceof MethodInsnNode method && method.getOpcode() == opcode
                && owner.equals(method.owner) && name.equals(method.name) && desc.equals(method.desc);
    }

    private static boolean isVariable(AbstractInsnNode insn, int opcode, int index) {
        return insn instanceof VarInsnNode variable && variable.getOpcode() == opcode && variable.var == index;
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String desc) {
        return annotations == null ? null : annotations.stream().filter(value -> desc.equals(value.desc))
                .findFirst().orElse(null);
    }

    private static Object annotationValue(AnnotationNode annotation, String name) {
        if (annotation != null && annotation.values != null) {
            for (int index = 0; index < annotation.values.size(); index += 2) {
                if (name.equals(annotation.values.get(index))) {
                    return annotation.values.get(index + 1);
                }
            }
        }
        return null;
    }
}
