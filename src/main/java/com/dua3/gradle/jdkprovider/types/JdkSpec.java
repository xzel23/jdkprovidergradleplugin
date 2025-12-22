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

import io.soabase.recordbuilder.core.RecordBuilder;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents specifications for a JDK.
 * <p>
 * Note:
 * Default values for `os`, `arch`, and `version` are resolved to the current system's
 * operating system, architecture, and version when not explicitly specified during construction.
 *
 * @param os the operating system family for the JDK
 * @param arch the system architecture for the JDK
 * @param version the version specification for the JDK
 * @param vendor the vendor specification for the JDK
 * @param nativeImageCapable whether the JDK supports native image compilation
 * @param javaFxBundled whether the JDK has JavaFX bundled
 */
@NullMarked
@RecordBuilder
public record JdkSpec(
        OSFamily os,
        SystemArchitecture arch,
        String version,
        String vendor,
        boolean nativeImageCapable,
        boolean javaFxBundled
) {
    private static final Logger LOG = Logging.getLogger(JdkSpec.class);

    /**
     * Generates a string representation of the {@code JdkSpec} object detailing its
     * characteristics, including version specification, operating system, architecture,
     * vendor, native image capability, and whether JavaFX is bundled. Each attribute is
     * included in the resulting string, separating individual components with a dash ('-').
     * <p>
     * Note: The string representation was chosen in a format that can be used as a filename.
     *
     * @return a string representation of the {@code JdkSpec} object, formatted as:
     *         "jdk-{version}-{os}-{arch}-{vendor}-{nativeImageCapable}-{javaFxBundled}".
     *         If any attribute is {@code null}, it is represented as "any" in the string.
     */
    @Override
    public String toString() {
        return Stream.of(
                        vendor,
                        version,
                        os,
                        arch,
                        nativeImageCapable ? "native_image" : null,
                        javaFxBundled ? "javafx" : null
                )
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.joining("-"));
    }
}
