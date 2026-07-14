package com.dua3.gradle.jdkprovider.plugin;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

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
}
