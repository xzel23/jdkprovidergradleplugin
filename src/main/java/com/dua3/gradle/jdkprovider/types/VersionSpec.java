// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 Axel Howind
// This file is part of the JDK Provider Gradle Plugin.
// The JDK Provider Gradle Plugin is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
// The JDK Provider Gradle Plugin is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
// You should have received a copy of the GNU General Public License
// along with this program. If not, see https://www.gnu.org/licenses/

package com.dua3.gradle.jdkprovider.types;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

/**
 * A representation of a version specification that supports major, minor, and patch components. 
 * This class provides methods for parsing and matching version specifications, while enforcing
 * constraints on the valid combinations of version components.
 *
 * @param major the major version component, null for "any", Integer.MAX_VALUE for "latest" or when used with "+"
 * @param minor the minor version component, null when not specified, Integer.MAX_VALUE when used with "+"
 * @param patch the patch version component, null when not specified, Integer.MAX_VALUE when used with "+"
 */
@NullMarked
public record VersionSpec(@Nullable Integer major, @Nullable Integer minor, @Nullable Integer patch) implements Comparable<VersionSpec> {

    /**
     * Canonical constructor.
     * <p>
     * Checks for valid version components.
     *
     * @param major the major version component
     * @param minor the minor version component
     * @param patch the patch version component
     *
     * @throws IllegalArgumentException if the version components are invalid.
     */
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

    /**
     * Constructs a string representation of the VersionSpec object based on its major, minor, and patch fields.
     * The representation follows these rules:
     * <ul>
     * <li>If the major version is null, returns "any".
     * <li>If the major version is Integer.MAX_VALUE, returns "latest".
     * <li>If the minor version is null, returns the major version as a string.
     * <li>If the minor version is Integer.MAX_VALUE, appends "+" to the major version.
     * <li>If the patch version is null, returns the major and minor versions joined by a period.
     * <li>If the patch version is Integer.MAX_VALUE, appends "+" to the major and minor versions.
     * <li>Otherwise, returns the major, minor, and patch versions joined by periods.
     * </ul>
     *
     * @return the string representation of the version specification.
     */
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

    /**
     * Retrieves the current runtime version and constructs a VersionSpec object
     * representing the major, minor, and patch versions.
     *
     * @return a VersionSpec object representing the current runtime version, with the feature
     *         version as the major, the interim version as the minor, and the update
     *         version as the patch.
     */
    public static VersionSpec current() {
        Runtime.Version runtimeVersion = Runtime.version();
        return new VersionSpec(runtimeVersion.feature(), runtimeVersion.interim(), runtimeVersion.update());
    }

    /**
     * Parses a version string into a {@code VersionSpec} object.
     * The method supports the following version formats:
     * <ul>
     *  <li>"any" (case-insensitive): represents any version.
     *  <li>"latest" (case-insensitive): represents the latest version.
     *  <li>"&lt;major&gt;": a specific major version.
     *  <li>"&lt;major&gt;+": any version with a major version greater than or equal to the specified value.
     *  <li>"&lt;major&gt;.&lt;minor&gt;": a specific major and minor version.
     *  <li>"&lt;major&gt;.&lt;minor&gt;+": any version with the specified major version and a minor version greater than or equal to the specified value.
     *  <li>"&lt;major&gt;.&lt;minor&gt;.&lt;patch&gt;": a specific major, minor, and patch version.
     * </ul>
     * <p>
     * Invalid formats or negative version numbers result in an {@code IllegalArgumentException}.
     *
     * @param s the version string to parse; must not be {@code null} or empty.
     * @return a {@code VersionSpec} instance corresponding to the parsed version string.
     * @throws IllegalArgumentException if the version string is {@code null},
     *                                  empty, improperly formatted, or contains invalid numeric values.
     */
    public static VersionSpec parse(@Nullable String s) {
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
                    if (major < 0) throw new IllegalArgumentException("negative major version: " + s);
                    if (plus) {
                        return new VersionSpec(major, Integer.MAX_VALUE, null);
                    }
                    return new VersionSpec(major, null, null);
                }
                case 2 -> {
                    int major = Integer.parseInt(parts[0]);
                    int minor = Integer.parseInt(parts[1]);
                    if (major < 0 || minor < 0) throw new IllegalArgumentException("negative version component: " + s);
                    if (plus) {
                        return new VersionSpec(major, minor, Integer.MAX_VALUE);
                    }
                    return new VersionSpec(major, minor, null);
                }
                case 3 -> {
                    if (plus) {
                        throw new IllegalArgumentException("'+' is not supported after the patch level: " + s);
                    }
                    int major = Integer.parseInt(parts[0]);
                    int minor = Integer.parseInt(parts[1]);
                    int patch = Integer.parseInt(parts[2]);
                    if (major < 0 || minor < 0 || patch < 0) throw new IllegalArgumentException("negative version component: " + s);
                    return new VersionSpec(major, minor, patch);
                }
                default -> throw new IllegalArgumentException("invalid version format: " + s);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid numeric value in version: " + s, e);
        }
    }

    /**
     * Determines whether the given {@code VersionSpec} object matches the current version 
     * specification based on the major, minor, and patch version fields.
     * Comparison logic considers the following rules:
     * <ul>
     *  <li>If the major version of the current object is {@code null}, any version matches.
     *  <li>If the major version is {@code Integer.MAX_VALUE}, it represents "latest" 
     *      and matches any given version.
     *  <li>If the minor version of the current object is {@code null}, all major-matching 
     *      versions are accepted.
     *  <li>If the minor version is {@code Integer.MAX_VALUE}, it indicates acceptance 
     *      of any minor version.
     *  <li>If the patch version of the current object is {@code null}, all major and minor 
     *      matching versions are accepted.
     *  <li>If the patch version is {@code Integer.MAX_VALUE}, it indicates acceptance 
     *      of any patch version.
     * </ul>
     *
     * @param actual the {@code VersionSpec} object to compare against; can be {@code null}. 
     *               If {@code null}, this method returns {@code false} unless the major 
     *               version of the current object is {@code null} or {@code Integer.MAX_VALUE}.
     * @return {@code true} if the provided {@code VersionSpec} matches the current version 
     *         specification, otherwise {@code false}.
     */
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

    @Override
    public int compareTo(VersionSpec other) {
        if (!Objects.equals(major, other.major)) {
            if (major == null) return -1;
            if (other.major == null) return 1;
            return major.compareTo(other.major);
        }
        if (!Objects.equals(minor, other.minor)) {
            if (minor == null) return -1;
            if (other.minor == null) return 1;
            return minor.compareTo(other.minor);
        }
        if (!Objects.equals(patch, other.patch)) {
            if (patch == null) return -1;
            if (other.patch == null) return 1;
            return patch.compareTo(other.patch);
        }
        return 0;
    }
}
