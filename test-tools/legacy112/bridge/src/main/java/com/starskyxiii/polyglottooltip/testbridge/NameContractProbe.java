package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraftforge.fml.common.Loader;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;
import java.io.PrintWriter;
import java.io.StringWriter;

final class NameContractProbe {
    private NameContractProbe() {}

    static JsonObject inspect() throws Exception {
        JsonObject result = new JsonObject();
        if (Loader.isModLoaded("polyglottooltip")) {
            JsonObject profiles = new JsonObject();
            Class<?> selector = NativeAccess.type("com.starskyxiii.polyglottooltip.legacy.bootstrap.IntegrationProfileSelector");
            for (String name : new String[]{"aa-names", "aa-bauble-names", "dml-names",
                    "tinker-script-names", "railcraft-names",
                    "binnie-names", "forge-bucket-names", "guide-api-names",
                    "ie-bullet-names", "chickens-names",
                    "de-mob-soul-names", "microblock-names", "top-original", "top-ce", "hwyla"}) {
                profiles.addProperty(name, (Boolean) NativeAccess.call(selector, "enabled", name));
            }
            result.add("profiles", profiles);
            try {
                Class<?> observer = NativeAccess.type("com.starskyxiii.polyglottooltip.legacy.bootstrap.NameTransformObserver");
                java.util.Map<?, ?> effective = (java.util.Map<?, ?>) NativeAccess.call(observer, "diagnostics");
                result.add("effectiveNameMethods", new com.google.gson.Gson().toJsonTree(effective));
                JsonObject admissions = new JsonObject();
                com.google.gson.JsonArray unobserved = new com.google.gson.JsonArray();
                java.util.Properties nameProfiles = (java.util.Properties) NativeAccess.get(observer, "profiles");
                for (String key : nameProfiles.stringPropertyNames()) {
                    int separator = key.indexOf('#');
                    admissions.addProperty(key, (Boolean) NativeAccess.call(observer, "admits", key.substring(0, separator), key.substring(separator + 1)));
                    // A pin for a method this pack never defines (another mod's class, or a normalized Mixin handler
                    // name such as Enchantment#utGetTranslatedName) also reads false above; list those apart.
                    if (!effective.containsKey(key)) unobserved.add(key);
                }
                result.add("nameMethodAdmissions", admissions);
                result.add("nameMethodsUnobserved", unobserved);
                result.add("namePatchedMethods", new com.google.gson.Gson().toJsonTree(NativeAccess.get(observer, "patched")));
                result.addProperty("tooltipCaptureSupported", (Boolean) NativeAccess.call(
                        NativeAccess.type("com.starskyxiii.polyglottooltip.legacy.bootstrap.TooltipCaptureAdmission"), "supported"));
            } catch (ClassNotFoundException ignored) {}
            try {
                Class<?> library = NativeAccess.type("com.starskyxiii.polyglottooltip.legacy.bootstrap.NameLibrarySource");
                JsonObject admission = new JsonObject();
                admission.addProperty("scalaBuilder", (Boolean) NativeAccess.call(library, "admitsScalaBuilder", false));
                admission.addProperty("scalaBuilderInteger", (Boolean) NativeAccess.call(library, "admitsScalaBuilder", true));
                java.lang.reflect.Method loaded = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class); loaded.setAccessible(true);
                for (String name : (String[]) NativeAccess.get(library, "SCALA_BUILDER_TYPES")) {
                    Class<?> type = (Class<?>) loaded.invoke(net.minecraft.launchwrapper.Launch.classLoader, name);
                    if (type == null) type = (Class<?>) loaded.invoke(ClassLoader.getSystemClassLoader(), name);
                    JsonObject row = new JsonObject();
                    row.addProperty("loaded", type != null);
                    if (type != null) {
                        row.addProperty("admitted", (Boolean) NativeAccess.call(library, "admits", type));
                        row.addProperty("loader", type.getClassLoader().getClass().getName());
                        row.addProperty("source", String.valueOf(type.getProtectionDomain().getCodeSource().getLocation()));
                    }
                    admission.add(name, row);
                }
                result.add("libraryNameAdmissions", admission);
            } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException | LinkageError ignored) { }
        }
        JsonArray materials = new JsonArray();
        if (Loader.isModLoaded("tconstruct") && Loader.isModLoaded("contenttweaker")) {
            Class<?> module = NativeAccess.type("stanhebben.zenscript.ZenModule");
            Class<?> loaderType = NativeAccess.type("stanhebben.zenscript.ZenModule$MyClassLoader");
            Map<?, ?> definitions = (Map<?, ?>) NativeAccess.get(module, "classes");
            Map<?, ?> loaded = (Map<?, ?>) NativeAccess.get(module, "loadedClasses");
            Class<?> cotType = NativeAccess.type("com.teamacronymcoders.contenttweaker.modules.tinkers.materials.CoTTConMaterial");
            java.lang.reflect.Field localizer = NativeAccess.field(cotType, "itemLocalizer");
            Collection<?> all = (Collection<?>) NativeAccess.call(NativeAccess.type("slimeknights.tconstruct.library.TinkerRegistry"), "getAllMaterials");
            for (Object material : all) {
                JsonObject row = new JsonObject();
                row.addProperty("material", (String) NativeAccess.field(NativeAccess.type("slimeknights.tconstruct.library.materials.Material"), "identifier").get(material));
                row.addProperty("materialClass", material.getClass().getName());
                materials.add(row);
                Object callback = cotType.isInstance(material) ? localizer.get(material) : null;
                row.addProperty("cotMaterial", cotType.isInstance(material));
                row.addProperty("callbackPresent", callback != null);
                if (material.getClass() != cotType) {
                    row.addProperty("disposition", "ordinary-or-unknown-material");
                    continue;
                }
                if (callback == null) {
                    row.addProperty("disposition", "no-callback");
                    continue;
                }
                Class<?> type = callback.getClass();
                ClassLoader loader = type.getClassLoader();
                row.addProperty("callbackClass", type.getName());
                row.addProperty("callbackIdentity", System.identityHashCode(callback));
                row.addProperty("classIdentity", System.identityHashCode(type));
                row.addProperty("loaderClass", loader == null ? null : loader.getClass().getName());
                row.addProperty("loaderIdentity", System.identityHashCode(loader));
                row.addProperty("loaderClassMatches", loader != null && loader.getClass() == loaderType);
                row.addProperty("loadedClassMatches", loaded.get(type.getName()) == type);
                boolean contractMatches = NativeAccess.type("com.teamacronymcoders.contenttweaker.modules.tinkers.utils.Functions$ItemLocalizer").isInstance(callback);
                row.addProperty("callbackContractMatches", contractMatches);
                Object source = definitions.get(type.getName());
                if (!(source instanceof byte[]) || ((byte[]) source).length > 65536) {
                    row.addProperty("disposition", "missing-or-oversized-definition");
                    continue;
                }
                byte[] bytes = ((byte[]) source).clone();
                StringBuilder digest = new StringBuilder();
                for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) digest.append(String.format("%02x", value & 255));
                row.addProperty("sha256", digest.toString());
                row.addProperty("classBytes", Base64.getEncoder().encodeToString(bytes));
                ClassNode node = new ClassNode();
                new ClassReader(bytes).accept(node, 0);
                row.addProperty("internalName", node.name);
                row.addProperty("associationEligible", contractMatches && loader != null && loader.getClass() == loaderType
                        && loaded.get(type.getName()) == type && node.name.equals(type.getName().replace('.', '/')));
                JsonArray methods = new JsonArray();
                for (MethodNode method : node.methods) {
                    if (!method.name.equals("handle")) continue;
                    JsonObject detail = new JsonObject();
                    detail.addProperty("descriptor", method.desc);
                    detail.addProperty("access", method.access);
                    detail.addProperty("handlers", method.tryCatchBlocks.size());
                    JsonArray opcodes = new JsonArray();
                    for (AbstractInsnNode instruction : method.instructions.toArray()) if (instruction.getOpcode() >= 0) opcodes.add(instruction.getOpcode());
                    detail.add("opcodes", opcodes);
                    Textifier text = new Textifier();
                    method.accept(new TraceMethodVisitor(text));
                    StringWriter writer = new StringWriter();
                    text.print(new PrintWriter(writer));
                    detail.addProperty("bytecode", writer.toString());
                    methods.add(detail);
                }
                row.add("methods", methods);
                row.addProperty("disposition", "definition-recorded");
                if (Loader.isModLoaded("polyglottooltip")) {
                    Object parsed = NativeAccess.call(NativeAccess.type("com.starskyxiii.polyglottooltip.legacy.integration.tconstruct.LiteralNameExpression"), "parse", bytes, type.getName());
                    row.addProperty("syntaxAccepted", parsed != null);
                    boolean profile = (Boolean) NativeAccess.call(NativeAccess.type("com.starskyxiii.polyglottooltip.legacy.bootstrap.IntegrationProfileSelector"), "enabled", "tinker-script-names");
                    row.addProperty("profileEnabled", profile);
                    row.addProperty("probeEligible", profile && row.get("associationEligible").getAsBoolean() && parsed != null);
                    if (parsed != null) row.addProperty("rendered", (String) NativeAccess.call(parsed, "render", "Audit base 42"));
                }
                boolean callbackStable = localizer.get(material) == callback;
                boolean associationStable = loaded.get(type.getName()) == type;
                Object after = definitions.get(type.getName());
                boolean definitionStable = after instanceof byte[] && java.util.Arrays.equals(bytes, (byte[]) after);
                row.addProperty("callbackStable", callbackStable);
                row.addProperty("classAssociationStable", associationStable);
                row.addProperty("definitionStable", definitionStable);
                if (row.has("probeEligible")) row.addProperty("probeEligible", row.get("probeEligible").getAsBoolean()
                        && callbackStable && associationStable && definitionStable);
            }
        }
        result.add("materials", materials);
        if (result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 4194304) throw new IllegalStateException("Name contracts exceed wire limit");
        return result;
    }
}
