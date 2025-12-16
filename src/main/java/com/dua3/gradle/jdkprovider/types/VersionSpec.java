package com.dua3.gradle.jdkprovider.types;

import org.gradle.api.JavaVersion;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

@NullMarked
public record VersionSpec(@Nullable Integer major, @Nullable Integer minor, @Nullable Integer patch) {

    public VersionSpec {
        // Valid states:
        // - any: (null, null, null)
        // - latest: (MAX, [any], [any])
        // - major only: (>=0, null, null)
        // - major+ : (>=0, MAX, null or MAX)
        // - major.minor: (>=0, >=0, null)
        // - major.minor+ : (>=0, >=0, MAX)
        // - major.minor.patch: (>=0, >=0, >=0)
        if ((major == null && (minor != null || patch != null))
                || (minor == null && patch != null)
                || (major != null && major < 0)
                || (minor != null && minor < 0)
                || (patch != null && patch < 0)) {
            throw new IllegalArgumentException(
                    "invalid version: major=" + major + ", minor=" + minor + ", patch=" + patch
            );
        }
    }

    @Override
    public String toString() {
        if (major == null) {
            return "any";
        }
        if (major == Integer.MAX_VALUE) {
            return "latest";
        }
        if (minor == null) {
            return Integer.toString(major);
        }
        if (minor == Integer.MAX_VALUE) {
            return major + "+";
        }
        if (patch == null) {
            return major + "." + minor;
        }
        if (patch == Integer.MAX_VALUE) {
            return major + "." + minor + "+";
        }
        return major + "." + minor + "." + patch;
    }

    public JavaVersion getJavaVersion() {
        if (major == null || major == Integer.MAX_VALUE) {
            throw new IllegalStateException("cannot convert " + this + " to JavaVersion");
        }
        return JavaVersion.toVersion(major);
    }

    public static VersionSpec current() {
        Runtime.Version runtimeVersion = Runtime.version();
        return new VersionSpec(runtimeVersion.feature(), runtimeVersion.interim(), runtimeVersion.update());
    }

    public static VersionSpec parse(String s) {
        if (s == null) {
            throw new IllegalArgumentException("version must not be null");
        }
        String str = s.trim();
        if (str.isEmpty()) {
            throw new IllegalArgumentException("empty version string");
        }

        String lower = str.toLowerCase(Locale.ROOT);
        if (lower.equals("any")) {
            return new VersionSpec(null, null, null);
        }
        if (lower.equals("latest")) {
            return new VersionSpec(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        // Patterns supported:
        //  - "<major>"
        //  - "<major>+"
        //  - "<major>.<minor>"
        //  - "<major>.<minor>+"
        //  - "<major>.<minor>.<patch>"
        boolean plus = lower.endsWith("+");
        String core = plus ? lower.substring(0, lower.length() - 1) : lower;

        if (core.endsWith(".")) {
            throw new IllegalArgumentException("unmatched '.'");
        }

        String[] parts = core.split("\\.");
        try {
            switch (parts.length) {
                case 1 -> {
                    int major = Integer.parseInt(parts[0]);
                    if (major < 0) throw new NumberFormatException();
                    if (plus) {
                        return new VersionSpec(major, Integer.MAX_VALUE, null);
                    }
                    return new VersionSpec(major, null, null);
                }
                case 2 -> {
                    int major = Integer.parseInt(parts[0]);
                    int minor = Integer.parseInt(parts[1]);
                    if (major < 0 || minor < 0) throw new NumberFormatException();
                    if (plus) {
                        return new VersionSpec(major, minor, Integer.MAX_VALUE);
                    }
                    return new VersionSpec(major, minor, null);
                }
                case 3 -> {
                    if (plus) {
                        throw new IllegalArgumentException("'+' is not supported after patch level: " + s);
                    }
                    int major = Integer.parseInt(parts[0]);
                    int minor = Integer.parseInt(parts[1]);
                    int patch = Integer.parseInt(parts[2]);
                    if (major < 0 || minor < 0 || patch < 0) throw new NumberFormatException();
                    return new VersionSpec(major, minor, patch);
                }
                default -> throw new IllegalArgumentException("invalid version format: " + s);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid numeric value in version: " + s, e);
        }
    }

    public boolean matches(@Nullable VersionSpec actual) {
        if (major == null) {
            return true; // any
        }
        if (actual == null) {
            return false;
        }
        if (major.equals(Integer.MAX_VALUE)) {
            return true; // latest - accept any actual
        }
        if (!major.equals(actual.major())) {
            return false;
        }

        // Minor
        if (minor == null) {
            return true;
        }
        if (minor.equals(Integer.MAX_VALUE)) {
            return true; // any minor
        }
        if (actual.minor() == null || !minor.equals(actual.minor())) {
            return false;
        }

        // Patch
        if (patch == null) {
            return true;
        }
        if (patch.equals(Integer.MAX_VALUE)) {
            return true; // any patch
        }
        return actual.patch() != null && patch.equals(actual.patch());
    }
}
