package com.dua3.gradle.jdkprovider.plugin;

import com.dua3.gradle.jdkprovider.types.JdkSpec;
import com.dua3.gradle.jdkprovider.types.OSFamily;
import com.dua3.gradle.jdkprovider.types.SystemArchitecture;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdkExtensionTest {

    @Test
    void testSetVersionInt() {
        Project project = ProjectBuilder.builder().build();
        JdkExtension extension = project.getObjects().newInstance(JdkExtension.class);

        extension.setVersion(21);
        assertEquals("21", String.valueOf(extension.getVersion().get()));
    }

    @Test
    void testSetVersionString() {
        Project project = ProjectBuilder.builder().build();
        JdkExtension extension = project.getObjects().newInstance(JdkExtension.class);

        extension.getVersion().set("17");
        assertEquals("17", String.valueOf(extension.getVersion().get()));

        extension.getVersion().set("25.0.1");
        assertEquals("25.0.1", String.valueOf(extension.getVersion().get()));

        extension.getVersion().set("25.0.1+");
        assertEquals("25.0.1+", String.valueOf(extension.getVersion().get()));
    }

    @Test
    void testSetLangVersion() {
        Project project = ProjectBuilder.builder().build();
        JdkExtension extension = project.getObjects().newInstance(JdkExtension.class);

        extension.setLangVersion(25);

        assertEquals(25, extension.getLangVersion().get());
    }

    @Test
    void testOverrides() {
        Project project = ProjectBuilder.builder().build();
        JdkExtension extension = project.getObjects().newInstance(JdkExtension.class);

        extension.getOverrides().create("java9", override -> {
            override.setVersion(9);
            override.setLangVersion(8);
        });

        assertEquals(1, extension.getOverrides().size());
        assertEquals("java9", extension.getOverrides().getByName("java9").getName());
        assertEquals(9, extension.getOverrides().getByName("java9").getVersion().get());
        assertEquals(8, extension.getOverrides().getByName("java9").getLangVersion().get());
    }

    @Test
    void testOverrideJavaLauncher() throws Exception {
        Project project = ProjectBuilder.builder().build();
        JdkExtension extension = project.getObjects().newInstance(JdkExtension.class);
        JdkSpecOverride override = extension.getOverrides().create("native");
        File jdkHome = Files.createTempDirectory("jdk-provider-override").toFile();
        JdkSpec spec = new JdkSpec(
                OSFamily.current(),
                SystemArchitecture.current(),
                Runtime.Version.parse("25"),
                "test",
                false,
                true
        );
        override.setJdkHome(jdkHome);
        override.setJdkSpec(spec);

        var launcher = extension.getJavaLauncher(project, "native").get();

        assertEquals(25, launcher.getMetadata().getLanguageVersion().asInt());
        assertEquals(jdkHome, launcher.getMetadata().getInstallationPath().getAsFile());
        assertEquals(
                new File(jdkHome, "bin/java" + (OSFamily.current() == OSFamily.WINDOWS ? ".exe" : "")),
                launcher.getExecutablePath().getAsFile()
        );
    }
}
