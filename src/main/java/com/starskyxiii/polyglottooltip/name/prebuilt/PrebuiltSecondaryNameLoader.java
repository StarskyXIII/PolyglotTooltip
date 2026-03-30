package com.starskyxiii.polyglottooltip.name.prebuilt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import com.starskyxiii.polyglottooltip.PolyglotTooltip;

import net.minecraft.client.Minecraft;

/**
 * Loads the prebuilt ManaMetal secondary name cache from disk into memory.
 * Called once at startup; gracefully degrades if the file does not exist.
 */
public final class PrebuiltSecondaryNameLoader {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    static final String CACHE_SUBDIR = "polyglottooltip/cache";
    static final String CACHE_FILENAME = "manametal-secondary-names.tsv";

    private PrebuiltSecondaryNameLoader() {}

    /** Attempts to load the cache from disk. Logs warnings on failure; never throws. */
    public static void tryLoad() {
        try {
            File cacheFile = getCacheFile();
            if (!cacheFile.exists()) {
                return; // no cache yet — silent degradation
            }

            Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> loaded = readCacheFile(cacheFile);
            PrebuiltSecondaryNameCache.replace(loaded);
            PolyglotTooltip.LOG.info(
                "[PolyglotTooltips] Loaded {} ManaMetal prebuilt secondary name entries from cache.",
                countEntries(loaded));
        } catch (Exception e) {
            PolyglotTooltip.LOG.warn(
                "[PolyglotTooltips] Failed to load ManaMetal prebuilt secondary name cache: {}",
                e.getMessage());
        }
    }

    /** Returns the expected location of the cache file. Package-visible for use by the builder. */
    static File getCacheFile() {
        Minecraft mc = Minecraft.getMinecraft();
        File root = mc != null ? mc.mcDataDir : new File(".");
        return new File(root, CACHE_SUBDIR + "/" + CACHE_FILENAME);
    }

    /**
     * TSV format (one row per entry, header row skipped):
     *   registry_name TAB damage TAB language_code TAB display_name
     */
    private static Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> readCacheFile(File file)
            throws Exception {
        Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> result =
            new HashMap<PrebuiltSecondaryNameIndexKey, Map<String, String>>();

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), UTF8));
            reader.readLine(); // skip header

            String line;
            while ((line = reader.readLine()) != null) {
                String[] cols = line.split("\t", -1);
                if (cols.length < 4) {
                    continue;
                }

                String registryName = unescape(cols[0]);
                String damageStr    = unescape(cols[1]);
                String languageCode = unescape(cols[2]);
                String displayName  = unescape(cols[3]);

                if (registryName.isEmpty() || damageStr.isEmpty()
                        || languageCode.isEmpty() || displayName.isEmpty()) {
                    continue;
                }

                int damage;
                try {
                    damage = Integer.parseInt(damageStr);
                } catch (NumberFormatException ignored) {
                    continue;
                }

                displayName = PrebuiltSecondaryNameNormalizer.normalize(registryName, damage, languageCode, displayName);
                if (displayName == null || displayName.isEmpty()) {
                    continue;
                }

                PrebuiltSecondaryNameIndexKey key =
                    new PrebuiltSecondaryNameIndexKey(registryName, damage);
                Map<String, String> langs = result.get(key);
                if (langs == null) {
                    langs = new HashMap<String, String>();
                    result.put(key, langs);
                }
                langs.put(languageCode, displayName);
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }

        return result;
    }

    private static String unescape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\t", "\t").replace("\\r", "\r").replace("\\n", "\n");
    }

    private static int countEntries(Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> data) {
        int total = 0;
        for (Map<String, String> langs : data.values()) {
            total += langs.size();
        }
        return total;
    }
}
