package com.starskyxiii.polyglottooltip.testbridge.nametable;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class NativeNameTableTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String ENV = digest('a'), SOURCE = digest('b');
    private static String digest(char value) { char[] chars = new char[64]; Arrays.fill(chars, value); return new String(chars); }
    private static JsonObject state() {
        return NativeNameTable.parse("{\"item\":\"minecraft:stone\",\"metadata\":0,\"damage\":0,\"count\":1,\"stack\":{\"type\":10,\"value\":{\"id\":{\"type\":8,\"value\":\"minecraft:stone\"},\"Count\":{\"type\":1,\"value\":\"1\"},\"Damage\":{\"type\":2,\"value\":\"0\"}}}}");
    }
    private static NativeNameTable.Builder builder() { return new NativeNameTable.Builder(ENV, "zh_tw", Arrays.asList("en_us", "zh_cn"), SOURCE); }
    private static NativeNameTable table() {
        NativeNameTable.Builder builder = builder(); builder.observe(state(), "石頭", "en_us", "Stone", null); builder.observe(state(), "石頭", "zh_cn", "石头", null); return builder.build();
    }
    private static NativeNameTable.Result lookup(NativeNameTable table, JsonObject state) { return table.lookup(state, "石頭", "en_us", ENV, true); }
    private Path target(String name) { return temporary.getRoot().toPath().resolve(name); }
    private static JsonObject scalar(int type, String value) { JsonObject result = new JsonObject(); result.addProperty("type", type); result.addProperty("value", value); return result; }
    private static JsonObject compound(JsonObject value) { JsonObject result = new JsonObject(); result.addProperty("type", 10); result.add("value", value); return result; }
    private static JsonObject fields(JsonObject value) { return value.getAsJsonObject("stack").getAsJsonObject("value"); }
    private static void fails(Runnable action) { try { action.run(); fail("Expected rejection"); } catch (IllegalArgumentException expected) { } }
    private void badFile(Path path) throws Exception { try { NativeNameTable.load(path); fail("Expected malformed persistence rejection"); } catch (IOException expected) { } }

    @Test public void environmentManifestLimitDoesNotRelaxOrdinaryRowsOrDuplicateChecks() {
        StringBuilder large = new StringBuilder("{\"inventory\":\"");
        for (int index = 0; index < 3_200_000; index++) large.append('a');
        large.append("\"}");
        String value = large.toString();
        fails(() -> NativeNameTable.parse(value));
        assertEquals(3_200_000, NativeNameTable.parseEnvironmentManifest(value).get("inventory").getAsString().length());
        fails(() -> NativeNameTable.parseEnvironmentManifest("{\"files\":[],\"files\":[]}"));
        char[] excessive = new char[16_777_217];
        fails(() -> NativeNameTable.parseEnvironmentManifest(new String(excessive)));
    }
    @Test public void compoundOrderingDoesNotChangeExactKey() {
        JsonObject value = state(), root = fields(value), reversed = new JsonObject();
        reversed.add("Damage", root.get("Damage")); reversed.add("Count", root.get("Count")); reversed.add("id", root.get("id"));
        JsonObject other = state(); other.getAsJsonObject("stack").add("value", reversed);
        assertEquals(NativeNameTable.key(value), NativeNameTable.key(other));
        assertEquals(NativeNameTable.Status.HIT, lookup(table(), other).status);
    }
    @Test public void numericTypesAndFloatingBitsAreNotCollapsed() {
        JsonObject a = state(), b = state(); fields(a).add("probe", scalar(1, "1")); fields(b).add("probe", scalar(3, "1"));
        assertNotEquals(NativeNameTable.key(a), NativeNameTable.key(b));
        fields(a).add("probe", scalar(5, "00000000")); fields(b).add("probe", scalar(5, "80000000"));
        assertNotEquals(NativeNameTable.key(a), NativeNameTable.key(b));
    }
    @Test public void listOrderCapabilitiesAndUnicodeArePreserved() {
        JsonObject a = state(), b = state();
        JsonObject listA = NativeNameTable.parse("{\"type\":9,\"elementType\":8,\"value\":[{\"type\":8,\"value\":\"鐵\"},{\"type\":8,\"value\":\"木\"}]}");
        JsonObject listB = NativeNameTable.parse("{\"type\":9,\"elementType\":8,\"value\":[{\"type\":8,\"value\":\"木\"},{\"type\":8,\"value\":\"鐵\"}]}");
        fields(a).add("ForgeCaps", listA); fields(b).add("ForgeCaps", listB);
        assertNotEquals(NativeNameTable.key(a), NativeNameTable.key(b)); assertTrue(NativeNameTable.key(a).contains("鐵"));
        assertNotEquals(NativeNameTable.key(state()), NativeNameTable.key(a));
    }
    @Test public void damageMetadataAndCountRemainIndependent() {
        NativeNameTable table = table();
        for (String field : Arrays.asList("metadata", "damage", "count")) {
            JsonObject value = state(); value.addProperty(field, field.equals("count") ? 2 : 1);
            assertEquals(NativeNameTable.Status.MISS, lookup(table, value).status);
        }
    }
    @Test public void builderDoesNotRetainMutableCallerJson() {
        JsonObject value = state(); NativeNameTable.Builder builder = builder(); builder.observe(value, "石頭", "en_us", "Stone", null);
        NativeNameTable table = builder.build(); fields(value).add("mutable", scalar(8, "changed"));
        assertEquals(NativeNameTable.Status.HIT, lookup(table, state()).status);
        assertEquals(NativeNameTable.Status.MISS, lookup(table, value).status);
        builder.observe(state(), "石頭", "en_us", "Different", null);
        assertEquals("Stone", lookup(table, state()).name);
    }
    @Test public void conflictingNativeNamesAreQuarantinedInEitherOrder() {
        for (List<String> names : Arrays.asList(Arrays.asList("A", "B"), Arrays.asList("B", "A"))) {
            NativeNameTable.Builder builder = builder();
            for (String name : names) builder.observe(state(), "石頭", "en_us", name, null);
            builder.observe(state(), "石頭", "en_us", "A", null); builder.observe(state(), "石頭", "zh_cn", "石头", null);
            NativeNameTable table = builder.build();
            assertEquals(NativeNameTable.Status.CONFLICT, lookup(table, state()).status);
            assertEquals("石头", table.lookup(state(), "石頭", "zh_cn", ENV, true).name);
        }
    }
    @Test public void rejectedAcquisitionCannotBeRehabilitatedByLaterSuccess() {
        for (boolean rejectionFirst : Arrays.asList(true, false)) {
            NativeNameTable.Builder builder = builder();
            if (rejectionFirst) builder.observe(state(), null, "en_us", null, "native-callback-error");
            builder.observe(state(), "石頭", "en_us", "Stone", null);
            if (!rejectionFirst) builder.observe(state(), null, "en_us", null, "native-callback-error");
            builder.observe(state(), "石頭", "en_us", "Stone", null);
            assertEquals(NativeNameTable.Status.REJECTED, lookup(builder.build(), state()).status);
        }
    }
    @Test public void staleContextWrongEnvironmentAndChangedPrimaryAbstain() {
        NativeNameTable table = table();
        assertEquals(NativeNameTable.Status.STALE_ENVIRONMENT, table.lookup(state(), "石頭", "en_us", digest('c'), true).status);
        assertEquals(NativeNameTable.Status.STALE_ENVIRONMENT, table.lookup(state(), "石頭", "en_us", ENV, false).status);
        assertEquals(NativeNameTable.Status.PRIMARY_MISMATCH, table.lookup(state(), "changed", "en_us", ENV, true).status);
        assertEquals(NativeNameTable.Status.MISS, table.lookup(state(), "石頭", "ja_jp", ENV, true).status);
    }
    @Test public void customNameUsesObservedLiteralEvenWithoutTableEntry() {
        JsonObject value = state(), tag = new JsonObject(), display = new JsonObject(); display.add("Name", scalar(8, "not used as oracle"));
        tag.add("display", compound(display)); fields(value).add("tag", compound(tag));
        NativeNameTable.Result actual = table().lookup(value, "§b100% 自訂\nName", "en_us", ENV, true);
        assertEquals(NativeNameTable.Status.LITERAL, actual.status); assertEquals("§b100% 自訂\nName", actual.name);
    }
    @Test public void persistenceRoundTripPreservesFormattingAndRejections() throws Exception {
        NativeNameTable.Builder builder = builder(); builder.observe(state(), "石頭", "en_us", "§a Stone ", null);
        builder.observe(state(), null, "zh_cn", null, "native-callback-error");
        Path path = target("table.jsonl"); builder.build().save(path); NativeNameTable loaded = NativeNameTable.load(path);
        assertEquals("§a Stone ", lookup(loaded, state()).name);
        assertEquals(NativeNameTable.Status.REJECTED, loaded.lookup(state(), "石頭", "zh_cn", ENV, true).status);
        assertEquals(2, loaded.statistics().get("pairs").getAsInt());
    }
    @Test public void truncatedAndCorruptTablesFailClosed() throws Exception {
        Path path = target("good.jsonl"); table().save(path); byte[] bytes = Files.readAllBytes(path);
        Path truncated = target("truncated.jsonl"); Files.write(truncated, Arrays.copyOf(bytes, bytes.length - 12)); badFile(truncated);
        String content = new String(bytes, StandardCharsets.UTF_8).replace("Stone", "Wrong");
        Path corrupt = target("corrupt.jsonl"); Files.write(corrupt, content.getBytes(StandardCharsets.UTF_8)); badFile(corrupt);
    }
    private Path reseal(String name, String replacement) throws Exception {
        Path good = target(name + "-good.jsonl"); table().save(good);
        List<String> lines = Files.readAllLines(good, StandardCharsets.UTF_8); lines.set(1, replacement == null ? lines.get(1) : replacement);
        String payload = lines.get(0) + "\n" + lines.get(1) + "\n";
        JsonObject footer = NativeNameTable.parse(lines.get(2)); footer.addProperty("sha256", NativeNameTable.sha256(payload.getBytes(StandardCharsets.UTF_8)));
        Path target = target(name + ".jsonl"); Files.write(target, (payload + footer + "\n").getBytes(StandardCharsets.UTF_8)); return target;
    }
    @Test public void duplicateJsonMembersAreRejectedEvenWithCorrectPayloadDigest() throws Exception {
        Path good = target("source.jsonl"); table().save(good); String row = Files.readAllLines(good, StandardCharsets.UTF_8).get(1);
        String duplicate = row.replace("\"targets\":{", "\"targets\":{\"en_us\":{\"primary\":\"石頭\",\"name\":\"Conflicting\"},");
        badFile(reseal("duplicate", duplicate));
    }
    @Test public void malformedPersistedCellDoesNotBecomeNewRejection() throws Exception {
        Path good = target("source.jsonl"); table().save(good); String row = Files.readAllLines(good, StandardCharsets.UTF_8).get(1);
        badFile(reseal("missing-name", row.replace(",\"name\":\"Stone\"", "")));
    }
    @Test public void parserRejectsDuplicateAndExcessiveDepthBeforeNbtValidation() {
        fails(() -> NativeNameTable.parse("{\"a\":1,\"a\":2}"));
        StringBuilder input = new StringBuilder(); for (int i = 0; i < 200; i++) input.append("{\"a\":"); input.append('0'); for (int i = 0; i < 200; i++) input.append('}');
        fails(() -> NativeNameTable.parse(input.toString()));
    }
    @Test public void malformedTypedValuesAndRootMismatchAreRejected() {
        JsonObject value = state(); fields(value).add("id", scalar(8, "minecraft:dirt")); fails(() -> NativeNameTable.key(value));
        JsonObject integer = state(); fields(integer).add("probe", scalar(1, "128")); fails(() -> NativeNameTable.key(integer));
        JsonObject zero = state(); zero.addProperty("count", 0); assertEquals(NativeNameTable.Status.INVALID_INPUT, lookup(table(), zero).status);
    }
    @Test public void hiddenStateWithUnchangedPrimaryRemainsAnExplicitLimitation() {
        NativeNameTable table = table();
        String laterNativeTargetFromHiddenWorldState = "Polished Stone";
        NativeNameTable.Result result = lookup(table, state());
        assertEquals(NativeNameTable.Status.HIT, result.status);
        assertNotEquals(laterNativeTargetFromHiddenWorldState, result.name);
        // A table cannot infer a change absent from its key/context/primary; report this counterexample, not universal safety.
    }
}
