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

import java.util.List;
import java.util.Objects;

/**
 * Represents the different types of system architectures and provides utility methods
 * for working with architecture-related data.
 * <p>
 * Each enumeration value is associated with a list of aliases that can be used to identify
 * the architecture. This allows flexible mapping from architecture strings to the
 * corresponding enumeration value.
 */
@NullMarked
public enum SystemArchitecture {
    /**
     * Represents the AARCH32 system architecture, also known as ARM 32-bit architecture.
     */
    AARCH32("arm", "arm32", "aarch32", "armv6l", "armv6", "armv7", "armv7l", "armv8l", "armhf"),
    /**
     * Represents the 64-bit ARM architecture, commonly known as `aarch64` or `arm64`.
     */
    AARCH64("aarch64", "arm64"),
    /**
     * Represents the x86_64 (64-bit) system architecture with common aliases.
     */
    X64("x86_64", "amd64", "x64"),
    /**
     * Represents the 32-bit x86 system architecture.
     */
    X86_32("x86", "i386", "i486", "i586", "i686", "x86-32");

    private final List<String> aliases;

    SystemArchitecture(String... aliases) {
        this.aliases = List.of(aliases);
    }

    /**
     * Retrieves the list of alias names associated with the system architecture.
     *
     * @return a list of alias strings used to identify this system architecture
     */
    public List<String> aliases() {
        return aliases;
    }

    /**
     * Returns the lowercase string representation of the enumeration constant's name.
     *
     * @return the name of the enumeration constant in lowercase using the root locale.
     */
    @Override
    public String toString() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Determines the current system architecture based on the `os.arch` system property.
     * The method retrieves and parses the architecture information to return the corresponding
     * {@code SystemArchitecture} enumerated value.
     *
     * @return the {@code SystemArchitecture} representing the current system's architecture.
     * @throws NullPointerException if the `os.arch` system property is not set.
     * @throws IllegalStateException if the value of the `os.arch` property cannot be mapped
     *         to a known {@code SystemArchitecture}.
     */
    public static SystemArchitecture current() {
        String osArch = Objects.requireNonNull(System.getProperty("os.arch"), "os.arch system property not set");
        return parse(osArch);
    }

    /**
     * Parses the input string representing an operating system architecture and returns the corresponding
     * {@code SystemArchitecture} enumeration value.
     * <p>
     * The input string is normalized by converting it to lowercase and trimming any leading or trailing whitespace.
     * It is then compared against known aliases for each architecture. If a match is found, the corresponding
     * {@code SystemArchitecture} value is returned. Otherwise, an {@code IllegalStateException} is thrown.
     *
     * @param osArch the operating system architecture as a string
     * @return the corresponding {@code SystemArchitecture} enumeration value
     * @throws IllegalStateException if the input string does not match any known architecture aliases
     */
    public static SystemArchitecture parse(String osArch) {
        String normalized = osArch.toLowerCase(java.util.Locale.ROOT).strip();
        for (SystemArchitecture arch : values()) {
            if (arch.aliases.contains(normalized)) return arch;
        }
        throw new IllegalStateException("Unknown architecture: " + osArch);
    }
}
