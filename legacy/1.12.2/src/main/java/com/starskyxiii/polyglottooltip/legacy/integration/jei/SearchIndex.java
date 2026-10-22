package com.starskyxiii.polyglottooltip.legacy.integration.jei;

public interface SearchIndex {
    int polyglot$generation();

    void polyglot$rebuild();

    void polyglot$indexPending(long budgetNanos);
}
