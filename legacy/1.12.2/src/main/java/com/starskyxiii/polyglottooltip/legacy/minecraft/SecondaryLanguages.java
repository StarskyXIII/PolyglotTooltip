package com.starskyxiii.polyglottooltip.legacy.minecraft;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.starskyxiii.polyglottooltip.legacy.Config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.Language;

@SuppressWarnings("deprecation")
public final class SecondaryLanguages implements IResourceManagerReloadListener {
    private static final SecondaryLanguages LISTENER = new SecondaryLanguages();
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static final List<Runnable> INVALIDATION = new CopyOnWriteArrayList<>();
    private static volatile Map<String, SecondaryDictionary> dictionaries = Collections.emptyMap();

    private SecondaryLanguages() {}

    public static void register() {
        IResourceManager manager = Minecraft.func_71410_x().func_110442_L();
        if (manager instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager) manager).func_110542_a(LISTENER);
        } else {
            rebuild();
        }
    }

    @Override
    public void func_110549_a(IResourceManager manager) {
        rebuild(manager);
    }

    public static void rebuild() {
        rebuild(Minecraft.func_71410_x().func_110442_L());
    }

    private static void rebuild(IResourceManager manager) {
        rebuild(new ResourceLangSource(manager));
    }

    static synchronized void rebuild(SecondaryDictionary.LangSource source) {
        try {
            Map<String, SecondaryDictionary> loaded = new LinkedHashMap<>();
            for (String code : Config.settings().displayLanguages()) {
                loaded.put(code, SecondaryDictionary.load(code, source));
            }
            dictionaries = Collections.unmodifiableMap(loaded);
        } catch (RuntimeException error) {
            FailureLog.warn("load|" + error.getClass().getName(), "Could not build secondary dictionaries", error);
        }
        GENERATION.incrementAndGet();
        for (Runnable callback : INVALIDATION) {
            try {
                callback.run();
            } catch (RuntimeException error) {
                FailureLog.warn("invalidate|" + FailureLog.failureKey(callback, error),
                        "Invalidation callback " + callback.getClass().getName() + " failed", error);
            }
        }
    }

    public static void addInvalidationListener(Runnable callback) {
        if (callback != null) {
            INVALIDATION.add(callback);
        }
    }

    public static void removeInvalidationListener(Runnable callback) {
        INVALIDATION.remove(callback);
    }

    public static Map<String, SecondaryDictionary> dictionaries() {
        return dictionaries;
    }

    public static int generation() {
        return GENERATION.get();
    }

    public static String currentLanguage() {
        Minecraft minecraft = Minecraft.func_71410_x();
        Language language = minecraft.func_135016_M() == null ? null : minecraft.func_135016_M().func_135041_c();
        String code = language == null ? minecraft.field_71474_y.field_74363_ab : language.func_135034_a();
        return code == null ? "" : code.toLowerCase(Locale.ROOT);
    }
}
