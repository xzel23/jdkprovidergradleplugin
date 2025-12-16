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

package com.dua3.gradle.jdkprovider.provision;

import com.dua3.gradle.jdkprovider.types.OSFamily;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Downloads, verifies, and extracts JDK archives into a managed cache and returns the JDK home path.
 */
public final class JdkProvisioner {
    private static final Logger LOGGER = Logging.getLogger(JdkProvisioner.class);

    /**
     * Default constructor for the JdkProvisioner class.
     */
    public JdkProvisioner() {
    }

    /**
     * Provisions a JDK by downloading, verifying, and extracting the archive from a given URI.
     * <p>
     * This method handles the caching of previously downloaded or extracted JDKs,
     * verifies the integrity of the downloaded files, and extracts the JDK to a
     * specified location. It ensures atomic extraction and creates a completion
     * marker upon successful operation. If an existing extracted JDK is detected
     * and valid, it will be reused instead of redownloading or re-extracting.
     *
     * @param downloadUri       The URI from which the JDK archive will be downloaded.
     * @param expectedSha256    The expected SHA-256 checksum of the archive for verification.
     * @param preferredFilename The preferred filename to use for the downloaded archive,
     *                          or null to auto-determine.
     * @param archiveType       The type of the archive (e.g., "zip," "tar.gz") to help determine handling.
     * @return The path to the root directory of the provisioned JDK.
     * @throws IOException           If an I/O error occurs during downloading, verifying,
     *                               extracting, or any other operation.
     * @throws InterruptedException  If the thread is interrupted while waiting for an operation to complete.
     */
    public Path provision(URI downloadUri,
                          String expectedSha256,
                          String preferredFilename,
                          String archiveType) throws IOException, InterruptedException {
        String cacheKey = hash(downloadUri.toString());

        Path base = cacheBaseDir();
        Path downloads = base.resolve("downloads");
        Path extracted = base.resolve("extracted").resolve(cacheKey);
        Path marker = extracted.resolve(".complete");

        if (Files.isRegularFile(marker)) {
            Path home = detectJdkHome(extracted);
            if (home != null) {
                LOGGER.info("Using cached JDK: {}", home);
                return home;
            }
            // If marker exists but detection fails, fall through to re-extract.
        }

        Files.createDirectories(downloads);
        String fileName = chooseFileName(downloadUri, preferredFilename, archiveType);
        Path archive = downloads.resolve(cacheKey + '-' + fileName);

        if (!Files.isRegularFile(archive)) {
            LOGGER.lifecycle("Downloading JDK from {}", downloadUri);
            HttpDownloader downloader = new HttpDownloader(
                    connectTimeoutMs(),
                    readTimeoutMs(),
                    retries()
            );
            downloader.downloadTo(downloadUri, archive);
        } else {
            LOGGER.info("Archive already in cache: {}", archive);
        }

        ChecksumUtil.verifySha256(archive, expectedSha256);

        // Extract atomically: extract to temp then move
        Path parent = extracted.getParent();
        Files.createDirectories(parent);
        Path tmp = parent.resolve(extracted.getFileName().toString() + ".tmp");
        if (Files.exists(tmp)) {
            deleteRecursively(tmp);
        }
        Files.createDirectories(tmp);
        LOGGER.lifecycle("Extracting JDK archive to {}", tmp);
        ArchiveExtractor.extract(archive, tmp);

        // If the archive contains a single top-level dir, move its contents to extracted
        if (Files.exists(extracted)) {
            deleteRecursively(extracted);
        }
        Files.createDirectories(extracted);
        moveContent(tmp, extracted);
        Files.deleteIfExists(tmp);

        // Create completion marker
        Files.writeString(marker, "ok");

        Path home = detectJdkHome(extracted);
        if (home == null) {
            throw new IOException("Failed to detect JDK home in extracted directory: " + extracted);
        }
        return home;
    }

    /**
     * Computes the SHA-256 hash of the given string and returns it as a hexadecimal string.
     *
     * @param s the input string to be hashed
     * @return the SHA-256 hash of the input string in hexadecimal format
     * @throws IllegalStateException if the SHA-256 algorithm is not available
     */
    private static String hash(String s) {
        String algorithm = "SHA-256";
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hashBytes = digest.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " not available", e);
        }
    }

    /**
     * Chooses an appropriate file name for a downloaded JDK archive, based on the provided
     * inputs and fallback mechanisms.
     * <p>
     * Steps:
     * 1. If a preferred file name is provided and is non-blank, it is returned.
     * 2. If the archive type is known, a generic file name with the appropriate extension
     *    for the archive type is returned.
     * 3. If neither of the above conditions is met, the last segment of the download URI
     *    path is used as a fallback name.
     *
     * @param downloadUri       The URI from which the file will be downloaded. This is used
     *                          as a fallback mechanism to determine the file name if no
     *                          preferred name or archive type is provided.
     * @param preferredFilename The preferred file name to use if specified and non-blank.
     *                          This value takes precedence over other mechanisms.
     * @param archiveType       The type of the archive (e.g., "zip," "tar.gz"). Used to
     *                          determine the appropriate file name extension if the preferred
     *                          file name is not provided.
     * @return A string representing the chosen file name. This is either the preferred file
     *         name, a synthesized name based on the archive type, or a fallback name derived
     *         from the URI.
     */
    private static String chooseFileName(URI downloadUri, String preferredFilename, String archiveType) {
        // 1) Prefer the filename provided by DiscoAPI
        if (preferredFilename != null && !preferredFilename.isBlank()) {
            return preferredFilename;
        }
        // 2) If we know the archive type, synthesize a reasonable name
        String ext = extensionForArchiveType(archiveType);
        if (!ext.isBlank()) {
            return "jdk" + ext; // a stable generic name with correct extension
        }
        // 3) Fallback to last path segment of the URI (may be a redirect without extension)
        return fileNameFromUri(downloadUri);
    }

    /**
     * Returns the file extension associated with the specified archive type.
     * <p>
     * The method determines the extension based on the provided archive type string.
     * If the string is null or does not match any known types, an empty string is returned.
     * <p>
     * Supported archive types:
     * <ul>
     * <li>"tar.gz" or "tgz" for archives with ".tar.gz" extension.
     * <li>"zip" for archives with ".zip" extension.
     * <li>"msi" for archives with ".msi" extension.
     * <li>"exe" for archives with ".exe" extension.
     * <li>"dmg" for archives with ".dmg" extension.
     * <li>"pkg" for archives with ".pkg" extension.
     * </ul>
     *
     * @param archiveType the type of the archive (e.g., "zip", "tar.gz"); case-insensitive.
     * @return the file extension corresponding to the archive type, or an empty string if the type is unknown or null.
     */
    private static String extensionForArchiveType(String archiveType) {
        if (archiveType == null) return "";
        String t = archiveType.toLowerCase(Locale.ROOT);
        return switch (t) {
            case "tar.gz", "tgz" -> ".tar.gz";
            case "zip" -> ".zip";
            case "msi" -> ".msi";
            case "exe" -> ".exe";
            case "dmg" -> ".dmg";
            case "pkg" -> ".pkg";
            default -> "";
        };
    }

    /**
     * Retrieves the base directory used for caching toolchain data for the Java resolver plugin.
     * <p>
     * This method constructs the cache location by resolving the "caches/javaresolverplugin-toolchains"
     * path relative to the Gradle user home directory. The Gradle user home directory is determined
     * using the {@link #getGradleUserHome()} method, which looks at system and environment variables
     * to determine its location.
     *
     * @return The path to the base directory used for caching toolchain data.
     */
    private static Path cacheBaseDir() {
        return getGradleUserHome().resolve("caches").resolve("jdkproviderplugin");
    }

    /**
     * Determines the location of the Gradle user home directory.
     * <p>
     * This method checks system and environment variables to locate the Gradle user home directory:
     * 1. If the system property "gradle.user.home" is set and non-blank, its value is used.
     * 2. If the environment variable "GRADLE_USER_HOME" is set and non-blank, its value is used.
     * 3. If neither is set or non-blank, the default location is resolved to a ".gradle" directory
     *    within the user's home directory as specified by the "user.home" system property.
     *
     * @return The path to the Gradle user home directory.
     */
    public static Path getGradleUserHome() {
        String sysProp = System.getProperty("gradle.user.home");
        if (sysProp != null && !sysProp.isBlank()) {
            return Paths.get(sysProp);
        }

        String env = System.getenv("GRADLE_USER_HOME");
        if (env != null && !env.isBlank()) {
            return Paths.get(env);
        }

        return Paths.get(System.getProperty("user.home")).resolve(".gradle");
    }

    /**
     * Retrieves the connection timeout value, in milliseconds.
     *
     * @return the connection timeout value in milliseconds.
     */
    private int connectTimeoutMs() {
        return 10_000;
    }

    /**
     * Returns the read timeout duration in milliseconds.
     *
     * @return the read timeout duration in milliseconds
     */
    private int readTimeoutMs() {
        return 10_000;
    }

    /**
     * Retrieves the number of retry attempts to perform for a failed operation.
     *
     * @return the number of retry attempts, as an integer.
     */
    private int retries() {
        return 10_000;
    }

    /**
     * Extracts the file name from a given URI.
     * <p>
     * The method determines the file name by taking the last segment of the URI's path,
     * after the final '/' character. If the path does not contain a valid segment, a
     * fallback name is generated using the hash code of the URI.
     *
     * @param uri the URI from which to extract the file name
     * @return the extracted file name or a fallback file name derived from the URI
     */
    private static String fileNameFromUri(URI uri) {
        String path = uri.getPath();
        int i = path.lastIndexOf('/') + 1;
        return i > 0 ? path.substring(i) : ("download-" + Math.absExact(uri.toString().hashCode()));
    }

    /**
     * Moves all files from the source directory to the destination directory.
     * <p>
     * This method transfers the contents of the specified source directory to the
     * specified destination directory. If a file with the same name exists in the
     * destination directory, it will be replaced.
     *
     * @param srcDir the path to the source directory containing files to move
     * @param dstDir the path to the destination directory where files will be moved
     * @throws IOException if an I/O error occurs during the file movement process
     */
    private static void moveContent(Path srcDir, Path dstDir) throws IOException {
        try (var stream = Files.list(srcDir)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                Path target = dstDir.resolve(p.getFileName().toString());
                Files.move(p, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * Deletes a directory and its contents recursively.
     * If the directory does not exist, the method returns immediately.
     *
     * @param dir the root directory to delete along with its contents
     * @throws IOException if an I/O error occurs while accessing or deleting files
     */
    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {Files.deleteIfExists(p);} catch (IOException ignored) { /* ignore */ }
                    });
        }
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
     * The method searches for well-known JDK layouts, such as:
     * 1) The presence of a 'bin/java' executable directly under the extracted root.
     * 2) A single subdirectory under the extracted root containing a 'bin/java' executable.
     * 3) macOS-style JDK bundles, where 'Contents/Home/bin/java' exists within a subdirectory.
     * 4) macOS-style JDK bundles directly under the extracted root ('Contents/Home/bin/java').
     *
     * @param extractedRoot the root path of the extracted directory to analyze for a JDK home.
     * @return the path to the JDK home directory if detected; otherwise, null.
     * @throws IOException if an I/O error occurs while attempting to access the files or directories.
     */
    private static Path detectJdkHome(Path extractedRoot) throws IOException {
        // Common layouts:
        // 1) extractedRoot/bin/java exists -> home
        Path direct = findJavaExecutable(extractedRoot);
        if (direct != null) return extractedRoot;

        // 2) extractedRoot/<single>/bin/java
        try (var stream = Files.list(extractedRoot)) {
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
        }
        // 4) macOS bundles directly under extractedRoot: Contents/Home
        Path macHomeDirect = extractedRoot.resolve("Contents").resolve("Home");
        if (findJavaExecutable(macHomeDirect) != null) return macHomeDirect;

        return null;
    }

}
