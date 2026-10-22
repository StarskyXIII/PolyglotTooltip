package com.starskyxiii.polyglottooltip.resourceheads;

import java.util.ArrayList;
import java.util.List;

public final class HeadEvents {
    private static final List<String> events = new ArrayList<>();
    private HeadEvents() { }
    public static synchronized void record(String event) { events.add(event); }
    public static synchronized List<String> snapshot() { return new ArrayList<>(events); }
}
