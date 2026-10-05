package com.starskyxiii.polyglottooltip.integration;
//~ minecraft_116_packages

import net.minecraft.client.Minecraft;

public final class SearchScreenRefresh {
    private static long generation;
    private SearchScreenRefresh() {
    }

    public interface Refreshable {
        void polyglot$refreshSearch();
    }

    public static long generation() {
        return generation;
    }

    public static void invalidate() {
        Minecraft minecraft = Minecraft.getInstance();
        Runnable refresh = () -> {
            generation++;
            if (minecraft.screen instanceof Refreshable) {
                ((Refreshable) minecraft.screen).polyglot$refreshSearch();
            }
        };
        if (minecraft.isSameThread()) {
            refresh.run();
        } else {
            minecraft.execute(refresh);
        }
    }
}
