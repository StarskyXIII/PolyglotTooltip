package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.client.ClientCommandHandler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Coverage of the minimal-core product on a pack: every JEI/HEI item ingredient goes through the product's own
 * {@code ItemNames.of} (layer 1, then the layer 2 resolvers), reached by reflection because the bridge is not compiled
 * against the product. Counts stacks with a shown secondary name, which layer or resolver produced it
 * ({@code SecondaryNames.source()}), hits and misses per item class, and samples of the classes that miss most. Hits are
 * split into complete and partial names ({@code SecondaryNames.partial()}, a resolver kept some primary-language parts);
 * the classes with the most partial names and the most frequent untranslated parts are reported with samples.
 * {@code item} reads one stack of the player's inventory with its tooltip and the secondary names an independent
 * dictionary (the one {@link ConsistencyProbe} builds) gives for its standard key, and translates extra keys the same way; {@code command} runs a client
 * command and returns the messages it sent.
 */
public final class CoverageProbe {
    private static final String PRODUCT = "com.starskyxiii.polyglottooltip.legacy.minecraft.";
    private static final Gson GSON = new Gson();
    private static final Map<String, ConsistencyProbe.Dictionary> dictionaries = new HashMap<>();

    private CoverageProbe() {}

    public static JsonObject run(JsonObject params) throws Exception {
        long started = System.nanoTime();
        int sampleLimit = params.has("samples") ? params.get("samples").getAsInt() : 5;
        int classLimit = params.has("classes") ? params.get("classes").getAsInt() : 30;
        Method of = Class.forName(PRODUCT + "ItemNames").getMethod("of", ItemStack.class);
        Method names = Class.forName(PRODUCT + "SecondaryNames").getMethod("names");
        Method source = Class.forName(PRODUCT + "SecondaryNames").getMethod("source");
        Method partial = optional(PRODUCT + "SecondaryNames", "partial");
        Method untranslated = optional(PRODUCT + "SecondaryNames", "untranslated");
        Class<?> check = Class.forName(PRODUCT + "ConsistencyCheck");
        Method resolve = check.getMethod("resolve", ItemStack.class, Map.class);
        Method handOff = Class.forName(PRODUCT + "ConsistencyCheck$Result").getMethod("handOff");
        Map<?, ?> productDictionaries = (Map<?, ?>) Class.forName(PRODUCT + "SecondaryLanguages").getMethod("dictionaries").invoke(null);

        ConsistencyProbe.Stacks stacks = ConsistencyProbe.Stacks.collect(params.has("source") ? params.get("source").getAsString() : "auto");
        Map<String, Integer> sources = new TreeMap<>(), misses = new TreeMap<>(), errors = new TreeMap<>();
        Map<String, Integer> fragments = new HashMap<>();
        Map<String, Bucket> byClass = new HashMap<>();
        int total = 0, hits = 0, consistent = 0, partials = 0;
        long nanos = 0;
        for (ItemStack stack : stacks.stacks) {
            Item item = stack.func_77973_b();
            String type = item == null ? "<none>" : item.getClass().getName();
            ResourceLocation id = item == null ? null : item.getRegistryName();
            Bucket bucket = byClass.computeIfAbsent(type, ignored -> new Bucket(id == null ? "<unregistered>" : id.func_110624_b()));
            total++;
            bucket.stacks++;
            String outcome;
            List<?> shown = null;
            Map<?, ?> parts = null;
            try {
                long before = System.nanoTime();
                Object resolved = of.invoke(null, stack);
                nanos += System.nanoTime() - before;
                shown = (List<?>) names.invoke(resolved);
                boolean layer1 = !(Boolean) handOff.invoke(resolve.invoke(null, stack, productDictionaries));
                if (layer1) consistent++;
                if (!shown.isEmpty()) {
                    hits++;
                    bucket.hits++;
                    outcome = String.valueOf(source.invoke(resolved));
                    sources.merge(outcome, 1, Integer::sum);
                    bucket.sources.merge(outcome, 1, Integer::sum);
                    if (partial != null && (Boolean) partial.invoke(resolved)) {
                        partials++;
                        bucket.partial++;
                        parts = (Map<?, ?>) untranslated.invoke(resolved);
                        for (Object list : parts.values()) {
                            for (Object part : (List<?>) list) fragments.merge(String.valueOf(part), 1, Integer::sum);
                        }
                    }
                } else {
                    outcome = stack.func_82837_s() ? "custom" : layer1 ? "consistentNoName" : "inconsistentUnresolved";
                    misses.merge(outcome, 1, Integer::sum);
                    bucket.misses.merge(outcome, 1, Integer::sum);
                }
            } catch (Throwable error) {
                Throwable cause = error instanceof java.lang.reflect.InvocationTargetException ? error.getCause() : error;
                outcome = "error";
                errors.merge(cause.getClass().getName(), 1, Integer::sum);
                misses.merge(outcome, 1, Integer::sum);
                bucket.misses.merge(outcome, 1, Integer::sum);
            }
            boolean hit = shown != null && !shown.isEmpty();
            if (hit && bucket.hitSamples.size() < 2) bucket.hitSamples.add(sample(stack, id, outcome, shown));
            if (parts != null && bucket.partialSamples.size() < sampleLimit) {
                JsonObject sample = sample(stack, id, outcome, shown);
                sample.add("untranslated", GSON.toJsonTree(parts));
                bucket.partialSamples.add(sample);
            }
            if (!hit && !"custom".equals(outcome) && bucket.missSamples.size() < sampleLimit) bucket.missSamples.add(sample(stack, id, outcome, null));
        }
        JsonObject result = new JsonObject();
        result.addProperty("source", stacks.source);
        result.addProperty("sourceError", stacks.error);
        result.addProperty("currentLanguage", Minecraft.func_71410_x().field_71474_y.field_74363_ab);
        result.add("languages", GSON.toJsonTree(new ArrayList<>(((Map<?, ?>) productDictionaries).keySet())));
        result.addProperty("stacks", total);
        result.addProperty("withSecondary", hits);
        result.addProperty("share", total == 0 ? 0 : (double) hits / total);
        result.addProperty("complete", hits - partials);
        result.addProperty("partial", partials);
        result.addProperty("partialShare", total == 0 ? 0 : (double) partials / total);
        result.addProperty("consistent", consistent);
        result.addProperty("consistentShare", total == 0 ? 0 : (double) consistent / total);
        result.add("bySource", ConsistencyProbe.BridgeJson.counts(sources));
        result.add("misses", ConsistencyProbe.BridgeJson.counts(misses));
        result.add("errors", ConsistencyProbe.BridgeJson.counts(errors));
        result.addProperty("meanMicros", total == 0 ? 0 : nanos / 1000.0 / total);
        result.addProperty("classes", byClass.size());
        List<Map.Entry<String, Bucket>> ranked = new ArrayList<>(byClass.entrySet());
        ranked.sort((a, b) -> b.getValue().missed() != a.getValue().missed()
                ? Integer.compare(b.getValue().missed(), a.getValue().missed()) : a.getKey().compareTo(b.getKey()));
        JsonArray top = new JsonArray();
        for (Map.Entry<String, Bucket> entry : ranked) {
            if (top.size() >= classLimit || entry.getValue().missed() == 0) break;
            top.add(entry.getValue().report(entry.getKey(), true));
        }
        result.add("topMissClasses", top);
        List<Map.Entry<String, Bucket>> byPartial = new ArrayList<>(byClass.entrySet());
        byPartial.sort((a, b) -> b.getValue().partial != a.getValue().partial
                ? Integer.compare(b.getValue().partial, a.getValue().partial) : a.getKey().compareTo(b.getKey()));
        JsonArray topPartial = new JsonArray();
        for (Map.Entry<String, Bucket> entry : byPartial) {
            if (topPartial.size() >= 20 || entry.getValue().partial == 0) break;
            topPartial.add(entry.getValue().partialReport(entry.getKey()));
        }
        result.add("topPartialClasses", topPartial);
        List<Map.Entry<String, Integer>> rankedFragments = new ArrayList<>(fragments.entrySet());
        rankedFragments.sort((a, b) -> !a.getValue().equals(b.getValue())
                ? Integer.compare(b.getValue(), a.getValue()) : a.getKey().compareTo(b.getKey()));
        JsonArray topFragments = new JsonArray();
        for (Map.Entry<String, Integer> entry : rankedFragments) {
            if (topFragments.size() >= 30) break;
            JsonObject fragment = new JsonObject();
            fragment.addProperty("text", entry.getKey());
            fragment.addProperty("count", entry.getValue());
            topFragments.add(fragment);
        }
        result.add("topUntranslated", topFragments);
        JsonArray all = new JsonArray();
        for (Map.Entry<String, Bucket> entry : ranked) all.add(entry.getValue().report(entry.getKey(), false));
        result.add("byClass", all);
        result.addProperty("elapsedMs", (System.nanoTime() - started) / 1_000_000);
        return result;
    }

    private static Method optional(String type, String name) {
        try {
            return Class.forName(type).getMethod(name);
        } catch (ReflectiveOperationException missing) {
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    public static JsonObject item(JsonObject params) throws Exception {
        Minecraft client = Minecraft.func_71410_x();
        if (client.field_71439_g == null) throw new IllegalStateException("No player");
        if (params.has("reload") && params.get("reload").getAsBoolean()) dictionaries.clear();
        List<String> languages = new ArrayList<>();
        if (params.has("languages")) for (JsonElement code : params.getAsJsonArray("languages")) languages.add(code.getAsString());
        JsonObject result = new JsonObject();
        if (params.has("keys")) {
            JsonObject keys = new JsonObject();
            for (JsonElement key : params.getAsJsonArray("keys")) {
                JsonObject entry = new JsonObject();
                entry.addProperty("standard", TextFormatting.func_110646_a(I18n.func_74838_a(key.getAsString())).trim());
                entry.add("dictionary", lookup(languages, key.getAsString()));
                keys.add(key.getAsString(), entry);
            }
            result.add("keys", keys);
        }
        if (!params.has("slot")) return result;
        int slot = params.get("slot").getAsInt();
        if (slot < 0 || slot > 35) throw new IllegalArgumentException("slot must be 0..35");
        ItemStack stack = client.field_71439_g.field_71071_by.func_70301_a(slot);
        result.addProperty("slot", slot);
        result.addProperty("empty", stack.func_190926_b());
        if (stack.func_190926_b()) return result;
        Item item = stack.func_77973_b();
        result.addProperty("item", String.valueOf(item.getRegistryName()));
        result.addProperty("metadata", stack.func_77960_j());
        result.addProperty("nbt", String.valueOf(stack.func_77978_p()));
        result.addProperty("class", item.getClass().getName());
        String key = item.func_77657_g(stack) + ".name";
        String expected = TextFormatting.func_110646_a(I18n.func_74838_a(key)).trim();
        String actual = TextFormatting.func_110646_a(stack.func_82833_r()).trim();
        result.addProperty("key", key);
        result.addProperty("standard", expected);
        result.addProperty("displayName", actual);
        result.addProperty("consistent", expected.equals(actual));
        result.add("dictionary", lookup(languages, key));
        result.add("tooltip", GSON.toJsonTree(stack.func_82840_a(client.field_71439_g, ITooltipFlag.TooltipFlags.NORMAL)));
        return result;
    }

    private static JsonObject lookup(List<String> languages, String key) {
        JsonObject values = new JsonObject();
        for (String code : languages) {
            ConsistencyProbe.Dictionary dictionary = dictionaries.computeIfAbsent(code,
                    language -> ConsistencyProbe.Dictionary.load(Minecraft.func_71410_x().func_110442_L(), language));
            String value = dictionary.entries.get(key);
            if (value != null) values.addProperty(code, value.trim());
        }
        return values;
    }

    public static JsonObject command(String command) {
        Minecraft client = Minecraft.func_71410_x();
        if (client.field_71439_g == null) throw new IllegalStateException("No player");
        Capture sender = new Capture(client.field_71439_g);
        JsonObject result = new JsonObject();
        result.addProperty("result", ClientCommandHandler.instance.func_71556_a(sender, command));
        result.add("messages", sender.messages);
        return result;
    }

    private static JsonObject sample(ItemStack stack, ResourceLocation id, String outcome, List<?> shown) {
        JsonObject sample = new JsonObject();
        sample.addProperty("id", String.valueOf(id));
        sample.addProperty("metadata", stack.func_77960_j());
        sample.addProperty("outcome", outcome);
        try { sample.addProperty("name", TextFormatting.func_110646_a(stack.func_82833_r())); }
        catch (Throwable error) { sample.addProperty("name", "<" + error.getClass().getSimpleName() + ">"); }
        try { sample.addProperty("key", stack.func_77973_b().func_77657_g(stack) + ".name"); }
        catch (Throwable error) { sample.addProperty("key", "<" + error.getClass().getSimpleName() + ">"); }
        if (shown != null) sample.add("secondary", GSON.toJsonTree(shown));
        if (stack.func_77942_o()) {
            String nbt = String.valueOf(stack.func_77978_p());
            sample.addProperty("nbt", nbt.length() > 200 ? nbt.substring(0, 200) + "…" : nbt);
        }
        return sample;
    }

    private static JsonObject component(ITextComponent component) {
        JsonObject value = new JsonObject();
        value.addProperty("text", component.func_150260_c());
        if (component instanceof TextComponentTranslation) {
            TextComponentTranslation translation = (TextComponentTranslation) component;
            value.addProperty("key", translation.func_150268_i());
            JsonArray args = new JsonArray();
            for (Object argument : translation.func_150271_j()) {
                if (argument instanceof ITextComponent) args.add(component((ITextComponent) argument));
                else {
                    JsonObject plain = new JsonObject();
                    plain.addProperty("text", String.valueOf(argument));
                    args.add(plain);
                }
            }
            value.add("args", args);
        }
        return value;
    }

    private static final class Bucket {
        final String mod;
        int stacks, hits, partial;
        final Map<String, Integer> sources = new TreeMap<>(), misses = new TreeMap<>();
        final List<JsonObject> hitSamples = new ArrayList<>(), missSamples = new ArrayList<>(), partialSamples = new ArrayList<>();

        Bucket(String mod) { this.mod = mod; }

        int missed() { return stacks - hits; }

        JsonObject report(String name, boolean samples) {
            JsonObject report = new JsonObject();
            report.addProperty("name", name);
            report.addProperty("mod", mod);
            report.addProperty("stacks", stacks);
            report.addProperty("hits", hits);
            report.addProperty("misses", missed());
            report.addProperty("partial", partial);
            report.add("bySource", ConsistencyProbe.BridgeJson.counts(sources));
            report.add("byMiss", ConsistencyProbe.BridgeJson.counts(misses));
            if (samples) report.add("missSamples", GSON.toJsonTree(missSamples));
            report.add("hitSamples", GSON.toJsonTree(hitSamples));
            return report;
        }

        JsonObject partialReport(String name) {
            JsonObject report = new JsonObject();
            report.addProperty("name", name);
            report.addProperty("mod", mod);
            report.addProperty("stacks", stacks);
            report.addProperty("hits", hits);
            report.addProperty("partial", partial);
            report.add("bySource", ConsistencyProbe.BridgeJson.counts(sources));
            report.add("partialSamples", GSON.toJsonTree(partialSamples));
            return report;
        }
    }

    private static final class Capture implements ICommandSender {
        private final ICommandSender delegate;
        final JsonArray messages = new JsonArray();

        Capture(ICommandSender delegate) { this.delegate = delegate; }

        @Override public String func_70005_c_() { return delegate.func_70005_c_(); }
        @Override public ITextComponent func_145748_c_() { return delegate.func_145748_c_(); }
        @Override public void func_145747_a(ITextComponent component) { messages.add(component(component)); delegate.func_145747_a(component); }
        @Override public boolean func_70003_b(int level, String command) { return delegate.func_70003_b(level, command); }
        @Override public BlockPos func_180425_c() { return delegate.func_180425_c(); }
        @Override public Vec3d func_174791_d() { return delegate.func_174791_d(); }
        @Override public World func_130014_f_() { return delegate.func_130014_f_(); }
        @Override public Entity func_174793_f() { return delegate.func_174793_f(); }
        @Override public MinecraftServer func_184102_h() { return delegate.func_184102_h(); }
    }
}
