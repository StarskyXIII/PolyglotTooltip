package com.starskyxiii.polyglottooltip.compat.figura;

import java.util.function.Supplier;

public final class FiguraEmojiContext {
    private static final ThreadLocal<Integer> HOVER_NAME_DEPTH = new ThreadLocal<>();

    private FiguraEmojiContext() {
    }

    public static void enterHoverName() {
        Integer depth = HOVER_NAME_DEPTH.get();
        HOVER_NAME_DEPTH.set(depth == null ? 1 : depth + 1);
    }

    public static void exitHoverName() {
        Integer depth = HOVER_NAME_DEPTH.get();
        if (depth == null || depth <= 1) {
            HOVER_NAME_DEPTH.remove();
        } else {
            HOVER_NAME_DEPTH.set(depth - 1);
        }
    }

    public static boolean isInHoverName() {
        Integer depth = HOVER_NAME_DEPTH.get();
        return depth != null && depth > 0;
    }

    public static <T> T supplyInHoverName(Supplier<T> supplier) {
        enterHoverName();
        try {
            return supplier.get();
        } finally {
            exitHoverName();
        }
    }
}
