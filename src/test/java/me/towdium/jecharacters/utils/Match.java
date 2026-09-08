package me.towdium.jecharacters.utils;

import java.util.ArrayList;
import java.util.List;

public final class Match {
    private static final List<String> CANDIDATES = new ArrayList<>();

    private Match() {
    }

    public static boolean contains(String candidate, CharSequence query) {
        CANDIDATES.add(candidate);
        if ("native-match".equals(candidate)) {
            return false;
        }
        return ("臺灣".equals(candidate) && "taiwan".contentEquals(query))
                || candidate.contains(query);
    }

    public static void clearCalls() {
        CANDIDATES.clear();
    }

    public static List<String> candidates() {
        return List.copyOf(CANDIDATES);
    }
}
