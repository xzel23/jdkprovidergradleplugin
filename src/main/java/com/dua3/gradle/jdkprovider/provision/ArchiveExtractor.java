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
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;

/**
 * Extracts archives supported for typical JDK packages: .zip and .tar.gz
 */
public final class ArchiveExtractor {
    private ArchiveExtractor() {}

    /**
     * Extracts the specified archive file into the target directory. Supports ZIP and TAR.GZ (or TGZ) formats.
     * The method will create the target directory if it does not already exist.
     *
     * @param archive   the path to the archive file to be extracted; must not be null
     * @param targetDir the target directory where the contents of the archive will be extracted; must not be null
     * @throws IOException if an I/O error occurs, if the archive format is not supported, or if an extraction entry attempts to escape the target directory
     */
    public static void extract(Path archive, Path targetDir) throws IOException {
        Objects.requireNonNull(archive);
        Objects.requireNonNull(targetDir);
        Files.createDirectories(targetDir);
        String name = archive.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".zip")) {
            extractZip(archive, targetDir);
        } else if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            extractTarGz(archive, targetDir);
        } else {
            throw new IOException("Unsupported archive type: " + archive);
        }
    }

    /**
     * Extracts a ZIP archive to the specified target directory.
     * Ensures that all extracted entries remain within the target directory
     * and applies POSIX file permissions based on the archive metadata.
     *
     * @param archive   the path to the ZIP archive to be extracted; must not be null
     * @param targetDir the target directory where the contents of the ZIP archive will be extracted; must not be null
     * @throws IOException if an I/O error occurs, if the ZIP archive contains malicious entries
     *                     attempting to escape the target directory, or if directory creation fails
     */
    private static void extractZip(Path archive, Path targetDir) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(archive));
             ZipArchiveInputStream zis = new ZipArchiveInputStream(in)) {
            ZipArchiveEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                Path out = targetDir.resolve(name).normalize();
                if (!out.startsWith(targetDir)) {
                    throw new IOException("Zip entry outside target dir: " + name);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    applyPosixMode(out, entry.getUnixMode());
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                    applyPosixMode(out, entry.getUnixMode());
                }
            }
        }
    }

    /**
     * Extracts a TAR.GZ archive to the specified target directory.
     * This method ensures that all extracted entries remain within the target directory
     * and applies POSIX file permissions based on the archive metadata.
     *
     * @param archive   the path to the TAR.GZ archive to be extracted; must not be null
     * @param targetDir the target directory where the contents of the TAR.GZ archive will be extracted; must not be null
     * @throws IOException if an I/O error occurs, if the TAR.GZ archive contains malicious entries
     *                     attempting to escape the target directory, or if directory creation or file writing fails
     */
    private static void extractTarGz(Path archive, Path targetDir) throws IOException {
        try (InputStream fin = Files.newInputStream(archive);
             BufferedInputStream bin = new BufferedInputStream(fin);
             GzipCompressorInputStream gcis = new GzipCompressorInputStream(bin);
             TarArchiveInputStream tais = new TarArchiveInputStream(gcis)) {
            TarArchiveEntry tae;
            while ((tae = tais.getNextEntry()) != null) {
                String name = tae.getName();
                Path out = targetDir.resolve(name).normalize();
                if (!out.startsWith(targetDir)) {
                    throw new IOException("Tar entry outside target dir: " + name);
                }
                if (tae.isDirectory()) {
                    Files.createDirectories(out);
                    applyPosixMode(out, tae.getMode());
                } else if (tae.isSymbolicLink()) {
                    // Create symlink if supported
                    Path linkTarget = Path.of(tae.getLinkName());
                    Files.createDirectories(out.getParent());
                    try {
                        Files.deleteIfExists(out);
                        Files.createSymbolicLink(out, linkTarget);
                    } catch (UnsupportedOperationException uoe) {
                        // Fallback: materialize as a regular file by copying bytes
                        Files.copy(tais, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(tais, out, StandardCopyOption.REPLACE_EXISTING);
                    applyPosixMode(out, tae.getMode());
                }
            }
        }
    }

    /**
     * Applies POSIX file permissions to the specified file or directory based on the provided mode value.
     * The mode is interpreted using UNIX-style permission bits for owner, group, and others.
     *
     * @param out the path to the file or directory to which the permissions should be applied; must not be null
     * @param mode the POSIX mode value in octal format, specifying the permission bits for owner, group, and others
     * @throws IOException if an I/O error occurs while setting the POSIX file permissions
     */
    private static void applyPosixMode(Path out, int mode) throws IOException {
        // On Windows, POSIX permissions are not supported by the default file system.
        // Skip setting them to avoid UnsupportedOperationException.
        if (OSFamily.current() == OSFamily.WINDOWS) {
            return;
        }
        if (mode == 0) return; // do not guess; rely on archive metadata
        EnumSet<PosixFilePermission> perms = EnumSet.noneOf(PosixFilePermission.class);
        // Apply mode bits (owner/group/others) similar to tar/zip unix mode
        if ((mode & 0400) != 0) perms.add(PosixFilePermission.OWNER_READ);
        if ((mode & 0200) != 0) perms.add(PosixFilePermission.OWNER_WRITE);
        if ((mode & 0100) != 0) perms.add(PosixFilePermission.OWNER_EXECUTE);
        if ((mode & 0040) != 0) perms.add(PosixFilePermission.GROUP_READ);
        if ((mode & 0020) != 0) perms.add(PosixFilePermission.GROUP_WRITE);
        if ((mode & 0010) != 0) perms.add(PosixFilePermission.GROUP_EXECUTE);
        if ((mode & 0004) != 0) perms.add(PosixFilePermission.OTHERS_READ);
        if ((mode & 0002) != 0) perms.add(PosixFilePermission.OTHERS_WRITE);
        if ((mode & 0001) != 0) perms.add(PosixFilePermission.OTHERS_EXECUTE);
        if (!perms.isEmpty()) {
            Files.setPosixFilePermissions(out, perms);
        }
    }
}
