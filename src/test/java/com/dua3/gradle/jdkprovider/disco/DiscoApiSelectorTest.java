package com.dua3.gradle.jdkprovider.disco;

import com.dua3.gradle.jdkprovider.types.JdkSpec;
import com.dua3.gradle.jdkprovider.types.OSFamily;
import com.dua3.gradle.jdkprovider.types.SystemArchitecture;
import com.dua3.gradle.jdkprovider.types.VersionSpec;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoApiSelectorTest {
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
        Logger logger = Logging.getLogger(DiscoApiSelectorTest.class);

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

        DiscoApiSelector selector = new DiscoApiSelector(baseUrl);

        var pkg = selector.findPackage(
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
}
