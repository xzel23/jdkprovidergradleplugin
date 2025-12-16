package com.dua3.gradle.jdkprovider.provision;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class ChecksumUtil {
    private ChecksumUtil() {}

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

    public static void verifySha256(Path file, String expectedHex) throws IOException {
        if (expectedHex == null || expectedHex.isBlank()) {
            return; // no-op if not provided
        }
        String actual = sha256Hex(file);
        if (!actual.equalsIgnoreCase(expectedHex.trim())) {
            throw new IOException("Checksum mismatch for " + file + ": expected=" + expectedHex + ", actual=" + actual);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
