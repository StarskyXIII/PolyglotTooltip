package com.starskyxiii.polyglottooltip.testbridge;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Bridge settings from a file when the launcher owns the JVM arguments: {@code polyglot-devbridge.properties}
 * in the game directory (the working directory of the client) supplies every {@code clientdevbridge.*} key
 * that the command line did not. Applied once, before the first setting is read.
 */
public final class BridgeProperties {
    public static final String FILE = "polyglot-devbridge.properties";
    private static boolean applied;

    private BridgeProperties() {}

    public static synchronized void apply() {
        if (applied) return;
        applied = true;
        Path file = Paths.get(System.getProperty("user.dir", "."), FILE);
        if (!Files.isRegularFile(file)) return;
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) { properties.load(reader); }
        catch (IOException error) { return; }
        // Product diagnostics (polyglottooltip.*) travel the same way, since launcher JVM arguments cannot be edited.
        for (String key : properties.stringPropertyNames())
            if ((key.startsWith("clientdevbridge.") || key.startsWith("polyglottooltip.")) && System.getProperty(key) == null)
                System.setProperty(key, properties.getProperty(key));
    }
}
