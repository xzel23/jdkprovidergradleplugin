package com.dua3.gradle.jdkprovider.integrationtest;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumableAttributeTest {

    @TempDir
    File tempDir;

    @Test
    void testConsumableConfigurationHasCorrectAttribute() throws IOException {
        // This test simulates the issue where a producer project's consumable configurations
        // don't have the TargetJvmVersion attribute set by the plugin,
        // causing Gradle to default them to the running JVM version.

        File projectDir = new File(tempDir, "consumable-attr");
        assertTrue(projectDir.mkdirs());

        Files.writeString(new File(projectDir, "settings.gradle.kts").toPath(),
                """
                        rootProject.name = "consumable-attr"
                        include("producer", "consumer")
                        """
        );

        // Both projects use the plugin and request Java 21.
        // We will run this test on whatever JVM is currently running (likely 21 or higher).
        // If the running JVM is > 21, and the plugin only sets the attribute on resolvable configs,
        // the producer's consumable configs might default to the running JVM version.

        String buildGradleContent =
                """
                        plugins {
                            `java-library`
                            id("com.dua3.gradle.jdkprovider")
                        }
                        repositories {
                            mavenCentral()
                        }
                        jdk {
                            version = 21
                        }
                        tasks.register("checkAttributes") {
                            doLast {
                                val targetJvmVersionAttr = Attribute.of("org.gradle.jvm.version", Int::class.javaObjectType)
                                configurations.matching { it.isCanBeConsumed && (it.name.endsWith("Elements") || it.name == "api" || it.name == "implementation") }.forEach { conf ->
                                    val attr = conf.attributes.getAttribute(targetJvmVersionAttr)
                                    println("DEBUG: configuration ${conf.name} TargetJvmVersion = $attr")
                                    if (attr != 21) {
                                        throw GradleException("Expected TargetJvmVersion to be 21 for ${conf.name}, but was $attr")
                                    }
                                }
                            }
                        }
                        """;

        File producerDir = new File(projectDir, "producer");
        assertTrue(producerDir.mkdirs());
        Files.writeString(new File(producerDir, "build.gradle.kts").toPath(), buildGradleContent);

        File consumerDir = new File(projectDir, "consumer");
        assertTrue(consumerDir.mkdirs());
        Files.writeString(new File(consumerDir, "build.gradle.kts").toPath(),
                buildGradleContent +
                        "dependencies {\n" +
                        "    implementation(project(\":producer\"))\n" +
                        "}\n"
        );

        // We need to ensure the running JVM is higher than 21 to see the failure
        // if the fix is not present and Gradle defaults to running JVM.
        // Actually, even if it's the same, it's good to check.
        // But the user's case is specifically: Running JVM 26, Resolved 25.

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments("checkAttributes", "--info", "--stacktrace")
                .withPluginClasspath()
                .forwardOutput()
                .build();

        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"));
    }
}
