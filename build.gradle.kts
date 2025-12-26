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

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.kotlin.dsl.withType

/* define dependency versions */

val recordBuilderVersion = "51"
val commonsCompressVersion = "1.28.0"
val jsonVersion = "20250517"
val mockWebServerVersion = "5.3.2"
val junitVersion = "6.0.1"

/* plugins */

plugins {
    `java-library`
    `java-gradle-plugin`
    `maven-publish`
    id("com.github.ben-manes.versions") version "0.53.0"
    id("com.gradle.plugin-publish") version "2.0.0"
}

/* define group and version for publishing */

group = "com.dua3.gradle"
version = "0.4.0-beta4"

/* compile using Java 21 */

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

/* define dependencies */

dependencies {
    annotationProcessor("io.soabase.record-builder:record-builder-processor:$recordBuilderVersion")
    compileOnly("io.soabase.record-builder:record-builder-core:$recordBuilderVersion")

    // Compile against Gradle public API (needed for resolver SPI types)
    implementation(gradleApi())
    // Lightweight archive extraction for tar/gzip support
    implementation("org.apache.commons:commons-compress:$commonsCompressVersion")
    // Minimal JSON parsing for DiscoAPI responses
    implementation("org.json:json:$jsonVersion")

    // Gradle TestKit and project builder for functional/unit tests
    testImplementation(gradleTestKit())
    // Mock HTTP server for DiscoAPI tests
    testImplementation("com.squareup.okhttp3:mockwebserver:$mockWebServerVersion")

    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

/* use JUnit for tests */
tasks.test {
    useJUnitPlatform()
}

/* configure the Gradle Plugin release */

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

/* configure the versions plugin: only suggest stable dependency versions */

// Determines version stability based on keywords and regex
fun isStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "[0-9,.v]+-?(rc|ea|alpha|beta|b|m|snapshot)([+-]?[0-9]*)?".toRegex()
    return stableKeyword || !regex.matches(version.lowercase())
}

tasks.withType<DependencyUpdatesTask> {
    // refuse non-stable versions
    rejectVersionIf {
        !isStable(candidate.version)
    }

    // dependencyUpdates fails in parallel mode with Gradle 9+ (https://github.com/ben-manes/gradle-versions-plugin/issues/968)
    doFirst {
        gradle.startParameter.isParallelProjectExecutionEnabled = false
    }
}
