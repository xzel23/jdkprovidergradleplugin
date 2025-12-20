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

import org.gradle.jvm.toolchain.JavaInstallationMetadata
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    application
    id("com.dua3.gradle.jdkprovider")
    id("org.graalvm.buildtools.native") version "0.11.1"
}

jdk {
    nativeImageCapable = true
}

application {
    mainClass.set("com.example.HelloNative")
}

project.afterEvaluate {
    println(jdk.jdkSpec.get().vendor)
    println(jdk.jdkHome.get())

    val jdkSpec = jdk.jdkSpec.get()
    val jdkHome = jdk.jdkHome.get()

    val jimd = object : JavaInstallationMetadata {
        override fun getLanguageVersion(): JavaLanguageVersion =
            JavaLanguageVersion.of(jdkSpec.versionSpec.major!!)

        override fun getJavaRuntimeVersion(): String = jdkSpec.versionSpec.toString()

        override fun getJvmVersion(): String = jdkSpec.versionSpec.toString()

        override fun getVendor(): String = jdkSpec.vendor.toString()

        override fun getInstallationPath(): Directory = jdkHome

        override fun isCurrentJvm(): Boolean = false
    }

    val javaLauncher = object : JavaLauncher {
        override fun getMetadata(): JavaInstallationMetadata = jimd

        override fun getExecutablePath(): RegularFile {
            val javaProvider: Provider<File> = project.provider { jdkHome.asFile.resolve("bin/java") }
            return project.layout.file(javaProvider).get()
        }
    }

    graalvmNative {
        binaries {
            named("main") {
                imageName.set("hello_native")
                mainClass.set("com.example.HelloNative")
                this.javaLauncher.set(javaLauncher)
            }
        }
    }
}