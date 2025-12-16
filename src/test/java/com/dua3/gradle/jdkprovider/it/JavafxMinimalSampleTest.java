package com.dua3.gradle.jdkprovider.it;

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
 * Integration test that attempts to build the samples/javafx-minimal project using Gradle TestKit.
 * The build is expected to fail currently, but this verifies the wiring and provides a starting point
 * for future end-to-end testing without mocks.
 */
public class JavafxMinimalSampleTest {

    @Test
    void buildJavafxMinimalSample() {
        File projectDir = new File("samples/javafx-minimal");
        assertTrue(projectDir.isDirectory(), "Sample project directory not found: " + projectDir.getAbsolutePath());

        try {
            GradleRunner runner = GradleRunner.create()
                    .withProjectDir(projectDir)
                    .withArguments("build", "--debug", "--stacktrace")
                    // Make the plugin-under-test available on the classpath (even if the sample does not apply it yet)
                    .withPluginClasspath()
                    .forwardOutput();

            BuildResult result = runner.build();

            assertNotNull(result.task(":compileJava"));
            TaskOutcome outcome = result.task(":compileJava").getOutcome();
            assertTrue(outcome == SUCCESS || outcome == UP_TO_DATE, "Build failed with outcome: " + outcome);
        } catch (Exception e) {
            fail("Build failed unexpectedly: " + e.getMessage());
        }
    }
}
