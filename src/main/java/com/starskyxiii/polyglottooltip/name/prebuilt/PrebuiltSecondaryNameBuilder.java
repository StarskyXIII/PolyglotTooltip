package com.starskyxiii.polyglottooltip.name.prebuilt;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.starskyxiii.polyglottooltip.PolyglotTooltip;
import com.starskyxiii.polyglottooltip.config.Config;
import com.starskyxiii.polyglottooltip.i18n.LanguageCache;
import com.starskyxiii.polyglottooltip.tooltip.SecondaryTooltipUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.Language;
import net.minecraft.client.resources.LanguageManager;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

/**
 * Builds the prebuilt ManaMetal secondary name cache by switching game language,
 * calling getDisplayName() on each ManaMetal item variant, then restoring.
 *
 * IMPORTANT: Language switching here is intentional and safe because this code
 * runs only during an explicit user command, never on the tooltip/render path.
 */
public final class PrebuiltSecondaryNameBuilder {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String MANAMETAL_PREFIX = "manametalmod:";

    /**
     * Controls whether the builder uses fast language reload (skips full resource reload)
     * or falls back to the full {@code mc.refreshResources()} path.
     *
     * Fast path: only reloads language data via LanguageManager.onResourceManagerReload().
     * Full path: triggers mc.refreshResources() which reloads textures, sounds, models, etc.
     *
     * Set to {@code false} to force full path for comparison testing.
     */
    private static final boolean USE_FAST_LANGUAGE_SWITCH = true;

    private PrebuiltSecondaryNameBuilder() {}

    /**
     * Rebuilds the cache for all ManaMetal items across all configured display languages.
     *
     * @return number of (item × language) entries written
     * @throws Exception if writing the cache file fails
     */
    public static int rebuild() throws Exception {
        Minecraft mc = Minecraft.getMinecraft();
        String savedLanguage = mc.gameSettings.language;

        PolyglotTooltip.LOG.info("[PolyglotTooltips] ManaMetal prebuild starting — language reload mode: {}",
            USE_FAST_LANGUAGE_SWITCH ? "fast" : "full");

        List<ItemStack> stacks = collectManaMetalStacks();
        Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> built =
            new LinkedHashMap<PrebuiltSecondaryNameIndexKey, Map<String, String>>();

        try {
            for (String lang : Config.displayLanguages) {
                // Language switch is safe here — we are in a command, not a render frame
                switchLanguage(mc, lang);

                for (ItemStack stack : stacks) {
                    if (stack == null || stack.getItem() == null) {
                        continue;
                    }

                    String registryName = getRegistryName(stack.getItem());
                    if (registryName == null || !registryName.startsWith(MANAMETAL_PREFIX)) {
                        continue;
                    }

                    String displayName = safeGetDisplayName(stack);
                    if (displayName == null || displayName.isEmpty()) {
                        continue;
                    }
                    displayName = PrebuiltSecondaryNameNormalizer.normalize(
                        registryName,
                        stack.getItemDamage(),
                        lang,
                        displayName);
                    if (displayName == null || displayName.isEmpty()) {
                        continue;
                    }

                    PrebuiltSecondaryNameIndexKey key =
                        new PrebuiltSecondaryNameIndexKey(registryName, stack.getItemDamage());
                    Map<String, String> langs = built.get(key);
                    if (langs == null) {
                        langs = new HashMap<String, String>();
                        built.put(key, langs);
                    }
                    langs.put(lang, displayName);
                }
            }
        } finally {
            // Always restore — even if something went wrong above
            switchLanguage(mc, savedLanguage);
            SecondaryTooltipUtil.clearInsertedLineCache();
        }

        // Write to temp first, then rename — prevents corrupting existing cache on failure
        writeCacheFile(built);
        PrebuiltSecondaryNameCache.replace(built);

        return countEntries(built);
    }

    // -------------------------------------------------------------------------
    // Item collection
    // -------------------------------------------------------------------------

    private static List<ItemStack> collectManaMetalStacks() {
        LinkedHashMap<StackKey, ItemStack> collected =
            new LinkedHashMap<StackKey, ItemStack>();

        for (Object entry : Item.itemRegistry) {
            if (!(entry instanceof Item)) {
                continue;
            }
            Item item = (Item) entry;
            String name = getRegistryName(item);
            if (name == null || !name.startsWith(MANAMETAL_PREFIX)) {
                continue;
            }
            collectSubItems(item, collected);
        }

        return new ArrayList<ItemStack>(collected.values());
    }

    private static void collectSubItems(Item item, Map<StackKey, ItemStack> collected) {
        List<ItemStack> variants = new ArrayList<ItemStack>();
        addSubItems(item, item.getCreativeTab(), variants);
        for (CreativeTabs tab : CreativeTabs.creativeTabArray) {
            addSubItems(item, tab, variants);
        }
        if (variants.isEmpty()) {
            variants.add(new ItemStack(item, 1, 0));
        }

        for (ItemStack stack : variants) {
            if (stack == null || stack.getItem() == null) {
                continue;
            }
            StackKey key = StackKey.of(stack);
            if (key != null && !collected.containsKey(key)) {
                collected.put(key, stack.copy());
            }
        }
    }

    private static void addSubItems(Item item, CreativeTabs tab, List<ItemStack> target) {
        if (item == null || tab == null) {
            return;
        }
        try {
            item.getSubItems(item, tab, target);
        } catch (Throwable ignored) {
            // Some items have buggy getSubItems — skip them
        }
    }

    // -------------------------------------------------------------------------
    // Language switching (command-only path)
    // -------------------------------------------------------------------------

    /**
     * Switches to {@code languageCode}.
     * Tries the fast path first; falls back to full path if it fails.
     * Fast vs full is controlled by {@link #USE_FAST_LANGUAGE_SWITCH}.
     */
    private static void switchLanguage(Minecraft mc, String languageCode) {
        if (mc == null || languageCode == null || languageCode.trim().isEmpty()) {
            return;
        }

        if (USE_FAST_LANGUAGE_SWITCH) {
            boolean fastSucceeded = fastSwitchLanguage(mc, languageCode.trim());
            if (!fastSucceeded) {
                PolyglotTooltip.LOG.warn(
                    "[PolyglotTooltips] Fast language switch failed for {}; falling back to full reload.",
                    languageCode.trim());
                fullSwitchLanguage(mc, languageCode.trim());
            }
        } else {
            fullSwitchLanguage(mc, languageCode.trim());
        }
    }

    /**
     * Fast language reload: only reloads locale data via
     * {@code LanguageManager.onResourceManagerReload()}, skipping full resource reload
     * (textures, sounds, models, renderGlobal, etc.).
     *
     * Equivalent to what {@code mc.refreshResources()} does for language, but nothing else.
     *
     * @return {@code true} if the fast path completed successfully
     */
    private static boolean fastSwitchLanguage(Minecraft mc, String languageCode) {
        try {
            LanguageManager languageManager = mc.getLanguageManager();
            if (languageManager == null) {
                return false;
            }

            net.minecraft.client.resources.IResourceManager resourceManager = mc.getResourceManager();
            if (resourceManager == null) {
                return false;
            }

            Language targetLanguage = findLanguage(languageManager, languageCode);
            if (targetLanguage == null) {
                PolyglotTooltip.LOG.warn(
                    "[PolyglotTooltips] Could not find language {} in LanguageManager; fast switch aborted.",
                    languageCode);
                return false;
            }

            languageManager.setCurrentLanguage(targetLanguage);
            mc.gameSettings.language = languageCode;

            // Directly invoke only the language reload step:
            // loads locale data files (en_US + target lang) and merges FML LanguageRegistry.
            // This is exactly what refreshResources() delegates to via the reload listener,
            // without triggering texture/sound/model reloads.
            languageManager.onResourceManagerReload(resourceManager);

            if (mc.fontRenderer != null) {
                mc.fontRenderer.setUnicodeFlag(
                    languageManager.isCurrentLocaleUnicode() || mc.gameSettings.forceUnicodeFont);
                mc.fontRenderer.setBidiFlag(languageManager.isCurrentLanguageBidirectional());
            }

            LanguageCache.clear();
            return true;
        } catch (Throwable t) {
            PolyglotTooltip.LOG.warn("[PolyglotTooltips] Fast language switch threw an exception.", t);
            return false;
        }
    }

    /**
     * Full language reload: original implementation using {@code mc.refreshResources()}.
     * Kept as fallback and for comparison testing.
     */
    private static void fullSwitchLanguage(Minecraft mc, String languageCode) {
        LanguageManager languageManager = mc.getLanguageManager();
        if (languageManager != null) {
            Language targetLanguage = findLanguage(languageManager, languageCode);
            if (targetLanguage != null) {
                languageManager.setCurrentLanguage(targetLanguage);
            } else {
                PolyglotTooltip.LOG.warn(
                    "[PolyglotTooltips] Could not find language {} in LanguageManager; using GameSettings fallback.",
                    languageCode);
            }
        }

        mc.gameSettings.language = languageCode;
        mc.refreshResources();
        if (mc.fontRenderer != null && languageManager != null) {
            mc.fontRenderer.setUnicodeFlag(
                languageManager.isCurrentLocaleUnicode() || mc.gameSettings.forceUnicodeFont);
            mc.fontRenderer.setBidiFlag(languageManager.isCurrentLanguageBidirectional());
        }
        LanguageCache.clear();
    }

    private static Language findLanguage(LanguageManager languageManager, String languageCode) {
        if (languageManager == null || languageCode == null || languageCode.isEmpty()) {
            return null;
        }

        for (Object entry : languageManager.getLanguages()) {
            if (!(entry instanceof Language)) {
                continue;
            }

            Language language = (Language) entry;
            if (languageCode.equalsIgnoreCase(language.getLanguageCode())) {
                return language;
            }
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // File I/O
    // -------------------------------------------------------------------------

    private static void writeCacheFile(
            Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> data) throws Exception {
        File cacheFile = PrebuiltSecondaryNameLoader.getCacheFile();
        File cacheDir = cacheFile.getParentFile();
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new IllegalStateException(
                "Cannot create cache directory: " + cacheDir.getAbsolutePath());
        }

        // Write to a temp file; only replace the real file after a successful write
        File tempFile = new File(cacheDir, "manametal-secondary-names.tmp");
        Writer writer = null;
        try {
            writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(tempFile), UTF8));
            writer.write("registry_name\tdamage\tlanguage_code\tdisplay_name");
            writer.write(System.getProperty("line.separator"));

            for (Map.Entry<PrebuiltSecondaryNameIndexKey, Map<String, String>> entry
                    : data.entrySet()) {
                PrebuiltSecondaryNameIndexKey key = entry.getKey();
                for (Map.Entry<String, String> langEntry : entry.getValue().entrySet()) {
                    String displayName = langEntry.getValue();
                    if (displayName == null || displayName.isEmpty()) {
                        continue;
                    }
                    writer.write(escape(key.registryName));
                    writer.write('\t');
                    writer.write(String.valueOf(key.damage));
                    writer.write('\t');
                    writer.write(escape(langEntry.getKey()));
                    writer.write('\t');
                    writer.write(escape(displayName));
                    writer.write(System.getProperty("line.separator"));
                }
            }
        } catch (Exception e) {
            tempFile.delete();
            throw e;
        } finally {
            if (writer != null) {
                writer.close();
            }
        }

        // Atomic rename: delete old, rename temp → final
        if (cacheFile.exists() && !cacheFile.delete()) {
            tempFile.delete();
            throw new IllegalStateException(
                "Cannot replace existing cache file: " + cacheFile.getAbsolutePath());
        }
        if (!tempFile.renameTo(cacheFile)) {
            tempFile.delete();
            throw new IllegalStateException(
                "Cannot rename temp file to cache file: " + cacheFile.getAbsolutePath());
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String safeGetDisplayName(ItemStack stack) {
        try {
            String name = stack.getDisplayName();
            if (name == null) {
                return null;
            }
            String stripped = EnumChatFormatting.getTextWithoutFormattingCodes(name);
            String result = stripped != null ? stripped.trim() : name.trim();
            return result.isEmpty() ? null : result;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String getRegistryName(Item item) {
        if (item == null) {
            return null;
        }
        try {
            Object name = Item.itemRegistry.getNameForObject(item);
            return name == null ? null : String.valueOf(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int countEntries(Map<PrebuiltSecondaryNameIndexKey, Map<String, String>> data) {
        int total = 0;
        for (Map<String, String> langs : data.values()) {
            total += langs.size();
        }
        return total;
    }

    /** Dedup key for item variant collection (no NBT — MVP scope). */
    private static final class StackKey {

        private final Object item;
        private final int damage;
        private final int hashCode;

        private StackKey(Object item, int damage) {
            this.item = item;
            this.damage = damage;
            int result = System.identityHashCode(item);
            result = 31 * result + damage;
            this.hashCode = result;
        }

        static StackKey of(ItemStack stack) {
            if (stack == null || stack.getItem() == null) {
                return null;
            }
            return new StackKey(stack.getItem(), stack.getItemDamage());
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof StackKey)) {
                return false;
            }
            StackKey other = (StackKey) obj;
            return item == other.item && damage == other.damage;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
