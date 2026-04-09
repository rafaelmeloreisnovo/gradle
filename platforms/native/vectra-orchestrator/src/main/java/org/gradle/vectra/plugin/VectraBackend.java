package org.gradle.vectra.plugin;

import java.util.Locale;

public enum VectraBackend {
    JAVA,
    C,
    ASM;

    public static VectraBackend parse(String value) {
        if (value == null) {
            return JAVA;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (VectraBackend candidate : values()) {
            if (candidate.name().equals(normalized)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unsupported Vectra backend '" + value + "'. Allowed values: java, c, asm.");
    }
}
