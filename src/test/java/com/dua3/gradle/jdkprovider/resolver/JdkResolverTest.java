// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 Axel Howind

package com.dua3.gradle.jdkprovider.resolver;

import com.dua3.gradle.jdkprovider.disco.DiscoApiClient;
import com.dua3.gradle.jdkprovider.local.JdkInstallation;
import com.dua3.gradle.jdkprovider.local.LocalJdkScanner;
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
import java.net.URI;
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

    @Test
    void prefersRemoteJdkWhenItHasNewerPatchVersionThanLocalJdk() {
        Path localHome = Path.of("/tmp/local-jdk-26.0.1");
        Path remoteHome = Path.of("/tmp/remote-jdk-26.0.2");

        ControlledResolver resolver = new ControlledResolver(
                List.of(jdkInstallation(localHome, "26.0.1")),
                List.of(discoPackage("26.0.2", "https://example.test/jdk-26.0.2.tar.gz")),
                remoteHome,
                Runtime.Version.parse("26.0.2"),
                null
        );

        Optional<JdkInstallation> result = resolver.resolve(queryForVersion("26"), false);

        assertTrue(result.isPresent());
        assertEquals(remoteHome, result.get().jdkHome());
        assertEquals(List.of("/jdk-26.0.2.tar.gz"), resolver.attemptedPaths);
    }

    @Test
    void fallsBackToLocalJdkWhenDiscoLookupThrowsError() {
        Path localHome = Path.of("/tmp/local-jdk-26.0.1");
        Path remoteHome = Path.of("/tmp/remote-jdk-unused");

        ControlledResolver resolver = new ControlledResolver(
                List.of(jdkInstallation(localHome, "26.0.1")),
                List.of(),
                remoteHome,
                Runtime.Version.parse("26.0.2"),
                new RuntimeException("Disco API unavailable")
        );

        Optional<JdkInstallation> result = resolver.resolve(queryForVersion("26"), false);

        assertTrue(result.isPresent());
        assertEquals(localHome, result.get().jdkHome());
        assertTrue(resolver.attemptedPaths.isEmpty());
    }

    @Test
    void prefersLocalJdkWhenRemoteHasSameVersion() {
        Path localHome = Path.of("/tmp/local-jdk-26.0.2");
        Path remoteHome = Path.of("/tmp/remote-jdk-26.0.2");

        ControlledResolver resolver = new ControlledResolver(
                List.of(jdkInstallation(localHome, "26.0.2")),
                List.of(discoPackage("26.0.2", "https://example.test/jdk-26.0.2.tar.gz")),
                remoteHome,
                Runtime.Version.parse("26.0.2"),
                null
        );

        Optional<JdkInstallation> result = resolver.resolve(queryForVersion("26"), false);

        assertTrue(result.isPresent());
        assertEquals(localHome, result.get().jdkHome());
        assertTrue(resolver.attemptedPaths.isEmpty());
    }

    private static JdkQuery queryForVersion(String version) {
        return JdkQueryBuilder.builder()
                .os(OSFamily.LINUX)
                .arch(SystemArchitecture.X64)
                .versionSpec(VersionSpec.parse(version))
                .build();
    }

    private static DiscoPackage discoPackage(String version, String downloadUri) {
        return new DiscoPackage(
                URI.create(downloadUri),
                "sha",
                "zulu",
                "tar.gz",
                "jdk-" + version + ".tar.gz",
                Runtime.Version.parse(version),
                OSFamily.LINUX,
                SystemArchitecture.X64,
                "glibc"
        );
    }

    private static JdkInstallation jdkInstallation(Path jdkHome, String version) {
        return new JdkInstallation(
                jdkHome,
                new JdkSpec(
                        OSFamily.LINUX,
                        SystemArchitecture.X64,
                        Runtime.Version.parse(version),
                        "zulu",
                        false,
                        false
                )
        );
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
        protected LocalJdkScanner createLocalJdkScanner() {
            // Return a scanner that finds no local JDKs to isolate the test from the environment
            return new LocalJdkScanner(java.util.Map.of(), java.nio.file.Path.of("/non-existent-path"));
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

    private static final class ControlledResolver extends JdkResolver {
        private final List<JdkInstallation> localJdks;
        private final List<DiscoPackage> discoPackages;
        private final Path provisionedJdkHome;
        private final Runtime.Version provisionedVersion;
        private final RuntimeException discoFailure;
        private final List<String> attemptedPaths = new ArrayList<>();

        private ControlledResolver(
                List<JdkInstallation> localJdks,
                List<DiscoPackage> discoPackages,
                Path provisionedJdkHome,
                Runtime.Version provisionedVersion,
                RuntimeException discoFailure
        ) {
            this.localJdks = localJdks;
            this.discoPackages = discoPackages;
            this.provisionedJdkHome = provisionedJdkHome;
            this.provisionedVersion = provisionedVersion;
            this.discoFailure = discoFailure;
        }

        @Override
        protected List<JdkInstallation> getCompatibleInstalledJdks(JdkQuery jdkQuery) {
            return localJdks;
        }

        @Override
        protected List<DiscoPackage> findDiscoPackages(JdkQuery jdkQuery) {
            if (discoFailure != null) {
                throw discoFailure;
            }
            return discoPackages;
        }

        @Override
        protected Path provisionPackage(DiscoPackage pkg) {
            attemptedPaths.add(pkg.downloadUri().getPath());
            return provisionedJdkHome;
        }

        @Override
        protected Optional<JdkInstallation> readJdkSpec(Path jdkHome) {
            return Optional.of(new JdkInstallation(
                    jdkHome,
                    new JdkSpec(
                            OSFamily.LINUX,
                            SystemArchitecture.X64,
                            provisionedVersion,
                            "zulu",
                            false,
                            false
                    )
            ));
        }
    }
}
