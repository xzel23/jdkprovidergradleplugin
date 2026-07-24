// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 Axel Howind

package com.dua3.gradle.jdkprovider.provision;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpDownloaderTest {
    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void downloadSlowResponseWithoutPrematureTimeout() throws Exception {
        String body = "x".repeat(2_000);
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(body)
                .throttleBody(100, 50, TimeUnit.MILLISECONDS));

        HttpDownloader downloader = new HttpDownloader(1_000, 100, 0);
        Path targetFile = Files.createTempFile("http-downloader-", ".bin");

        try {
            downloader.downloadTo(server.url("/downloads/jdk.tar.gz").uri(), targetFile);
            assertEquals(body.length(), Files.size(targetFile));
        } finally {
            Files.deleteIfExists(targetFile);
        }
    }

    @Test
    void retriesAfterResponseBodyIsDisconnected() throws Exception {
        byte[] expected = "0123456789".getBytes();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("abc")
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(new String(expected)));

        HttpDownloader downloader = new HttpDownloader(1_000, 1_000, 1);
        Path targetFile = Files.createTempFile("http-downloader-", ".bin");

        try {
            downloader.downloadTo(server.url("/downloads/jdk.tar.gz").uri(), targetFile);
            assertArrayEquals(expected, Files.readAllBytes(targetFile));
            assertEquals(2, server.getRequestCount());
        } finally {
            Files.deleteIfExists(targetFile);
        }
    }

    @Test
    void preservesExistingTargetWhenResponseBodyIsDisconnected() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("abc")
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));

        HttpDownloader downloader = new HttpDownloader(1_000, 1_000, 0);
        Path targetFile = Files.createTempFile("http-downloader-", ".bin");
        byte[] originalContent = "previous archive".getBytes(StandardCharsets.UTF_8);
        Files.write(targetFile, originalContent);

        try {
            assertThrows(IOException.class,
                    () -> downloader.downloadTo(server.url("/downloads/jdk.tar.gz").uri(), targetFile));
            assertArrayEquals(originalContent, Files.readAllBytes(targetFile));
        } finally {
            Files.deleteIfExists(targetFile);
        }
    }
}
