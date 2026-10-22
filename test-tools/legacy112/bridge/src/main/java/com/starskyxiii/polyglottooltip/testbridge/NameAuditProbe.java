package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import mezz.jei.Internal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public final class NameAuditProbe {
    private static final Gson GSON = new Gson();
    private static JsonObject collection;
    private static NameAuditQuery collectionQuery;
    private static final Set<String> completedCollections = new HashSet<>();
    private static java.lang.ref.WeakReference<Object> identityWorld = new java.lang.ref.WeakReference<>(null);
    private static java.lang.ref.WeakReference<Object> identityPlayer = new java.lang.ref.WeakReference<>(null);
    private static long worldGeneration;
    private static ItemStack rendered;
    private static GuiChest renderScreen;

    private NameAuditProbe() { }

    public static JsonElement call(String method, JsonObject params) throws Exception {
        if (!Boolean.getBoolean("clientdevbridge.nameAudit")) throw new IllegalStateException("Name audit is disabled");
        Minecraft client = Minecraft.func_71410_x();
        if (client.field_71439_g == null || client.field_71441_e == null) throw new IllegalStateException("Audit requires a ready world and player");
        switch (method) {
            case "audit.identity": return identity();
            case "audit.catalog": return catalog();
            case "audit.contracts": return NameContractProbe.inspect();
            case "audit.entityEvidence": return NameEntityEvidenceProbe.inspect(params);
            case "audit.collect": return collect(params);
            case "audit.observe": return observe(params);
            case "audit.guardChecks": return guardChecks(params);
            case "audit.dependencyGuards": return dependencyGuards(params);
            case "audit.compositionGuards": return compositionGuards(params);
            case "audit.diagnose": return diagnose(params.getAsJsonArray("samples"));
            case "audit.item": return item(params);
            case "audit.captureHeld": return captureHeld();
            case "audit.compositeCraft": return NameCompositeProbe.craft();
            case "audit.compositeInspect": return compositeInspect(params.getAsJsonArray("samples"));
            default: throw new IllegalArgumentException("Unknown audit method " + method);
        }
    }

    private static JsonObject identity() throws Exception {
        Minecraft client = Minecraft.func_71410_x();
        if (identityWorld.get() != client.field_71441_e || identityPlayer.get() != client.field_71439_g) {
            identityWorld = new java.lang.ref.WeakReference<>(client.field_71441_e);
            identityPlayer = new java.lang.ref.WeakReference<>(client.field_71439_g);
            worldGeneration++;
        }
        JsonObject result = new JsonObject();
        result.addProperty("fixtureNonce", System.getProperty("clientdevbridge.fixtureNonce", ""));
        result.addProperty("gameDirectory", client.field_71412_D.getCanonicalPath());
        result.addProperty("language", client.field_71474_y.field_74363_ab);
        result.addProperty("userHome", System.getProperty("user.home"));
        result.addProperty("product", Loader.isModLoaded("polyglottooltip"));
        result.addProperty("jvmStartTime", java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        result.addProperty("worldGeneration", worldGeneration);
        result.addProperty("observerInputSha256", NameAuditCapture.inputHash());
        result.addProperty("observerOutputSha256", NameAuditCapture.outputHash());
        result.addProperty("observerStage", "audit-transformer-output");
        result.addProperty("observerMethod", "SecondaryTooltipRows.insert(List,List,int)");
        result.addProperty("nameObserverInputSha256", NameAuditCapture.nameInputHash());
        result.addProperty("nameObserverOutputSha256", NameAuditCapture.nameOutputHash());
        result.addProperty("enchantmentFinalObserver", NameAuditFinalObserver.state());
        result.addProperty("nameObservationRoute", Boolean.getBoolean("clientdevbridge.nameAuditProductRows") ? "product-rows" : "root-insertion");
        result.add("modifiers", modifiers());
        if (Loader.isModLoaded("polyglottooltip")) {
            Object context = NativeAccess.call(NativeAccess.type("com.starskyxiii.polyglottooltip.legacy.minecraft.MinecraftLanguageAdapter"), "context");
            result.add("settings", GSON.toJsonTree(NativeAccess.get(context, "settings")));
        }
        return result;
    }

    private static JsonObject modifiers() {
        JsonObject result = new JsonObject();
        result.addProperty("shift", GuiScreen.func_146272_n());
        result.addProperty("control", GuiScreen.func_146271_m());
        return result;
    }

    static byte[] binary(ItemStack stack) throws IOException {
        NBTTagCompound compound = stack.func_77955_b(new NBTTagCompound());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream stream = new DataOutputStream(bytes)) { CompressedStreamTools.func_74800_a(compound, stream); }
        if (bytes.size() > 4194304) throw new IOException("Audit stack exceeds 4 MiB");
        return bytes.toByteArray();
    }

    private static JsonObject encoded(ItemStack stack) throws IOException { return NameAuditNbt.read(binary(stack)); }

    private static ItemStack decode(JsonObject sample) throws IOException {
        String value = sample.get("serialized").getAsString();
        if (value.length() > 5592412) throw new IOException("Encoded audit stack exceeds limit");
        byte[] bytes = Base64.getDecoder().decode(value);
        NameAuditNbt.read(bytes);
        try (DataInputStream stream = new DataInputStream(new ByteArrayInputStream(bytes))) {
            NBTTagCompound compound = CompressedStreamTools.func_152456_a(stream, new NBTSizeTracker(4194304));
            if (stream.read() != -1) throw new IOException("Trailing serialized stack data");
            return new ItemStack(compound);
        }
    }

    private static JsonObject failure(String stage, Throwable error) {
        JsonObject value = new JsonObject();
        value.addProperty("stage", stage);
        value.addProperty("class", error.getClass().getName());
        value.addProperty("message", String.valueOf(error.getMessage()));
        return value;
    }

    private static JsonObject collect(JsonObject params) throws Exception {
        if (params.has("start") && params.get("start").getAsBoolean()) {
            NameAuditQuery query = new NameAuditQuery(params);
            if (collectionQuery != null && collectionQuery.id.equals(query.id)) {
                if (!collectionQuery.sha256.equals(query.sha256)) throw new IllegalArgumentException("Collection query changed");
            } else {
                if (completedCollections.contains(query.id)) throw new IllegalStateException("Collection ID has expired");
                collection = collect(query);
                collectionQuery = query;
                completedCollections.add(query.id);
            }
        }
        if (collection == null) throw new IllegalStateException("Collection has not started");
        String requestedId = params.has("collectionId") ? params.get("collectionId").getAsString() : "legacy";
        if (!collectionQuery.id.equals(requestedId)) throw new IllegalStateException("Collection ID is not current");
        int offset = params.has("offset") ? params.get("offset").getAsInt() : 0;
        int limit = params.has("limit") ? params.get("limit").getAsInt() : 64;
        JsonArray samples = collection.getAsJsonArray("samples");
        if (offset < 0 || offset > samples.size() || limit < 1 || limit > 128) throw new IllegalArgumentException("Invalid collection page");
        JsonObject page = new JsonObject();
        page.addProperty("collectionId", collectionQuery.id);
        page.addProperty("querySha256", collectionQuery.sha256);
        page.addProperty("offset", offset);
        page.addProperty("total", samples.size());
        page.addProperty("complete", collection.get("complete").getAsBoolean());
        if (offset == 0) {
            page.add("sources", collection.get("sources"));
            page.add("failures", collection.get("failures"));
            page.add("limits", collection.get("limits"));
        }
        JsonArray rows = new JsonArray();
        int pageBytes = page.toString().getBytes(StandardCharsets.UTF_8).length + 256;
        if (pageBytes >= 4194304) throw new IOException("Collection metadata exceeds wire limit");
        for (int index = offset; index < Math.min(samples.size(), offset + limit); index++) {
            JsonElement row = samples.get(index);
            int size = row.toString().getBytes(StandardCharsets.UTF_8).length + 1;
            if (pageBytes + size > 4194304) {
                if (rows.size() == 0) throw new IOException("Collection page exceeds wire limit");
                break;
            }
            rows.add(row);
            pageBytes += size;
        }
        page.addProperty("nextOffset", offset + rows.size());
        page.add("samples", rows);
        return page;
    }

    private static JsonObject catalog() {
        Set<String> items = new TreeSet<>();
        JsonObject numericIds = new JsonObject();
        for (Item item : ForgeRegistries.ITEMS) {
            if (item.getRegistryName() != null) {
                String name = item.getRegistryName().toString();
                items.add(name);
                numericIds.addProperty(name, Item.func_150891_b(item));
            }
        }
        JsonObject result = new JsonObject();
        result.add("items", GSON.toJsonTree(items));
        result.add("numericIds", numericIds);
        result.addProperty("sha256", sha256(result.get("items").toString().getBytes(StandardCharsets.UTF_8)));
        return result;
    }

    private static JsonObject collect(NameAuditQuery query) throws Exception {
            Collector collector = new Collector(query);
            if (query.fixtureSet != null) {
                Map<String, NameAuditFixtures.Case> fixtures = NameAuditFixtures.create();
                collector.plannedCases = new ArrayList<>(fixtures.keySet());
                for (Map.Entry<String, NameAuditFixtures.Case> fixture : fixtures.entrySet()) {
                    JsonObject source = collector.source("control:manual:" + fixture.getKey(), String.valueOf(fixture.getValue().stack.func_77973_b().getRegistryName()));
                    source.addProperty("returned", 1);
                    source.add("seedStack", encoded(fixture.getValue().seed));
                    byte[] seed = binary(fixture.getValue().seed);
                    source.addProperty("seedSerialized", Base64.getEncoder().encodeToString(seed));
                    source.addProperty("seedBinarySha256", sha256(seed));
                    source.add("fixtureTargetStack", encoded(fixture.getValue().stack));
                    collector.add(fixture.getValue().stack, source);
                }
                return collector.result();
            }
            Collection<ItemStack> ingredients = Internal.getIngredientRegistry().getAllIngredients(ItemStack.class);
            JsonObject source = collector.source("ingredient-registry", "all-item-stacks");
            source.addProperty("returned", ingredients.size());
            for (ItemStack stack : ingredients) collector.add(stack, source);
            for (Item item : ForgeRegistries.ITEMS) {
                if (item.getRegistryName() == null || !query.selects(item.getRegistryName().toString())) continue;
                String id = item.getRegistryName().toString();
                source = collector.source("registry-default", id);
                source.addProperty("returned", 1);
                try { collector.add(new ItemStack(item), source); }
                catch (RuntimeException | LinkageError error) { source.add("error", failure("registry-construction", error)); }
                Set<CreativeTabs> tabs = new LinkedHashSet<>();
                tabs.add(CreativeTabs.field_78027_g);
                source = collector.source("creative-tabs", id);
                try {
                    CreativeTabs[] nativeTabs = item.getCreativeTabs();
                    source.addProperty("returned", nativeTabs.length);
                    tabs.addAll(Arrays.asList(nativeTabs));
                } catch (RuntimeException | LinkageError error) { source.add("error", failure("creative-tabs", error)); }
                for (CreativeTabs tab : tabs) {
                    if (tab == null) continue;
                    source = collector.source("subitems:" + tab.func_78013_b(), id);
                    try {
                        NonNullList<ItemStack> stacks = NonNullList.func_191196_a();
                        item.func_150895_a(tab, stacks);
                        source.addProperty("returned", stacks.size());
                        for (ItemStack stack : stacks) collector.add(stack, source);
                    } catch (RuntimeException | LinkageError error) {
                        source.add("error", failure("enumeration", error));
                    }
                }
            }
            collector.control(new ItemStack(Blocks.field_150348_b), "stone");
            ItemStack literal = new ItemStack(Blocks.field_150348_b);
            literal.func_151001_c("Audit literal name 42");
            collector.control(literal, "literal");
            int nativeCount = collector.samples.size();
            for (String namespace : query.namespaces) {
                for (int index = 0; index < nativeCount; index++) {
                    JsonObject sample = collector.samples.get(index).getAsJsonObject();
                    if (!sample.get("item").getAsString().startsWith(namespace + ":")) continue;
                    JsonObject origin = collector.sources.get(sample.get("source").getAsInt()).getAsJsonObject();
                    if (!origin.get("kind").getAsString().equals("ingredient-registry")) continue;
                    try {
                        ItemStack modLiteral = decode(sample);
                        modLiteral.func_151001_c("Audit literal " + namespace + " 42");
                        collector.control(modLiteral, "literal-" + namespace);
                    } catch (IOException | RuntimeException | LinkageError error) {
                        JsonObject controlError = failure("literal-control-construction", error);
                        controlError.addProperty("item", sample.get("item").getAsString());
                        controlError.addProperty("source", sample.get("source").getAsInt());
                        collector.failures.add(controlError);
                    }
                    break;
                }
            }
            return collector.result();
    }

    private static final class Collector {
        private final NameAuditQuery query;
        private final JsonArray samples = new JsonArray(), sources = new JsonArray(), failures = new JsonArray();
        private long bytes;
        private boolean capped;
        private List<String> plannedCases;
        private Collector(NameAuditQuery query) { this.query = query; }
        private JsonObject source(String kind, String owner) {
            JsonObject source = new JsonObject();
            source.addProperty("id", sources.size());
            source.addProperty("kind", kind);
            source.addProperty("owner", owner);
            source.addProperty("matched", 0);
            source.addProperty("serialized", 0);
            sources.add(source);
            return source;
        }
        private void control(ItemStack stack, String name) {
            JsonObject source = source("control:" + name, String.valueOf(stack.func_77973_b().getRegistryName()));
            source.addProperty("returned", 1);
            add(stack, source);
        }
        private void add(ItemStack original, JsonObject source) {
            if (original == null || original.func_190926_b() || original.func_77973_b().getRegistryName() == null) return;
            if (source.get("kind").getAsString().equals("ingredient-registry")
                    && !query.selects(original.func_77973_b().getRegistryName().toString())) return;
            source.addProperty("matched", source.get("matched").getAsInt() + 1);
            if (samples.size() >= query.sampleLimit || bytes >= 134217728) { capped = true; return; }
            try {
                JsonObject before = encoded(original);
                int originalCount = original.func_190916_E();
                ItemStack copy = original.func_77946_l();
                copy.func_190920_e(1);
                byte[] data = binary(copy);
                if (bytes + data.length > 134217728) { capped = true; return; }
                bytes += data.length;
                JsonObject row = new JsonObject();
                row.addProperty("source", source.get("id").getAsInt());
                row.addProperty("item", copy.func_77973_b().getRegistryName().toString());
                row.addProperty("metadata", copy.func_77960_j());
                row.addProperty("originalCount", originalCount);
                row.addProperty("serialized", Base64.getEncoder().encodeToString(data));
                row.add("stack", NameAuditNbt.read(data));
                row.add("sourceBefore", before);
                row.add("sourceAfter", encoded(original));
                if (row.toString().getBytes(StandardCharsets.UTF_8).length > 4194304) {
                    JsonObject limit = new JsonObject();
                    limit.addProperty("stage", "collection-wire-limit");
                    limit.addProperty("source", source.get("id").getAsInt());
                    limit.addProperty("item", row.get("item").getAsString());
                    limit.addProperty("metadata", copy.func_77960_j());
                    limit.addProperty("binaryBytes", data.length);
                    limit.addProperty("binarySha256", sha256(data));
                    failures.add(limit);
                    return;
                }
                samples.add(row);
                source.addProperty("serialized", source.get("serialized").getAsInt() + 1);
            } catch (IOException | RuntimeException | LinkageError error) {
                JsonObject row = failure("collection-serialization", error);
                row.addProperty("source", source.get("id").getAsInt());
                row.addProperty("item", String.valueOf(original.func_77973_b().getRegistryName()));
                row.addProperty("metadata", original.func_77960_j());
                failures.add(row);
            }
        }
        private JsonObject result() {
            JsonObject result = new JsonObject(), limits = new JsonObject();
            result.add("samples", samples);
            result.add("sources", sources);
            result.add("failures", failures);
            boolean sourceErrors = false;
            for (JsonElement source : sources) sourceErrors |= source.getAsJsonObject().has("error");
            result.addProperty("complete", !capped && failures.size() == 0 && !sourceErrors);
            limits.addProperty("sampleLimit", query.sampleLimit);
            limits.addProperty("binaryByteLimit", 134217728);
            limits.addProperty("binaryBytes", bytes);
            limits.addProperty("capped", capped);
            if (plannedCases != null) limits.add("plannedCases", GSON.toJsonTree(plannedCases));
            result.add("limits", limits);
            return result;
        }
    }

    private static JsonObject observe(JsonObject request) throws Exception {
        JsonArray samples = request.getAsJsonArray("samples");
        String mode = NameAuditMode.read(request);
        ITooltipFlag flags = mode.equals("ADVANCED") ? ITooltipFlag.TooltipFlags.ADVANCED : ITooltipFlag.TooltipFlags.NORMAL;
        int repetitions = request.has("repetitions") ? request.get("repetitions").getAsInt() : 1;
        if (repetitions < 1 || repetitions > 3) throw new IllegalArgumentException("Audit repetitions must be 1..3");
        if (samples == null || samples.size() < 1 || samples.size() > 32) throw new IllegalArgumentException("Audit batch must contain 1..32 samples");
        Minecraft client = Minecraft.func_71410_x();
        JsonArray rows = new JsonArray();
        for (JsonElement element : samples) {
            ItemStack stack = null;
            for (int repetition = 0; repetition < repetitions; repetition++) {
            JsonObject sample = element.getAsJsonObject(), row = new JsonObject();
            row.add("id", sample.get("id"));
            row.addProperty("callOrdinal", repetition + 1);
            row.addProperty("sameObject", repetition > 0);
            row.addProperty("tooltipFlag", mode);
            row.add("modifiers", modifiers());
            String stage = "decode";
            NameAuditCapture.Frame frame = null;
            try {
                if (repetition == 0) stack = decode(sample);
                if (stack == null) throw new IllegalStateException("Initial decode failed");
                row.add("before", encoded(stack));
                row.addProperty("literalCustomName", stack.func_82837_s());
                stage = "tooltip";
                frame = NameAuditCapture.begin(stack, request.has("phases") && request.get("phases").getAsBoolean());
                List<String> lines;
                try { lines = stack.func_82840_a(client.field_71439_g, flags); }
                finally { NameAuditCapture.end(); }
                row.add("tooltip", GSON.toJsonTree(lines));
                JsonObject observation = new JsonObject();
                observation.addProperty("nativeNameCaptured", frame.nativeNameCaptured);
                observation.addProperty("nativeName", frame.nativeName);
                observation.addProperty("initialTitleCaptured", frame.initialTitleCaptured);
                observation.addProperty("initialTitle", frame.initialTitle);
                observation.addProperty("rootList", frame.initialList == lines);
                row.add("nameObservation", observation);
                JsonObject body = new JsonObject();
                body.addProperty("captured", frame.nativeBodyCaptured);
                body.addProperty("rootList", frame.nativeBodyList == lines);
                body.add("rows", GSON.toJsonTree(frame.nativeBodyBeforeAppend));
                row.add("nativeBodyObservation", body);
                if (lines.getClass().getName().equals("com.starskyxiii.polyglottooltip.legacy.ObservedTooltipRows")) {
                    Object context = NativeAccess.call(lines, "owner");
                    row.add("annotationOutcomes", GSON.toJsonTree(NativeAccess.get(context, "outcomes")));
                }
                if (frame.productObservation != null) row.add("productNameObservation", productObservation(frame.productObservation, stack));
                JsonArray inserted = new JsonArray();
                for (NameAuditCapture.Insertion insertion : frame.insertions) {
                    JsonObject value = new JsonObject();
                    value.addProperty("text", insertion.text);
                    value.addProperty("rootList", insertion.target == lines);
                    value.addProperty("tooltipFrames", insertion.depth);
                    JsonArray indices = new JsonArray();
                    for (int index = 0; index < lines.size(); index++) if (lines.get(index) == insertion.text) indices.add(index);
                    value.add("indices", indices);
                    inserted.add(value);
                }
                row.add("created", inserted);
                JsonArray enchantments = new JsonArray();
                for (NameAuditEnchantmentCapture.Receipt receipt : frame.enchantments) {
                    JsonObject value = new JsonObject();
                    net.minecraft.enchantment.Enchantment enchantment = (net.minecraft.enchantment.Enchantment) receipt.enchantment;
                    value.addProperty("enchantment", net.minecraftforge.fml.common.registry.ForgeRegistries.ENCHANTMENTS.getKey(enchantment).toString());
                    value.addProperty("level", receipt.level);
                    value.addProperty("line", receipt.line);
                    value.addProperty("index", receipt.index);
                    value.addProperty("inserted", receipt.inserted);
                    value.addProperty("surviving", receipt.survives(lines));
                    value.addProperty("rootList", receipt.rows == lines);
                    value.addProperty("sourceSha256", receipt.source);
                    if (receipt.annotatedLine != null) {
                        value.addProperty("annotatedLine", receipt.annotatedLine);
                        value.addProperty("producer", receipt.producer);
                        JsonArray annotationIndices = new JsonArray();
                        for (int index = 0; index < lines.size(); index++) if (lines.get(index) == receipt.annotatedLine) annotationIndices.add(index);
                        value.add("annotationIndices", annotationIndices);
                    }
                    enchantments.add(value);
                }
                row.add("nativeEnchantments", enchantments);
                row.add("rowEvents", GSON.toJsonTree(frame.rowEvents));
                row.add("phases", frame.phases);
                row.addProperty("enchantmentCaptureComplete", frame.pendingEnchantment == null && !frame.failed
                        && (frame.initialList instanceof NameAuditRows || frame.productObservation != null));
                stage = "after-serialization";
                row.add("after", encoded(stack));
            } catch (IOException | RuntimeException | LinkageError error) {
                row.add("error", failure(stage, error));
                if (stack != null && !row.has("after")) {
                    try { row.add("after", encoded(stack)); }
                    catch (IOException | RuntimeException | LinkageError afterError) { row.add("afterError", failure("after-serialization", afterError)); }
                }
            } finally {
                NameAuditCapture.end();
                row.addProperty("captureFailed", frame != null && frame.failed);
            }
            rows.add(row);
            }
        }
        JsonObject result = new JsonObject();
        result.add("rows", rows);
        result.add("identity", identity());
        result.addProperty("tooltipFlag", mode);
        return result;
    }

    private static JsonObject guardChecks(JsonObject request) throws Exception {
        ItemStack stack = decode(request.getAsJsonObject("sample"));
        String mode = NameAuditMode.read(request);
        ITooltipFlag flags = mode.equals("ADVANCED") ? ITooltipFlag.TooltipFlags.ADVANCED : ITooltipFlag.TooltipFlags.NORMAL;
        GuardObservation positive = guardObservation(stack, flags);
        Class<?> adapter = NativeAccess.type("com.starskyxiii.polyglottooltip.legacy.minecraft.MinecraftLanguageAdapter");
        java.lang.reflect.Field current = adapter.getDeclaredField("current");
        current.setAccessible(true);
        Object snapshot = current.get(null);
        java.lang.reflect.Method append = NativeAccess.type("com.starskyxiii.polyglottooltip.legacy.ItemTooltipAppender")
                .getMethod("appendNames", ItemStack.class, List.class, String.class, positive.frame.productObservation.getClass());
        JsonObject result = new JsonObject();
        result.addProperty("tooltipFlag", mode);
        result.addProperty("nativeName", positive.frame.nativeName);
        result.add("original", GSON.toJsonTree(positive.original));
        // The native tooltip already appended once. Replaying its plan would spend the same observation budget twice.
        result.add("positive", GSON.toJsonTree(positive.original));
        result.addProperty("positiveCurrent", true);
        GuardObservation beforeReplacement = guardObservation(stack, flags);
        List<String> replacement = new ArrayList<>(beforeReplacement.base);
        replacement.set(0, "Replacement title (#1234/5)");
        result.addProperty("beforeReplacementCurrent", true);
        result.add("beforeEventReplacement", GSON.toJsonTree(guardAppend(append, stack, replacement, replacement.get(0), beforeReplacement.frame.productObservation)));
        result.addProperty("beforeReplacementAfterCurrent", (Boolean) NativeAccess.call(beforeReplacement.frame.productObservation, "current", stack));
        GuardObservation afterReplacement = guardObservation(stack, flags);
        result.addProperty("afterReplacementCurrent", true);
        result.add("afterEventReplacement", GSON.toJsonTree(guardAppend(append, stack, replacement, afterReplacement.base.get(0), afterReplacement.frame.productObservation)));
        result.addProperty("afterReplacementAfterCurrent", (Boolean) NativeAccess.call(afterReplacement.frame.productObservation, "current", stack));
        result.add("replacementBase", GSON.toJsonTree(replacement));
        GuardObservation nbt = guardObservation(stack, flags);
        result.addProperty("nbtBeforeCurrent", true);
        NBTTagCompound originalTag = stack.func_77978_p();
        NBTTagCompound mutatedTag = originalTag == null ? new NBTTagCompound() : originalTag.func_74737_b();
        String key = request.has("mutationKey") ? request.get("mutationKey").getAsString() : "nameGuardTarget";
        String value = request.has("mutationValue") ? request.get("mutationValue").getAsString() : "changed";
        mutatedTag.func_74778_a(key, value);
        stack.func_77982_d(mutatedTag);
        try {
            result.addProperty("mutatedNativeName", stack.func_82833_r());
            result.addProperty("nbtChangedCurrent", (Boolean) NativeAccess.call(nbt.frame.productObservation, "current", stack));
            result.add("nbtChanged", GSON.toJsonTree(guardAppend(append, stack, nbt.base, nbt.base.get(0), nbt.frame.productObservation)));
            result.addProperty("nativeWritePreserved", value.equals(stack.func_77978_p().func_74779_i(key)));
            result.add("mutatedTooltip", GSON.toJsonTree(stack.func_82840_a(Minecraft.func_71410_x().field_71439_g, flags)));
        } finally { stack.func_77982_d(originalTag); }
        result.addProperty("nbtRestoredCurrent", (Boolean) NativeAccess.call(nbt.frame.productObservation, "current", stack));
        GuardObservation generation = guardObservation(stack, flags);
        result.addProperty("generationBeforeCurrent", true);
        try {
            java.lang.reflect.Constructor<?> constructor = snapshot.getClass().getDeclaredConstructor(NativeAccess.get(snapshot, "settings").getClass(), String.class,
                    NativeAccess.type("com.starskyxiii.polyglottooltip.legacy.minecraft.LanguageTable"), List.class, long.class, long.class);
            constructor.setAccessible(true);
            Object updated = constructor.newInstance(NativeAccess.get(snapshot, "settings"), NativeAccess.get(snapshot, "primary"), NativeAccess.get(snapshot, "primaryTable"),
                    NativeAccess.get(snapshot, "tables"), (Long) NativeAccess.get(snapshot, "generation") + 1, NativeAccess.get(snapshot, "nativeNameEpoch"));
            current.set(null, updated);
            result.addProperty("generationChangedCurrent", (Boolean) NativeAccess.call(generation.frame.productObservation, "current", stack));
            result.add("generationChanged", GSON.toJsonTree(guardAppend(append, stack, generation.base, generation.base.get(0), generation.frame.productObservation)));
            result.addProperty("newGenerationPreserved", current.get(null) == updated);
        } finally { current.set(null, snapshot); }
        result.addProperty("generationRestoredCurrent", (Boolean) NativeAccess.call(generation.frame.productObservation, "current", stack));
        result.add("base", GSON.toJsonTree(positive.base));
        result.addProperty("restoredCurrent", (Boolean) NativeAccess.call(nbt.frame.productObservation, "current", stack));
        guardObservation(stack, flags);
        result.addProperty("freshCurrent", true);
        return result;
    }

    private static GuardObservation guardObservation(ItemStack stack, ITooltipFlag flags) throws Exception {
        NameAuditCapture.Frame frame = NameAuditCapture.begin(stack);
        List<String> original;
        try { original = stack.func_82840_a(Minecraft.func_71410_x().field_71439_g, flags); }
        finally { NameAuditCapture.end(); }
        if (frame.failed || frame.productObservation == null || !frame.initialTitleCaptured
                || !(Boolean) NativeAccess.call(frame.productObservation, "current", stack))
            throw new IllegalStateException("A current supported observation is required before each guard check");
        List<String> base = new ArrayList<>(original);
        for (NameAuditCapture.Insertion insertion : frame.insertions) {
            if (insertion.target == original && insertion.depth == 1) base.removeIf(value -> value == insertion.text);
        }
        return new GuardObservation(frame, original, base);
    }

    private static final class GuardObservation {
        final NameAuditCapture.Frame frame;
        final List<String> original, base;
        GuardObservation(NameAuditCapture.Frame frame, List<String> original, List<String> base) {
            this.frame = frame; this.original = original; this.base = base;
        }
    }

    private static List<String> guardAppend(java.lang.reflect.Method append, ItemStack stack, List<String> base, String title, Object observation) throws Exception {
        List<String> result = new ArrayList<>(base);
        append.invoke(null, stack, result, title, observation);
        return result;
    }

    private static JsonObject dependencyGuards(JsonObject request) throws Exception {
        ItemStack stack = decode(request.getAsJsonObject("sample"));
        ITooltipFlag flags = NameAuditMode.read(request).equals("ADVANCED") ? ITooltipFlag.TooltipFlags.ADVANCED : ITooltipFlag.TooltipFlags.NORMAL;
        NameAuditCapture.Frame frame = NameAuditCapture.begin(stack);
        List<String> original;
        try { original = stack.func_82840_a(Minecraft.func_71410_x().field_71439_g, flags); }
        finally { NameAuditCapture.end(); }
        if (frame.failed || frame.productObservation == null || NativeAccess.get(frame.productObservation, "dependencies") == null)
            throw new IllegalStateException("No dependency plan");
        List<String> base = new ArrayList<>(original);
        for (NameAuditCapture.Insertion insertion : frame.insertions) if (insertion.target == original && insertion.depth == 1)
            base.removeIf(value -> value == insertion.text);
        Class<?> appender = NativeAccess.type("com.starskyxiii.polyglottooltip.legacy.ItemTooltipAppender");
        java.lang.reflect.Method append = appender.getMethod("appendNames", ItemStack.class, List.class, String.class, frame.productObservation.getClass());
        @SuppressWarnings("unchecked") List<Object> resolvers = (List<Object>) NativeAccess.get(appender, "resolvers");
        Class<?> resolverType = NativeAccess.type(appender.getName() + "$NameResolver");
        int[] calls = {0};
        Object resolver = java.lang.reflect.Proxy.newProxyInstance(resolverType.getClassLoader(), new Class<?>[]{resolverType}, (proxy, method, args) -> {
            if (method.getName().equals("resolve")) { calls[0]++; return null; }
            if (method.getName().equals("equals")) return proxy == args[0];
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            return "audit resolver";
        });
        JsonObject result = new JsonObject();
        result.add("original", GSON.toJsonTree(original)); result.add("base", GSON.toJsonTree(base));
        result.add("observation", productObservation(frame.productObservation, stack));
        Object originalTag = NativeAccess.get(stack, "field_77990_d");
        int damage = (Integer) NativeAccess.get(stack, "field_77991_e"), count = (Integer) NativeAccess.get(stack, "field_77994_a");
        resolvers.add(resolver);
        try {
            for (String mutation : new String[]{"unchanged", "unrelated-tag", "display", "count", "damage"}) {
                NativeAccess.set(stack, "field_77990_d", originalTag);
                NativeAccess.set(stack, "field_77991_e", damage);
                NativeAccess.set(stack, "field_77994_a", count);
                if (mutation.equals("count")) NativeAccess.set(stack, "field_77994_a", count + 1);
                if (mutation.equals("damage")) NativeAccess.set(stack, "field_77991_e", damage + 1);
                if (mutation.equals("display") || mutation.equals("unrelated-tag")) {
                    NBTTagCompound tag = originalTag == null ? new NBTTagCompound() : ((NBTTagCompound) originalTag).func_74737_b();
                    tag.func_74778_a(mutation.equals("display") ? "display" : "auditUnrelated", "changed");
                    NativeAccess.set(stack, "field_77990_d", tag);
                }
                int before = calls[0];
                JsonObject row = new JsonObject();
                row.addProperty("current", (Boolean) NativeAccess.call(frame.productObservation, "current", stack));
                row.addProperty("strictCurrent", (Boolean) NativeAccess.call(frame.productObservation, "strictCurrent", stack));
                row.add("tooltip", GSON.toJsonTree(guardAppend(append, stack, base, base.get(0), frame.productObservation)));
                row.addProperty("resolverCalls", calls[0] - before);
                result.add(mutation, row);
            }
        } finally {
            resolvers.remove(resolver);
            NativeAccess.set(stack, "field_77990_d", originalTag);
            NativeAccess.set(stack, "field_77991_e", damage);
            NativeAccess.set(stack, "field_77994_a", count);
        }
        result.addProperty("restoredCurrent", (Boolean) NativeAccess.call(frame.productObservation, "current", stack));
        return result;
    }

    private static JsonObject compositionGuards(JsonObject request) throws Exception {
        ItemStack stack = decode(request.getAsJsonObject("sample"));
        ITooltipFlag flags = NameAuditMode.read(request).equals("ADVANCED") ? ITooltipFlag.TooltipFlags.ADVANCED : ITooltipFlag.TooltipFlags.NORMAL;
        NameAuditCapture.Frame frame = NameAuditCapture.begin(stack);
        List<String> original;
        try { original = stack.func_82840_a(Minecraft.func_71410_x().field_71439_g, flags); }
        finally { NameAuditCapture.end(); }
        if (frame.failed || frame.productObservation == null) throw new IllegalStateException("No composition observation");
        Object composition = NativeAccess.get(frame.productObservation, "composition");
        if (composition == null || !NativeAccess.get(composition, "family").equals("pristine")) throw new IllegalStateException("Unexpected composition");
        List<String> base = new ArrayList<>(original);
        for (NameAuditCapture.Insertion insertion : frame.insertions) if (insertion.target == original && insertion.depth == 1)
            base.removeIf(value -> value == insertion.text);
        Class<?> appender = NativeAccess.type("com.starskyxiii.polyglottooltip.legacy.ItemTooltipAppender");
        java.lang.reflect.Method append = appender.getMethod("appendNames", ItemStack.class, List.class, String.class, frame.productObservation.getClass());
        Item item = stack.func_77973_b();
        Object metadata = NativeAccess.get(item, "metadata"), name = NativeAccess.get(metadata, "displayName");
        Class<?> mod = NativeAccess.type("mustapelto.deepmoblearning.DMLRelearned");
        Object proxy = NativeAccess.get(mod, "proxy"), locale = NativeAccess.get(I18n.class, "field_135054_a");
        @SuppressWarnings("unchecked") Map<String, String> translations = (Map<String, String>) NativeAccess.get(locale, "field_135032_a");
        String key = "deepmoblearning.pristine_matter.display_name", template = translations.get(key);
        int damage = (Integer) NativeAccess.get(stack, "field_77991_e");
        JsonObject result = new JsonObject();
        result.add("base", GSON.toJsonTree(base));
        result.add("observation", productObservation(frame.productObservation, stack));
        for (String mutation : new String[]{"unchanged", "metadata-name", "metadata-object", "proxy", "template", "template-map", "damage"}) {
            try {
                if (mutation.equals("metadata-name")) NativeAccess.set(metadata, "displayName", "audit changed literal");
                if (mutation.equals("metadata-object")) NativeAccess.set(item, "metadata", null);
                if (mutation.equals("proxy")) NativeAccess.set(mod, "proxy", null);
                if (mutation.equals("template")) translations.put(key, "audit changed template %s");
                if (mutation.equals("template-map")) NativeAccess.set(locale, "field_135032_a", new HashMap<>(translations));
                if (mutation.equals("damage")) NativeAccess.set(stack, "field_77991_e", damage + 1);
                JsonObject row = new JsonObject();
                row.addProperty("current", (Boolean) NativeAccess.call(frame.productObservation, "current", stack));
                row.add("tooltip", GSON.toJsonTree(guardAppend(append, stack, base, base.get(0), frame.productObservation)));
                result.add(mutation, row);
            } finally {
                NativeAccess.set(item, "metadata", metadata);
                NativeAccess.set(metadata, "displayName", name);
                NativeAccess.set(mod, "proxy", proxy);
                NativeAccess.set(locale, "field_135032_a", translations);
                translations.put(key, template);
                NativeAccess.set(stack, "field_77991_e", damage);
            }
        }
        result.addProperty("restoredCurrent", (Boolean) NativeAccess.call(frame.productObservation, "current", stack));
        return result;
    }

    private static String sha256(byte[] bytes) {
        try {
            StringBuilder result = new StringBuilder();
            for (byte value : java.security.MessageDigest.getInstance("SHA-256").digest(bytes)) result.append(String.format("%02x", value & 255));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private static JsonObject captureHeld() throws Exception {
        ItemStack original = Minecraft.func_71410_x().field_71439_g.func_184614_ca();
        if (original.func_190926_b()) throw new IllegalStateException("The main hand is empty");
        JsonObject result = new JsonObject();
        result.add("identity", identity());
        result.addProperty("capturedAtMillis", System.currentTimeMillis());
        result.addProperty("source", "manual-main-hand");
        result.addProperty("originalCount", original.func_190916_E());
        result.add("sourceBefore", encoded(original));
        ItemStack copy = original.func_77946_l();
        copy.func_190920_e(1);
        byte[] data = binary(copy);
        result.addProperty("item", copy.func_77973_b().getRegistryName().toString());
        result.addProperty("metadata", copy.func_77960_j());
        result.addProperty("serialized", Base64.getEncoder().encodeToString(data));
        result.addProperty("binarySha256", sha256(data));
        result.add("stack", NameAuditNbt.read(data));
        result.add("sourceAfter", encoded(original));
        return result;
    }

    private static JsonObject diagnose(JsonArray samples) {
        if (samples == null || samples.size() < 1 || samples.size() > 32) throw new IllegalArgumentException("Audit batch must contain 1..32 samples");
        JsonArray rows = new JsonArray();
        for (JsonElement element : samples) {
            JsonObject sample = element.getAsJsonObject(), row = new JsonObject();
            row.add("id", sample.get("id"));
            try { row.add("diagnostics", diagnostics(decode(sample))); }
            catch (Exception | LinkageError error) { row.add("diagnosticError", failure("diagnostics", error)); }
            rows.add(row);
        }
        JsonObject result = new JsonObject();
        result.add("rows", rows);
        return result;
    }

    private static JsonObject diagnostics(ItemStack stack) throws Exception {
        JsonObject result = new JsonObject();
        Item item = stack.func_77973_b();
        result.addProperty("item", String.valueOf(item.getRegistryName()));
        result.addProperty("metadata", stack.func_77960_j());
        result.addProperty("class", item.getClass().getName());
        result.addProperty("classSource", String.valueOf(item.getClass().getProtectionDomain().getCodeSource().getLocation()));
        result.addProperty("nameOwner", item.getClass().getMethod("func_77653_i", ItemStack.class).getDeclaringClass().getName());
        result.addProperty("keyOwner", item.getClass().getMethod("func_77667_c", ItemStack.class).getDeclaringClass().getName());
        String key = item.func_77667_c(stack) + ".name";
        result.addProperty("key", key);
        result.addProperty("keyExists", I18n.func_188566_a(key));
        result.addProperty("keyValue", I18n.func_135052_a(key));
        result.addProperty("displayName", stack.func_82833_r());
        if (Loader.isModLoaded("polyglottooltip")) result.add("resolverNames", GSON.toJsonTree(NativeAccess.call(
                NativeAccess.type("com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryNameResolver"), "resolveItemNames", stack)));
        return result;
    }

    private static JsonObject productObservation(Object observation, ItemStack stack) throws Exception {
        JsonObject result = new JsonObject();
        result.addProperty("rawKey", (String) NativeAccess.call(observation, "rawKey"));
        result.addProperty("current", (Boolean) NativeAccess.call(observation, "current", stack));
        for (String flag : new String[]{"invalid", "named", "titled", "rawCaptured", "rawInvalid"}) {
            result.addProperty(flag, (Boolean) NativeAccess.get(observation, flag));
        }
        Object candidate = NativeAccess.get(observation, "candidate"), plan = NativeAccess.get(candidate, "plan");
        result.addProperty("claimed", (Boolean) NativeAccess.get(candidate, "claimed"));
        result.addProperty("planned", plan != null);
        try {
            result.addProperty("dependencyPlan", NativeAccess.get(observation, "dependencies") != null);
            result.addProperty("strictCurrent", (Boolean) NativeAccess.call(observation, "strictCurrent", stack));
        } catch (NoSuchFieldException | NoSuchMethodException ignored) { }
        try {
            Object consumed = NativeAccess.get(observation, "consumed");
            result.addProperty("consumedClass", consumed == null ? null : consumed.getClass().getName());
            if (consumed != null) {
                result.addProperty("consumedState", String.valueOf(NativeAccess.get(consumed, "state")));
                JsonObject progress = new JsonObject();
                for (String field : new String[]{"next", "phase", "stage", "step", "styleIndex"}) {
                    try {
                        Object value = NativeAccess.get(consumed, field);
                        if (value instanceof String && ((String) value).length() <= 256) progress.addProperty(field, (String) value);
                        else if (value instanceof Number) progress.addProperty(field, (Number) value);
                    } catch (NoSuchFieldException ignored) { }
                }
                result.add("consumedProgress", progress);
                JsonObject budget = new JsonObject();
                Object source = NativeAccess.get(consumed, "budget");
                for (String field : new String[]{"nodes", "facts", "capturedBytes", "resultCharacters"})
                    budget.addProperty(field, (Number) NativeAccess.get(source, field));
                budget.addProperty("valid", (Boolean) NativeAccess.get(source, "valid"));
                result.add("consumedBudget", budget);
            }
            Object raw = NativeAccess.get(observation, "rawFacts");
            result.addProperty("rawFacts", raw != null);
            if (raw != null) {
                result.addProperty("rawExpected", (String) NativeAccess.get(raw, "expected"));
                result.addProperty("rawIndependent", (Boolean) NativeAccess.get(raw, "independent"));
                result.addProperty("rawCurrent", (Boolean) NativeAccess.call(NativeAccess.get(raw, "facts"), "current"));
            }
            Object composition = NativeAccess.get(observation, "composition");
            result.addProperty("compositionFamily", composition == null ? null : (String) NativeAccess.get(composition, "family"));
            Object item = NativeAccess.get(stack, "field_151002_e");
            if (item != null) {
                result.addProperty("itemClass", item.getClass().getName());
                for (String name : new String[]{"func_77653_i", "func_77657_g", "func_77667_c"})
                    result.addProperty(name + "Owner", item.getClass().getMethod(name, ItemStack.class).getDeclaringClass().getName());
            }
        } catch (NoSuchFieldException | NoSuchMethodException | LinkageError ignored) { }
        try {
            Object composite = NativeAccess.get(observation, "composite");
            result.addProperty("compositeCaptured", composite != null);
            if (composite != null) {
                Object trace;
                try { trace = NativeAccess.get(composite, "trace"); }
                catch (NoSuchFieldException ignored) { trace = composite; }
                result.addProperty("compositeStep", (Integer) NativeAccess.get(trace, "step"));
                result.addProperty("compositeInvalid", (Boolean) NativeAccess.get(trace, "invalid"));
                result.addProperty("compositeSealed", (Boolean) NativeAccess.get(trace, "sealed"));
            }
        } catch (NoSuchFieldException ignored) { }
        Object languages = NativeAccess.get(observation, "languages");
        Object primary = NativeAccess.get(languages, "primaryTable");
        result.addProperty("primaryTable", primary != null);
        if (plan != null && primary != null) result.addProperty("reconstructedPrimary", (String) NativeAccess.call(plan, "render", primary));
        return result;
    }

    private static JsonObject compositeInspect(JsonArray samples) {
        if (samples == null || samples.size() < 1 || samples.size() > 16) throw new IllegalArgumentException("Composite inspection batch size");
        JsonArray rows = new JsonArray();
        for (JsonElement element : samples) {
            JsonObject sample = element.getAsJsonObject(), row = new JsonObject();
            row.add("id", sample.get("id"));
            try {
                Object projection = NativeAccess.create("com.starskyxiii.polyglottooltip.legacy.minecraft.NameCompositeFacts", decode(sample));
                NativeAccess.call(projection, "capture");
                Object facts = NativeAccess.get(projection, "facts");
                JsonArray guards = new JsonArray();
                for (Object guard : (List<?>) NativeAccess.get(facts, "guards")) guards.add(((java.util.function.BooleanSupplier) guard).getAsBoolean());
                row.add("guards", guards);
                row.addProperty("current", (Boolean) NativeAccess.call(facts, "current"));
            } catch (Exception | LinkageError error) {
                Throwable cause = error instanceof java.lang.reflect.InvocationTargetException ? ((java.lang.reflect.InvocationTargetException) error).getCause() : error;
                row.add("error", failure("composite-projection-inspection", cause));
                row.add("trace", GSON.toJsonTree(cause.getStackTrace()));
            }
            rows.add(row);
        }
        JsonObject result = new JsonObject(); result.add("rows", rows); return result;
    }

    private static JsonObject item(JsonObject params) throws Exception {
        Minecraft client = Minecraft.func_71410_x();
        if (params.has("sample")) {
            rendered = decode(params.getAsJsonObject("sample"));
            InventoryBasic inventory = new InventoryBasic("Name audit", true, 27);
            inventory.func_70299_a(0, rendered);
            renderScreen = new GuiChest(client.field_71439_g.field_71071_by, inventory);
            client.func_147108_a(renderScreen);
        }
        if (renderScreen == null || client.field_71462_r != renderScreen) throw new IllegalStateException("Audit render screen is not current");
        Slot slot = renderScreen.field_147002_h.func_75139_a(0);
        JsonObject result = new JsonObject();
        result.add("stack", encoded(rendered));
        result.addProperty("slotSame", slot.func_75211_c() == rendered);
        result.addProperty("screenIdentity", System.identityHashCode(renderScreen));
        result.addProperty("stackIdentity", System.identityHashCode(rendered));
        result.addProperty("x", (Integer) NativeAccess.get(renderScreen, "field_147003_i") + slot.field_75223_e + 8);
        result.addProperty("y", (Integer) NativeAccess.get(renderScreen, "field_147009_r") + slot.field_75221_f + 8);
        return result;
    }
}
