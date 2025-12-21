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

package com.dua3.gradle.jdkprovider.integrationtest;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration test that attempts to build the samples/helloNative project using Gradle TestKit.
 * <p>
 * If no matching JDK is installed, the build needs a working internet connection.
 */
class HelloNativeSampleTest {

    @Test
    void buildHelloNativeSample() {
        File projectDir = new File("samples/helloNative");
        assertTrue(projectDir.isDirectory(), "Sample project directory not found: " + projectDir.getAbsolutePath());

        try {
            GradleRunner runner = GradleRunner.create()
                    .withProjectDir(projectDir)
                    .withArguments("nativeCompile", "--no-build-cache", "--no-configuration-cache", "--stacktrace")
                    // Make the plugin-under-test available on the classpath
                    .withPluginClasspath()
                    .forwardOutput();

            BuildResult result = runner.build();

            assertNotNull(result.task(":compileJava"));
            TaskOutcome outcome = result.task(":compileJava").getOutcome();
            assertTrue(outcome == SUCCESS || outcome == UP_TO_DATE, "Build failed with outcome: " + outcome);

            File nativeExecutable = new File(projectDir, "build/native/nativeCompile/hello_native" + (System.getProperty("os.name").toLowerCase().contains("win") ? ".exe" : ""));
            assertTrue(nativeExecutable.exists(), "Native executable should exist at: " + nativeExecutable.getAbsolutePath());
            assertTrue(nativeExecutable.canExecute(), "Native executable should be executable");
        } catch (Exception e) {
            fail("Build failed unexpectedly: " + e.getMessage());
        }
    }
}
