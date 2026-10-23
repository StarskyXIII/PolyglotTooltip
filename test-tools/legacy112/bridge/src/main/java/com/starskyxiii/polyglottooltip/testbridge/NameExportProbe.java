package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;

import java.io.BufferedWriter;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes every recipe-viewer (or creative) item stack's primary name and, for each loaded secondary language, the name
 * the minimal-core product resolves, to a UTF-8 TSV under {@code polyglottooltip-export/} in the game directory. One row
 * per stack and language: which layer or resolver produced the name, whether the tooltip shows it, and why a language
 * has no name. A translation-diagnostics tool; the product itself has no export.
 */
final class NameExportProbe {
    static final String DIRECTORY = "polyglottooltip-export";
    static final String HEADER = "index\tid\tmetadata\tnbt\tclass\tkey\tprimary\tstandard\tconsistent\tsource\tlanguage\tsecondary\tshown\tstatus\tdetail";

    private NameExportProbe() { }

    static JsonObject run(JsonObject params) throws Exception {
        long started = System.nanoTime();
        String file = params.has("file") ? params.get("file").getAsString()
                : "names-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".tsv";
        if (!file.matches("[A-Za-z0-9_-]{1,80}\\.tsv")) throw new IllegalArgumentException("Invalid export file name");
        int limit = params.has("limit") ? params.get("limit").getAsInt() : Integer.MAX_VALUE;
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        Path directory = new File(Minecraft.func_71410_x().field_71412_D, DIRECTORY).toPath();
        Path target = directory.resolve(file), partial = directory.resolve(file + ".partial");
        if (Files.exists(target) || Files.exists(partial)) throw new IllegalArgumentException("Export file already exists: " + target);

        Product product = new Product();
        Map<?, ?> dictionaries = product.dictionaries();
        List<String> languages = new ArrayList<>();
        for (Object code : dictionaries.keySet()) languages.add(String.valueOf(code));
        ConsistencyProbe.Stacks stacks = ConsistencyProbe.Stacks.collect(params.has("source") ? params.get("source").getAsString() : "auto");
        Map<String, Integer> statuses = new TreeMap<>(), sources = new TreeMap<>();
        int written = 0, rows = 0;
        Files.createDirectories(directory);
        try (BufferedWriter out = Files.newBufferedWriter(partial, StandardCharsets.UTF_8)) {
            out.write(HEADER);
            out.write('\n');
            for (ItemStack stack : stacks.stacks) {
                if (written >= limit) break;
                for (Row row : rows(product, stack, dictionaries, languages)) {
                    out.write(String.valueOf(written));
                    for (String cell : row.cells()) {
                        out.write('\t');
                        out.write(escape(cell));
                    }
                    out.write('\n');
                    statuses.merge(row.language + ":" + row.status, 1, Integer::sum);
                    rows++;
                }
                sources.merge(String.valueOf(product.lastSource), 1, Integer::sum);
                written++;
            }
        }
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        JsonObject result = new JsonObject();
        result.addProperty("path", target.toAbsolutePath().toString());
        result.addProperty("source", stacks.source);
        result.addProperty("sourceError", stacks.error);
        result.addProperty("currentLanguage", Minecraft.func_71410_x().field_71474_y.field_74363_ab);
        result.add("languages", new com.google.gson.Gson().toJsonTree(languages));
        result.addProperty("available", stacks.stacks.size());
        result.addProperty("stacks", written);
        result.addProperty("rows", rows);
        result.add("byStatus", ConsistencyProbe.BridgeJson.counts(statuses));
        result.add("bySource", ConsistencyProbe.BridgeJson.counts(sources));
        result.addProperty("elapsedMs", (System.nanoTime() - started) / 1_000_000);
        return result;
    }

    private static List<Row> rows(Product product, ItemStack stack, Map<?, ?> dictionaries, List<String> languages) {
        Row base = new Row();
        product.lastSource = "error";
        try {
            Item item = stack.func_77973_b();
            ResourceLocation id = item == null ? null : item.getRegistryName();
            base.id = String.valueOf(id);
            base.metadata = String.valueOf(stack.func_77960_j());
            base.nbt = stack.func_77942_o() ? String.valueOf(stack.func_77978_p()) : "";
            base.type = item == null ? "" : item.getClass().getName();
            if (stack.func_82837_s()) {
                base.primary = TextFormatting.func_110646_a(stack.func_82833_r()).trim();
                product.lastSource = "custom";
                return perLanguage(base, languages, language -> base.with(language, "", false, "custom", ""));
            }
            Object result = product.resolve(stack, dictionaries);
            String key = (String) product.key.invoke(result);
            base.key = key == null ? "" : key;
            base.primary = String.valueOf(product.primary.invoke(result));
            Object expected = product.expected.invoke(result);
            base.standard = expected == null ? "" : String.valueOf(expected);
            boolean handOff = (Boolean) product.handOff.invoke(result);
            base.consistent = String.valueOf(!handOff);
            Map<?, ?> names;
            if (!handOff) {
                names = (Map<?, ?>) product.names.invoke(result);
                base.source = "standard";
            } else {
                Object resolution = product.resolution(stack, result, dictionaries);
                names = (Map<?, ?>) product.resolutionNames.invoke(resolution);
                Object resolver = product.resolver.invoke(resolution);
                base.source = resolver == null ? "" : resolver.getClass().getSimpleName();
            }
            List<?> shown = product.shown(stack);
            product.lastSource = base.source.isEmpty() ? "none" : base.source;
            Map<?, ?> resolved = names == null ? Collections.emptyMap() : names;
            return perLanguage(base, languages, language -> {
                Object name = resolved.get(language);
                if (name != null) {
                    String secondary = String.valueOf(name).trim();
                    String plain = TextFormatting.func_110646_a(secondary).trim();
                    return base.with(language, secondary, shown.contains(secondary),
                            plain.equals(base.primary) ? "same" : "translated", "");
                }
                if (!handOff) return base.with(language, "", false, "missingKey", "");
                if (key == null) return base.with(language, "", false, "noKey", "");
                return base.with(language, "", false, base.source.isEmpty() ? "unresolved" : "resolverMissing", "");
            });
        } catch (Throwable error) {
            Throwable cause = error instanceof InvocationTargetException ? error.getCause() : error;
            String detail = cause.getClass().getName();
            product.lastSource = "error";
            return perLanguage(base, languages, language -> base.with(language, "", false, "error", detail));
        }
    }

    private interface RowFactory { Row make(String language); }

    private static List<Row> perLanguage(Row base, List<String> languages, RowFactory factory) {
        List<Row> rows = new ArrayList<>();
        if (languages.isEmpty()) rows.add(base.with("", "", false, "noLanguage", ""));
        else for (String language : languages) rows.add(factory.make(language));
        return rows;
    }

    static String escape(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\': out.append("\\\\"); break;
                case '\t': out.append("\\t"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                default: out.append(character);
            }
        }
        return out.toString();
    }

    private static final class Row {
        String id = "", metadata = "", nbt = "", type = "", key = "", primary = "", standard = "", consistent = "", source = "";
        String language = "", secondary = "", status = "", detail = "";
        boolean shown;

        Row with(String language, String secondary, boolean shown, String status, String detail) {
            Row row = new Row();
            row.id = id; row.metadata = metadata; row.nbt = nbt; row.type = type; row.key = key; row.primary = primary;
            row.standard = standard; row.consistent = consistent; row.source = source;
            row.language = language; row.secondary = secondary; row.shown = shown; row.status = status; row.detail = detail;
            return row;
        }

        String[] cells() {
            return new String[]{id, metadata, nbt, type, key, primary, standard, consistent, source, language, secondary,
                    String.valueOf(shown), status, detail};
        }
    }

    private static final class Product {
        final Method of, shownNames, check, key, primary, expected, handOff, names, resolutionOf, resolutionNames, resolver, dictionaries;
        String lastSource;

        Product() throws ReflectiveOperationException {
            String base = ProductNames.PACKAGE;
            ClassLoader loader = Product.class.getClassLoader();
            Class<?> itemNames = Class.forName(base + "ItemNames", true, loader);
            Class<?> secondary = Class.forName(base + "SecondaryNames", true, loader);
            Class<?> consistency = Class.forName(base + "ConsistencyCheck", true, loader);
            Class<?> result = Class.forName(base + "ConsistencyCheck$Result", true, loader);
            Class<?> resolvers = Class.forName(base + "SpecialItemNameResolvers", true, loader);
            Class<?> resolution = Class.forName(base + "SpecialItemNameResolvers$Resolution", true, loader);
            of = itemNames.getMethod("of", ItemStack.class);
            shownNames = secondary.getMethod("names");
            check = consistency.getMethod("resolve", ItemStack.class, Map.class);
            key = result.getMethod("key");
            primary = result.getMethod("primary");
            expected = result.getMethod("expected");
            handOff = result.getMethod("handOff");
            names = result.getMethod("names");
            resolutionOf = resolvers.getMethod("resolution", ItemStack.class, result, Map.class);
            resolutionNames = resolution.getMethod("names");
            resolver = resolution.getMethod("resolver");
            dictionaries = Class.forName(base + "SecondaryLanguages", true, loader).getMethod("dictionaries");
        }

        Map<?, ?> dictionaries() throws ReflectiveOperationException { return (Map<?, ?>) dictionaries.invoke(null); }

        Object resolve(ItemStack stack, Map<?, ?> dictionaries) throws ReflectiveOperationException {
            return check.invoke(null, stack, dictionaries);
        }

        Object resolution(ItemStack stack, Object result, Map<?, ?> dictionaries) throws ReflectiveOperationException {
            return resolutionOf.invoke(null, stack, result, dictionaries);
        }

        List<?> shown(ItemStack stack) throws ReflectiveOperationException {
            return (List<?>) shownNames.invoke(of.invoke(null, stack));
        }
    }
}
