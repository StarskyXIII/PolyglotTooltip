package com.starskyxiii.polyglottooltip.integration.rs2;

import com.google.common.base.Supplier;
import com.starskyxiii.polyglottooltip.minecraft.MinecraftLanguageAdapter;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class Rs2TooltipCache<T> implements Supplier<T> {
    private static final AtomicLong GENERATION = new AtomicLong();
    private static boolean registered;
    private final Supplier<T> delegate;
    private long cachedGeneration = -1;
    private T value;

    private Rs2TooltipCache(Supplier<T> delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    public static synchronized <T> Supplier<T> memoize(Supplier<T> delegate) {
        if (!registered) {
            MinecraftLanguageAdapter.getInstance().registerDependentCacheInvalidator(Rs2TooltipCache::invalidate);
            registered = true;
        }
        return new Rs2TooltipCache<>(delegate);
    }

    static void invalidate() {
        GENERATION.incrementAndGet();
    }

    @Override
    public synchronized T get() {
        long generation = GENERATION.get();
        if (cachedGeneration != generation) {
            T updated = delegate.get();
            value = updated;
            cachedGeneration = generation;
        }
        return value;
    }
}
