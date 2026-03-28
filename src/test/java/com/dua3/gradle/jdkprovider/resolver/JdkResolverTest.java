// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 Axel Howind

package com.dua3.gradle.jdkprovider.resolver;

import com.dua3.gradle.jdkprovider.disco.DiscoApiClient;
import com.dua3.gradle.jdkprovider.local.JdkInstallation;
import com.dua3.gradle.jdkprovider.types.DiscoPackage;
import com.dua3.gradle.jdkprovider.types.JdkQuery;
import com.dua3.gradle.jdkprovider.types.JdkQueryBuilder;
import com.dua3.gradle.jdkprovider.types.JdkSpec;
import com.dua3.gradle.jdkprovider.types.OSFamily;
import com.dua3.gradle.jdkprovider.types.SystemArchitecture;
import com.dua3.gradle.jdkprovider.types.VersionSpec;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdkResolverTest {

    private MockWebServer server;

    @BeforeEach
    void setup() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void resolverTriesNextPackageWhenProvisioningFails() {
        String json = "{\n" +
                "  \"result\": [\n" +
                "    {\n" +
                "      \"package_type\": \"jdk\",\n" +
                "      \"distribution\": \"zulu\",\n" +
                "      \"java_version\": \"99\",\n" +
                "      \"archive_type\": \"zip\",\n" +
                "      \"directly_downloadable\": true,\n" +
                "      \"operating_system\": \"linux\",\n" +
                "      \"architecture\": \"x64\",\n" +
                "      \"link\": {\"pkg_download_redirect\": \"" + server.url("/downloads/broken.zip") + "\"}\n" +
                "    },\n" +
                "    {\n" +
                "      \"package_type\": \"jdk\",\n" +
                "      \"distribution\": \"zulu\",\n" +
                "      \"java_version\": \"99\",\n" +
                "      \"archive_type\": \"zip\",\n" +
                "      \"directly_downloadable\": true,\n" +
                "      \"operating_system\": \"linux\",\n" +
                "      \"architecture\": \"x64\",\n" +
                "      \"link\": {\"pkg_download_redirect\": \"" + server.url("/downloads/good.zip") + "\"}\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        server.enqueue(new MockResponse().setResponseCode(200).setBody(json));

        Path jdkHome = Path.of("/tmp/fake-jdk-home");
        TestResolver resolver = new TestResolver(server.url("/disco/v3.0/packages").toString(), jdkHome);

        JdkQuery query = JdkQueryBuilder.builder()
                .os(OSFamily.LINUX)
                .arch(SystemArchitecture.X64)
                .versionSpec(VersionSpec.parse("99"))
                .build();

        Optional<JdkInstallation> result = resolver.resolve(query, false);

        assertTrue(result.isPresent());
        assertEquals(jdkHome, result.get().jdkHome());
        assertEquals(List.of("/downloads/broken.zip", "/downloads/good.zip"), resolver.attemptedPaths);
    }

    private static final class TestResolver extends JdkResolver {
        private final String baseUrl;
        private final Path jdkHome;
        private final List<String> attemptedPaths = new ArrayList<>();

        private TestResolver(String baseUrl, Path jdkHome) {
            this.baseUrl = baseUrl;
            this.jdkHome = jdkHome;
        }

        @Override
        protected DiscoApiClient createDiscoApiClient() {
            return new DiscoApiClient(baseUrl);
        }

        @Override
        protected Path provisionPackage(DiscoPackage pkg) throws IOException {
            attemptedPaths.add(pkg.downloadUri().getPath());
            if (pkg.downloadUri().getPath().contains("broken")) {
                throw new IOException("HTTP 404 when downloading: " + pkg.downloadUri());
            }
            return jdkHome;
        }

        @Override
        protected Optional<JdkInstallation> readJdkSpec(Path jdkHome) {
            return Optional.of(new JdkInstallation(
                    jdkHome,
                    new JdkSpec(
                            OSFamily.LINUX,
                            SystemArchitecture.X64,
                            Runtime.Version.parse("99"),
                            "zulu",
                            false,
                            false
                    )
            ));
        }
    }
}
