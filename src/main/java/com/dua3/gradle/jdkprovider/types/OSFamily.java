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

import java.util.Locale;

/**
 * Enum representing various families of operating systems.
 * It provides utilities for determining the current operating system
 * and parsing a string representation of an operating system name into
 * an {@code OSFamily} instance.
 */
@NullMarked
public enum OSFamily {
    /**
     * Represents the AIX operating system family.
     */
    AIX,
    /**
     * Represents the FreeBSD operating system family.
     */
    FREE_BSD,
    /**
     * Represents the Linux operating system family.
     */
    LINUX,
    /**
     * Represents the macOS family of operating systems.
     */
    MACOS,
    /**
     * Represents the QNX operating system family.
     */
    QNX,
    /**
     * Enumeration constant representing the Solaris operating system.
     */
    SOLARIS,
    /**
     * Represents the Microsoft Windows family of operating systems.
     */
    WINDOWS;

    /**
     * Represents the pre-determined {@code OSFamily} constant corresponding to the
     * current operating system of the environment in which the application is running.
     */
    private static final OSFamily CURRENT_OS = parse(System.getProperty("os.name").toLowerCase(Locale.ROOT));

    /**
     * Returns the name of this enum constant converted to lowercase.
     *
     * @return the lowercase string representation of this enum constant
     */
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Determines the current operating system based on the system property "os.name".
     *
     * @return the {@code OSFamily} corresponding to the current operating system.
     * @throws IllegalStateException if the operating system is unknown or unrecognized.
     */
    public static OSFamily current() {
        return CURRENT_OS;
    }

    /**
     * Parses the provided operating system name into an {@code OSFamily} instance.
     * <p>
     * This method matches the given operating system name against predefined patterns
     * to determine its corresponding {@code OSFamily}.
     *
     * @param osName the name of the operating system, typically obtained from system properties
     *               or other sources, e.g., "os.name". It is case-insensitive.
     * @return the matching {@code OSFamily} instance for the given operating system name
     * @throws IllegalStateException if the operating system name does not match any known {@code OSFamily}
     */
    public static OSFamily parse(String osName) {
        osName = osName.toLowerCase(Locale.ROOT);
        if (osName.contains("mac") || osName.contains("darwin")) {
            return MACOS;
        } else if (osName.contains("win")) {
            return WINDOWS;
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            if (osName.contains("aix")) {
                return AIX;
            }
            return LINUX;
        } else if (osName.contains("sunos") || osName.contains("solaris")) {
            return SOLARIS;
        } else if (osName.contains("freebsd")) {
            return FREE_BSD;
        } else if (osName.contains("qnx")) {
            return QNX;
        }

        throw new IllegalStateException("Unknown operating system: " + osName);
    }
}
