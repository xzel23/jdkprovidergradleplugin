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

plugins {
    `java-library`
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "2.0.0"
}

group = "com.dua3.gradle"
version = "0.2.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    annotationProcessor("io.soabase.record-builder:record-builder-processor:51")
    compileOnly("io.soabase.record-builder:record-builder-core:51")

    // Compile against Gradle public API (needed for resolver SPI types)
    implementation(gradleApi())
    // Lightweight archive extraction for tar/gzip support
    implementation("org.apache.commons:commons-compress:1.28.0")
    // Minimal JSON parsing for DiscoAPI responses
    implementation("org.json:json:20240303")

    // Gradle TestKit and project builder for functional/unit tests
    testImplementation(gradleTestKit())
    // Mock HTTP server for DiscoAPI tests
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    website.set("https://github.com/xzel23/jdkprovidergradleplugin")
    vcsUrl.set("https://github.com/xzel23/jdkprovidergradleplugin")
    plugins {
        register("jdk") {
            id = "com.dua3.gradle.jdkprovider"
            implementationClass = "com.dua3.gradle.jdkprovider.plugin.JdkProviderPlugin"
            displayName = "JDK Provider Plugin"
            description = "Resolves and provisions suitable JDK for Gradle builds using the Foojay DiscoAPI"
            tags.set(
                listOf(
                    "java",
                    "jdk",
                    "jdk-download",
                    "javafx",
                    "graalvm",
                    "native-image",
                    "toolchain",
                    "foojay",
                    "discoapi",
                    // include ARM architecture tags as the plugin is an alternative to using toolchains with the
                    // openjfx javafx-plugin which is unsupported on ARM
                    "aarch64",
                    "arm"
                )
            )
        }
    }
}
