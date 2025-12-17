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
    application
    id("com.dua3.gradle.jdkprovider")
}

jdk {
    version = "25"
    vendor = JvmVendorSpec.BELLSOFT
    javaFxBundled = true
}

application {
    mainClass.set("com.example.HelloFx")
}

// Compile and run with the JavaFX controls module enabled (assuming a JavaFX-bundled JDK)
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--add-modules=javafx.controls"))
}

tasks.named<JavaExec>("run") {
    jvmArgs = listOf("--add-modules=javafx.controls")
}
