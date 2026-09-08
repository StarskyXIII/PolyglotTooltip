package com.starskyxiii.polyglottooltip;

import java.util.Objects;

public final class FabricConfigValue<T> {
    private final T defaultValue;
    private volatile T value;

    public FabricConfigValue(T defaultValue) {
        this.defaultValue = Objects.requireNonNull(defaultValue);
        this.value = defaultValue;
    }

    public T get() {
        return value;
    }

    public T getDefault() {
        return defaultValue;
    }

    public void set(T value) {
        this.value = Objects.requireNonNull(value);
    }
}
