package com.starskyxiii.polyglottooltip.integration;

//? if neoforge && >=1.21.1 && <=1.21.1 {
import net.minecraft.client.Minecraft;

public final class SearchScreenRefresh {
    private SearchScreenRefresh() {
    }

    public interface Refreshable {
        void polyglot$refreshSearch();
    }

    public static void invalidate() {
        Minecraft minecraft = Minecraft.getInstance();
        Runnable refresh = () -> {
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

