package com.starskyxiii.polyglottooltip.testbridge.nametable;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Research-only immutable table. Inputs are frozen JSON; this class has no Minecraft callbacks. */
public final class NativeNameTable {
    public static final String SCHEMA = "polyglot-native-name-table-prototype-v1";
    public static final int MAX_STATE_BYTES = 1_048_576, MAX_ENTRIES = 250_000;
    public static final long MAX_FILE_BYTES = 1_500_000_000L;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    public enum Status { HIT, LITERAL, MISS, CONFLICT, REJECTED, STALE_ENVIRONMENT, PRIMARY_MISMATCH, INVALID_INPUT }

    public static final class Result {
        public final Status status;
        public final String name, reason;
        Result(Status status, String name, String reason) { this.status = status; this.name = name; this.reason = reason; }
        public JsonObject json() {
            JsonObject value = new JsonObject(); value.addProperty("status", status.name());
            if (name != null) value.addProperty("name", name);
            if (reason != null) value.addProperty("reason", reason);
            return value;
        }
    }
    private static final class Cell {
        final String primary, name, rejection;
        Cell(String primary, String name, String rejection) { this.primary = primary; this.name = name; this.rejection = rejection; }
        boolean same(Cell other) { return Objects.equals(primary, other.primary) && Objects.equals(name, other.name) && Objects.equals(rejection, other.rejection); }
    }
    private final String environment, primaryLanguage;
    private final Map<String, Map<String, Cell>> entries;
    private final Set<String> languages;
    private final String sourceDigest;

    private NativeNameTable(Builder builder) {
        environment = builder.environment; primaryLanguage = builder.primaryLanguage; sourceDigest = builder.sourceDigest;
        languages = Collections.unmodifiableSet(new TreeSet<>(builder.languages));
        Map<String, Map<String, Cell>> values = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Cell>> entry : builder.entries.entrySet())
            values.put(entry.getKey(), Collections.unmodifiableMap(new TreeMap<>(entry.getValue())));
        entries = Collections.unmodifiableMap(values);
    }

    public static final class Builder {
        private final String environment, primaryLanguage, sourceDigest;
        private final Set<String> languages = new TreeSet<>();
        private final Map<String, Map<String, Cell>> entries = new LinkedHashMap<>();
        public Builder(String environment, String primaryLanguage, Collection<String> languages, String sourceDigest) {
            if (environment == null || !environment.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Environment must be SHA-256");
            if (sourceDigest == null || !sourceDigest.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Source digest must be SHA-256");
            language(primaryLanguage);
            if (languages == null || languages.isEmpty() || languages.size() > 8) throw new IllegalArgumentException("Use 1..8 target languages");
            for (String code : languages) { language(code); if (!this.languages.add(code)) throw new IllegalArgumentException("Duplicate language"); }
            this.environment = environment; this.primaryLanguage = primaryLanguage; this.sourceDigest = sourceDigest;
        }
        /** Rejections and conflicts are sticky. Later successful observations cannot rehabilitate them. */
        public void observe(JsonObject state, String primary, String language, String name, String rejection) {
            observeKey(key(state), primary, language, name, rejection);
        }
        private void observeKey(String key, String primary, String language, String name, String rejection) {
            if (!languages.contains(language)) throw new IllegalArgumentException("Undeclared language");
            if (rejection != null && (rejection.isEmpty() || rejection.length() > 256)) throw new IllegalArgumentException("Invalid rejection");
            if (rejection == null && (!validName(primary) || !validName(name))) rejection = "empty-or-invalid-native-name";
            if (primary != null && primary.length() > 32768) primary = null;
            if (name != null && name.length() > 32768) name = null;
            Map<String, Cell> row = entries.get(key);
            if (row == null) {
                if (entries.size() >= MAX_ENTRIES) throw new IllegalArgumentException("Table entry limit");
                row = new TreeMap<>(); entries.put(key, row);
            }
            Cell next = new Cell(primary, name, rejection), old = row.get(language);
            if (old == null) row.put(language, next);
            else if (!old.same(next)) {
                if ("conflicting-native-observations".equals(old.rejection)) return;
                if (old.rejection != null) return;
                row.put(language, new Cell(null, null, rejection == null ? "conflicting-native-observations" : rejection));
            }
        }
        public NativeNameTable build() { return new NativeNameTable(this); }
    }

    public Result lookup(JsonObject state, String primary, String target, String observedEnvironment, boolean contextCurrent) {
        if (!contextCurrent || !environment.equals(observedEnvironment)) return result(Status.STALE_ENVIRONMENT, "activation-context-changed");
        try {
            String key = key(state);
            if (!validName(primary)) return result(Status.INVALID_INPUT, "invalid-primary");
            if (literal(state)) return new Result(Status.LITERAL, primary, "caller-observed-custom-name");
            return lookupKey(key, primary, target);
        } catch (IllegalArgumentException error) { return result(Status.INVALID_INPUT, error.getMessage()); }
    }

    /** Only for already-validated frozen keys and an independently checked activation; benchmark separately. */
    public Result lookupKey(String key, String primary, String target) {
        if (!languages.contains(target)) return result(Status.MISS, "language-not-collected");
        Map<String, Cell> row = entries.get(key);
        Cell cell = row == null ? null : row.get(target);
        if (cell == null) return result(Status.MISS, "exact-state-not-collected");
        if (cell.rejection != null) return result("conflicting-native-observations".equals(cell.rejection) ? Status.CONFLICT : Status.REJECTED, cell.rejection);
        if (!Objects.equals(primary, cell.primary)) return result(Status.PRIMARY_MISMATCH, "native-primary-changed");
        return new Result(Status.HIT, cell.name, null);
    }
    private static Result result(Status status, String reason) { return new Result(status, null, reason); }
    private static boolean validName(String value) { return value != null && !value.trim().isEmpty() && value.length() <= 32768; }
    private static void language(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9_]{1,31}")) throw new IllegalArgumentException("Invalid language");
    }

    public String environment() { return environment; }
    public String primaryLanguage() { return primaryLanguage; }
    public Set<String> languages() { return languages; }
    public int size() { return entries.size(); }
    public JsonObject statistics() {
        JsonObject result = new JsonObject(), reasons = new JsonObject();
        long pairs = 0, accepted = 0, same = 0;
        for (Map<String, Cell> row : entries.values()) for (Cell cell : row.values()) {
            pairs++;
            if (cell.rejection == null) { accepted++; if (cell.primary.equals(cell.name)) same++; }
            else reasons.addProperty(cell.rejection, reasons.has(cell.rejection) ? reasons.get(cell.rejection).getAsLong() + 1 : 1);
        }
        result.addProperty("states", size()); result.addProperty("pairs", pairs); result.addProperty("admittedPairs", accepted);
        result.addProperty("sameAsPrimaryPairs", same); result.addProperty("differentFromPrimaryPairs", accepted - same);
        result.add("rejections", reasons); return result;
    }

    /** Full canonical string is the map key; hashing is not used as a lossy equality substitute. */
    public static String key(JsonObject state) {
        if (state == null) throw new IllegalArgumentException("Missing state");
        String item = string(state, "item");
        if (!item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || item.length() > 512) throw new IllegalArgumentException("Invalid registry item");
        int metadata = integer(state, "metadata"), damage = integer(state, "damage"), count = integer(state, "count");
        if (count < 1) throw new IllegalArgumentException("Empty stack");
        JsonObject stack = object(state.get("stack"));
        JsonObject canonicalNbt = nbt(stack, 0, new int[]{32768});
        if (integer(canonicalNbt, "type") != 10) throw new IllegalArgumentException("Expected compound root");
        JsonObject fields = canonicalNbt.getAsJsonObject("value"), id = object(fields.get("id"));
        if (integer(id, "type") != 8 || !item.equals(string(id, "value"))) throw new IllegalArgumentException("Item/root identity mismatch");
        JsonObject value = new JsonObject();
        value.addProperty("item", item); value.addProperty("metadata", metadata); value.addProperty("damage", damage); value.addProperty("count", count);
        value.add("stack", canonicalNbt);
        String key = GSON.toJson(value);
        if (key.getBytes(StandardCharsets.UTF_8).length > MAX_STATE_BYTES) throw new IllegalArgumentException("Canonical state size limit");
        return key;
    }
    public static boolean literal(JsonObject state) {
        JsonObject root = object(state.get("stack")).getAsJsonObject("value");
        JsonObject tag = childCompound(root, "tag"), display = tag == null ? null : childCompound(tag, "display");
        if (display == null || !display.has("Name")) return false;
        JsonObject name = object(display.get("Name"));
        return integer(name, "type") == 8;
    }
    private static JsonObject childCompound(JsonObject parent, String field) {
        if (!parent.has(field)) return null;
        JsonObject node = object(parent.get(field));
        return integer(node, "type") == 10 ? object(node.get("value")) : null;
    }

    private static JsonObject nbt(JsonObject value, int depth, int[] budget) {
        if (depth > 64 || --budget[0] < 0) throw new IllegalArgumentException("NBT complexity limit");
        int type = integer(value, "type");
        if (type < 1 || type > 12) throw new IllegalArgumentException("Invalid NBT type");
        Set<String> fields = new HashSet<>(Arrays.asList(type == 9 ? new String[]{"type", "elementType", "value"} : new String[]{"type", "value"}));
        if (!fields(value).equals(fields)) throw new IllegalArgumentException("Unexpected typed NBT fields");
        JsonObject result = new JsonObject(); result.addProperty("type", type);
        if (type <= 8) {
            String data = string(value, "value");
            if (data.length() > MAX_STATE_BYTES) throw new IllegalArgumentException("NBT scalar size limit");
            if (type <= 4) {
                try {
                    long number = Long.parseLong(data);
                    if (!data.equals(Long.toString(number)) || type == 1 && (number < -128 || number > 127)
                            || type == 2 && (number < -32768 || number > 32767)
                            || type == 3 && (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE)) throw new NumberFormatException();
                } catch (NumberFormatException error) { throw new IllegalArgumentException("Invalid integer NBT"); }
            } else if (type == 5 || type == 6) {
                if (!data.matches(type == 5 ? "[0-9a-f]{8}" : "[0-9a-f]{16}")) throw new IllegalArgumentException("Invalid floating bits");
            } else if (type == 7) {
                try { if (!Base64.getEncoder().encodeToString(Base64.getDecoder().decode(data)).equals(data)) throw new IllegalArgumentException(); }
                catch (IllegalArgumentException error) { throw new IllegalArgumentException("Invalid byte array"); }
            }
            result.addProperty("value", data);
        } else if (type == 10) {
            JsonObject data = object(value.get("value")), sorted = new JsonObject();
            if (data.size() > budget[0]) throw new IllegalArgumentException("NBT complexity limit");
            for (String field : new TreeSet<>(fields(data))) sorted.add(field, nbt(object(data.get(field)), depth + 1, budget));
            result.add("value", sorted);
        } else {
            JsonArray data = array(value.get("value")), values = new JsonArray();
            if (data.size() > budget[0]) throw new IllegalArgumentException("NBT array limit");
            int element = type == 9 ? integer(value, "elementType") : 0;
            if (type == 9) {
                if (element < 0 || element > 12 || element == 0 && data.size() != 0) throw new IllegalArgumentException("Invalid list type");
                result.addProperty("elementType", element);
            }
            for (JsonElement child : data) {
                if (type == 9) {
                    JsonObject node = nbt(object(child), depth + 1, budget);
                    if (integer(node, "type") != element) throw new IllegalArgumentException("Mixed NBT list");
                    values.add(node);
                } else {
                    if (--budget[0] < 0) throw new IllegalArgumentException("NBT complexity limit");
                    JsonObject scalar = new JsonObject(); scalar.addProperty("type", type == 11 ? 3 : 4); scalar.add("value", child);
                    values.add(nbt(scalar, depth + 1, budget).get("value"));
                }
            }
            result.add("value", values);
        }
        return result;
    }

    public void save(Path path) throws IOException {
        if (Files.exists(path)) throw new IOException("Refuse to overwrite table");
        MessageDigest digest = digest();
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
            JsonObject header = new JsonObject(); header.addProperty("schema", SCHEMA); header.addProperty("environment", environment);
            header.addProperty("primaryLanguage", primaryLanguage); header.add("languages", GSON.toJsonTree(languages)); header.addProperty("sourceDigest", sourceDigest);
            write(writer, header, digest);
            for (Map.Entry<String, Map<String, Cell>> entry : entries.entrySet()) {
                JsonObject row = new JsonObject(), targets = new JsonObject(); row.addProperty("key", entry.getKey());
                for (Map.Entry<String, Cell> target : entry.getValue().entrySet()) {
                    Cell cell = target.getValue(); JsonObject value = new JsonObject();
                    if (cell.primary != null) value.addProperty("primary", cell.primary);
                    if (cell.name != null) value.addProperty("name", cell.name);
                    if (cell.rejection != null) value.addProperty("rejection", cell.rejection);
                    targets.add(target.getKey(), value);
                }
                row.add("targets", targets); write(writer, row, digest);
            }
            JsonObject footer = new JsonObject(); footer.addProperty("end", true); footer.addProperty("rows", size());
            footer.addProperty("sha256", hex(digest.digest())); writer.write(GSON.toJson(footer)); writer.write('\n');
        }
        if (Files.size(path) > MAX_FILE_BYTES) throw new IOException("Table file exceeds size limit");
    }

    public static NativeNameTable load(Path path) throws IOException {
        if (Files.size(path) > MAX_FILE_BYTES) throw new IOException("Table file exceeds size limit");
        MessageDigest digest = digest();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line = boundedLine(reader); if (line == null) throw new IOException("Missing header");
            JsonObject header = parse(line); update(digest, line);
            requireFields(header, "schema", "environment", "primaryLanguage", "languages", "sourceDigest");
            if (!SCHEMA.equals(string(header, "schema"))) throw new IOException("Wrong schema");
            List<String> languages = new ArrayList<>(); for (JsonElement value : array(header.get("languages"))) languages.add(value.getAsString());
            Builder builder = new Builder(string(header, "environment"), string(header, "primaryLanguage"), languages, string(header, "sourceDigest"));
            int rows = 0; Set<String> seen = new HashSet<>();
            while ((line = boundedLine(reader)) != null) {
                JsonObject row = parse(line);
                if (row.has("end")) {
                    requireFields(row, "end", "rows", "sha256");
                    if (!row.get("end").getAsBoolean() || integer(row, "rows") != rows || !hex(digest.digest()).equals(string(row, "sha256"))
                            || boundedLine(reader) != null) throw new IOException("Invalid table footer or payload digest");
                    return builder.build();
                }
                if (++rows > MAX_ENTRIES) throw new IOException("Too many table rows");
                requireFields(row, "key", "targets");
                update(digest, line); String key = string(row, "key");
                if (!key(parse(key)).equals(key) || !seen.add(key)) throw new IOException("Noncanonical or duplicate table key");
                JsonObject targets = object(row.get("targets"));
                if (targets.size() < 1 || targets.size() > languages.size()) throw new IOException("Invalid table targets");
                for (Map.Entry<String, JsonElement> entry : targets.entrySet()) {
                    JsonObject value = object(entry.getValue());
                    if (!new HashSet<>(Arrays.asList("primary", "name", "rejection")).containsAll(fields(value))) throw new IOException("Unexpected persisted cell field");
                    String primary = optional(value, "primary"), name = optional(value, "name"), rejection = optional(value, "rejection");
                    if (primary != null && primary.length() > 32768 || name != null && name.length() > 32768
                            || rejection == null && (!validName(primary) || !validName(name))) throw new IOException("Malformed persisted cell");
                    builder.observeKey(key, primary, entry.getKey(), name, rejection);
                }
            }
            throw new IOException("Truncated table: no footer");
        } catch (IllegalArgumentException | IllegalStateException error) { throw new IOException("Invalid table: " + error.getMessage(), error); }
    }
    private static void write(BufferedWriter writer, JsonObject value, MessageDigest digest) throws IOException {
        String line = GSON.toJson(value); writer.write(line); writer.write('\n'); update(digest, line);
    }
    private static void update(MessageDigest digest, String line) { digest.update((line + "\n").getBytes(StandardCharsets.UTF_8)); }
    public static String boundedLine(BufferedReader reader) throws IOException {
        StringBuilder value = new StringBuilder();
        for (int ch; (ch = reader.read()) != -1;) {
            if (ch == '\n') return value.toString();
            if (value.length() >= MAX_STATE_BYTES * 3) throw new IOException("Input line limit");
            value.append((char) ch);
        }
        if (value.length() != 0) throw new IOException("Unterminated final line");
        return null;
    }
    /** Strict bounded parsing, including duplicate-member rejection before Gson can overwrite a member. */
    public static JsonObject parse(String value) {
        return parseBounded(value, MAX_STATE_BYTES * 3);
    }
    /** Environment inventories have many small file records; state/table-row limits stay unchanged. */
    public static JsonObject parseEnvironmentManifest(String value) {
        return parseBounded(value, 16_777_216);
    }
    private static JsonObject parseBounded(String value, int maxCharacters) {
        if (value == null || value.length() > maxCharacters) throw new IllegalArgumentException("JSON input limit");
        try (JsonReader reader = new JsonReader(new StringReader(value))) {
            reader.setLenient(false);
            JsonObject result = object(parseValue(reader, 0, new int[]{262144}));
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing JSON input");
            return result;
        } catch (IOException error) { throw new IllegalArgumentException("Malformed JSON", error); }
    }
    private static JsonElement parseValue(JsonReader reader, int depth, int[] budget) throws IOException {
        if (depth > 160 || --budget[0] < 0) throw new IllegalArgumentException("JSON complexity limit");
        switch (reader.peek()) {
            case BEGIN_OBJECT: {
                JsonObject result = new JsonObject(); reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (result.has(name)) throw new IllegalArgumentException("Duplicate JSON member");
                    result.add(name, parseValue(reader, depth + 1, budget));
                }
                reader.endObject(); return result;
            }
            case BEGIN_ARRAY: {
                JsonArray result = new JsonArray(); reader.beginArray();
                while (reader.hasNext()) result.add(parseValue(reader, depth + 1, budget));
                reader.endArray(); return result;
            }
            case STRING: return new JsonPrimitive(reader.nextString());
            case NUMBER: {
                String number = reader.nextString();
                if (number.length() > 64) throw new IllegalArgumentException("JSON number limit");
                return new JsonPrimitive(new java.math.BigDecimal(number));
            }
            case BOOLEAN: return new JsonPrimitive(reader.nextBoolean());
            case NULL: reader.nextNull(); return JsonNull.INSTANCE;
            default: throw new IllegalArgumentException("Unexpected JSON token");
        }
    }
    public static String sha256(byte[] bytes) { return hex(digest().digest(bytes)); }
    public static String sha256(Path path) throws IOException {
        MessageDigest digest = digest(); byte[] bytes = new byte[65536];
        try (InputStream input = Files.newInputStream(path)) { for (int size; (size = input.read(bytes)) != -1;) digest.update(bytes, 0, size); }
        return hex(digest.digest());
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String hex(byte[] bytes) { StringBuilder out = new StringBuilder(); for (byte value : bytes) out.append(String.format(Locale.ROOT, "%02x", value & 255)); return out.toString(); }
    private static JsonObject object(JsonElement value) { if (value == null || !value.isJsonObject()) throw new IllegalArgumentException("Expected object"); return value.getAsJsonObject(); }
    private static JsonArray array(JsonElement value) { if (value == null || !value.isJsonArray()) throw new IllegalArgumentException("Expected array"); return value.getAsJsonArray(); }
    private static Set<String> fields(JsonObject value) { Set<String> names = new HashSet<>(); for (Map.Entry<String, JsonElement> entry : value.entrySet()) names.add(entry.getKey()); return names; }
    private static void requireFields(JsonObject value, String... names) { if (!fields(value).equals(new HashSet<>(Arrays.asList(names)))) throw new IllegalArgumentException("Unexpected JSON fields"); }
    private static String string(JsonObject value, String field) {
        JsonElement element = value.get(field);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected string: " + field);
        return element.getAsString();
    }
    private static String optional(JsonObject value, String field) { return value.has(field) ? string(value, field) : null; }
    private static int integer(JsonObject value, String field) {
        JsonElement element = value.get(field);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Expected integer: " + field);
        try { return new java.math.BigDecimal(element.getAsString()).intValueExact(); }
        catch (ArithmeticException | NumberFormatException error) { throw new IllegalArgumentException("Invalid integer: " + field); }
    }
}
