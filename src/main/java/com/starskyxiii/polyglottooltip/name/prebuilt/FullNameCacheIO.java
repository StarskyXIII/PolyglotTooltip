package com.starskyxiii.polyglottooltip.name.prebuilt;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;

import com.starskyxiii.polyglottooltip.PolyglotTooltip;

/**
 * Reads and writes the full-registry name cache TSV file.
 *
 * File location: .minecraft/polyglottooltip/cache/full-name-cache.tsv
 * Format: registry_name TAB damage TAB language_code TAB display_name
 *
 * Tabs and newlines inside field values are escaped as \t / \r / \n.
 * Writes to a .tmp file first, then atomically renames to prevent cache corruption.
 */
public final class FullNameCacheIO {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    static final String CACHE_SUBDIR = "polyglottooltip/cache";
    static final String CACHE_FILENAME = "full-name-cache.tsv";

    private FullNameCacheIO() {}

    // -------------------------------------------------------------------------
    // Path
    // -------------------------------------------------------------------------

    public static File getCacheFile() {
        Minecraft mc = Minecraft.getMinecraft();
        File root = mc != null ? mc.mcDataDir : new File(".");
        return new File(root, CACHE_SUBDIR + "/" + CACHE_FILENAME);
    }

    // -------------------------------------------------------------------------
    // Load
    // -------------------------------------------------------------------------

    /** Attempts to load the cache from disk into FullNameCache. Logs warnings on failure; never throws. */
    public static void tryLoad() {
        try {
            File cacheFile = getCacheFile();
            if (!cacheFile.exists()) {
                return; // no cache yet — silent degradation
            }
            Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> loaded = read(cacheFile);
            FullNameCache.replace(loaded);
            PolyglotTooltip.LOG.info(
                "[PolyglotTooltips] Loaded {} full name cache entries from {}.",
                countEntries(loaded),
                cacheFile.getName());
        } catch (Exception e) {
            PolyglotTooltip.LOG.warn("[PolyglotTooltips] Failed to load full name cache: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /**
     * Writes data to the cache TSV file atomically (write to .tmp, then rename).
     *
     * @throws Exception on I/O failure
     */
    public static void write(Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> data) throws Exception {
        File cacheFile = getCacheFile();
        File dir = cacheFile.getParentFile();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Cannot create cache dir: " + dir.getAbsolutePath());
        }

        File tmp = new File(dir, CACHE_FILENAME + ".tmp");
        Writer w = null;
        try {
            w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), UTF8));
            w.write("registry_name\tdamage\tlanguage_code\tdisplay_name");
            w.write(System.getProperty("line.separator"));

            for (Map.Entry<PrebuiltSecondaryNameIndexKey, Map<String, String>> entry : data.entrySet()) {
                PrebuiltSecondaryNameIndexKey key = entry.getKey();
                for (Map.Entry<String, String> lang : entry.getValue().entrySet()) {
                    String name = lang.getValue();
                    if (name == null || name.isEmpty()) continue;
                    w.write(esc(key.registryName));
                    w.write('\t');
                    w.write(String.valueOf(key.damage));
                    w.write('\t');
                    w.write(esc(lang.getKey()));
                    w.write('\t');
                    w.write(esc(name));
                    w.write(System.getProperty("line.separator"));
                }
            }
        } catch (Exception e) {
            tmp.delete();
            throw e;
        } finally {
            if (w != null) w.close();
        }

        if (cacheFile.exists() && !cacheFile.delete()) {
            tmp.delete();
            throw new IllegalStateException("Cannot replace cache file: " + cacheFile.getAbsolutePath());
        }
        if (!tmp.renameTo(cacheFile)) {
            // Do NOT delete tmp here — it still contains the full build data.
            // The caller can retry or the user can manually rename the .tmp file.
            throw new IllegalStateException(
                "Cannot rename tmp to cache file: " + cacheFile.getAbsolutePath()
                + " (data retained in: " + tmp.getAbsolutePath() + ")");
        }
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    private static Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> read(File file) throws Exception {
        Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> result =
            new HashMap<PrebuiltSecondaryNameIndexKey, Map<String, String>>();
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(new FileInputStream(file), UTF8));
            r.readLine(); // skip header
            String line;
            while ((line = r.readLine()) != null) {
                String[] cols = line.split("\t", -1);
                if (cols.length < 4) continue;
                String reg = unesc(cols[0]);
                String dmgS = unesc(cols[1]);
                String lang = unesc(cols[2]);
                String name = unesc(cols[3]);
                if (reg.isEmpty() || dmgS.isEmpty() || lang.isEmpty() || name.isEmpty()) continue;
                int dmg;
                try {
                    dmg = Integer.parseInt(dmgS);
                } catch (NumberFormatException e) {
                    continue;
                }
                PrebuiltSecondaryNameIndexKey key = new PrebuiltSecondaryNameIndexKey(reg, dmg);
                Map<String, String> langs = result.get(key);
                if (langs == null) {
                    langs = new HashMap<String, String>();
                    result.put(key, langs);
                }
                langs.put(lang, name);
            }
        } finally {
            if (r != null) r.close();
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static int countEntries(Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> data) {
        int total = 0;
        for (Map<String, String> langs : data.values()) total += langs.size();
        return total;
    }

    private static String esc(String v) {
        if (v == null) return "";
        return v.replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String unesc(String v) {
        if (v == null) return "";
        return v.replace("\\t", "\t").replace("\\r", "\r").replace("\\n", "\n");
    }
}
