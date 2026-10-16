package com.bsg.ai.services.extraction.summary.constant;

public enum Language {
    ENGLISH("English"),
    TURKISH("Turkish"),
    POLISH("Polish"),
    ARABIC("Arabic"),
    RUSSIAN("Russian"),
    GERMAN("German"),
    FRENCH("French"),
    SPANISH("Spanish"),
    ITALIAN("Italian");

    private final String displayName;

    Language(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Language fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Language value cannot be null or blank");
        }

        for (Language language : Language.values()) {
            if (language.name().equalsIgnoreCase(value)
                    || language.getDisplayName().equalsIgnoreCase(value)) {
                return language;
            }
        }

        throw new IllegalArgumentException("Unsupported language: " + value);
    }
}