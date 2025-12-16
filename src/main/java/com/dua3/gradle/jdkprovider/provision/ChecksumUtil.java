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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for calculating and verifying SHA-256 file checksums.
 * This class is final and cannot be instantiated.
 */
public final class ChecksumUtil {
    private ChecksumUtil() {}

    /**
     * Computes the SHA-256 checksum of the contents of the specified file and returns it as a hexadecimal string.
     *
     * @param file the path to the file whose SHA-256 checksum is to be computed
     * @return the SHA-256 checksum of the file as a hexadecimal string
     * @throws IOException if an I/O error occurs while reading the file
     * @throws IllegalStateException if the SHA-256 algorithm is not available
     */
    public static String sha256Hex(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    if (n == 0) continue;
                    md.update(buf, 0, n);
                }
            }
            byte[] digest = md.digest();
            return toHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Verifies that the SHA-256 checksum of the specified file matches the expected checksum.
     * If the expected checksum is null or blank, the method is a no-op and does not perform verification.
     *
     * @param file the path to the file whose checksum is to be verified
     * @param expectedHex the expected SHA-256 checksum in hexadecimal format
     * @throws IOException if an I/O error occurs while reading the file or if the checksum does not match
     */
    public static void verifySha256(Path file, String expectedHex) throws IOException {
        if (expectedHex == null || expectedHex.isBlank()) {
            return; // no-op if not provided
        }
        String actual = sha256Hex(file);
        if (!actual.equalsIgnoreCase(expectedHex.trim())) {
            throw new IOException("Checksum mismatch for " + file + ": expected=" + expectedHex + ", actual=" + actual);
        }
    }

    /**
     * Converts an array of bytes to its hexadecimal string representation.
     *
     * @param bytes the array of bytes to be converted
     * @return the hexadecimal string representation of the input byte array
     */
    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
