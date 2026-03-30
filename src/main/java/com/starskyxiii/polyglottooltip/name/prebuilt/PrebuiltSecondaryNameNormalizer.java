package com.starskyxiii.polyglottooltip.name.prebuilt;

import java.util.LinkedHashMap;
import java.util.Map;

final class PrebuiltSecondaryNameNormalizer {

    private static final String ENGLISH_LANGUAGE_CODE = "en_US";
    private static final String ITEM_ALCHEMY_GEM = "manametalmod:ItemAlchemyGem";
    private static final String ITEM_FISH_BUCKETS = "manametalmod:ItemFishBuckets";
    private static final String ITEM_PET_EGGS = "manametalmod:ItemPetEggs1";
    private static final String ITEM_TRANSFORM_SLATES = "manametalmod:ItemTransformSlates";

    private static final String[] ALCHEMY_GEM_NAMES = new String[]{
        "Diamond",
        "Emerald",
        "Nether Quartz",
        "Lapis Lazuli",
        "Amber",
        "Amethyst",
        "Aquamarine",
        "Citrine",
        "Iolite",
        "Garnet",
        "Jade",
        "Moonstone",
        "Opal",
        "Ruby",
        "Sapphire",
        "Spinel",
        "Sunstone",
        "Tanzanite",
        "Tourmaline",
        "Zircon",
        "Chrysoberyl",
        "Turquoise",
        "Agate",
        "Jet",
        "Tiger's Eye"
    };

    private static final Map<Integer, String> FISH_BUCKET_TRANSLATIONS = new LinkedHashMap<Integer, String>();

    static {
        FISH_BUCKET_TRANSLATIONS.put(Integer.valueOf(0), "Raw Fish Water Bucket");
        FISH_BUCKET_TRANSLATIONS.put(Integer.valueOf(1), "Raw Salmon Water Bucket");
        FISH_BUCKET_TRANSLATIONS.put(Integer.valueOf(2), "Live Perch Water Bucket");
        FISH_BUCKET_TRANSLATIONS.put(Integer.valueOf(3), "Live Carp Water Bucket");
        FISH_BUCKET_TRANSLATIONS.put(Integer.valueOf(4), "Live Yellow Jack Water Bucket");
        FISH_BUCKET_TRANSLATIONS.put(Integer.valueOf(5), "Live Golden Carp Water Bucket");
        FISH_BUCKET_TRANSLATIONS.put(Integer.valueOf(6), "Live Ancient Bass Water Bucket");
        FISH_BUCKET_TRANSLATIONS.put(Integer.valueOf(7), "Live Black Dragon Fish Water Bucket");
    }

    private PrebuiltSecondaryNameNormalizer() {}

    static String normalize(String registryName, int damage, String languageCode, String displayName) {
        if (displayName == null) {
            return null;
        }

        String normalized = displayName.trim();
        if (normalized.isEmpty()) {
            return normalized;
        }
        if (!ENGLISH_LANGUAGE_CODE.equalsIgnoreCase(languageCode)) {
            return normalized;
        }

        normalized = normalizeAlchemyGem(registryName, damage, normalized);
        normalized = normalizeFishBucket(registryName, damage, normalized);
        normalized = normalizeArmorName(registryName, normalized);
        normalized = normalizePetEgg(registryName, damage, normalized);
        normalized = normalizeTransformationPowder(registryName, damage, normalized);

        return normalized;
    }

    private static String normalizeAlchemyGem(String registryName, int damage, String displayName) {
        if (!ITEM_ALCHEMY_GEM.equals(registryName)) {
            return displayName;
        }

        int gemIndex = damage < 0 ? -1 : damage / 9;
        if (gemIndex < 0 || gemIndex >= ALCHEMY_GEM_NAMES.length) {
            return displayName;
        }

        String prefix = stripTrailingNonAscii(displayName);
        if (prefix.isEmpty()) {
            return displayName;
        }

        return prefix + ' ' + ALCHEMY_GEM_NAMES[gemIndex];
    }

    private static String normalizeFishBucket(String registryName, int damage, String displayName) {
        if (!ITEM_FISH_BUCKETS.equals(registryName)) {
            return displayName;
        }

        String translated = FISH_BUCKET_TRANSLATIONS.get(Integer.valueOf(damage));
        return translated == null || translated.isEmpty() ? displayName : translated;
    }

    private static String normalizeArmorName(String registryName, String displayName) {
        String partName = getArmorPartName(registryName);
        if (partName == null) {
            return displayName;
        }

        String prefix = stripTrailingNonAscii(displayName);
        if (prefix.isEmpty()) {
            prefix = stripKnownArmorSuffix(displayName, partName);
        }
        if (prefix.isEmpty()) {
            return displayName;
        }

        return prefix + ' ' + partName;
    }

    private static String normalizePetEgg(String registryName, int damage, String displayName) {
        if (!ITEM_PET_EGGS.equals(registryName)) {
            return displayName;
        }

        String normalized = displayName;
        if (damage == 94) {
            normalized = "Big Black Tower Pet Egg";
        } else if (normalized.endsWith("Pet Egg") && !normalized.endsWith(" Pet Egg")) {
            normalized = normalized.substring(0, normalized.length() - "Pet Egg".length()).trim() + " Pet Egg";
        }

        return normalized;
    }

    private static String normalizeTransformationPowder(String registryName, int damage, String displayName) {
        if (!ITEM_TRANSFORM_SLATES.equals(registryName)) {
            return displayName;
        }

        String normalized = displayName;
        if (damage == 94) {
            normalized = "Big Black Tower Transformation Powder";
        } else if (normalized.endsWith("Transformation Powder")
            && !normalized.endsWith(" Transformation Powder")) {
            normalized = normalized.substring(
                0,
                normalized.length() - "Transformation Powder".length()).trim() + " Transformation Powder";
        }

        return normalized;
    }

    private static String getArmorPartName(String registryName) {
        if (registryName == null) {
            return null;
        }
        if (registryName.endsWith("_helmet")) {
            return "Helmet";
        }
        if (registryName.endsWith("_chestplate")) {
            return "Chestplate";
        }
        if (registryName.endsWith("_leggings")) {
            return "Leggings";
        }
        if (registryName.endsWith("_boots")) {
            return "Boots";
        }
        return null;
    }

    private static String stripKnownArmorSuffix(String displayName, String partName) {
        if (displayName == null || partName == null || displayName.isEmpty()) {
            return "";
        }

        if (displayName.endsWith(partName)) {
            return displayName.substring(0, displayName.length() - partName.length()).trim();
        }

        return "";
    }

    private static String stripTrailingNonAscii(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        int endIndex = value.length();
        while (endIndex > 0) {
            char current = value.charAt(endIndex - 1);
            if (current <= 127) {
                break;
            }
            endIndex--;
        }

        return value.substring(0, endIndex).trim();
    }
}
