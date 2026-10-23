package com.starskyxiii.polyglottooltip.legacy.integration.names;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;

import com.starskyxiii.polyglottooltip.core.LanguageCodePolicy;
import com.starskyxiii.polyglottooltip.legacy.PolyglotTooltipMod;
import com.starskyxiii.polyglottooltip.legacy.integration.IntegrationGuard;
import com.starskyxiii.polyglottooltip.legacy.minecraft.ConsistencyCheck;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SecondaryDictionary;
import com.starskyxiii.polyglottooltip.legacy.minecraft.SpecialItemNameResolver;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public final class Ic2NameResolver implements SpecialItemNameResolver {
    static final String CLASS_PREFIX = "ic2.core.";
    private static final Map<String, Map<String, String>> LANGUAGES = new ConcurrentHashMap<>();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private final IntegrationGuard guard;

    public Ic2NameResolver(IntegrationGuard guard) {
        this.guard = guard;
    }

    static void invalidate() {
        LANGUAGES.clear();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return guard.active() && stack.func_77973_b().getClass().getName().startsWith(CLASS_PREFIX);
    }

    @Override
    public String resolve(ItemStack stack, ConsistencyCheck.Result result, String languageCode,
                          SecondaryDictionary dictionary) {
        return guard.call(() -> {
            Map<String, String> ic2 = LANGUAGES.computeIfAbsent(languageCode,
                    code -> load(Minecraft.func_71410_x().func_110442_L(), code));
            return name(stack.func_77973_b().func_77667_c(stack), result.primary(), NameParts.PRIMARY,
                    key -> ic2.containsKey(key) ? ic2.get(key) : dictionary.get(key));
        }, null);
    }

    static String name(String key, String primary, Function<String, String> primaryLang,
                       Function<String, String> secondaryLang) {
        return key == null ? null : NameParts.checked(primaryLang.apply(key), primary, () -> secondaryLang.apply(key));
    }

    private static Map<String, String> load(IResourceManager manager, String languageCode) {
        Map<String, String> entries = new HashMap<>();
        for (String language : LanguageCodePolicy.fallbackChain(languageCode)) {
            ResourceLocation location = new ResourceLocation("ic2", "lang_ic2/" + language + ".properties");
            try {
                for (IResource resource : manager.func_135056_b(location)) {
                    try (IResource open = resource;
                         Reader reader = new InputStreamReader(open.func_110527_b(), StandardCharsets.UTF_8)) {
                        parse(reader, entries::put);
                    }
                }
            } catch (FileNotFoundException missing) {
                continue;
            } catch (IOException error) {
                if (WARNED.add(location.toString())) {
                    LogManager.getLogger(PolyglotTooltipMod.MOD_ID).warn("Could not read " + location, error);
                }
            }
        }
        return Collections.unmodifiableMap(entries);
    }

    static void parse(Reader reader, BiConsumer<String, String> sink) throws IOException {
        Properties properties = new Properties();
        properties.load(reader);
        for (String key : properties.stringPropertyNames()) {
            boolean global = key.startsWith("achievement.") || key.startsWith("itemGroup.") || key.startsWith("death.");
            sink.accept(global ? key : "ic2." + key, properties.getProperty(key));
        }
    }
}
