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

package com.dua3.gradle.jdkprovider.plugin;

import com.dua3.gradle.jdkprovider.local.LocalJdkScanner;
import com.dua3.gradle.jdkprovider.resolver.JdkResolver;
import com.dua3.gradle.jdkprovider.types.JdkSpec;
import com.dua3.gradle.jdkprovider.types.OSFamily;
import com.dua3.gradle.jdkprovider.types.VersionSpec;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A Gradle plugin for managing JDK dependencies in a project. This plugin resolves and configures
 * a specific JDK based on user-defined criteria, downloads it if necessary, and integrates it into
 * the build tasks such as compilation, execution, testing, and documentation generation.
 * <p>
 * The plugin registers an extension named "jdk" of type {@link JdkExtension}.
 * This extension allows users to specify JDK requirements such as version, vendor, operating system,
 * architecture, and additional capabilities (e.g., native image support or bundled JavaFX).
 * <p>
 * Upon evaluation of the project:
 * 1. The plugin resolves the requested JDK based on the specification provided through the extension.
 * 2. If automatic download is enabled and the Gradle offline mode is not active, the plugin
 *    attempts to download the matching JDK if not found in standard installation paths or in the
 *    local cache.
 * 3. The identified JDK is copied or linked into the project's build directory.
 * 4. The plugin updates relevant build tasks (e.g., JavaExec, JavaCompile, Test, Javadoc) to
 *    use the resolved JDK executables.
 * <p>
 * If no matching JDK is found, or if an error occurs during resolution or installation, the
 * plugin throws a {@link GradleException} with details about the failure.
 */
public abstract class JdkProviderPlugin implements Plugin<Project> {
    private static final Logger LOGGER = Logging.getLogger(JdkProviderPlugin.class);

    /**
     * Apply the plugin to the project as described in the plugin class description.
     *
     * @param project the project to apply the plugin to
     */
    @Override
    public void apply(Project project) {
        Logger logger = project.getLogger();

        // Check if another plugin has registered an extension with the same name
        if (project.getExtensions().findByName("jdk") != null) {
            throw new GradleException(
                    "A 'jdk' extension already exists. Only plugin controlling JDK resolution may be applied."
            );
        }

        // Optionally warn if a toolchain is configured differently
        JavaPluginExtension javaExt = project.getExtensions().findByType(JavaPluginExtension.class);
        if (javaExt != null) {
            JavaToolchainSpec toolchain = javaExt.getToolchain();

            // Gradle always sets a default language version; compare it to the running JVM
            boolean languageVersionSet = toolchain.getLanguageVersion().isPresent();
            if (languageVersionSet) {
                project.getLogger().warn("The JDK Provider plugin will override the toolchain settings.");
            }
        }

        // Register the 'jdk' extension
        JdkExtension extension = project.getExtensions().create("jdk", JdkExtension.class, project.getObjects());

        project.afterEvaluate(p -> {
            // resolve and install JDK into project build dir
            JdkSpec jdkSpec = JdkSpec.builder()
                    .vendor(extension.getVendor().getOrNull())
                    .version(VersionSpec.parse(extension.getVersion().getOrElse("any")))
                    .os(extension.getOs().getOrNull())
                    .arch(extension.getArch().getOrNull())
                    .nativeImageCapable(extension.getNativeImageCapable().getOrNull())
                    .javaFxBundled(extension.getJavaFxBundled().getOrNull())
                    .build();

            logger.debug("afterEvaluate: jdkSpec = {}", jdkSpec);

            Boolean automaticDownload = extension.getAutomaticDownload().getOrElse(true);
            boolean gradleOfflineMode = project.getGradle().getStartParameter().isOffline();
            boolean offlineMode = gradleOfflineMode || !automaticDownload;

            Path buildDir = project.getLayout().getBuildDirectory().getAsFile().get().toPath();
            Path jdkDir = buildDir.resolve("jdk");
            new JdkResolver().resolve(jdkSpec, offlineMode)
                    .ifPresentOrElse(
                            jdkInstallation -> copyOrLinkJdkDirectory(jdkInstallation.jdkHome(), jdkDir),
                            () -> {
                                throw new GradleException("No matching JDK found for " + jdkSpec + " in local cache or DiscoAPI.");
                            }
                    );
            logger.debug("JDK is present in {}", jdkDir);
            String jdkinfo = LocalJdkScanner.readJdkSpec(jdkDir)
                    .map(jdkInstallation -> jdkInstallation.jdkSpec().toString())
                    .orElse(jdkDir.toString());
            logger.lifecycle("JDK for this build: {}", jdkinfo);

            // wire tasks
            Path jdkBin = jdkDir.resolve("bin");
            String executableExtension = OSFamily.current() == OSFamily.WINDOWS ? ".exe" : "";
            String java = jdkBin.resolve("java" + executableExtension).toString();
            String javac = jdkBin.resolve("javac" + executableExtension).toString();

            p.getTasks().withType(JavaExec.class).forEach(task -> task.setExecutable(java));
            p.getTasks().withType(Test.class).forEach(task -> task.setExecutable(java));
            p.getTasks().withType(Javadoc.class).forEach(task -> task.setExecutable(java));

            p.getTasks().withType(JavaCompile.class).forEach(task -> {
                task.getOptions().setFork(true);
                task.getOptions().getForkOptions().setExecutable(javac);
            });
        });
    }

    /**
     * Copies or creates a symbolic link for the JDK directory from the specified source path to the target path.
     * If symbolic linking is not supported, the method falls back to copying the directory content recursively.
     *
     * @param jdkPath the path to the source JDK directory to be linked or copied
     * @param target the target path where the JDK should be linked or copied to
     * @throws GradleException if an error occurs during linking or copying the JDK directory
     * @throws NullPointerException if the parent of the target path is null
     */
    private static void copyOrLinkJdkDirectory(Path jdkPath, Path target) {
        if (Files.exists(target)) {
            LOGGER.info("JDK already present at target location: {}", target);
        } else {
            // create the parent folder if necessary
            try {
                Files.createDirectories(Objects.requireNonNull(target.getParent(), "target parent must not be null"));
            } catch (IOException e) {
                throw new GradleException("Failed to create directory: " + target.getParent(), e);
            }

            // try to create symlink
            try {
                Files.createSymbolicLink(target, jdkPath);
                LOGGER.info("created symbolic link to JDK: {} -> {}", jdkPath, target);
            } catch (UnsupportedOperationException | FileSystemException ignored) {
                LOGGER.debug("JDK symlinking not supported, copying instead: {} -> {}", jdkPath, target);
                try (Stream<Path> files = Files.walk(jdkPath)) {
                    files.forEach(sourcePath -> {
                        try {
                            Path targetPath = target.resolve(jdkPath.relativize(sourcePath));
                            if (Files.isDirectory(sourcePath)) {
                                Files.createDirectories(targetPath);
                            } else {
                                Files.copy(sourcePath, targetPath);
                            }
                        } catch (IOException e) {
                            throw new GradleException("Failed to copy file: " + sourcePath, e);
                        }
                    });
                } catch (IOException e) {
                    throw new GradleException("Failed to copy JDK directory: " + jdkPath + " -> " + target, e);
                }
            } catch (IOException e) {
                throw new GradleException("Failed to create symbolic link: " + jdkPath + " -> " + target, e);
            }
        }
    }
}
