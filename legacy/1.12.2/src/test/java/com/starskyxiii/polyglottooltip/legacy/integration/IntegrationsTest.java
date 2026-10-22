package com.starskyxiii.polyglottooltip.legacy.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class IntegrationsTest {
    private final List<String> events = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    private Integrations.Entry entry(String id, String modid) {
        return new Integrations.Entry(id, modid, guard -> {
            events.add("construct " + id);
            return new Recording(id, modid);
        });
    }

    private Integrations registry(List<Integrations.Entry> entries, List<String> loaded, List<String> off) {
        return new Integrations(entries, loaded::contains, id -> !off.contains(id),
                (message, error) -> warnings.add(message));
    }

    @Test
    public void constructsOnlyLoadedAndEnabledIntegrations() {
        Integrations integrations = registry(
                Arrays.asList(entry("jei", "jei"), entry("top", "theoneprobe"), entry("hwyla", "waila")),
                Arrays.asList("jei", "waila"), Collections.singletonList("hwyla"));
        integrations.start();
        assertEquals(Arrays.asList("construct jei", "start jei"), events);
        assertEquals(Collections.singletonList("hwyla (turned off in the config)"), integrations.disabled());
        assertTrue(warnings.isEmpty());
    }

    @Test
    public void constructionFailureWarnsOnceAndSkips() {
        Integrations.Entry broken = new Integrations.Entry("ae2", "appliedenergistics2", guard -> {
            throw new NoClassDefFoundError("appeng/api/Missing");
        });
        Integrations integrations = registry(Arrays.asList(broken, entry("jei", "jei")),
                Arrays.asList("appliedenergistics2", "jei"), Collections.<String>emptyList());
        integrations.start();
        assertEquals(Arrays.asList("construct jei", "start jei"), events);
        assertEquals(1, warnings.size());
        assertTrue(integrations.disabled().get(0).startsWith("ae2 (could not be constructed"));
    }

    @Test
    public void invalidationReachesStartedIntegrationsAndGuardsFailures() {
        List<Integrations.Entry> entries = Collections.singletonList(new Integrations.Entry("jei", "jei",
                guard -> new Recording("jei", "jei") {
                    @Override
                    public void invalidate() {
                        events.add("invalidate jei");
                        throw new IllegalStateException("expected");
                    }
                }));
        Integrations integrations = registry(entries, Collections.singletonList("jei"),
                Collections.<String>emptyList());
        integrations.start();
        for (int i = 0; i < IntegrationGuard.FAILURE_LIMIT + 1; i++) {
            integrations.invalidate();
        }
        assertEquals(IntegrationGuard.FAILURE_LIMIT, events.stream().filter("invalidate jei"::equals).count());
        assertEquals(1, warnings.size());
        assertTrue(integrations.disabled().get(0).startsWith("jei (20 errors"));
    }

    private class Recording implements Integration {
        private final String id;
        private final String modid;

        Recording(String id, String modid) {
            this.id = id;
            this.modid = modid;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String modid() {
            return modid;
        }

        @Override
        public void start() {
            events.add("start " + id);
        }
    }
}
