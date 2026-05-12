package com.dua3.gradle.jdkprovider.integrationtest;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyResolutionTest {

    @TempDir
    File tempDir;

    @Test
    void testDependencyResolutionWithHigherJvmVersion() throws IOException {
        // This test simulates the issue where a dependency requires a higher JVM version than the one running Gradle.
        // We use a dummy project that depends on a library known to have TargetJvmVersion attribute.
        // Since we can't easily find a public library that ONLY supports Java 25+,
        // we will simulate this by creating a multi-project build where one project produces a Java 25 variant
        // and another project (the consumer) tries to resolve it while running on a lower JVM.

        File projectDir = new File(tempDir, "dep-res");
        assertTrue(projectDir.mkdirs());

        Files.writeString(new File(projectDir, "settings.gradle.kts").toPath(),
                """
                        rootProject.name = "dep-res"
                        include("producer", "consumer")
                        """
        );

        File producerDir = new File(projectDir, "producer");
        assertTrue(producerDir.mkdirs());
        Files.writeString(new File(producerDir, "build.gradle.kts").toPath(),
                """
                        plugins {
                            `java-library`
                        }
                        java {
                            toolchain {
                                languageVersion.set(JavaLanguageVersion.of(25))
                            }
                        }
                        """
        );
        mkdir(new File(producerDir, "src/main/java/com/example/producer"));
        Files.writeString(new File(producerDir, "src/main/java/com/example/producer/Producer.java").toPath(),
                "package com.example.producer; public class Producer {}");

        File consumerDir = new File(projectDir, "consumer");
        assertTrue(consumerDir.mkdirs());
        Files.writeString(new File(consumerDir, "build.gradle.kts").toPath(),
                """
                        plugins {
                            `java-library`
                            id("com.dua3.gradle.jdkprovider")
                        }
                        jdk {
                            version = 25
                        }
                        dependencies {
                            implementation(project(":producer"))
                        }
                        """
        );
        mkdir(new File(consumerDir, "src/main/java/com/example/consumer"));
        Files.writeString(new File(consumerDir, "src/main/java/com/example/consumer/Consumer.java").toPath(),
                "package com.example.consumer; import com.example.producer.Producer; public class Consumer { Producer p; }");

        // Run Gradle with current JVM (likely < 25 if it's a standard CI/Dev environment)
        // If the plugin works, it should set TargetJvmVersion to 25, allowing resolution of the producer.
        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments(":consumer:compileJava", "--stacktrace")
                .withPluginClasspath()
                .forwardOutput()
                .build();

        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"));
    }

    private void mkdir(File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("Failed to create directory: " + dir);
        }
    }
}
