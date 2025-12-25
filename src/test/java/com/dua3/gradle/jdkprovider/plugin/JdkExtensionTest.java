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
    }

    @Test
    void testOverrides() {
        Project project = ProjectBuilder.builder().build();
        JdkExtension extension = project.getObjects().newInstance(JdkExtension.class);

        extension.getOverrides().create("java9", override -> {
            override.setVersion(9);
        });

        assertEquals(1, extension.getOverrides().size());
        assertEquals("java9", extension.getOverrides().getByName("java9").getName());
        assertEquals(9, extension.getOverrides().getByName("java9").getVersion().get());
    }
}
