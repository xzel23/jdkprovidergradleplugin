package com.dua3.gradle.jdkprovider.provision;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

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
 * This provisioner is independent of the Gradle Toolchain SPI wiring; the resolver can call it later.
 */
public final class JdkProvisioner {
    private static final Logger LOGGER = Logging.getLogger(JdkProvisioner.class);

    public JdkProvisioner() {
    }

    /**
     * Provision a JDK by downloading and caching an archive.
     *
     * @param downloadUri   the archive URL
     * @param expectedSha256 optional expected SHA-256 hex (nullable/blank to skip)
     * @return path to the JDK home directory
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

    private static Path cacheBaseDir() {
        return getGradleUserHome().resolve("caches").resolve("javaresolverplugin-toolchains");
    }

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

    private int connectTimeoutMs() {
        return 10_000;
    }

    private int readTimeoutMs() {
        return 10_000;
    }

    private int retries() {
        return 10_000;
    }

    private static String fileNameFromUri(URI uri) {
        String path = uri.getPath();
        int i = path.lastIndexOf('/') + 1;
        return i > 0 ? path.substring(i) : ("download-" + Math.absExact(uri.toString().hashCode()));
    }

    private static void moveContent(Path srcDir, Path dstDir) throws IOException {
        try (var stream = Files.list(srcDir)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                Path target = dstDir.resolve(p.getFileName().toString());
                Files.move(p, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {Files.deleteIfExists(p);} catch (IOException ignored) { /* ignore */ }
                    });
        }
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }

    private static Path findJavaExecutable(Path home) {
        Path bin = home.resolve("bin");
        Path java = bin.resolve(isWindows() ? "java.exe" : "java");
        if (Files.isRegularFile(java)) return java;
        return null;
    }

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
