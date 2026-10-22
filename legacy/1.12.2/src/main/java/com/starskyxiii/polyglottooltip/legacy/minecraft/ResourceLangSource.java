package com.starskyxiii.polyglottooltip.legacy.minecraft;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

final class ResourceLangSource implements SecondaryDictionary.LangSource {
    private final IResourceManager manager;

    ResourceLangSource(IResourceManager manager) {
        this.manager = manager;
    }

    @Override
    public void read(String languageCode, BiConsumer<String, String> sink) {
        List<String> domains;
        try {
            domains = new ArrayList<>(manager.func_135055_a());
        } catch (RuntimeException error) {
            FailureLog.warn("domains|" + error.getClass().getName(),
                    "Could not list resource domains for language " + languageCode, error);
            return;
        }
        for (String domain : domains) {
            ResourceLocation location = new ResourceLocation(domain, "lang/" + languageCode + ".lang");
            List<IResource> resources;
            try {
                resources = manager.func_135056_b(location);
            } catch (FileNotFoundException missing) {
                continue;
            } catch (IOException | RuntimeException error) {
                FailureLog.warn(location + "|" + error.getClass().getName(),
                        "Could not list language files " + location, error);
                continue;
            }
            for (int index = 0; index < resources.size(); index++) {
                readFile(location, index, resources.get(index), sink);
            }
        }
    }

    private static void readFile(ResourceLocation location, int index, IResource resource,
                                 BiConsumer<String, String> sink) {
        Map<String, String> entries = new LinkedHashMap<>();
        try (IResource open = resource;
             Reader reader = new InputStreamReader(open.func_110527_b(), StandardCharsets.UTF_8)) {
            SecondaryDictionary.parse(reader, entries::put);
        } catch (IOException | RuntimeException error) {
            FailureLog.warn(location + "#" + index + "|" + error.getClass().getName(),
                    "Skipped unreadable language file " + location + " (pack entry " + index + ")", error);
            return;
        }
        entries.forEach(sink);
    }
}
