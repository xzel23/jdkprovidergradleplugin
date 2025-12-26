// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 Axel Howind
// This file is part of the JDK Provider Gradle Plugin.

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
 * Integration test for the multi-release sample.
 */
class MultiReleaseSampleTest {

    @Test
    void buildMultiReleaseSample() {
        File projectDir = new File("samples/multi-release");
        assertTrue(projectDir.isDirectory(), "Sample project directory not found: " + projectDir.getAbsolutePath());

        try {
            GradleRunner runner = GradleRunner.create()
                    .withProjectDir(projectDir)
                    .withArguments("clean", "jar", "--no-build-cache", "--no-configuration-cache", "--info", "--stacktrace")
                    .withPluginClasspath()
                    .forwardOutput();

            BuildResult result = runner.build();

            assertNotNull(result.task(":compileJava"));
            TaskOutcome outcome = result.task(":compileJava").getOutcome();
            assertTrue(outcome == SUCCESS || outcome == UP_TO_DATE, "compileJava failed with outcome: " + outcome);

            assertNotNull(result.task(":compileJava21Java"));
            TaskOutcome outcome21 = result.task(":compileJava21Java").getOutcome();
            assertTrue(outcome21 == SUCCESS || outcome21 == UP_TO_DATE, "compileJava21Java failed with outcome: " + outcome21);

            assertNotNull(result.task(":compileJava25Java"));
            TaskOutcome outcome25 = result.task(":compileJava25Java").getOutcome();
            assertTrue(outcome25 == SUCCESS || outcome25 == UP_TO_DATE, "compileJava25Java failed with outcome: " + outcome25);

            assertNotNull(result.task(":jar"));
            TaskOutcome jarOutcome = result.task(":jar").getOutcome();
            assertTrue(jarOutcome == SUCCESS || jarOutcome == UP_TO_DATE, "jar failed with outcome: " + jarOutcome);

            File jarFile = new File(projectDir, "build/libs/multi-release-sample-1.0-SNAPSHOT.jar");
            assertTrue(jarFile.exists(), "JAR file not found: " + jarFile.getAbsolutePath());
        } catch (Exception e) {
            fail("Build failed unexpectedly: " + e.getMessage());
        }
    }
}
