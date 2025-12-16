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

package com.dua3.gradle.jdkprovider.disco;

import com.dua3.gradle.jdkprovider.types.JdkSpec;
import com.dua3.gradle.jdkprovider.types.OSFamily;
import com.dua3.gradle.jdkprovider.types.SystemArchitecture;
import com.dua3.gradle.jdkprovider.types.VersionSpec;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoApiClientTest {
    private MockWebServer server;
    private String baseUrl;

    @BeforeEach
    void setup() throws IOException {
        server = new MockWebServer();
        server.start();
        baseUrl = server.url("/disco/v3.0").toString();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void selectorFindsPackageFromMockServer() {
        // Prepare fake package list
        String downloadPath = "/downloads/jdk.zip";
        String json = "{\n" +
                "  \"result\": [\n" +
                "    {\n" +
                "      \"package_type\": \"jdk\",\n" +
                "      \"distribution\": \"zulu\",\n" +
                "      \"archive_type\": \"zip\",\n" +
                "      \"directly_downloadable\": true,\n" +
                "      \"link\": { \n" +
                "         \"pkg_download_redirect\": \"" + server.url(downloadPath) + "\"\n" +
                "      }\n" +
                "    }\n" +
                "  ],\n" +
                "  \"message\": \"1 package(s) found\"\n" +
                "}";
        server.enqueue(new MockResponse().setResponseCode(200).setBody(json));

        DiscoApiClient client = new DiscoApiClient(baseUrl);

        var pkg = client.findPackage(
                JdkSpec.builder()
                        .version(VersionSpec.parse("21"))
                        .os(OSFamily.MACOS)
                        .arch(SystemArchitecture.AARCH64)
                        .vendor(JvmVendorSpec.AZUL)
                        .javaFxBundled(false)
                        .build()
        );

        assertTrue(pkg.isPresent());
        assertEquals("zulu", pkg.get().vendor());
        assertTrue(pkg.get().downloadUri().toString().contains(downloadPath));
    }

    @Test
    void buildsQueryWithJavaFxBundled() {
        DiscoApiClient client = new DiscoApiClient();
        URI uri = client.buildPackagesQueryUrl(
                JdkSpec.builder()
                        .version(VersionSpec.parse("21"))
                        .os(OSFamily.MACOS)
                        .arch(SystemArchitecture.AARCH64)
                        .vendor(JvmVendorSpec.AZUL)
                        .javaFxBundled(true)
                        .build()
        );
        String q = uri.toString();
        assertTrue(q.contains("version=21"));
        assertTrue(q.contains("operating_system=macos"));
        assertTrue(q.contains("architecture=aarch64"));
        assertTrue(q.contains("distribution=zulu"));
        assertTrue(q.contains("release_status=ga"));
        assertTrue(q.contains("javafx_bundled=true"));
    }
}
