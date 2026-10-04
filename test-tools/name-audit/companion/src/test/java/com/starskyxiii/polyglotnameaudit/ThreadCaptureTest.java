package com.starskyxiii.polyglotnameaudit;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class ThreadCaptureTest {
    @Test void backgroundWorkCannotContaminateCurrentSample() {
        ThreadCapture<String> capture = new ThreadCapture<>();
        List<String> values = capture.begin();
        try {
            capture.capture("sample");
            CompletableFuture.runAsync(() -> {
                capture.capture("unscoped background");
                List<String> background = capture.begin();
                try { capture.capture("scoped background"); }
                finally { capture.end(); }
                assertEquals(List.of("scoped background"), background);
            }).join();
            assertEquals(List.of("sample"), values);
        } finally { capture.end(); }
        capture.capture("outside scope");
        assertEquals(List.of("sample"), values);
    }

    @Test void nestedScopesAreRejectedAndExceptionCleanupAllowsNextSample() {
        ThreadCapture<String> capture = new ThreadCapture<>();
        try {
            capture.begin();
            assertThrows(IllegalStateException.class, capture::begin);
        } finally { capture.end(); }
        List<String> next = capture.begin();
        try { capture.capture("next"); }
        finally { capture.end(); }
        assertEquals(List.of("next"), next);
    }
}
