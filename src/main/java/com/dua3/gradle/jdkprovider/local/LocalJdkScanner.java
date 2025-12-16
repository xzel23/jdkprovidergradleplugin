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

package com.dua3.gradle.jdkprovider.local;

import com.dua3.gradle.jdkprovider.types.JdkSpec;
import com.dua3.gradle.jdkprovider.types.OSFamily;
import com.dua3.gradle.jdkprovider.types.SystemArchitecture;
import com.dua3.gradle.jdkprovider.types.VersionSpec;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.jvm.inspection.JvmVendor;
import org.gradle.jvm.toolchain.JvmVendorSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Scans the local machine for JDK installations.
 */
public final class LocalJdkScanner {
    private static final Pattern PATTERN_STRIP_TRAILING_NON_DIGITS = Pattern.compile("[^\\d]+$");

    private static final Logger LOGGER = Logging.getLogger(LocalJdkScanner.class);

    /**
     * Constructs a new instance of {@code LocalJdkScanner}.
     * This class is responsible for scanning the local system to discover installed JDKs,
     * leveraging common locations such as the system's {@code JAVA_HOME} environment variable
     * or paths specified in configuration properties (e.g., Gradle's installation paths).
     * When instantiated, the scanner is ready to perform discovery operations.
     */
    public LocalJdkScanner() {
    }

    /**
     * Try to find JDK homes from typical locations: JAVA_HOME and Gradle's installation paths property.
     *
     * @return JDK homes
     */
    public List<JdkInstallation> getInstalledJdks() {
        List<Path> homes = new ArrayList<>();

        // JAVA_HOME
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) {
            Path p = Paths.get(javaHome).toAbsolutePath().normalize();
            if (Files.isDirectory(p)) {
                LOGGER.debug("[Java Scanner] Found JAVA_HOME={}", p);
                homes.add(p);
            }
        }

        // org.gradle.java.installations.paths (comma separated)
        String gradlePaths = System.getProperty("org.gradle.java.installations.paths", "");
        if (!gradlePaths.isBlank()) {
            for (String s : gradlePaths.split(",")) {
                Path p = Paths.get(s.trim()).toAbsolutePath().normalize();
                if (Files.isDirectory(p)) {
                    LOGGER.debug("[Java Scanner] Found JDK installation at {}", p);
                    homes.add(p);
                }
            }
        }

        return homes.stream().map(LocalJdkScanner::readJdkSpec).filter(Optional::isPresent).map(Optional::get).toList();
    }

    /**
     * Attempts to read the JDK spec by reading the release file.
     *
     * @param jdkHome JDK home
     * @return JDK spec
     */
    public static Optional<JdkInstallation> readJdkSpec(Path jdkHome) {
        int major = -1;
        int minor = -1;
        int patch = -1;
        OSFamily os = null;
        SystemArchitecture arch = null;
        JvmVendorSpec vendor = null;
        Boolean nativeImageCapable = null;
        boolean javafxBundled = false;

        // Try release file
        Path release = jdkHome.resolve("release");
        if (!Files.isRegularFile(release)) {
            LOGGER.debug("[Java Scanner] JDK at {} does not contain a release file", jdkHome);
            return Optional.empty();
        }

        try {
            List<String> lines = Files.readAllLines(release);
            for (String line : lines) {
                // split into attribute and value
                int splitAt = line.indexOf('=');
                if (splitAt < 0) {
                    continue;
                }
                String attribute = line.substring(0, splitAt).trim();
                String value = line.substring(splitAt + 1).trim();
                int q1 = value.indexOf('"');
                int q2 = value.lastIndexOf('"');
                if (q1 >= 0 && q2 > q1) {
                    value = value.substring(q1 + 1, q2);
                }

                // process attributes
                switch (attribute) {
                    case "JAVA_VERSION" -> {
                        String[] parts = value.split("\\.");
                        major = parseVersionPart(parts, 0, -1);
                        minor = parseVersionPart(parts, 1, 0);
                        patch = parseVersionPart(parts, 2, 0);
                    }
                    case "OS_NAME" -> os = OSFamily.parse(value);
                    case "OS_ARCH" -> arch = SystemArchitecture.parse(value);
                    case "IMPLEMENTOR" -> vendor = getVendorFromImplementor(value);
                    case "MODULES" -> javafxBundled = getJavaFXBundledFromModules(value);
                    case "GRAALVM_VERSION" -> nativeImageCapable = true; // GraalVM generally provides native-image
                }
            }

            if (major < 0) {
                LOGGER.debug("[Java Scanner] Failed to determine JDK version from release file: {}", release);
                return Optional.empty();
            }
            if (arch == null) {
                LOGGER.debug("[Java Scanner] Failed to determine JDK architecture from release file: {}", release);
                return Optional.empty();
            }
            if (os == null) {
                LOGGER.debug("[Java Scanner] Failed to determine Operating System from release file: {}", release);
                return Optional.empty();
            }
            if (vendor == null) {
                LOGGER.debug("[Java Scanner] Failed to determine JDK vendor from release file: {}", release);
            }

            // If GraalVM flag wasn't present in release file, try to detect native-image tool presence
            if (nativeImageCapable == null) {
                Path bin = jdkHome.resolve("bin");
                boolean hasNativeImage = Files.isExecutable(bin.resolve("native-image"))
                        || Files.isExecutable(bin.resolve("native-image.cmd"))
                        || Files.isExecutable(bin.resolve("native-image.exe"));
                if (hasNativeImage) {
                    nativeImageCapable = true;
                }
            }

            VersionSpec version = new VersionSpec(major, minor, patch);
            JdkSpec jdkSpec = new JdkSpec(os, arch, version, vendor, nativeImageCapable, javafxBundled);
            JdkInstallation jdkInstallation = new JdkInstallation(jdkHome, jdkSpec);
            LOGGER.debug("[Java Scanner] found Installation: {}", jdkInstallation);

            return Optional.of(jdkInstallation);
        } catch (IOException e) {
            LOGGER.debug("[Java Scanner] Failed to read JDK release file: {}", release, e);
            return Optional.empty();
        }
    }

    /**
     * Determines if the JavaFX module is bundled from the provided module list.
     *
     * @param value A space-separated string containing the names of modules within a JDK.
     * @return {@code true} if the JavaFX base module ("javafx.base") is included in the provided module list,
     *         {@code false} otherwise.
     */
    private static boolean getJavaFXBundledFromModules(String value) {
        return Arrays.asList(value.split("\\s")).contains("javafx.base");
    }

    /**
     * Converts an implementor's identifier into a human-readable vendor name.
     *
     * @param value The identifier string representing the implementor (e.g., from the release file).
     * @return The display name of the vendor associated with the implementor, or an appropriate default value.
     */
    private static JvmVendorSpec getVendorFromImplementor(String value) {
        String displayName = JvmVendor.fromString(value).getDisplayName();
        // Use Gradle's matching to allow flexible matching of vendor strings
        return JvmVendorSpec.matching(displayName);
    }

    /**
     * Parses a specific part of a version string array into an integer.
     * If the requested part is not available or cannot be parsed, a default value is returned.
     *
     * @param parts The array of version string parts, typically split by a delimiter (e.g., ".").
     * @param idx   The index of the part in the array to parse.
     * @param def   The default value to return if the specified part is unavailable or not a valid integer.
     * @return The parsed integer value of the specified version part, or the default value if parsing fails or the part is not present.
     */
    private static int parseVersionPart(String[] parts, int idx, int def) {
        try {
            return parts.length > idx
                    ? Integer.parseInt(PATTERN_STRIP_TRAILING_NON_DIGITS.matcher(parts[idx]).replaceFirst(""))
                    : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * Filters the list of installed JDKs to identify those that are compatible based on
     * the provided {@link JdkSpec} instance.
     *
     * @param jdkSpec The {@link JdkSpec} to match against.
     * @return A list of {@code JdkInstallation} instances that meet the compatibility requirements.
     */
    public List<JdkInstallation> getCompatibleInstalledJdks(JdkSpec jdkSpec) {
        LOGGER.debug("[Java Scanner] Looking for JDKs compatible with {}", jdkSpec);
        return getInstalledJdks()
                .stream()
                .filter(jdkInstallation ->
                        jdkInstallation.jdkSpec().isCompatibleWith(jdkSpec)
                )
                .toList();
    }
}
