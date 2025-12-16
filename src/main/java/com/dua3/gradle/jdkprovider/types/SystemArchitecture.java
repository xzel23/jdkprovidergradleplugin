package com.dua3.gradle.jdkprovider.types;

import org.jspecify.annotations.NullMarked;

import java.util.Objects;

@NullMarked
public enum SystemArchitecture {
    AARCH64,
    AARCH32,
    X64;

    @Override
    public String toString() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static SystemArchitecture current() {
        String osArch = Objects.requireNonNull(System.getProperty("os.arch"), "os.arch system property not set");

        return parse(osArch);
    }

    public static SystemArchitecture parse(String osArch) {
        String normalized = osArch.toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "x86_64", "amd64", "x64" -> X64;
            case "aarch64", "arm64" -> AARCH64;
            case "arm", "arm32", "aarch32" -> AARCH32;
            default -> throw new IllegalStateException("Unknown architecture: " + osArch);
        };
    }
}
