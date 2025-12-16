package com.dua3.gradle.jdkprovider.plugin;

import com.dua3.gradle.jdkprovider.resolver.JdkResolver;
import com.dua3.gradle.jdkprovider.types.JdkSpec;
import com.dua3.gradle.jdkprovider.types.OSFamily;
import com.dua3.gradle.jdkprovider.types.VersionSpec;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

public abstract class JdkProviderPlugin implements Plugin<Project> {
    private static final Logger LOGGER = Logging.getLogger(JdkProviderPlugin.class);

    @Override
    public void apply(Project project) {
        // Register the extension
        JdkProviderExtension extension = project.getExtensions().create("jdkProvider", JdkProviderExtension.class, project.getObjects());

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

            LOGGER.debug("afterEvaluate: jdkSpec = {}", jdkSpec);

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
            LOGGER.debug("JDK is present in {}", jdkDir);

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

            // log
            LOGGER.lifecycle("JDK Provider plugin applied (automatic download = {}, offline mode = {}).", automaticDownload, gradleOfflineMode);
        });
    }

    private static void copyOrLinkJdkDirectory(Path jdkPath, Path target) {
        if (Files.exists(target)) {
            LOGGER.info("JDK already present at target location: {}", target);
        } else {
            try {
                Files.createDirectories(Objects.requireNonNull(target.getParent(), "target parent must not be null"));
                Files.createSymbolicLink(target, jdkPath);
                LOGGER.info("created symbolic link to JDK: {} -> {}", jdkPath, target);
            } catch (IOException e) {
                throw new GradleException("Failed to create symbolic link: " + jdkPath + " -> " + target, e);
            } catch (UnsupportedOperationException ignored) {
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
            }
        }
    }
}
