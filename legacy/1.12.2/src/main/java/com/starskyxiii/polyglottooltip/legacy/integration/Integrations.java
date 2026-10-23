package com.starskyxiii.polyglottooltip.legacy.integration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.logging.log4j.LogManager;

import com.starskyxiii.polyglottooltip.legacy.Config;
import com.starskyxiii.polyglottooltip.legacy.PolyglotTooltipMod;
import com.starskyxiii.polyglottooltip.legacy.integration.ae2.Ae2Integration;
import com.starskyxiii.polyglottooltip.legacy.integration.carbonconfig.CarbonConfigIntegration;
import com.starskyxiii.polyglottooltip.legacy.integration.generic.GenericIntegration;
import com.starskyxiii.polyglottooltip.legacy.integration.hwyla.HwylaIntegration;
import com.starskyxiii.polyglottooltip.legacy.integration.jei.JeiIntegration;
import com.starskyxiii.polyglottooltip.legacy.integration.names.NamesIntegration;
import com.starskyxiii.polyglottooltip.legacy.integration.rs.RsIntegration;
import com.starskyxiii.polyglottooltip.legacy.integration.theoneprobe.TopIntegration;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryLanguages;

import net.minecraftforge.fml.common.Loader;

public final class Integrations {
    public static final class Entry {
        final String id;
        final String modid;
        final Function<IntegrationGuard, Integration> factory;

        public Entry(String id, String modid, Function<IntegrationGuard, Integration> factory) {
            this.id = id;
            this.modid = modid;
            this.factory = factory;
        }
    }

    private static final List<Entry> KNOWN = Collections.unmodifiableList(Arrays.asList(
            new Entry(JeiIntegration.ID, JeiIntegration.MODID, JeiIntegration::new),
            new Entry(TopIntegration.ID, TopIntegration.MODID, TopIntegration::new),
            new Entry(HwylaIntegration.ID, HwylaIntegration.MODID, HwylaIntegration::new),
            new Entry(Ae2Integration.ID, Ae2Integration.MODID, Ae2Integration::new),
            new Entry(RsIntegration.ID, RsIntegration.MODID, RsIntegration::new),
            new Entry(NamesIntegration.POTION_ID, null, NamesIntegration::potion),
            new Entry(NamesIntegration.FORESTRY_ID, NamesIntegration.FORESTRY_MODID, NamesIntegration::forestry),
            new Entry(NamesIntegration.IC2_ID, NamesIntegration.IC2_MODID, NamesIntegration::ic2),
            new Entry(NamesIntegration.MICROBLOCK_ID, NamesIntegration.MICROBLOCK_MODID, NamesIntegration::microblock),
            new Entry(NamesIntegration.EXTRACELLS_ID, NamesIntegration.EXTRACELLS_MODID, NamesIntegration::extracells),
            new Entry(GenericIntegration.BARE_KEY_ID, null, GenericIntegration::bareKey),
            new Entry(GenericIntegration.FORMAT_ID, null, GenericIntegration::format),
            new Entry(GenericIntegration.COMPOSITE_ID, null, GenericIntegration::composite),
            new Entry(CarbonConfigIntegration.ID, CarbonConfigIntegration.MODID, CarbonConfigIntegration::new)));
    private static volatile Integrations installed;

    private final List<Entry> entries;
    private final Predicate<String> modLoaded;
    private final Predicate<String> enabled;
    private final BiConsumer<String, Throwable> warn;
    private final Map<String, String> inactive = new LinkedHashMap<>();
    private final Map<Integration, IntegrationGuard> started = new LinkedHashMap<>();

    public Integrations(List<Entry> entries,
                        Predicate<String> modLoaded,
                        Predicate<String> enabled,
                        BiConsumer<String, Throwable> warn) {
        this.entries = entries;
        this.modLoaded = modLoaded;
        this.enabled = enabled;
        this.warn = warn;
    }

    public static void install() {
        Integrations integrations = new Integrations(KNOWN, Loader::isModLoaded, Config::integrationEnabled,
                LogManager.getLogger(PolyglotTooltipMod.MOD_ID)::warn);
        integrations.start();
        installed = integrations;
        SecondaryLanguages.addInvalidationListener(integrations::invalidate);
    }

    public static List<Entry> known() {
        return KNOWN;
    }

    public static List<String> describeDisabled() {
        Integrations integrations = installed;
        return integrations == null ? Collections.<String>emptyList() : integrations.disabled();
    }

    public synchronized void start() {
        for (Entry entry : entries) {
            if (entry.modid != null && !modLoaded.test(entry.modid)) {
                continue;
            }
            if (!enabled.test(entry.id)) {
                inactive.put(entry.id, "turned off in the config");
                continue;
            }
            IntegrationGuard guard = new IntegrationGuard(entry.id, warn);
            Integration integration;
            try {
                integration = entry.factory.apply(guard);
            } catch (RuntimeException | LinkageError error) {
                inactive.put(entry.id, "could not be constructed: " + error);
                warn.accept("Skipped the " + entry.id + " integration because it could not be constructed", error);
                continue;
            }
            started.put(integration, guard);
            guard.run(integration::start);
        }
    }

    public synchronized void invalidate() {
        for (Map.Entry<Integration, IntegrationGuard> entry : started.entrySet()) {
            entry.getValue().run(entry.getKey()::invalidate);
        }
    }

    public synchronized List<String> active() {
        List<String> active = new ArrayList<>();
        for (IntegrationGuard guard : started.values()) {
            if (guard.active()) {
                active.add(guard.id());
            }
        }
        return active;
    }

    public synchronized List<String> disabled() {
        List<String> disabled = new ArrayList<>();
        for (Map.Entry<String, String> entry : inactive.entrySet()) {
            disabled.add(entry.getKey() + " (" + entry.getValue() + ")");
        }
        for (IntegrationGuard guard : started.values()) {
            if (!guard.active()) {
                disabled.add(guard.id() + " (" + guard.disabledReason() + ")");
            }
        }
        return disabled;
    }
}
