package com.starskyxiii.polyglottooltip.legacy.integration;

public interface Integration {
    String id();

    String modid();

    void start();

    default void invalidate() {}
}
