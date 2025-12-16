package com.dua3.gradle.jdkprovider.disco;

import com.dua3.gradle.jdkprovider.types.JdkSpec;
import com.dua3.gradle.jdkprovider.types.OSFamily;
import com.dua3.gradle.jdkprovider.types.SystemArchitecture;
import com.dua3.gradle.jdkprovider.types.VersionSpec;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoApiClientTest {
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
