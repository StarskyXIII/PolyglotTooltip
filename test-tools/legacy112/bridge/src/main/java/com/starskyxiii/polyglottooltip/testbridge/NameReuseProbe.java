package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.Map;

public final class NameReuseProbe {
    private static final String INPUTS =
            "com.starskyxiii.polyglottooltip.legacy.nametable.NameTableInputs";
    private static final Gson GSON = new Gson();
    private static Object heldBinding, heldObservation;
    private static net.minecraft.item.ItemStack heldStack;

    private NameReuseProbe() {}

    public static JsonObject inspect(JsonObject parameters) throws Exception {
        String action = parameters.has("action") ? parameters.get("action").getAsString() : "state";
        if (action.equals("scopeGlobals")) return scopeGlobals();
        if (action.equals("scopeArm") || action.equals("scopeStart") || action.equals("scopeState")) {
            if (!Boolean.getBoolean("clientdevbridge.nameTablePrototype")) throw new IllegalStateException("Owned name-table fixture required");
            Class<?> validation = NativeAccess.type("com.starskyxiii.polyglottooltip.legacy.nametable.NameScopedValidation");
            if (action.equals("scopeArm")) NativeAccess.call(validation, "arm");
            if (action.equals("scopeStart")) NativeAccess.call(validation, "start");
            return GSON.toJsonTree(NativeAccess.call(validation, "status")).getAsJsonObject();
        }
        if (action.equals("rawControls")) return rawControls();
        if (action.equals("rawCatalogControls")) return rawCatalogControls();
        if (action.equals("query")) return query(parameters);
        if (action.equals("jeiKeyAudit")) return jeiKeyAudit(parameters);
        if (action.equals("aliasAudit")) return aliasAudit(parameters);
        if (action.equals("contextHold") || action.equals("contextCheck")) return context(action);
        if (action.equals("retry") || action.equals("cancel")) {
            NativeAccess.call(NativeAccess.type("com.starskyxiii.polyglottooltip.legacy.nametable.NameTableRuntime"), action);
            action = "state";
        }
        if (!action.equals("state"))
            throw new IllegalArgumentException("Unknown name reuse probe action");
        JsonObject result = new JsonObject();
        result.add("table", GSON.toJsonTree(NativeAccess.call(NativeAccess.type(
                "com.starskyxiii.polyglottooltip.legacy.nametable.NameTableRuntime"), "status")));
        Object binding = NativeAccess.call(NativeAccess.type(
                "com.starskyxiii.polyglottooltip.legacy.nametable.NameTableRuntime"), "binding");
        if (binding != null) {
            Object context = NativeAccess.get(binding, "context");
            result.addProperty("scope", (String) NativeAccess.get(context, "identity"));
            result.addProperty("runtimeContext", System.identityHashCode(context));
        }
        Class<?> inputs = NativeAccess.type(INPUTS);
        result.addProperty("status", (String) NativeAccess.call(inputs, "status"));
        result.addProperty("nativeIndependentReady", NativeAccess.call(NativeAccess.type(
                "com.starskyxiii.polyglottooltip.legacy.nametable.NameNativeLanguage"), "capture") != null);
        result.addProperty("nativeIndependentUnavailable", (String) NativeAccess.call(NativeAccess.type(
                "com.starskyxiii.polyglottooltip.legacy.nametable.NameNativeLanguage"), "unavailable"));
        // Which live binding the native receipt no longer matches, and the language the settings select, for a
        // table that waits on waiting-native-language (Divine Journey 2 with RPMTW forcing zh_tw at construction).
        try {
            Class<?> hooks = NativeAccess.type("com.starskyxiii.polyglottooltip.legacy.bootstrap.NativeLanguageHooks");
            Object snapshot = NativeAccess.call(hooks, "snapshot");
            Object stamp = snapshot == null ? null : NativeAccess.get(snapshot, "stamp");
            result.addProperty("nativeBindingFailure", stamp == null ? "no-receipt" : String.valueOf(NativeAccess.call(hooks, "bindingFailure", stamp)));
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.func_71410_x();
            result.addProperty("settingsLanguage", client.field_71474_y == null ? "null" : client.field_71474_y.field_74363_ab);
            result.addProperty("managerLanguage", client.func_135016_M().func_135041_c().func_135034_a());
            if (stamp != null) {
                result.addProperty("stampLanguage", String.valueOf(NativeAccess.get(stamp, "language")));
                result.addProperty("stampManagerCurrent", NativeAccess.get(stamp, "languageManager") == client.func_135016_M());
                result.addProperty("stampResourcesCurrent", NativeAccess.get(stamp, "resourceManager") == client.func_110442_L());
            }
        } catch (Throwable error) { result.addProperty("nativeBindingFailure", "probe: " + error); }
        result.add(
                "nativeLanguage",
                GSON.toJsonTree(
                        NativeAccess.call(
                                NativeAccess.type(
                                        "com.starskyxiii.polyglottooltip.legacy.bootstrap.NativeLanguageHooks"),
                                "diagnostics")));
        Class<?> admission =
                NativeAccess.type(
                        "com.starskyxiii.polyglottooltip.legacy.bootstrap.NameAcquisitionAdmission");
        result.add(
                "acquisitionHooks", GSON.toJsonTree(NativeAccess.call(admission, "diagnostics")));
        result.addProperty(
                "acquisitionHooksReady", (Boolean) NativeAccess.call(admission, "ready"));
        Class<?> preference =
                NativeAccess.type(
                        "com.starskyxiii.polyglottooltip.legacy.bootstrap.NameLanguagePreference");
        JsonObject preferences = new JsonObject();
        preferences.addProperty("active", (Boolean) NativeAccess.call(preference, "active"));
        preferences.addProperty("failure", (String) NativeAccess.call(preference, "failure"));
        preferences.addProperty(
                "maskedSaves", (Number) NativeAccess.call(preference, "maskedSaves"));
        preferences.addProperty(
                "maskedPackets", (Number) NativeAccess.call(preference, "maskedPackets"));
        result.add("languagePreference", preferences);
        Object applied = NativeAccess.call(inputs, "applied");
        result.addProperty("appliedState", NativeAccess.call(applied, "state").toString());
        result.add("reasons", GSON.toJsonTree(NativeAccess.call(applied, "reasons")));
        result.addProperty("startupArtifacts", ((java.util.List<?>) NativeAccess.call(inputs, "startupArtifacts")).size());
        Object identity = NativeAccess.call(inputs, "current");
        result.addProperty("current", identity != null);
        if (identity != null) {
            result.addProperty("identity", (String) NativeAccess.get(identity, "identity"));
            result.addProperty("identityNativeLanguage", (String) NativeAccess.get(identity, "nativeLanguage"));
            result.add("resourcePacks", GSON.toJsonTree(NativeAccess.get(identity, "resourcePacks")));
            result.add("identityLanguages", GSON.toJsonTree(NativeAccess.get(identity, "languages")));
        }
        return result;
    }

    private static JsonObject context(String action) throws Exception {
        if (!Boolean.getBoolean("clientdevbridge.nameTablePrototype"))
            throw new IllegalStateException("Owned name-table fixture required");
        Class<?> runtime = NativeAccess.type("com.starskyxiii.polyglottooltip.legacy.nametable.NameTableRuntime");
        Class<?> observation = NativeAccess.type("com.starskyxiii.polyglottooltip.legacy.minecraft.NativeTableObservation");
        Object binding = NativeAccess.call(runtime, "binding");
        if (action.equals("contextHold")) {
            if (binding == null) throw new IllegalStateException("Current binding required");
            heldStack = new net.minecraft.item.ItemStack(net.minecraft.init.Items.field_151042_j);
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.func_71410_x();
            heldStack.func_82840_a(client.field_71439_g, net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL);
            heldStack.func_82840_a(client.field_71439_g, net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL);
            heldObservation = NativeAccess.call(observation, "observed", heldStack, heldStack.func_82833_r());
            if (heldObservation == null) throw new IllegalStateException("Current observation required");
            heldBinding = binding;
        }
        if (heldBinding == null || heldObservation == null) throw new IllegalStateException("Hold a context first");
        JsonObject result = new JsonObject();
        result.addProperty("heldBindingCurrent", (Boolean) NativeAccess.call(heldBinding, "current"));
        result.addProperty("heldObservationCurrent", (Boolean) NativeAccess.call(heldObservation, "current", heldStack));
        result.addProperty("heldMayRebind", (Boolean) NativeAccess.call(runtime, "mayRebind", heldBinding));
        result.addProperty("sameBinding", binding == heldBinding);
        Object heldContext = NativeAccess.get(heldBinding, "context");
        result.addProperty("heldScope", (String) NativeAccess.get(heldContext, "identity"));
        result.addProperty("heldGeneration", (Number) NativeAccess.get(heldBinding, "generation"));
        if (binding != null) {
            Object currentContext = NativeAccess.get(binding, "context");
            result.addProperty("currentScope", (String) NativeAccess.get(currentContext, "identity"));
            result.addProperty("sameRuntimeContext", currentContext == heldContext);
            result.addProperty("currentGeneration", (Number) NativeAccess.get(binding, "generation"));
            Object current = NativeAccess.call(observation, "observed", heldStack, heldStack.func_82833_r());
            result.addProperty("freshObservationCurrent", current != null && (Boolean) NativeAccess.call(current, "current", heldStack));
            if (current != null) result.addProperty("freshEnglishName", (String) NativeAccess.call(current, "name", "en_us"));
        }
        return result;
    }

    /**
     * Read-only: capture every JEI item ingredient against the bound table and summarise misses by
     * item class and by mod, with decoded key headers for a few misses and the table's rows for the same item.
     * {@code focus} names item classes whose every ingredient is listed with the stored rows of the same
     * id and damage, and the names a tooltip would resolve for a copy of it (one native call per copy).
     */
    private static JsonObject jeiKeyAudit(JsonObject parameters) throws Exception {
        java.util.Set<String> focus = new java.util.HashSet<>();
        if (parameters.has("focus")) for (com.google.gson.JsonElement value : parameters.getAsJsonArray("focus")) focus.add(value.getAsString());
        String product = "com.starskyxiii.polyglottooltip.legacy.nametable.";
        Object binding = NativeAccess.call(NativeAccess.type(product + "NameTableRuntime"), "binding");
        if (binding == null) throw new IllegalStateException("No current table binding");
        Object table = NativeAccess.get(binding, "table"), registry = NativeAccess.get(binding, "registry");
        Class<?> raw = NativeAccess.type(product + "RawStackState");
        java.util.Collection<net.minecraft.item.ItemStack> ingredients =
                mezz.jei.Internal.getIngredientRegistry().getAllIngredients(net.minecraft.item.ItemStack.class);
        Map<String, int[]> byClass = new java.util.TreeMap<>(), byMod = new java.util.TreeMap<>();
        Map<String, com.google.gson.JsonArray> focused = new java.util.TreeMap<>();
        Map<String, Integer> focusHits = new java.util.HashMap<>();
        Map<String, Integer> failures = new java.util.TreeMap<>();
        Map<String, com.google.gson.JsonArray> samples = new java.util.LinkedHashMap<>();
        int hits = 0, misses = 0;
        for (net.minecraft.item.ItemStack stack : ingredients) {
            String type = stack.func_77973_b() == null ? "<none>" : stack.func_77973_b().getClass().getName();
            int[] counts = byClass.computeIfAbsent(type, ignored -> new int[3]);
            Object name = stack.func_77973_b() == null ? null : stack.func_77973_b().getRegistryName();
            int[] mod = byMod.computeIfAbsent(name == null ? "<none>" : name.toString().split(":", 2)[0], ignored -> new int[3]);
            Object key;
            try { key = NativeAccess.call(raw, "capture", stack, registry); }
            catch (java.lang.reflect.InvocationTargetException error) {
                counts[2]++; mod[2]++;
                failures.merge(String.valueOf(error.getCause() == null ? error : error.getCause().getMessage()), 1, Integer::sum);
                if (focus.contains(type) && focused.computeIfAbsent(type, ignored -> new com.google.gson.JsonArray()).size() < 40) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("captureFailure", String.valueOf(error.getCause()));
                    entry.addProperty("nbt", String.valueOf(stack.func_77978_p()));
                    entry.add("names", resolvedNames(stack));
                    focused.get(type).add(entry);
                }
                continue;
            }
            boolean hit = (Boolean) NativeAccess.call(table, "contains", key);
            if (focus.contains(type) && focused.computeIfAbsent(type, ignored -> new com.google.gson.JsonArray()).size() < 40
                    && (!hit || focusHits.merge(type, 1, Integer::sum) <= 3)) {
                JsonObject entry = header((byte[]) NativeAccess.call(key, "bytes"));
                entry.addProperty("hit", hit);
                entry.addProperty("nbt", String.valueOf(stack.func_77978_p()));
                entry.addProperty("capNbt", String.valueOf(NativeAccess.get(stack, "capNBT")));
                entry.add("names", resolvedNames(stack));
                focused.get(type).add(entry);
            }
            if (hit) { counts[0]++; mod[0]++; hits++; continue; }
            counts[1]++; mod[1]++; misses++;
            com.google.gson.JsonArray list = samples.get(type);
            if (list == null && samples.size() < 12) samples.put(type, list = new com.google.gson.JsonArray());
            if (list != null && list.size() < 2) {
                JsonObject sample = header((byte[]) NativeAccess.call(key, "bytes"));
                sample.addProperty("nbt", String.valueOf(stack.func_77978_p()));
                list.add(sample);
            }
        }
        // Table rows for the sampled item ids, so a miss can be compared with what was stored.
        java.util.Set<String> ids = new java.util.HashSet<>(), focusIds = new java.util.HashSet<>();
        for (com.google.gson.JsonArray list : samples.values())
            for (com.google.gson.JsonElement value : list) ids.add(value.getAsJsonObject().get("id").getAsString());
        for (com.google.gson.JsonArray list : focused.values())
            for (com.google.gson.JsonElement value : list)
                if (value.getAsJsonObject().has("id") && !value.getAsJsonObject().get("hit").getAsBoolean())
                    focusIds.add(value.getAsJsonObject().get("id").getAsString() + "@" + value.getAsJsonObject().get("damage").getAsInt());
        Map<String, com.google.gson.JsonArray> stored = new java.util.TreeMap<>(), storedFocus = new java.util.TreeMap<>();
        for (Object key : (Iterable<?>) NativeAccess.call(table, "keys")) {
            JsonObject header = header((byte[]) NativeAccess.call(key, "bytes"));
            String id = header.get("id").getAsString(), idDamage = id + "@" + header.get("damage").getAsInt();
            if (focusIds.contains(idDamage)) {
                com.google.gson.JsonArray list = storedFocus.computeIfAbsent(idDamage, ignored -> new com.google.gson.JsonArray());
                if (list.size() < 4) {
                    JsonObject row = header((byte[]) NativeAccess.call(key, "bytes"));
                    try {
                        net.minecraft.item.ItemStack owned = (net.minecraft.item.ItemStack) NativeAccess.call(raw, "reconstruct", key, registry);
                        row.addProperty("nbt", String.valueOf(owned.func_77978_p()));
                        row.addProperty("capNbt", String.valueOf(NativeAccess.get(owned, "capNBT")));
                    } catch (Exception error) { row.addProperty("nbt", "<" + error + ">"); }
                    list.add(row);
                }
            }
            if (!ids.contains(id)) continue;
            com.google.gson.JsonArray list = stored.computeIfAbsent(id, ignored -> new com.google.gson.JsonArray());
            if (list.size() < 3) {
                try {
                    net.minecraft.item.ItemStack owned = (net.minecraft.item.ItemStack) NativeAccess.call(raw, "reconstruct", key, registry);
                    header.addProperty("nbt", String.valueOf(owned.func_77978_p()));
                } catch (Exception error) { header.addProperty("nbt", "<" + error + ">"); }
                list.add(header);
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("ingredients", ingredients.size()); result.addProperty("hits", hits); result.addProperty("misses", misses);
        JsonObject classes = new JsonObject();
        byClass.entrySet().stream().filter(entry -> entry.getValue()[1] + entry.getValue()[2] > 0)
                .sorted((left, right) -> (right.getValue()[1] + right.getValue()[2]) - (left.getValue()[1] + left.getValue()[2]))
                .limit(40).forEach(entry -> {
                    com.google.gson.JsonArray counts = new com.google.gson.JsonArray();
                    for (int count : entry.getValue()) counts.add(count);
                    classes.add(entry.getKey(), counts);
                });
        result.add("classesHitMissFailed", classes);
        JsonObject mods = new JsonObject();
        byMod.forEach((mod, counts) -> {
            com.google.gson.JsonArray values = new com.google.gson.JsonArray();
            for (int count : counts) values.add(count);
            mods.add(mod, values);
        });
        result.add("modsHitMissFailed", mods);
        result.add("focus", GSON.toJsonTree(focused));
        result.add("storedForFocusMisses", GSON.toJsonTree(storedFocus));
        result.add("failures", GSON.toJsonTree(failures));
        result.add("missSamples", GSON.toJsonTree(samples));
        result.add("storedForSampleIds", GSON.toJsonTree(stored));
        return result;
    }

    /**
     * Read-only replay of the alias index for the recipe viewer elements whose item class is in {@code focus}:
     * the element's cached display name, the catalogue key the index would use, the table's stored primary for
     * it, and what the index's table read and its owned-stack fallback each return. Elements the index would
     * leave for the main-thread drain are listed first; at most {@code limit} per class.
     */
    private static JsonObject aliasAudit(JsonObject parameters) throws Exception {
        String product = "com.starskyxiii.polyglottooltip.legacy.";
        java.util.Set<String> focus = new java.util.HashSet<>();
        for (com.google.gson.JsonElement value : parameters.getAsJsonArray("focus")) focus.add(value.getAsString());
        int limit = parameters.has("limit") ? parameters.get("limit").getAsInt() : 60;
        Class<?> runtime = NativeAccess.type(product + "nametable.NameTableRuntime");
        Object binding = NativeAccess.call(runtime, "binding");
        if (binding == null) throw new IllegalStateException("No current table binding");
        Class<?> aliases = NativeAccess.type(product + "integration.jei.RecipeViewerAliases");
        Object state = NativeAccess.call(aliases, "active");
        if (state == null) throw new IllegalStateException("No alias state");
        Object source = NativeAccess.get(state, "source");
        java.util.List<?> elements = (java.util.List<?>) NativeAccess.get(source, "elements");
        Class<?> helper = NativeAccess.type(product + "integration.jei.RecipeViewerNameHelper");
        Object table = NativeAccess.get(binding, "table"), registry = NativeAccess.get(binding, "registry"), jeiKeys = NativeAccess.get(binding, "jeiKeys");
        java.util.Locale locale = (java.util.Locale) NativeAccess.call(helper, "catalogueLocale");
        Object view = NativeAccess.call(binding, "catalogueView");
        String language = (String) NativeAccess.get(NativeAccess.get(binding, "nativeLanguage"), "language");
        Map<String, com.google.gson.JsonArray> missing = new java.util.TreeMap<>(), indexed = new java.util.TreeMap<>();
        Map<String, int[]> counts = new java.util.TreeMap<>();
        // Identity: the catalogue's JEI source is this registry snapshot, keyed by stack object.
        java.util.Set<Object> registryStacks = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        registryStacks.addAll(mezz.jei.Internal.getIngredientRegistry().getAllIngredients(net.minecraft.item.ItemStack.class));
        int[] visibility = new int[4];
        Map<String, int[]> unindexed = new java.util.TreeMap<>();
        for (Object element : elements) {
            Object ingredient = NativeAccess.call(element, "getIngredient");
            boolean visible = (Boolean) NativeAccess.call(element, "isVisible"), registered = registryStacks.contains(ingredient);
            visibility[(visible ? 0 : 2) + (registered ? 0 : 1)]++;
            // Elements the table read cannot index (cheap: no native call); by ingredient type and visibility.
            if (NativeAccess.call(helper, "indexedNames", element, binding) == null) {
                String kind = ingredient instanceof net.minecraft.item.ItemStack ? "item" : ingredient == null ? "null" : ingredient.getClass().getSimpleName();
                unindexed.computeIfAbsent(kind, ignored -> new int[4])[(visible ? 0 : 2) + (registered ? 0 : 1)]++;
            }
            if (!(ingredient instanceof net.minecraft.item.ItemStack)) continue;
            net.minecraft.item.ItemStack stack = (net.minecraft.item.ItemStack) ingredient;
            String type = stack.func_77973_b().getClass().getName();
            if (!focus.contains(type)) continue;
            JsonObject row = new JsonObject();
            row.addProperty("damage", stack.func_77960_j());
            row.addProperty("nbt", String.valueOf(stack.func_77978_p()));
            row.addProperty("elementClass", element.getClass().getName());
            row.addProperty("displayName", String.valueOf(NativeAccess.call(element, "getDisplayName")));
            Object catalogueKey = jeiKeys == null ? null : NativeAccess.call(jeiKeys, "key", ingredient);
            Object liveKey;
            try { liveKey = NativeAccess.call(NativeAccess.type(product + "nametable.RawStackState"), "capture", stack, registry); }
            catch (Exception error) { liveKey = null; row.addProperty("liveKeyFailure", String.valueOf(error.getCause())); }
            row.addProperty("visible", (Boolean) NativeAccess.call(element, "isVisible"));
            row.addProperty("inRegistry", registryStacks.contains(stack));
            if (liveKey != null) row.add("liveKey", header((byte[]) NativeAccess.call(liveKey, "bytes")));
            row.addProperty("catalogueKey", catalogueKey != null);
            row.addProperty("catalogueKeyEqualsLive", catalogueKey != null && catalogueKey.equals(liveKey));
            Object key = catalogueKey != null ? catalogueKey : liveKey;
            row.addProperty("tableRow", key != null && (Boolean) NativeAccess.call(table, "contains", key));
            if (key != null) row.addProperty("tablePrimary", String.valueOf(NativeAccess.call(table, "capturedName", key, language)));
            Object read = NativeAccess.call(helper, "indexedNames", element, binding);
            row.add("indexedNames", GSON.toJsonTree(read));
            Object fallback = null;
            if (read == null) {
                try { fallback = NativeAccess.call(helper, "catalogueNames", element, binding, locale, view); }
                catch (Exception error) { row.addProperty("fallbackFailure", String.valueOf(error.getCause())); }
                row.add("catalogueNames", GSON.toJsonTree(fallback));
            }
            boolean miss = read == null && fallback == null;
            int[] count = counts.computeIfAbsent(type, ignored -> new int[3]);
            count[read != null ? 0 : fallback != null ? 1 : 2]++;
            com.google.gson.JsonArray list = (miss ? missing : indexed).computeIfAbsent(type, ignored -> new com.google.gson.JsonArray());
            if (list.size() < (miss ? limit : 3)) list.add(row);
        }
        JsonObject result = new JsonObject();
        result.addProperty("language", language);
        result.add("indexedFallbackMissing", GSON.toJsonTree(counts));
        // [visible+registered, visible only, hidden+registered, hidden only]
        result.add("elementsByVisibilityRegistry", GSON.toJsonTree(visibility));
        result.add("unindexedByVisibilityRegistry", GSON.toJsonTree(unindexed));
        result.add("missing", GSON.toJsonTree(missing));
        result.add("indexedSamples", GSON.toJsonTree(indexed));
        return result;
    }

    /** Languages a tooltip would resolve for a copy of the ingredient; the copy takes the native call. */
    private static com.google.gson.JsonElement resolvedNames(net.minecraft.item.ItemStack ingredient) {
        try {
            String product = "com.starskyxiii.polyglottooltip.legacy.minecraft.";
            net.minecraft.item.ItemStack stack = ingredient.func_77946_l();
            String primary = stack.func_82833_r();
            Object observed = NativeAccess.call(NativeAccess.type(product + "NativeTableObservation"), "observed", stack, primary);
            Class<?> resolver = NativeAccess.type(product + "SecondaryNameResolver");
            java.util.function.Supplier<Object> fallback = () -> {
                try { return NativeAccess.call(resolver, "fallbackLanguages", stack, primary); }
                catch (Exception error) { throw new IllegalStateException(error); }
            };
            JsonObject result = new JsonObject();
            result.addProperty("primary", primary);
            // ItemStack.copy() hands the copy capNBT = serializeNBT(); does the copy keep the catalogue key?
            try {
                Class<?> raw = NativeAccess.type("com.starskyxiii.polyglottooltip.legacy.nametable.RawStackState");
                Object binding = NativeAccess.call(NativeAccess.type("com.starskyxiii.polyglottooltip.legacy.nametable.NameTableRuntime"), "binding");
                Object registry = NativeAccess.get(binding, "registry");
                net.minecraft.item.ItemStack copy = ingredient.func_77946_l();
                Object original = NativeAccess.call(raw, "capture", ingredient, registry), copied = NativeAccess.call(raw, "capture", copy, registry);
                result.addProperty("copyKeyEqual", original.equals(copied));
                result.addProperty("copyCapNbt", String.valueOf(NativeAccess.get(copy, "capNBT")));
                result.addProperty("copyInTable", (Boolean) NativeAccess.call(NativeAccess.get(binding, "table"), "contains", copied));
            } catch (Exception error) { result.addProperty("copyKey", String.valueOf(error)); }
            result.add("languages", GSON.toJsonTree(NativeAccess.call(NativeAccess.type(product + "CompleteNameResolver"), "resolve", stack, primary, observed, fallback)));
            return result;
        } catch (Exception | LinkageError error) { return new com.google.gson.JsonPrimitive(String.valueOf(error)); }
    }

    /** Raw key header: item id, count, damage, capability state and descriptors, plus a digest of the NBT tail. */
    private static JsonObject header(byte[] bytes) throws Exception {
        java.io.DataInputStream input = new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes));
        JsonObject result = new JsonObject();
        result.addProperty("schema", input.readInt());
        result.addProperty("id", text(input));
        result.addProperty("count", input.readInt()); result.addProperty("damage", input.readInt());
        int capabilities = input.readUnsignedByte();
        result.addProperty("capabilities", capabilities);
        if (capabilities >= 8) {
            com.google.gson.JsonArray providers = new com.google.gson.JsonArray();
            for (int index = input.readInt(); index > 0; index--) providers.add(text(input));
            result.add("providers", providers);
        }
        byte[] tail = new byte[input.available()];
        input.readFully(tail);
        result.addProperty("nbtBytes", tail.length);
        result.addProperty("nbtSha256", hex(java.security.MessageDigest.getInstance("SHA-256").digest(tail)).substring(0, 16));
        result.addProperty("keyBytes", bytes.length);
        return result;
    }

    private static String text(java.io.DataInputStream input) throws java.io.IOException {
        int length = input.readInt();
        if (length < 0 || length > 65_536) throw new java.io.IOException("Key text length");
        char[] value = new char[length];
        for (int index = 0; index < length; index++) value[index] = input.readChar();
        return new String(value);
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder();
        for (byte part : value) result.append(String.format("%02x", part & 0xff));
        return result.toString();
    }

    private static JsonObject query(JsonObject parameters) throws Exception {
        com.google.gson.JsonArray samples = parameters.getAsJsonArray("samples");
        if (samples == null || samples.size() < 1 || samples.size() > 32) throw new IllegalArgumentException("Use 1..32 samples");
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.func_71410_x();
        if (client.field_71441_e == null || client.field_71439_g == null) throw new IllegalStateException("World required");
        String product = "com.starskyxiii.polyglottooltip.legacy.";
        Class<?> runtime = NativeAccess.type(product + "nametable.NameTableRuntime");
        Class<?> observation = NativeAccess.type(product + "minecraft.NativeTableObservation");
        Class<?> resolver = NativeAccess.type(product + "minecraft.SecondaryNameResolver");
        Class<?> coordinator = NativeAccess.type(product + "minecraft.CompleteNameResolver");
        com.google.gson.JsonArray rows = new com.google.gson.JsonArray();
        for (com.google.gson.JsonElement value : samples) {
            JsonObject sample = value.getAsJsonObject(), row = new JsonObject();
            row.add("sampleId", sample.get("sampleId")); row.add("item", sample.get("item"));
            try {
                net.minecraft.item.ItemStack stack = (net.minecraft.item.ItemStack) NativeAccess.call(NameTableProbe.class, "decode", sample);
                row.addProperty("capabilitiesBeforeFirstTooltip", capabilityState(stack));
                Map<?, ?> before = (Map<?, ?>) NativeAccess.call(runtime, "status");
                NameAuditCapture.Frame frame = NameAuditCapture.begin(stack);
                try { row.add("tooltip", GSON.toJsonTree(stack.func_82840_a(client.field_71439_g, net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL))); }
                finally { NameAuditCapture.end(); }
                Map<?, ?> after = (Map<?, ?>) NativeAccess.call(runtime, "status");
                row.addProperty("tooltipTableHits", ((Number) after.get("hits")).longValue() - ((Number) before.get("hits")).longValue());
                row.addProperty("nativeCaptured", frame.nativeNameCaptured);
                row.addProperty("nativeName", frame.nativeName);
                row.addProperty("capabilitiesAfterFirstTooltip", capabilityState(stack));
                before = (Map<?, ?>) NativeAccess.call(runtime, "status");
                NameAuditCapture.Frame repeat = NameAuditCapture.begin(stack);
                try { row.add("repeatTooltip", GSON.toJsonTree(stack.func_82840_a(client.field_71439_g, net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL))); }
                finally { NameAuditCapture.end(); }
                after = (Map<?, ?>) NativeAccess.call(runtime, "status");
                row.addProperty("repeatTooltipTableHits", ((Number) after.get("hits")).longValue() - ((Number) before.get("hits")).longValue());
                row.addProperty("repeatNativeName", repeat.nativeName);
                row.addProperty("capabilitiesAfterRepeatTooltip", capabilityState(stack));
                if (frame.nativeNameCaptured) {
                    Object observed = NativeAccess.call(observation, "observed", stack, frame.nativeName);
                    java.util.function.Supplier<Object> fallback = () -> {
                        try { return NativeAccess.call(resolver, "fallbackLanguages", stack, frame.nativeName); }
                        catch (Exception error) { throw new IllegalStateException(error); }
                    };
                    row.add("languages", GSON.toJsonTree(NativeAccess.call(coordinator, "resolve", stack, frame.nativeName, observed, fallback)));
                    row.add("searchNames", GSON.toJsonTree(NativeAccess.call(resolver, "resolveItemNames", stack, frame.nativeName)));
                }
            } catch (Exception | LinkageError error) { row.addProperty("error", error.toString()); }
            rows.add(row);
        }
        JsonObject result = new JsonObject(); result.add("rows", rows);
        result.add("table", GSON.toJsonTree(NativeAccess.call(runtime, "status")));
        return result;
    }

    private static int capabilityState(net.minecraft.item.ItemStack stack) {
        try { return ((Number) NativeAccess.call(NativeAccess.type(
                "com.starskyxiii.polyglottooltip.legacy.nametable.RawCapabilityState"), "capture", stack)).intValue(); }
        catch (Exception | LinkageError unavailable) { return -1; }
    }

    private static JsonObject scopeGlobals() throws Exception {
        if (!Boolean.getBoolean("clientdevbridge.nameTablePrototype")) throw new IllegalStateException("Owned name-table fixture required");
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.func_71410_x();
        Object locale = NativeAccess.get(client.func_135016_M(), "field_135049_a");
        Object legacy = NativeAccess.get(NativeAccess.type("net.minecraft.util.text.translation.I18n"), "field_74839_a");
        JsonObject result = new JsonObject();
        result.addProperty("language", client.field_71474_y.field_74363_ab);
        result.addProperty("localeIdentity", System.identityHashCode(locale));
        result.addProperty("legacyIdentity", System.identityHashCode(legacy));
        result.addProperty("clientDictionary", dictionaryHash(NativeAccess.get(locale, "field_135032_a")));
        result.addProperty("legacyDictionary", dictionaryHash(NativeAccess.get(legacy, "field_74816_c")));
        result.addProperty("legacyTimestamp", (Number) NativeAccess.get(legacy, "field_150511_e"));
        result.addProperty("fontUnicode", (Boolean) NativeAccess.get(client.field_71466_p, "field_78293_l"));
        result.addProperty("fontBidi", (Boolean) NativeAccess.get(client.field_71466_p, "field_78294_m"));
        result.addProperty("forceUnicode", client.field_71474_y.field_151455_aw);
        result.addProperty("scopeActive", (Boolean) NativeAccess.call(NativeAccess.type(
                "com.starskyxiii.polyglottooltip.legacy.bootstrap.NativeScopedLanguage"), "active"));
        return result;
    }

    private static String dictionaryHash(Object value) throws Exception {
        if (value == null || value.getClass() != java.util.HashMap.class && value.getClass() != java.util.LinkedHashMap.class)
            throw new IllegalStateException("Unadmitted dictionary map");
        java.util.TreeMap<String, String> ordered = new java.util.TreeMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String)) throw new IllegalStateException("Dictionary entry type");
            ordered.put((String) entry.getKey(), (String) entry.getValue());
        }
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        for (Map.Entry<String, String> entry : ordered.entrySet()) {
            for (String text : new String[] {entry.getKey(), entry.getValue()}) {
                int length = text.length();
                for (int shift = 24; shift >= 0; shift -= 8) digest.update((byte) (length >>> shift));
                for (int index = 0; index < length; index++) { char c = text.charAt(index); digest.update((byte) (c >>> 8)); digest.update((byte) c); }
            }
        }
        StringBuilder hash = new StringBuilder();
        for (byte b : digest.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return hash.toString();
    }

    private static JsonObject rawCatalogControls() throws Exception {
        String prefix = "com.starskyxiii.polyglottooltip.legacy.nametable.";
        Object registry = NativeAccess.type(prefix + "RawItemRegistry").getConstructor().newInstance();
        Class<?> raw = NativeAccess.type(prefix + "RawStackState");
        Class<?> caps = NativeAccess.type(prefix + "RawCapabilityState");
        com.google.gson.JsonArray rows = new com.google.gson.JsonArray();
        int inspected = 0;
        for (net.minecraft.item.ItemStack stack : mezz.jei.Internal.getIngredientRegistry().getAllIngredients(net.minecraft.item.ItemStack.class)) {
            if (++inspected > 128 || rows.size() >= 12) break;
            Object key;
            try { key = NativeAccess.call(raw, "capture", stack, registry); }
            catch (Exception | LinkageError unavailable) { continue; }
            JsonObject row = new JsonObject();
            row.addProperty("sourceCapabilityState", (Number) NativeAccess.call(caps, "capture", stack));
            row.addProperty("keyBytes", (Number) NativeAccess.call(key, "size"));
            try {
                net.minecraft.item.Item item = (net.minecraft.item.Item) NativeAccess.get(stack, "field_151002_e");
                row.addProperty("item", item.getRegistryName().toString());
                net.minecraft.item.ItemStack fresh = new net.minecraft.item.ItemStack(item,
                        (Integer) NativeAccess.get(stack, "field_77994_a"), (Integer) NativeAccess.get(stack, "field_77991_e"));
                row.addProperty("freshCapabilityState", (Number) NativeAccess.call(caps, "capture", fresh));
                Object owned = NativeAccess.call(raw, "reconstruct", key, registry);
                row.addProperty("ownedCapabilityState", (Number) NativeAccess.call(caps, "capture", owned));
                row.addProperty("ownedExact", key.equals(NativeAccess.call(raw, "capture", owned, registry)));
            } catch (Exception | LinkageError error) {
                Throwable cause = error;
                while (cause.getCause() != null) cause = cause.getCause();
                row.addProperty("failure", cause.toString());
            }
            row.addProperty("originalUnchanged", key.equals(NativeAccess.call(raw, "capture", stack, registry)));
            rows.add(row);
        }
        JsonObject result = new JsonObject(); result.addProperty("inspected", inspected); result.add("rows", rows);
        return result;
    }

    private static JsonObject rawControls() throws Exception {
        String prefix = "com.starskyxiii.polyglottooltip.legacy.nametable.";
        Object registry = NativeAccess.type(prefix + "RawItemRegistry").getConstructor().newInstance();
        Class<?> raw = NativeAccess.type(prefix + "RawStackState");
        com.google.gson.JsonArray rows = new com.google.gson.JsonArray();
        for (String id : new String[] {"minecraft:stone", "minecraft:iron_sword", "minecraft:potion"}) {
            net.minecraft.item.Item item = net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.getValue(new net.minecraft.util.ResourceLocation(id));
            NativeAccess.call(raw, "requireBoundedItem", item);
            for (int variant = 0; variant < 4; variant++) {
                JsonObject row = new JsonObject();
                row.addProperty("item", id);
                row.addProperty("variant", variant);
                net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(item, 3, 5);
                if (variant > 0) {
                    net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
                    if (variant > 1) {
                        net.minecraft.nbt.NBTTagCompound display = new net.minecraft.nbt.NBTTagCompound();
                        display.func_74778_a(variant == 2 ? "Name" : "LocName", variant == 2 ? "custom\ud800" : "tile.stone.name");
                        tag.func_74782_a("display", display);
                    }
                    stack.func_77982_d(tag);
                }
                try {
                    Object snapshot = NativeAccess.call(raw, "snapshot", stack, registry), key = NativeAccess.get(snapshot, "key");
                    row.addProperty("captured", true);
                    row.addProperty("keyBytes", (Number) NativeAccess.call(key, "size"));
                    row.addProperty("customName", (Boolean) NativeAccess.get(snapshot, "customName"));
                    row.addProperty("localizedName", (Boolean) NativeAccess.get(snapshot, "localizedName"));
                    Object owned = NativeAccess.call(raw, "reconstruct", key, registry);
                    row.addProperty("ownedExact", key.equals(NativeAccess.call(raw, "capture", owned, registry)));
                    row.addProperty("originalUnchanged", key.equals(NativeAccess.call(raw, "capture", stack, registry)));
                } catch (Exception | LinkageError error) {
                    row.addProperty("failure", error.toString());
                }
                rows.add(row);
            }
        }
        JsonObject result = new JsonObject();
        result.add("rows", rows);
        return result;
    }
}
