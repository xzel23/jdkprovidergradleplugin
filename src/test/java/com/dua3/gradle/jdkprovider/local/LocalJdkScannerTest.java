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

package com.dua3.gradle.jdkprovider.local;

import com.dua3.gradle.jdkprovider.types.JdkSpec;
import com.dua3.gradle.jdkprovider.types.OSFamily;
import com.dua3.gradle.jdkprovider.types.SystemArchitecture;
import com.dua3.gradle.jdkprovider.types.VersionSpec;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalJdkScannerTest {
    private String originalPathsProp;

    @BeforeEach
    void stashProp() {
        originalPathsProp = System.getProperty("org.gradle.java.installations.paths");
    }

    @AfterEach
    void restoreProp() {
        if (originalPathsProp == null) {
            System.clearProperty("org.gradle.java.installations.paths");
        } else {
            System.setProperty("org.gradle.java.installations.paths", originalPathsProp);
        }
    }

    @Test
    void detectsJavaFxAndFeatureVersionViaHeuristics() throws IOException {
        Path fakeJdk = Files.createTempDirectory("fake-jdk-");

        // create
        Path bin = fakeJdk.resolve("bin");
        Files.createDirectories(bin);
        Files.writeString(bin.resolve("java"), "java");

        // create jmods/javafx-graphics.jmod to trigger JavaFX detection
        Path jmods = fakeJdk.resolve("jmods");
        Files.createDirectories(jmods);
        Files.writeString(jmods.resolve("javafx-graphics.jmod"), "");

        // create a release file with JAVA_VERSION="21.0.3"
        Path release = fakeJdk.resolve("release");
        Files.writeString(release, """
                IMPLEMENTOR="Azul Systems, Inc."
                IMPLEMENTOR_VERSION="Zulu25.30+17-CA"
                JAVA_RUNTIME_VERSION="25.0.1+8-LTS"
                JAVA_VERSION="25.0.1"
                JAVA_VERSION_DATE="2025-10-21"
                LIBC="default"
                MODULES="java.base com.azul.tooling java.logging java.management java.security.sasl java.naming jdk.jfr com.azul.crs.client java.compiler java.datatransfer java.xml java.prefs java.desktop java.instrument java.rmi java.management.rmi java.net.http java.scripting java.security.jgss java.transaction.xa java.sql java.sql.rowset java.xml.crypto java.se java.smartcardio javafx.base jdk.unsupported javafx.graphics javafx.controls javafx.fxml javafx.media jdk.unsupported.desktop javafx.swing jdk.jsobject jdk.xml.dom javafx.web jdk.accessibility jdk.internal.jvmstat jdk.attach jdk.charsets jdk.internal.opt jdk.zipfs jdk.compiler jdk.crypto.cryptoki jdk.crypto.ec jdk.dynalink jdk.internal.ed jdk.editpad jdk.internal.vm.ci jdk.graal.compiler jdk.graal.compiler.management jdk.hotspot.agent jdk.httpserver jdk.incubator.vector jdk.internal.le jdk.internal.md jdk.jartool jdk.javadoc jdk.jcmd jdk.management jdk.management.agent jdk.jconsole jdk.jdeps jdk.jdwp.agent jdk.jdi jdk.jlink jdk.jpackage jdk.jshell jdk.jstatd jdk.localedata jdk.management.jfr jdk.naming.dns jdk.naming.rmi jdk.net jdk.nio.mapmode jdk.sctp jdk.security.auth jdk.security.jgss jfx.incubator.input jfx.incubator.richtext"
                OS_ARCH="aarch64"
                OS_NAME="Darwin"
                SOURCE=".:git:90b923e6d1bf"
                """
        );

        // make scanner discover our path
        System.setProperty("org.gradle.java.installations.paths", fakeJdk.toString());

        LocalJdkScanner scanner = new LocalJdkScanner();

        JdkSpec jdkSpec = JdkSpec.builder()
                .version(VersionSpec.parse("25"))
                .os(OSFamily.MACOS)
                .arch(SystemArchitecture.AARCH64)
                .vendor(JvmVendorSpec.AZUL)
                .javaFxBundled(true)
                .build();

        List<JdkInstallation> found = scanner.getCompatibleInstalledJdks(jdkSpec);
        assertTrue(found.stream().map(JdkInstallation::jdkHome).anyMatch(fakeJdk::equals));
    }
}

