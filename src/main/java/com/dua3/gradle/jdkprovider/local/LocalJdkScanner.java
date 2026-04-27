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

import com.dua3.gradle.jdkprovider.provision.JdkProvisioner;
import com.dua3.gradle.jdkprovider.types.JdkQuery;
import com.dua3.gradle.jdkprovider.types.JdkSpec;
import com.dua3.gradle.jdkprovider.types.OSFamily;
import com.dua3.gradle.jdkprovider.types.SystemArchitecture;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.jvm.inspection.JvmVendor;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Scans the local machine for JDK installations.
 */
public final class LocalJdkScanner {

    private static final Logger LOGGER = Logging.getLogger(LocalJdkScanner.class);

    private final Map<String, String> environment;
    private final Path cacheDir;

    /**
     * Constructs a new instance of {@code LocalJdkScanner}.
     * This class is responsible for scanning the local system to discover installed JDKs,
     * leveraging common locations such as the system's {@code JAVA_HOME} environment variable
     * or paths specified in configuration properties (e.g., Gradle's installation paths).
     * When instantiated, the scanner is ready to perform discovery operations.
     */
    public LocalJdkScanner() {
        this(System.getenv(), JdkProvisioner.getCachedJdksDir());
    }

    /**
     * Constructs a new instance of {@code LocalJdkScanner} with a custom environment.
     *
     * @param environment the environment variables to use for scanning
     */
    LocalJdkScanner(Map<String, String> environment) {
        this(environment, JdkProvisioner.getCachedJdksDir());
    }

    /**
     * Constructs a new instance of {@code LocalJdkScanner} with a custom environment and cache directory.
     *
     * @param environment the environment variables to use for scanning
     * @param cacheDir    the cache directory to scan for JDKs
     */
    LocalJdkScanner(Map<String, String> environment, Path cacheDir) {
        this.environment = environment;
        this.cacheDir = cacheDir;
    }

    /**
     * Determines if the current operating system is Windows.
     *
     * @return true if the operating system is Windows, false otherwise
     */
    private static boolean isWindows() {
        return OSFamily.current() == OSFamily.WINDOWS;
    }

    /**
     * Attempts to locate the Java executable within the specified Java home directory.
     *
     * @param home the path to the Java home directory
     * @return the path to the Java executable if found, or null if not found
     */
    private static @Nullable Path findJavaExecutable(Path home) {
        Path bin = home.resolve("bin");
        Path java = bin.resolve(isWindows() ? "java.exe" : "java");
        if (Files.isRegularFile(java)) return java;
        return null;
    }

    /**
     * Detects the home directory of a JDK installation by analyzing the given extracted root directory.
     * The method searches for well-known JDK layouts, such as
     * <ol>
     * <li>The presence of a 'bin/java' executable directly under the extracted root.
     * <li>A single subdirectory under the extracted root containing a 'bin/java' executable.
     * <li>macOS-style JDK bundles, where 'Contents/Home/bin/java' exists within a subdirectory.
     * <li>macOS-style JDK bundles directly under the extracted root ('Contents/Home/bin/java').
     * </ol>
     *
     * @param jdkRoot the root path of the extracted directory to analyze for a JDK home.
     * @return the path to the JDK home directory if detected; otherwise, null.
     */
    public static Path detectJdkHome(Path jdkRoot) {
        // Common layouts:
        // 1) extractedRoot/bin/java exists -> home
        Path direct = findJavaExecutable(jdkRoot);
        if (direct != null) return jdkRoot;

        // 2) extractedRoot/<single>/bin/java
        try (var stream = Files.list(jdkRoot)) {
            var it = stream.filter(Files::isDirectory).iterator();
            if (it.hasNext()) {
                Path first = it.next();
                if (!it.hasNext()) { // single dir
                    Path nested = findJavaExecutable(first);
                    if (nested != null) return first;
                    // 3) macOS bundles: <dir>/Contents/Home/bin/java
                    Path macHome = first.resolve("Contents").resolve("Home");
                    Path macJava = findJavaExecutable(macHome);
                    if (macJava != null) return macHome;
                }
            }
        } catch (IOException e) {
            LOGGER.debug("could not detect JDK home: {}", jdkRoot, e);
            return null;
        }
        // 4) macOS bundles directly under extractedRoot: Contents/Home
        Path macHomeDirect = jdkRoot.resolve("Contents").resolve("Home");
        if (findJavaExecutable(macHomeDirect) != null) return macHomeDirect;

        return null;
    }

    /**
     * Try to find JDK homes from typical locations: JAVA_HOME and Gradle's installation paths property.
     *
     * @return JDK homes
     */
    public List<JdkInstallation> getInstalledJdks() {
        Set<Path> homes = new LinkedHashSet<>();

        // JAVA_HOME Variables - in GitHub CI, several JAVA_HOME_* variables are set, so do not only test JAVA_HOME.
        environment.entrySet().stream()
                .filter(e -> e.getKey().startsWith("JAVA_HOME"))
                .filter(e -> !e.getValue().isBlank())
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    Path candidatePath = Paths.get(e.getValue()).toAbsolutePath().normalize();
                    if (Files.isDirectory(candidatePath)) {
                        LOGGER.debug("[JDK Provider - JDK Scanner] Found candidate in {}={}", e.getKey(), candidatePath);
                        homes.add(candidatePath);
                    } else {
                        LOGGER.debug("[JDK Provider - JDK Scanner] {}={} is not a directory", e.getKey(), candidatePath);
                    }
                });

        // system dependent JDK detection
        switch (OSFamily.current()) {
            case MACOS -> detectInstalledJdkMacOs(homes);
            case LINUX -> detectInstalledJdkLinux(homes);
            case WINDOWS -> detectInstalledJdkWindows(homes);
            default -> LOGGER.debug("[JDK Provider - JDK Scanner] No JDK detection available for current OS: {}", OSFamily.current());
        }

        // org.gradle.java.installations.paths (comma separated)
        String gradlePaths = System.getProperty("org.gradle.java.installations.paths", "");
        if (!gradlePaths.isBlank()) {
            for (String s : gradlePaths.split(",")) {
                Path p = Paths.get(s.trim()).toAbsolutePath().normalize();
                if (Files.isDirectory(p)) {
                    LOGGER.debug("[JDK Provider - JDK Scanner] Found candidate JDK installation: {}", p);
                    homes.add(p);
                }
            }
        }

        // JDK Provider cache
        if (Files.isDirectory(cacheDir)) {
            try (Stream<Path> paths = Files.list(cacheDir)) {
                paths
                        .filter(Files::isDirectory)
                        .forEach(p -> {
                            LOGGER.debug("[JDK Provider - JDK Scanner] Found candidate JDK installation in cache dir: {}", p);
                            homes.add(p);
                        });
            } catch (IOException e) {
                LOGGER.warn("[JDK Provider - JDK Scanner] Failed to scan cache directory {}: {}", cacheDir, e.getMessage());
            }
        }

        return homes.stream()
                .map(LocalJdkScanner::readJdkSpec)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    /**
     * Detects and collects the filesystem paths of installed JDKs on macOS by scanning
     * common locations where JDKs are typically installed.
     * <p>
     * This method collects candidate JDK installation paths and adds them to the provided list
     * if the detected paths are valid directories.
     *
     * @param homes a collection that will be populated with the detected JDK installation paths on macOS
     */
    private static void detectInstalledJdkMacOs(Collection<Path> homes) {
        Path baseDir = Paths.get("/Library/Java/JavaVirtualMachines");
        if (Files.isDirectory(baseDir)) {
            try (Stream<Path> stream = Files.list(baseDir)) {
                stream.filter(Files::isDirectory)
                        .forEach(p -> {
                            LOGGER.debug("[JDK Provider - JDK Scanner] Found candidate JDK installation in {}: {}", baseDir, p);
                            homes.add(p);
                        });
            } catch (IOException e) {
                LOGGER.debug("[JDK Provider - JDK Scanner] Failed to scan directory: {}", baseDir, e);
            }
        }
    }

    /**
     * Detects and collects the filesystem paths of JDK installations on Linux systems by scanning
     * common locations where JDKs are typically installed.
     * <p>
     * The method searches within predefined directories (e.g., /usr/lib/jvm and /usr/java)
     * and adds any valid directories to the provided collection of JDK home paths.
     *
     * @param homes a collection that will be populated with the detected JDK installation paths
     */
    private static void detectInstalledJdkLinux(Collection<Path> homes) {
        List<Path> commonPaths = List.of(
                Paths.get("/usr/lib/jvm"),
                Paths.get("/usr/java")
        );

        for (Path baseDir : commonPaths) {
            if (Files.isDirectory(baseDir)) {
                try (Stream<Path> stream = Files.list(baseDir)) {
                    stream.filter(Files::isDirectory)
                            .forEach(homes::add);
                } catch (IOException e) {
                    LOGGER.debug("Failed to scan directory: {}", baseDir, e);
                }
            }
        }
    }

    /**
     * Detects and collects the filesystem paths of installed JDKs on a Windows operating system
     * by scanning common installation directories.
     * <p>
     * The method iterates over predefined directories known to commonly contain JDK installations
     * (e.g., "C:\\Program Files\\Java"). Within each identified directory, it checks for subdirectories
     * that could represent individual JDK installations and adds their paths to the provided collection.
     *
     * @param homes a collection that will be populated with the detected JDK installation paths on Windows
     */
    private static void detectInstalledJdkWindows(Collection<Path> homes) {
        List<Path> commonPaths = List.of(
                Paths.get("C:\\Program Files\\Java"),
                Paths.get("C:\\Program Files\\Eclipse Adoptium"),
                Paths.get("C:\\Program Files\\Amazon Corretto"),
                Paths.get("C:\\Program Files\\Zulu"),
                Paths.get("C:\\Program Files\\Microsoft")
        );

        for (Path baseDir : commonPaths) {
            if (Files.isDirectory(baseDir)) {
                try (Stream<Path> stream = Files.list(baseDir)) {
                    stream.filter(Files::isDirectory)
                            .forEach(homes::add);
                } catch (IOException e) {
                    LOGGER.debug("[JDK Provider - JDK Scanner] Failed to scan directory: {}", baseDir, e);
                }
            }
        }
    }

    /**
     * Attempts to read the JDK spec by reading the release file.
     *
     * @param jdkDir JDK home
     * @return JDK spec
     */
    public static Optional<JdkInstallation> readJdkSpec(Path jdkDir) {
        Path jdkHome = detectJdkHome(jdkDir);
        if (jdkHome == null) {
            LOGGER.debug("[JDK Provider - JDK Scanner] Could not determine JDK home: {}", jdkDir);
            return Optional.empty();
        }

        Runtime.Version version = null;
        OSFamily os = null;
        SystemArchitecture arch = null;
        String vendor = null;
        boolean javafxBundled = false;

        // Try release file
        Path release = jdkHome.resolve("release");
        if (!Files.isRegularFile(release)) {
            LOGGER.debug("[JDK Provider - JDK Scanner] JDK at {} does not contain a release file", jdkDir);
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
                    case "JAVA_VERSION" -> version = parseVersion(value);
                    case "OS_NAME" -> os = OSFamily.parse(value);
                    case "OS_ARCH" -> arch = SystemArchitecture.parse(value);
                    case "IMPLEMENTOR" -> vendor = getVendorFromImplementor(value);
                    case "MODULES" -> javafxBundled = getJavaFXBundledFromModules(value);
                }
            }

            if (version == null) {
                LOGGER.warn("[JDK Provider - JDK Scanner] Failed to determine JDK version from release file: {}", release);
                return Optional.empty();
            }
            if (arch == null) {
                LOGGER.warn("[JDK Provider - JDK Scanner] Failed to determine JDK architecture from release file: {}", release);
                return Optional.empty();
            }
            if (os == null) {
                LOGGER.warn("[JDK Provider - JDK Scanner] Failed to determine Operating System from release file: {}", release);
                return Optional.empty();
            }
            if (vendor == null) {
                LOGGER.warn("[JDK Provider - JDK Scanner] Failed to determine JDK vendor from release file: {}", release);
                return Optional.empty();
            }

            // If no GraalVM flag was present in the release file, try to detect native-image tool presence
            Path bin = jdkHome.resolve("bin");
            boolean nativeImageCapable = Files.isExecutable(bin.resolve("native-image"))
                    || Files.isExecutable(bin.resolve("native-image.cmd"))
                    || Files.isExecutable(bin.resolve("native-image.exe"));

            JdkSpec jdkSpec = new JdkSpec(os, arch, version, vendor, nativeImageCapable, javafxBundled);
            JdkInstallation jdkInstallation = new JdkInstallation(jdkHome, jdkSpec);
            LOGGER.debug("[JDK Provider - JDK Scanner] found Installation: {}", jdkInstallation);

            return Optional.of(jdkInstallation);
        } catch (IOException e) {
            LOGGER.debug("[JDK Provider - JDK Scanner] Failed to read JDK release file: {}", release, e);
        } catch (IllegalArgumentException e) {
            LOGGER.debug("[JDK Provider - JDK Scanner] Failed to parse data in JDK release file: {}", release, e);
        }
        return Optional.empty();
    }

    private static Runtime.Version parseVersion(String value) {
        // pre-Java 9 JDKs append the build number with an underscore, replace it with a dot
        return Runtime.Version.parse(value.replace('_', '.'));
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
    private static String getVendorFromImplementor(String value) {
        return JvmVendor.fromString(value).getDisplayName();
    }

    /**
     * Filters the list of installed JDKs to identify those that are compatible based on
     * the provided {@link JdkSpec} instance.
     *
     * @param jdkQuery The {@link JdkSpec} to match against.
     * @return A list of {@code JdkInstallation} instances that meet the compatibility requirements.
     */
    public List<JdkInstallation> getCompatibleInstalledJdks(JdkQuery jdkQuery) {
        LOGGER.debug("[JDK Provider - JDK Scanner] Looking for JDKs compatible with {}", jdkQuery);
        return getInstalledJdks()
                .stream()
                .filter(jdkInstallation -> JdkQuery.isCompatible(jdkInstallation.jdkSpec(), jdkQuery))
                .toList();
    }
}
