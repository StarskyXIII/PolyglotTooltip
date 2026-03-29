package com.starskyxiii.polyglottooltip;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public final class Ae2VersionDetector {

    private static volatile Ae2Flavor ae2Flavor;
    private static final String GUI_INTERFACE_TERMINAL_CLASS =
        "appeng/client/gui/implementations/GuiInterfaceTerminal.class";
    // We deliberately inspect the raw class resource instead of loading the target class,
    // because late mixin selection must not trigger AE2 classloading ahead of Mixin.
    // The current heuristic assumes official rv3 exposes only the 2-arg
    // itemStackMatchesSearchTerm(ItemStack, String) signature, while AE2 Unofficial
    // exposes the 3-arg itemStackMatchesSearchTerm(ItemStack, String, boolean) variant.
    // If a future build ships both overloads, this detector should be revisited.
    private static final String OFFICIAL_METHOD_MARKER = "itemStackMatchesSearchTerm";
    private static final String OFFICIAL_METHOD_DESCRIPTOR =
        "(Lnet/minecraft/item/ItemStack;Ljava/lang/String;)Z";
    private static final String UNOFFICIAL_METHOD_DESCRIPTOR =
        "(Lnet/minecraft/item/ItemStack;Ljava/lang/String;Z)Z";

    private Ae2VersionDetector() {}

    public static boolean isOfficialAe2(Set<String> loadedMods) {
        if (loadedMods == null || !loadedMods.contains("appliedenergistics2")) {
            return false;
        }

        return detectFlavor() == Ae2Flavor.OFFICIAL;
    }

    public static boolean isUnofficialAe2(Set<String> loadedMods) {
        if (loadedMods == null || !loadedMods.contains("appliedenergistics2")) {
            return false;
        }

        return detectFlavor() == Ae2Flavor.UNOFFICIAL;
    }

    private static Ae2Flavor detectFlavor() {
        Ae2Flavor cached = ae2Flavor;
        if (cached != null) {
            return cached;
        }

        synchronized (Ae2VersionDetector.class) {
            if (ae2Flavor != null) {
                return ae2Flavor;
            }

            String classData = readClassResourceAsLatin1(GUI_INTERFACE_TERMINAL_CLASS);
            if (classData == null) {
                ae2Flavor = Ae2Flavor.NONE;
                return ae2Flavor;
            }

            if (classData.contains(OFFICIAL_METHOD_MARKER) && classData.contains(OFFICIAL_METHOD_DESCRIPTOR)) {
                ae2Flavor = Ae2Flavor.OFFICIAL;
                return ae2Flavor;
            }

            if (classData.contains(OFFICIAL_METHOD_MARKER) && classData.contains(UNOFFICIAL_METHOD_DESCRIPTOR)) {
                ae2Flavor = Ae2Flavor.UNOFFICIAL;
                return ae2Flavor;
            }

            ae2Flavor = Ae2Flavor.NONE;
            return ae2Flavor;
        }
    }

    private static String readClassResourceAsLatin1(String resourcePath) {
        ClassLoader classLoader = Ae2VersionDetector.class.getClassLoader();
        if (classLoader == null) {
            return null;
        }

        try {
            InputStream inputStream = classLoader.getResourceAsStream(resourcePath);
            if (inputStream == null) {
                return null;
            }

            try {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                return new String(outputStream.toByteArray(), StandardCharsets.ISO_8859_1);
            } finally {
                inputStream.close();
            }
        } catch (IOException ignored) {
            return null;
        }
    }

    private enum Ae2Flavor {
        NONE,
        OFFICIAL,
        UNOFFICIAL
    }
}
