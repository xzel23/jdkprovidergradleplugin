package com.dua3.gradle.jdkprovider.resolver;

import com.dua3.gradle.jdkprovider.disco.DiscoApiClient;
import com.dua3.gradle.jdkprovider.local.JdkInstallation;
import com.dua3.gradle.jdkprovider.provision.JdkProvisioner;
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

class JdkValidationTest {

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
    void resolverTriesNextPackageWhenValidationFails() {
        String json = "{\n" +
                "  \"result\": [\n" +
                "    {\n" +
                "      \"package_type\": \"jdk\",\n" +
                "      \"distribution\": \"mandrel\",\n" +
                "      \"java_version\": \"21.3.6\",\n" +
                "      \"archive_type\": \"zip\",\n" +
                "      \"directly_downloadable\": true,\n" +
                "      \"operating_system\": \"windows\",\n" +
                "      \"architecture\": \"amd64\",\n" +
                "      \"link\": {\"pkg_download_redirect\": \"" + server.url("/downloads/bad-jdk.zip") + "\"}\n" +
                "    },\n" +
                "    {\n" +
                "      \"package_type\": \"jdk\",\n" +
                "      \"distribution\": \"zulu\",\n" +
                "      \"java_version\": \"21\",\n" +
                "      \"archive_type\": \"zip\",\n" +
                "      \"directly_downloadable\": true,\n" +
                "      \"operating_system\": \"windows\",\n" +
                "      \"architecture\": \"amd64\",\n" +
                "      \"link\": {\"pkg_download_redirect\": \"" + server.url("/downloads/good-jdk.zip") + "\"}\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        server.enqueue(new MockResponse().setResponseCode(200).setBody(json));

        Path badJdkHome = Path.of("/tmp/bad-jdk-home");
        Path goodJdkHome = Path.of("/tmp/good-jdk-home");
        
        TestResolver resolver = new TestResolver(server.url("/disco/v3.0/packages").toString(), badJdkHome, goodJdkHome);

        JdkQuery query = JdkQueryBuilder.builder()
                .os(OSFamily.WINDOWS)
                .arch(SystemArchitecture.X64)
                .versionSpec(VersionSpec.parse("21"))
                .build();

        Optional<JdkInstallation> result = resolver.resolve(query, false);

        assertTrue(result.isPresent(), "Result should be present");
        assertEquals(goodJdkHome, result.get().jdkHome(), "Should have picked the good JDK home");
        assertEquals(List.of("/downloads/bad-jdk.zip", "/downloads/good-jdk.zip"), resolver.attemptedPaths);
        assertTrue(resolver.isBlacklisted, "Bad URI should have been blacklisted");
    }

    @Test
    void resolverSkipsBlacklistedPackages() {
        String json = "{\n" +
                "  \"result\": [\n" +
                "    {\n" +
                "      \"package_type\": \"jdk\",\n" +
                "      \"distribution\": \"mandrel\",\n" +
                "      \"java_version\": \"21.3.6\",\n" +
                "      \"archive_type\": \"zip\",\n" +
                "      \"directly_downloadable\": true,\n" +
                "      \"operating_system\": \"windows\",\n" +
                "      \"architecture\": \"amd64\",\n" +
                "      \"link\": {\"pkg_download_redirect\": \"" + server.url("/downloads/bad-jdk.zip") + "\"}\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        server.enqueue(new MockResponse().setResponseCode(200).setBody(json));

        Path badJdkHome = Path.of("/tmp/bad-jdk-home");
        Path goodJdkHome = Path.of("/tmp/good-jdk-home");

        TestResolver resolver = new TestResolver(server.url("/disco/v3.0/packages").toString(), badJdkHome, goodJdkHome);
        resolver.isBlacklisted = true; // Pretend the only available package is already blacklisted

        JdkQuery query = JdkQueryBuilder.builder()
                .os(OSFamily.WINDOWS)
                .arch(SystemArchitecture.X64)
                .versionSpec(VersionSpec.parse("21"))
                .build();

        Optional<JdkInstallation> result = resolver.resolve(query, false);

        assertTrue(result.isEmpty(), "Result should be empty because only package was blacklisted");
        assertTrue(resolver.attemptedPaths.isEmpty(), "No provisioning should have been attempted");
    }

    private static final class TestResolver extends JdkResolver {
        private final String baseUrl;
        private final Path badJdkHome;
        private final Path goodJdkHome;
        private final List<String> attemptedPaths = new ArrayList<>();
        private boolean isBlacklisted = false;

        private TestResolver(String baseUrl, Path badJdkHome, Path goodJdkHome) {
            this.baseUrl = baseUrl;
            this.badJdkHome = badJdkHome;
            this.goodJdkHome = goodJdkHome;
        }

        @Override
        protected DiscoApiClient createDiscoApiClient() {
            return new DiscoApiClient(baseUrl);
        }

        @Override
        protected JdkProvisioner createJdkProvisioner() {
            return new JdkProvisioner() {
                @Override
                public void blacklist(java.net.URI downloadUri) {
                    isBlacklisted = true;
                }

                @Override
                public boolean isBlacklisted(java.net.URI downloadUri) {
                    return isBlacklisted;
                }

                @Override
                public void removeExtractedJdk(Path jdkHome) {
                    // no-op
                }
            };
        }

        @Override
        protected Path provisionPackage(DiscoPackage pkg) {
            attemptedPaths.add(pkg.downloadUri().getPath());
            if (pkg.downloadUri().getPath().contains("bad")) {
                return badJdkHome;
            } else {
                return goodJdkHome;
            }
        }

        @Override
        protected Optional<JdkInstallation> readJdkSpec(Path jdkHome) {
            if (jdkHome.equals(badJdkHome)) {
                return Optional.of(new JdkInstallation(
                        jdkHome,
                        new JdkSpec(
                                OSFamily.WINDOWS,
                                SystemArchitecture.X64,
                                Runtime.Version.parse("11"), // Mismatch!
                                "mandrel",
                                false,
                                false
                        )
                ));
            } else {
                return Optional.of(new JdkInstallation(
                        jdkHome,
                        new JdkSpec(
                                OSFamily.WINDOWS,
                                SystemArchitecture.X64,
                                Runtime.Version.parse("21"),
                                "zulu",
                                false,
                                false
                        )
                ));
            }
        }
    }
}
