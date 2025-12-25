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

import com.dua3.gradle.jdkprovider.local.JdkInstallation;
import com.dua3.gradle.jdkprovider.resolver.JdkResolver;
import com.dua3.gradle.jdkprovider.types.JdkQuery;
import com.dua3.gradle.jdkprovider.types.JdkQueryBuilder;
import com.dua3.gradle.jdkprovider.types.OSFamily;
import com.dua3.gradle.jdkprovider.types.VersionSpec;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

import java.util.HashMap;
import java.util.Map;

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

    /**
     * Default constructor.
     */
    public JdkProviderPlugin() {
        // nothing to do
    }

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
            Boolean automaticDownload = extension.getAutomaticDownload().getOrElse(true);
            boolean gradleOfflineMode = project.getGradle().getStartParameter().isOffline();
            boolean offlineMode = gradleOfflineMode || !automaticDownload;

            JdkResolver jdkResolver = new JdkResolver();

            // resolve global JDK
            JdkQuery globalJdkQuery = createJdkQuery(extension, null);
            JdkInstallation globalJdkInstallation = jdkResolver.resolve(globalJdkQuery, offlineMode)
                    .orElseThrow(() -> new GradleException("No matching JDK found for " + globalJdkQuery));

            logger.debug("[JDK Provider - Plugin] Global JDK for build: {}", globalJdkInstallation.jdkHome());
            logger.lifecycle("JDK for this build: {}", globalJdkInstallation.jdkSpec());

            // resolve overrides
            Map<String, JdkInstallation> overrideInstallations = new HashMap<>();
            extension.getOverrides().forEach(override -> {
                JdkQuery overrideQuery = createJdkQuery(extension, override);
                JdkInstallation overrideInstallation = jdkResolver.resolve(overrideQuery, offlineMode)
                        .orElseThrow(() -> new GradleException("No matching JDK found for override '" + override.getName() + "': " + overrideQuery));
                overrideInstallations.put(override.getName(), overrideInstallation);
                logger.lifecycle("JDK override for '{}': {}", override.getName(), overrideInstallation.jdkSpec());
            });

            // wire tasks
            String executableExtension = OSFamily.current() == OSFamily.WINDOWS ? ".exe" : "";

            p.getTasks().withType(JavaExec.class).configureEach(task -> {
                JdkInstallation installation = getInstallationForTask(task.getName(), p, globalJdkInstallation, overrideInstallations);
                String java = installation.jdkHome().resolve("bin/java" + executableExtension).toAbsolutePath().toString();
                task.getInputs().property("jdkHome", installation.jdkHome().toAbsolutePath().toString());
                task.setExecutable(java);
            });
            p.getTasks().withType(Test.class).configureEach(task -> {
                JdkInstallation installation = getInstallationForTask(task.getName(), p, globalJdkInstallation, overrideInstallations);
                String java = installation.jdkHome().resolve("bin/java" + executableExtension).toAbsolutePath().toString();
                task.getInputs().property("jdkHome", installation.jdkHome().toAbsolutePath().toString());
                task.setExecutable(java);
            });
            p.getTasks().withType(Javadoc.class).configureEach(task -> {
                JdkInstallation installation = getInstallationForTask(task.getName(), p, globalJdkInstallation, overrideInstallations);
                String javadoc = installation.jdkHome().resolve("bin/javadoc" + executableExtension).toAbsolutePath().toString();
                task.getInputs().property("jdkHome", installation.jdkHome().toAbsolutePath().toString());
                task.setExecutable(javadoc);
            });

            p.getTasks().withType(JavaCompile.class).configureEach(task -> {
                JdkInstallation installation = getInstallationForTask(task.getName(), p, globalJdkInstallation, overrideInstallations);
                String javac = installation.jdkHome().resolve("bin/javac" + executableExtension).toAbsolutePath().toString();
                task.getInputs().property("jdkHome", installation.jdkHome().toAbsolutePath().toString());
                task.getOptions().setFork(true);
                task.getOptions().getForkOptions().setExecutable(javac);

                // automatically set release if not set
                if (!task.getOptions().getRelease().isPresent()) {
                    int featureVersion = installation.jdkSpec().version().feature();
                    if (featureVersion >= 9) {
                        task.getOptions().getRelease().set(featureVersion);
                        logger.info("[JDK Provider] Setting release to {} for task '{}'", featureVersion, task.getName());
                    }
                }
            });

            // set resolved JDK properties in extension (read-only for build scripts)
            extension.setJdkHome(globalJdkInstallation.jdkHome().toFile());
            extension.setJdkSpec(globalJdkInstallation.jdkSpec());
        });
    }

    private JdkQuery createJdkQuery(JdkExtension extension, JdkSpecOverride override) {
        if (override == null) {
            return JdkQueryBuilder.builder()
                    .vendorSpec(extension.getVendor().getOrNull())
                    .versionSpec(VersionSpec.parse(String.valueOf(extension.getVersion().getOrElse("any"))))
                    .os(extension.getOs().getOrNull())
                    .arch(extension.getArch().getOrNull())
                    .nativeImageCapable(extension.getNativeImageCapable().getOrElse(false))
                    .javaFxBundled(extension.getJavaFxBundled().getOrElse(false))
                    .build();
        } else {
            return JdkQueryBuilder.builder()
                    .vendorSpec(override.getVendor().getOrElse(extension.getVendor().getOrNull()))
                    .versionSpec(VersionSpec.parse(String.valueOf(override.getVersion().getOrElse(extension.getVersion().getOrElse("any")))))
                    .os(override.getOs().getOrElse(extension.getOs().getOrNull()))
                    .arch(override.getArch().getOrElse(extension.getArch().getOrNull()))
                    .nativeImageCapable(override.getNativeImageCapable().getOrElse(extension.getNativeImageCapable().getOrElse(false)))
                    .javaFxBundled(override.getJavaFxBundled().getOrElse(extension.getJavaFxBundled().getOrElse(false)))
                    .build();
        }
    }

    private JdkInstallation getInstallationForTask(String taskName, Project project, JdkInstallation globalInstallation, Map<String, JdkInstallation> overrides) {
        SourceSetContainer sourceSets = project.getExtensions().findByType(SourceSetContainer.class);
        if (sourceSets == null) {
            return globalInstallation;
        }

        return sourceSets.stream()
                .filter(ss -> {
                    String compileTaskName = ss.getCompileJavaTaskName();
                    String javadocTaskName = ss.getJavadocTaskName();
                    String testTaskName = ss.getTaskName("test", null);
                    String runTaskName = ss.getTaskName("run", null);

                    return taskName.equals(compileTaskName) ||
                            taskName.equals(javadocTaskName) ||
                            taskName.equals(testTaskName) ||
                            taskName.equals(runTaskName);
                })
                .filter(ss -> overrides.containsKey(ss.getName()))
                .map(ss -> overrides.get(ss.getName()))
                .findFirst()
                .orElse(globalInstallation);
    }
}
