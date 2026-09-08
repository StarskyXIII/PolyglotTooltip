package com.starskyxiii.polyglottooltip.integration.rs2;

//? if >1.20.1 {
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class Rs2SearchUtilTest {

    @Test
    void readsLegacySingleNameSurface() {
        assertEquals(List.of("Legacy Name"),
                copy(GridResourceNameAdapter.getPrimaryNames(new LegacyGridResource("Legacy Name"))));

        List<String> nullable = copy(GridResourceNameAdapter.getPrimaryNames(new LegacyGridResource(null)));
        assertEquals(1, nullable.size());
        assertNull(nullable.getFirst());
    }

    @Test
    void preservesCurrentSearchableNameAliases() {
        Set<String> aliases = Set.of("Primary Name", "Alias One", "Alias Two");
        assertEquals(aliases,
                Set.copyOf(copy(GridResourceNameAdapter.getPrimaryNames(new CurrentGridResource(aliases)))));
        assertNull(GridResourceNameAdapter.getPrimaryNames(new CurrentGridResource(null)));
    }

    private static List<String> copy(Iterable<String> names) {
        List<String> result = new ArrayList<>();
        names.forEach(result::add);
        return result;
    }

    private record LegacyGridResource(String name) {
        public String getName() {
            return name;
        }
    }

    private record CurrentGridResource(Set<String> searchableNames) {
        public Set<String> getSearchableNames() {
            return searchableNames;
        }
    }
}
//?}
