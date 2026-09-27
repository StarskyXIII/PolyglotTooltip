package com.starskyxiii.polyglottooltip.integration.ae2;

import appeng.client.gui.me.common.Repo;
import com.starskyxiii.polyglottooltip.minecraft.MinecraftLanguageAdapter;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class Ae2SearchRefresh {
    private static final WeakIdentityRegistry<Map<?, ?>> CACHES = new WeakIdentityRegistry<>();
    private static final WeakIdentityRegistry<Repo> REPOS = new WeakIdentityRegistry<>();
    private static final WeakIdentityRegistry<Refreshable> SCREENS = new WeakIdentityRegistry<>();
    private static boolean registered;

    private Ae2SearchRefresh() {
    }

    public interface Refreshable {
        void polyglot$refreshSearch();
    }

    public static void registerCache(Map<?, ?> cache) {
        CACHES.add(cache);
        registerInvalidator();
    }

    public static void registerRepo(Repo repo) {
        REPOS.add(repo);
        registerInvalidator();
    }

    public static void registerScreen(Refreshable screen) {
        SCREENS.add(screen);
        registerInvalidator();
    }

    private static synchronized void registerInvalidator() {
        if (!registered) {
            MinecraftLanguageAdapter.getInstance().registerDependentCacheInvalidator(Ae2SearchRefresh::invalidate);
            registered = true;
        }
    }

    private static void invalidate() {
        List<Map<?, ?>> caches = CACHES.snapshot();
        List<Repo> repos = REPOS.snapshot();
        List<Refreshable> screens = SCREENS.snapshot();
        for (Map<?, ?> cache : caches) {
            cache.clear();
        }
        for (Repo repo : repos) {
            repo.updateView();
        }
        for (Refreshable screen : screens) {
            screen.polyglot$refreshSearch();
        }
    }

    static final class WeakIdentityRegistry<T> {
        private final List<WeakReference<T>> references = new ArrayList<>();

        synchronized void add(T value) {
            boolean present = false;
            Iterator<WeakReference<T>> iterator = references.iterator();
            while (iterator.hasNext()) {
                T existing = iterator.next().get();
                if (existing == null) {
                    iterator.remove();
                } else if (existing == value) {
                    present = true;
                }
            }
            if (!present) {
                references.add(new WeakReference<>(value));
            }
        }

        synchronized List<T> snapshot() {
            List<T> values = new ArrayList<>();
            Iterator<WeakReference<T>> iterator = references.iterator();
            while (iterator.hasNext()) {
                T value = iterator.next().get();
                if (value == null) {
                    iterator.remove();
                } else {
                    values.add(value);
                }
            }
            return values;
        }
    }
}
