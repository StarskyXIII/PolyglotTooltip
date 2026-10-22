package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Coverage-gap measurement for the minimal-core design (docs/refactoring/2026-10-22-legacy112-minimal-core-design.md §8).
 * For every item stack the pack exposes, recompute the vanilla standard naming path with public API only
 * ({@code Item#getUnlocalizedName(stack)} → inefficient translate → {@code + ".name"} → translate → trim) and compare it
 * with {@code ItemStack#getDisplayName()} stripped of style codes. Equal means a key-based secondary dictionary can
 * name the stack; unequal means only a mod-specific resolver could. Secondary dictionaries are built from
 * {@code assets/<domain>/lang/<code>.lang} through the resource manager, the way vanilla's Locale does, so the
 * run also measures how many consistent keys those files actually hold. The product jar must be absent: its mixins
 * change the standard path. Nothing here is cached or hooked; every failure is counted per stack.
 */
public final class ConsistencyProbe {
    private static final Pattern NUMERIC_VARIANT = Pattern.compile("%(\\d+\\$)?[\\d.]*[df]");

    private ConsistencyProbe() {}

    public static JsonObject run(JsonObject params) throws Exception {
        long started = System.nanoTime();
        int sampleLimit = params.has("samples") ? params.get("samples").getAsInt() : 5;
        String source = params.has("source") ? params.get("source").getAsString() : "auto";
        boolean fluids = !params.has("fluids") || params.get("fluids").getAsBoolean();
        List<String> languages = new ArrayList<>();
        if (params.has("languages")) for (com.google.gson.JsonElement code : params.getAsJsonArray("languages")) languages.add(code.getAsString());
        if (languages.isEmpty()) languages.add("en_us");

        Minecraft client = Minecraft.func_71410_x();
        JsonObject result = new JsonObject();
        result.addProperty("currentLanguage", client.field_71474_y.field_74363_ab);
        result.addProperty("world", client.field_71441_e != null);
        result.addProperty("java", System.getProperty("java.version"));
        result.addProperty("productLoaded", Loader.isModLoaded("polyglottooltip"));

        Map<String, Dictionary> dictionaries = new LinkedHashMap<>();
        JsonObject dictionaryReport = new JsonObject();
        for (String code : languages) {
            Dictionary dictionary = Dictionary.load(client.func_110442_L(), code);
            dictionaries.put(code, dictionary);
            dictionaryReport.add(code, dictionary.report());
        }
        result.add("dictionaries", dictionaryReport);

        Stacks stacks = Stacks.collect(source);
        result.addProperty("source", stacks.source);
        result.addProperty("sourceError", stacks.error);

        Totals totals = new Totals(languages);
        Map<String, Bucket> byClass = new HashMap<>(), byMod = new HashMap<>();
        Map<String, Integer> errors = new TreeMap<>();
        long pathNanos = 0;
        for (ItemStack stack : stacks.stacks) {
            Item item = stack.func_77973_b();
            String type = item == null ? "<none>" : item.getClass().getName();
            ResourceLocation id = item == null ? null : item.getRegistryName();
            String mod = id == null ? "<unregistered>" : id.func_110624_b();
            Bucket cls = byClass.computeIfAbsent(type, ignored -> new Bucket(mod, languages)), owner = byMod.computeIfAbsent(mod, ignored -> new Bucket(mod, languages));
            totals.stacks++; cls.stacks++; owner.stacks++;
            Row row = new Row();
            try {
                if (stack.func_82837_s()) {
                    totals.custom++; cls.custom++; owner.custom++;
                    continue;
                }
                long before = System.nanoTime();
                // The public, overridable path vanilla's getItemStackDisplayName takes: getUnlocalizedNameInefficiently
                // (mods such as Botania override it to change the key) + ".name", translated and trimmed. Style codes
                // in a lang value are not a naming difference, so both sides are compared without them.
                String inefficient = item.func_77657_g(stack);
                row.key = (inefficient == null ? "" : inefficient) + ".name";
                row.expected = TextFormatting.func_110646_a(I18n.func_74838_a(row.key)).trim();
                String raw = stack.func_82833_r();
                row.actual = TextFormatting.func_110646_a(raw == null ? "" : raw).trim();
                pathNanos += System.nanoTime() - before;
                row.consistent = row.expected.equals(row.actual);
                row.untranslated = row.expected.equals(row.key);
                row.overridesDisplayName = declares(item.getClass(), "func_77653_i", ItemStack.class) != Item.class;
                row.overridesInefficient = declares(item.getClass(), "func_77657_g", ItemStack.class) != Item.class;
                row.contains = !row.consistent && !row.expected.isEmpty() && row.actual.contains(row.expected);
            } catch (Throwable error) {
                row.error = error.getClass().getSimpleName();
                errors.merge(row.error, 1, Integer::sum);
            }
            for (Bucket bucket : new Bucket[] {cls, owner}) bucket.count(row, dictionaries, sampleLimit, stack, id);
            totals.count(row, dictionaries);
        }
        result.addProperty("standardPathMeanMicros", totals.stacks - totals.custom == 0 ? 0 : pathNanos / 1000.0 / (totals.stacks - totals.custom));
        result.add("totals", totals.report());
        result.add("errors", BridgeJson.counts(errors));
        result.add("byMod", Bucket.ranked(byMod, Integer.MAX_VALUE, sampleLimit));
        result.add("byClass", Bucket.ranked(byClass, 400, sampleLimit));
        result.addProperty("classes", byClass.size());
        if (fluids) result.add("fluids", fluids(dictionaries, sampleLimit));
        result.addProperty("elapsedMs", (System.nanoTime() - started) / 1_000_000);
        return result;
    }

    private static Class<?> declares(Class<?> type, String name, Class<?>... parameters) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { current.getDeclaredMethod(name, parameters); return current; }
            catch (NoSuchMethodException ignored) { /* keep climbing */ }
        }
        return null;
    }

    /** Fluids follow the same shape: {@code Fluid#getUnlocalizedName(stack)} translated against {@code getLocalizedName(stack)}. */
    private static JsonObject fluids(Map<String, Dictionary> dictionaries, int sampleLimit) {
        JsonObject report = new JsonObject();
        int total = 0, consistent = 0, untranslated = 0, errors = 0;
        Map<String, int[]> hits = new LinkedHashMap<>();
        for (String code : dictionaries.keySet()) hits.put(code, new int[2]);
        JsonArray samples = new JsonArray();
        for (Map.Entry<String, Fluid> entry : FluidRegistry.getRegisteredFluids().entrySet()) {
            total++;
            try {
                Fluid fluid = entry.getValue();
                FluidStack stack = new FluidStack(fluid, 1000);
                String key = fluid.getUnlocalizedName(stack);
                String expected = key == null ? "" : I18n.func_74838_a(key).trim();
                String actual = TextFormatting.func_110646_a(String.valueOf(fluid.getLocalizedName(stack))).trim();
                if (expected.equals(actual)) {
                    consistent++;
                    if (expected.equals(key)) untranslated++;
                    for (Map.Entry<String, Dictionary> dictionary : dictionaries.entrySet()) {
                        int[] counts = hits.get(dictionary.getKey());
                        if (dictionary.getValue().entries.containsKey(key)) counts[0]++; else counts[1]++;
                    }
                } else if (samples.size() < sampleLimit * 4) {
                    JsonObject sample = new JsonObject();
                    sample.addProperty("fluid", entry.getKey());
                    sample.addProperty("class", fluid.getClass().getName());
                    sample.addProperty("key", key);
                    sample.addProperty("expected", expected);
                    sample.addProperty("actual", actual);
                    samples.add(sample);
                }
            } catch (Throwable error) {
                errors++;
            }
        }
        report.addProperty("total", total);
        report.addProperty("consistent", consistent);
        report.addProperty("consistentUntranslated", untranslated);
        report.addProperty("inconsistent", total - consistent - errors);
        report.addProperty("errors", errors);
        JsonObject languages = new JsonObject();
        for (Map.Entry<String, int[]> entry : hits.entrySet()) {
            JsonObject counts = new JsonObject();
            counts.addProperty("hit", entry.getValue()[0]);
            counts.addProperty("miss", entry.getValue()[1]);
            languages.add(entry.getKey(), counts);
        }
        report.add("languages", languages);
        report.add("inconsistentSamples", samples);
        return report;
    }

    static final class Row {
        String key, expected, actual, error;
        boolean consistent, untranslated, overridesDisplayName, overridesInefficient, contains;
        String category() {
            if (error != null) return "error";
            if (consistent) return untranslated ? "consistentUntranslated" : "consistent";
            return overridesDisplayName ? "inconsistentOverride" : "inconsistentExternal";
        }
    }

    /** One secondary-language dictionary built like vanilla Locale: en_us first, then the language's own files on top. */
    static final class Dictionary {
        final String code;
        final Map<String, String> entries = new HashMap<>();
        final Set<String> ownKeys = new java.util.HashSet<>();
        int files, ownFiles, domains, failures;
        final Map<String, Integer> failureKinds = new TreeMap<>();

        private Dictionary(String code) { this.code = code; }

        static Dictionary load(IResourceManager manager, String code) {
            Dictionary dictionary = new Dictionary(code);
            List<String> chain = new ArrayList<>();
            if (!"en_us".equalsIgnoreCase(code)) chain.add("en_us");
            chain.add(code);
            Set<String> domains = manager.func_135055_a();
            dictionary.domains = domains.size();
            for (String language : chain) {
                boolean own = language.equalsIgnoreCase(code);
                for (String domain : domains) {
                    List<IResource> resources;
                    try { resources = manager.func_135056_b(new ResourceLocation(domain, "lang/" + language + ".lang")); }
                    catch (IOException missing) { continue; }
                    catch (RuntimeException error) { dictionary.failure(error); continue; }
                    for (IResource resource : resources) {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.func_110527_b(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.isEmpty() || line.charAt(0) == '#') continue;
                                int split = line.indexOf('=');
                                if (split < 0) continue;
                                String key = line.substring(0, split), value = NUMERIC_VARIANT.matcher(line.substring(split + 1)).replaceAll("%$1s");
                                dictionary.entries.put(key, value);
                                if (own) dictionary.ownKeys.add(key);
                            }
                            dictionary.files++;
                            if (own) dictionary.ownFiles++;
                        } catch (IOException | RuntimeException error) {
                            dictionary.failure(error);
                        } finally {
                            try { resource.close(); } catch (IOException ignored) { /* already drained */ }
                        }
                    }
                }
            }
            return dictionary;
        }

        private void failure(Throwable error) {
            failures++;
            failureKinds.merge(error.getClass().getSimpleName(), 1, Integer::sum);
        }

        JsonObject report() {
            JsonObject report = new JsonObject();
            report.addProperty("domains", domains);
            report.addProperty("files", files);
            report.addProperty("ownFiles", ownFiles);
            report.addProperty("entries", entries.size());
            report.addProperty("ownKeys", ownKeys.size());
            report.addProperty("failures", failures);
            report.add("failureKinds", BridgeJson.counts(failureKinds));
            return report;
        }
    }

    static final class Stacks {
        final List<ItemStack> stacks = new ArrayList<>();
        String source, error;

        static Stacks collect(String requested) {
            Stacks result = new Stacks();
            if (!requested.equals("creative") && Loader.isModLoaded("jei")) {
                try {
                    Collection<ItemStack> ingredients = mezz.jei.Internal.getIngredientRegistry().getAllIngredients(ItemStack.class);
                    result.stacks.addAll(ingredients);
                    result.source = "jei";
                    return result;
                } catch (Throwable error) {
                    result.error = "jei: " + error;
                }
            }
            for (Item item : Item.field_150901_e) {
                try {
                    NonNullList<ItemStack> variants = NonNullList.func_191196_a();
                    item.func_150895_a(CreativeTabs.field_78027_g, variants);
                    if (variants.isEmpty()) variants.add(new ItemStack(item));
                    result.stacks.addAll(variants);
                } catch (Throwable error) {
                    result.stacks.add(new ItemStack(item));
                }
            }
            result.source = "creative";
            return result;
        }
    }

    static final class Totals {
        final List<String> languages;
        int stacks, custom, consistent, consistentUntranslated, inconsistentOverride, inconsistentExternal, inconsistentContains, inconsistentKeyless, errors;
        final Map<String, int[]> perLanguage = new LinkedHashMap<>();

        Totals(List<String> languages) {
            this.languages = languages;
            for (String code : languages) perLanguage.put(code, new int[4]);
        }

        void count(Row row, Map<String, Dictionary> dictionaries) {
            switch (row.category()) {
                case "error": errors++; return;
                case "consistent": consistent++; break;
                case "consistentUntranslated": consistent++; consistentUntranslated++; break;
                case "inconsistentOverride": inconsistentOverride++; if (row.contains) inconsistentContains++; if (row.untranslated) inconsistentKeyless++; return;
                default: inconsistentExternal++; if (row.contains) inconsistentContains++; if (row.untranslated) inconsistentKeyless++; return;
            }
            languageHits(row, dictionaries, perLanguage);
        }

        /** [hit, exact (in the language's own files), miss, redundant (secondary text equals the primary)] over consistent rows. */
        static void languageHits(Row row, Map<String, Dictionary> dictionaries, Map<String, int[]> target) {
            for (Map.Entry<String, Dictionary> entry : dictionaries.entrySet()) {
                int[] counts = target.get(entry.getKey());
                String value = entry.getValue().entries.get(row.key);
                if (value == null) { counts[2]++; continue; }
                counts[0]++;
                if (entry.getValue().ownKeys.contains(row.key)) counts[1]++;
                if (value.trim().equals(row.actual)) counts[3]++;
            }
        }

        JsonObject report() {
            JsonObject report = new JsonObject();
            report.addProperty("stacks", stacks);
            report.addProperty("custom", custom);
            report.addProperty("consistent", consistent);
            report.addProperty("consistentUntranslated", consistentUntranslated);
            report.addProperty("inconsistent", inconsistentOverride + inconsistentExternal);
            report.addProperty("inconsistentOverride", inconsistentOverride);
            report.addProperty("inconsistentExternal", inconsistentExternal);
            report.addProperty("inconsistentContains", inconsistentContains);
            report.addProperty("inconsistentKeyless", inconsistentKeyless);
            report.addProperty("errors", errors);
            int measured = stacks - custom - errors;
            report.addProperty("consistentShare", measured == 0 ? 0 : consistent / (double) measured);
            report.add("languages", languageReport(perLanguage));
            return report;
        }

        static JsonObject languageReport(Map<String, int[]> counts) {
            JsonObject languages = new JsonObject();
            for (Map.Entry<String, int[]> entry : counts.entrySet()) {
                JsonObject language = new JsonObject();
                language.addProperty("hit", entry.getValue()[0]);
                language.addProperty("exact", entry.getValue()[1]);
                language.addProperty("fallbackOnly", entry.getValue()[0] - entry.getValue()[1]);
                language.addProperty("miss", entry.getValue()[2]);
                language.addProperty("redundant", entry.getValue()[3]);
                languages.add(entry.getKey(), language);
            }
            return languages;
        }
    }

    static final class Bucket {
        final String mod;
        int stacks, custom, consistent, consistentUntranslated, inconsistentOverride, inconsistentExternal, inconsistentContains, inconsistentKeyless, errors;
        final Map<String, int[]> perLanguage = new LinkedHashMap<>();
        final JsonArray samples = new JsonArray();

        Bucket(String mod, List<String> languages) {
            this.mod = mod;
            for (String code : languages) perLanguage.put(code, new int[4]);
        }

        int inconsistent() { return inconsistentOverride + inconsistentExternal; }

        /** Composite (the standard name is embedded in the actual one) and keyless (the key has no lang entry at all) shares. */
        private void inconsistent(Row row) {
            if (row.contains) inconsistentContains++;
            if (row.untranslated) inconsistentKeyless++;
        }

        void count(Row row, Map<String, Dictionary> dictionaries, int sampleLimit, ItemStack stack, ResourceLocation id) {
            switch (row.category()) {
                case "error": errors++; break;
                case "consistent": consistent++; Totals.languageHits(row, dictionaries, perLanguage); break;
                case "consistentUntranslated": consistent++; consistentUntranslated++; Totals.languageHits(row, dictionaries, perLanguage); break;
                case "inconsistentOverride": inconsistentOverride++; inconsistent(row); break;
                default: inconsistentExternal++; inconsistent(row); break;
            }
            if (row.consistent || samples.size() >= sampleLimit) return;
            JsonObject sample = new JsonObject();
            sample.addProperty("item", String.valueOf(id));
            sample.addProperty("meta", stack.func_77960_j());
            sample.addProperty("tag", stack.func_77942_o() ? String.valueOf(stack.func_77978_p()) : null);
            sample.addProperty("key", row.key);
            sample.addProperty("expected", row.expected);
            sample.addProperty("actual", row.actual);
            sample.addProperty("category", row.category());
            sample.addProperty("overridesInefficient", row.overridesInefficient);
            sample.addProperty("contains", row.contains);
            if (row.error != null) sample.addProperty("error", row.error);
            samples.add(sample);
        }

        JsonObject report(String name) {
            JsonObject report = new JsonObject();
            report.addProperty("name", name);
            report.addProperty("mod", mod);
            report.addProperty("stacks", stacks);
            report.addProperty("custom", custom);
            report.addProperty("consistent", consistent);
            report.addProperty("consistentUntranslated", consistentUntranslated);
            report.addProperty("inconsistent", inconsistent());
            report.addProperty("inconsistentOverride", inconsistentOverride);
            report.addProperty("inconsistentExternal", inconsistentExternal);
            report.addProperty("inconsistentContains", inconsistentContains);
            report.addProperty("inconsistentKeyless", inconsistentKeyless);
            report.addProperty("errors", errors);
            report.add("languages", Totals.languageReport(perLanguage));
            report.add("samples", samples);
            return report;
        }

        /** Buckets with the most inconsistent stacks first, then by errors, then by size; limited to {@code limit} entries. */
        static JsonArray ranked(Map<String, Bucket> buckets, int limit, int sampleLimit) {
            List<Map.Entry<String, Bucket>> entries = new ArrayList<>(buckets.entrySet());
            entries.sort((left, right) -> {
                int byInconsistent = Integer.compare(right.getValue().inconsistent(), left.getValue().inconsistent());
                if (byInconsistent != 0) return byInconsistent;
                int byErrors = Integer.compare(right.getValue().errors, left.getValue().errors);
                if (byErrors != 0) return byErrors;
                int bySize = Integer.compare(right.getValue().stacks, left.getValue().stacks);
                return bySize != 0 ? bySize : left.getKey().compareTo(right.getKey());
            });
            JsonArray array = new JsonArray();
            for (Map.Entry<String, Bucket> entry : entries) {
                if (array.size() >= limit) break;
                array.add(entry.getValue().report(entry.getKey()));
            }
            return array;
        }
    }

    static final class BridgeJson {
        static JsonObject counts(Map<String, Integer> counts) {
            JsonObject object = new JsonObject();
            for (Map.Entry<String, Integer> entry : counts.entrySet()) object.addProperty(entry.getKey(), entry.getValue());
            return object;
        }
    }
}
