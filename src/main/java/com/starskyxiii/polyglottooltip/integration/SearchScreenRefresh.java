package com.starskyxiii.polyglottooltip.integration;

//? if neoforge && >=1.21.1 && <=1.21.1 {
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
            if (minecraft.screen instanceof Refreshable screen) {
                screen.polyglot$refreshSearch();
            }
        };
        if (minecraft.isSameThread()) {
            refresh.run();
        } else {
            minecraft.execute(refresh);
        }
    }
}
//?}
