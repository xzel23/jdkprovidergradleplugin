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

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchiveExtractorTest {
    @Test
    void extractZipArchive() throws IOException {
        Path tmpDir = Files.createTempDirectory("ae-zip-");
        Path archive = tmpDir.resolve("sample.zip");

        // build a small zip
        try (OutputStream out = Files.newOutputStream(archive);
             ZipOutputStream zos = new ZipOutputStream(out)) {
            zos.putNextEntry(new ZipEntry("dir/"));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("dir/hello.txt"));
            zos.write("hi".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        Path extractTo = tmpDir.resolve("out");
        ArchiveExtractor.extract(archive, extractTo);

        assertTrue(Files.isRegularFile(extractTo.resolve("dir/hello.txt")));
        assertEquals("hi", Files.readString(extractTo.resolve("dir/hello.txt")));
    }

    @Test
    void extractTarGzArchive() throws IOException {
        Path tmpDir = Files.createTempDirectory("ae-tgz-");
        Path archive = tmpDir.resolve("sample.tar.gz");

        // build a small tar.gz
        try (OutputStream fout = Files.newOutputStream(archive, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
             GzipCompressorOutputStream gz = new GzipCompressorOutputStream(fout);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(gz)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

            byte[] content = "hi".getBytes(StandardCharsets.UTF_8);
            TarArchiveEntry entryDir = new TarArchiveEntry("dir/");
            entryDir.setMode(0755);
            entryDir.setSize(0);
            tar.putArchiveEntry(entryDir);
            tar.closeArchiveEntry();

            TarArchiveEntry entry = new TarArchiveEntry("dir/hello.txt");
            entry.setMode(0644);
            entry.setSize(content.length);
            tar.putArchiveEntry(entry);
            tar.write(content);
            tar.closeArchiveEntry();
            tar.finish();
        }

        Path extractTo = tmpDir.resolve("out");
        ArchiveExtractor.extract(archive, extractTo);

        assertTrue(Files.isRegularFile(extractTo.resolve("dir/hello.txt")));
        assertEquals("hi", Files.readString(extractTo.resolve("dir/hello.txt")));
    }
}
