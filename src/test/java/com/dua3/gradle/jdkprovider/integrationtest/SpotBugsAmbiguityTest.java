package com.dua3.gradle.jdkprovider.integrationtest;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SpotBugsAmbiguityTest {

    @TempDir
    File tempDir;

    @Test
    void testSpotBugsAmbiguity() throws IOException {
        // This test simulates the issue where SpotBugs creates configurations that conflict with TargetJvmVersion attribute.

        File projectDir = new File(tempDir, "spotbugs-test");
        assertTrue(projectDir.mkdirs());

        Files.writeString(new File(projectDir, "settings.gradle.kts").toPath(),
                "rootProject.name = \"spotbugs-test\"\n"
        );

        // Use 21 as it's common
        Files.writeString(new File(projectDir, "build.gradle.kts").toPath(),
                """
                        plugins {
                            `java-library`
                            id("com.dua3.gradle.jdkprovider")
                            id("com.github.spotbugs") version "6.0.26"
                        }
                        repositories {
                            mavenCentral()
                        }
                        jdk {
                            version = 21
                        }
                        """
        );

        File srcDir = new File(projectDir, "src/main/java/com/example");
        assertTrue(srcDir.mkdirs());
        Files.writeString(new File(srcDir, "Example.java").toPath(),
                "package com.example; public class Example {}");

        // Running a task that triggers dependency resolution, like spotbugsMain or just help
        // The issue was reported during 'distTar' but it was due to runtimeClasspath resolution 
        // which SpotBugs variants were interfering with.

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments("help", "--stacktrace")
                .withPluginClasspath()
                .forwardOutput()
                .build();

        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"));
    }
}
