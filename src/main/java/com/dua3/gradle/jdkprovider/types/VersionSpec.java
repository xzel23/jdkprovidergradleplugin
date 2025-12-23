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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents a version specification with a range defined by minimum and maximum versions.
 * This class allows specifying whether the boundaries of the range are inclusive or exclusive.
 * It provides utility methods for common version specifications, such as the latest version,
 * any version, or a specific version.
 *
 */
@NullMarked
public final class VersionSpec {
    private static final int MAX_COMPONENT_VALUE = Integer.MAX_VALUE;

    private static final Runtime.Version MIN_VERSION = Runtime.Version.parse("1");
    private static final Runtime.Version MAX_VERSION = Runtime.Version.parse(Integer.toString(MAX_COMPONENT_VALUE));

    private static final VersionSpec ANY = new VersionSpec(MIN_VERSION, MAX_VERSION, "any");
    private static final VersionSpec LATEST = new VersionSpec(MIN_VERSION, Runtime.Version.parse(Integer.toString(MAX_COMPONENT_VALUE - 1)), "latest");
    private static final VersionSpec LATEST_LTS = new VersionSpec(MIN_VERSION, Runtime.Version.parse(Integer.toString(MAX_COMPONENT_VALUE - 2)), "latest_lts");
    private final Runtime.Version min;
    private final Runtime.Version max;
    private final String text;

    private VersionSpec(Runtime.Version min, Runtime.Version max, String text) {
        if (min.compareTo(max) >= 0) {
            throw new IllegalArgumentException("min version must be < max version");
        }
        this.min = min;
        this.max = max;
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }

    /**
     * Creates a {@code VersionSpec} instance based on the specified runtime version.
     * The resulting {@code VersionSpec} includes the given version, the next version,
     * and the string representation of the version.
     *
     * @param v the {@code Runtime.Version} object representing the version to base the {@code VersionSpec} on
     * @return a {@code VersionSpec} instance constructed from the specified runtime version
     */
    public static VersionSpec of(Runtime.Version v) {
        return new VersionSpec(v, next(v), v.toString());
    }

    private static Runtime.Version supremum(Runtime.Version v) {
        return Runtime.Version.parse(v + ".1");
    }

    private static Runtime.Version next(Runtime.Version v) {
        if (v.equals(MAX_VERSION)) {
            return MAX_VERSION;
        }

        List<Integer> list = new ArrayList<>(v.version());
        if (list.getLast() == MAX_COMPONENT_VALUE) {
            list = list.subList(0, list.size() - 1);
        }
        list.set(list.size() - 1, list.getLast() + 1);
        return parseFromInts(list);
    }

    /**
     * Retrieves the current runtime version and constructs a VersionSpec object
     * representing the major, minor, patch, and build versions.
     *
     * @return a VersionSpec object representing the current runtime version, with the feature
     * version as the major, the interim version as the minor, the update
     * version as the patch, and the build version as the build.
     */
    public static VersionSpec current() {
        return of(Runtime.version());
    }

    /**
     * Creates a {@code VersionSpec} instance representing the latest version.
     * The latest version is defined as having the maximum possible major version value,
     * with minor, patch, and build versions unspecified.
     *
     * @return a {@code VersionSpec} instance representing the latest version.
     */
    public static VersionSpec latest() {
        return LATEST;
    }

    /**
     * Returns a {@code VersionSpec} instance representing the latest Long-Term Support (LTS) version.
     * The latest LTS version is defined as the most recent version officially designated as LTS.
     *
     * @return a {@code VersionSpec} instance representing the latest LTS version.
     */
    public static VersionSpec latestLts() {
        return LATEST_LTS;
    }

    /**
     * Creates a {@code VersionSpec} instance that matches any version.
     *
     * @return a {@code VersionSpec} instance representing any version.
     */
    public static VersionSpec any() {
        return ANY;
    }

    /**
     * Parses a string representation of a version specification.
     * <p>
     * Supported formats:
     * <ul>
     * <li>"any": matches any version</li>
     * <li>"latest": matches the latest version</li>
     * <li>"latest_lts": matches the latest LTS version</li>
     * <li>"X.Y.Z": matches a specific version</li>
     * <li>"X..Y": matches versions from X to Y (inclusive)</li>
     * <li>"X..&lt;Y": matches versions from X (inclusive) to Y (exclusive)</li>
     * <li>"&gt;X..Y": matches versions from X (exclusive) to Y (inclusive)</li>
     * <li>"&gt;X..&lt;Y": matches versions from X (exclusive) to Y (exclusive)</li>
     * <li>"&lt;=X": matches versions up to and including X</li>
     * <li>"&lt;X": matches versions less than X</li>
     * <li>"&gt;=X": matches versions from X onwards</li>
     * <li>"&gt;X": matches versions greater than X</li>
     * </ul>
     *
     * @param s the string to parse
     * @return a VersionSpec object representing the parsed specification
     * @throws IllegalArgumentException if the string cannot be parsed
     */
    public static VersionSpec parse(String s) {
        s = s.strip();

        if (s.isBlank()) {
            throw new IllegalArgumentException("Version specification cannot be blank");
        }

        // Handle special cases
        return switch (s) {
            case "latest" -> LATEST;
            case "latest_lts" -> LATEST_LTS;
            case "any" -> ANY;
            default -> {
                String[] parts = s.split("\\.\\.");
                yield switch (parts.length) {
                    case 1 -> parseSingleVersion(s, true);
                    case 2 -> parseVersionRange(s, parts[0], parts[1]);
                    default -> throw new IllegalArgumentException("Invalid version specification: " + s);
                };
            }
        };
    }

    private static VersionSpec parseVersionRange(String s, String sMin, String sMax) {
        VersionSpec min = parseSingleVersion(sMin, false);
        VersionSpec max = parseSingleVersion(sMax, false);
        return new VersionSpec(min.min, max.max, s);
    }

    private static Runtime.Version replaceLastComponent(Runtime.Version v, int value) {
        List<Integer> list = new ArrayList<>(v.version());
        if (!list.isEmpty()) {
            list.set(list.size() - 1, value);
        }
        return parseFromInts(list);
    }

    private static Runtime.Version parseFromInts(List<Integer> list) {
        return Runtime.Version.parse(list.stream().map(Object::toString).collect(Collectors.joining(".")));
    }

    private static VersionSpec parseSingleVersion(String s, boolean allowSuffix) {
        // handle format: x+, x.y+
        if (allowSuffix && s.endsWith("+")) {
            Runtime.Version min = Runtime.Version.parse(s.substring(0, s.length() - 1));
            Runtime.Version max = replaceLastComponent(min, MAX_COMPONENT_VALUE);
            return new VersionSpec(min, max, s);
        }

        // handle format: x.*, x.y.*
        if (allowSuffix && s.endsWith(".*")) {
            Runtime.Version min = Runtime.Version.parse(s.substring(0, s.length() - 2));
            Runtime.Version max = Runtime.Version.parse(min + "." + MAX_COMPONENT_VALUE);
            return new VersionSpec(min, max, s);
        }

        // handle format: >=x, >=x.y
        if (s.startsWith(">=")) {
            Runtime.Version min = Runtime.Version.parse(s.substring(2));
            return new VersionSpec(min, MAX_VERSION, s);
        }

        // handle format: >=x, >=x.y
        if (s.startsWith(">")) {
            Runtime.Version min = Runtime.Version.parse(s.substring(1));
            return new VersionSpec(supremum(min), MAX_VERSION, s);
        }

        // handle format: <=x, <=x.y
        if (s.startsWith("<=")) {
            Runtime.Version max = supremum(Runtime.Version.parse(s.substring(2)));
            return new VersionSpec(MIN_VERSION, max, s);
        }

        // handle format: >x, >x.y
        if (s.startsWith("<")) {
            Runtime.Version max = Runtime.Version.parse(s.substring(2));
            return new VersionSpec(MIN_VERSION, max, s);
        }

        // otherwise, it must be a simple version
        return of(Runtime.Version.parse(s));
    }

    /**
     * Determines if this instance refers to a fixed feature only, i.e., represents a single Java feature version
     * with no interim, update, etc fields set.
     *
     * @return {@code true} if this instances refers to a single Java feature version, otherwise {@code false}.
     */
    public boolean isFixedFeature() {
        return text.matches("^\\d+$");
    }

    /**
     * Determines if the specified runtime version falls within the range defined by
     * the current {@code VersionSpec} instance.
     *
     * @param v the {@code Runtime.Version} object to check against the version range
     * @return {@code true} if the specified version is greater than or equal to the
     *         minimum version and less than the maximum version; {@code false} otherwise
     */
    public boolean matches(Runtime.Version v) {
        return min.compareTo(v) <= 0 && max.compareTo(v) > 0;
    }

    /**
     * Retrieves the minimum runtime version specified by this {@code VersionSpec}.
     *
     * @return the minimum runtime version
     */
    public Runtime.Version min() {
        return min;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof VersionSpec that)) return false;
        return Objects.equals(min, that.min) &&
                Objects.equals(max, that.max) &&
                Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(min, max, text);
    }

}
