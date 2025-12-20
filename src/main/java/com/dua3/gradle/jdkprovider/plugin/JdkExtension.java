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

import com.dua3.gradle.jdkprovider.types.JdkSpec;
import com.dua3.gradle.jdkprovider.types.OSFamily;
import com.dua3.gradle.jdkprovider.types.SystemArchitecture;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.Property;
import org.gradle.jvm.toolchain.JvmVendorSpec;

import javax.inject.Inject;

/**
 * Extension for configuring the JDK selection.
 */
public abstract class JdkExtension {

    private final Property<OSFamily> os;
    private final Property<SystemArchitecture> arch;
    private final Property<Object> version;
    private final Property<JvmVendorSpec> vendor;
    private final Property<Boolean> nativeImageCapable;
    private final Property<Boolean> javaFxBundled;
    private final Property<Boolean> automaticDownload;
    // read-only properties exposed to users; set internally by the plugin
    private final DirectoryProperty jdkHome;
    private final Property<JdkSpec> jdkSpec;

    /**
     * Constructs a new {@code JdkExtension} instance with properties to configure JDK selection.
     *
     * @param objects an {@code ObjectFactory} used to create and initialize properties for the JDK
     *                configuration, such as operating system, system architecture, version,
     *                vendor, and boolean flags for specific capabilities.
     */
    @Inject
    public JdkExtension(ObjectFactory objects) {
        this.os = objects.property(OSFamily.class);
        this.arch = objects.property(SystemArchitecture.class);
        this.version = objects.property(Object.class);
        this.vendor = objects.property(JvmVendorSpec.class);
        this.nativeImageCapable = objects.property(Boolean.class);
        this.javaFxBundled = objects.property(Boolean.class);
        this.automaticDownload = objects.property(Boolean.class);
        this.jdkHome = objects.directoryProperty();
        this.jdkSpec = objects.property(JdkSpec.class);
    }

    /**
     * Retrieves the property representing the operating system family.
     *
     * @return a {@code Property<OSFamily>} that holds the operating system family configuration.
     */
    public Property<OSFamily> getOs() {
        return os;
    }

    /**
     * Retrieves the system architecture configuration property.
     *
     * @return a {@link Property} representing the system architecture, which indicates the
     *         type of system architecture (e.g., AARCH32, AARCH64, X64, X86_32).
     */
    public Property<SystemArchitecture> getArch() {
        return arch;
    }

    /**
     * Retrieves the version of the JDK configured for the extension.
     *
     * @return a {@link Property} containing the version.
     */
    public Property<Object> getVersion() {
        return version;
    }

    /**
     * Set the JDK version.
     * <p>
     * This is a convenience method that allows setting the version using an {@code int} argument.
     *
     * @param version the JDK version to set
     */
    public void setVersion(int version) {
        this.version.set(version);
    }

    /**
     * Set the JDK version.
     *
     * @param version the JDK version to set
     */
    public void setVersion(String version) {
        this.version.set(version);
    }


    /**
     * Retrieves the property representing the vendor specification of the JVM.
     *
     * @return a property instance containing the JVM vendor specification.
     */
    public Property<JvmVendorSpec> getVendor() {
        return vendor;
    }

    /**
     * Retrieves the property indicating whether the JDK is capable of building native images.
     *
     * @return a {@code Property<Boolean>} representing whether native image capability is supported.
     */
    public Property<Boolean> getNativeImageCapable() {
        return nativeImageCapable;
    }

    /**
     * Retrieves the property indicating whether the JavaFX runtime is bundled with the JDK.
     *
     * @return a {@code Property<Boolean>} representing whether the JDK includes JavaFX.
     */
    public Property<Boolean> getJavaFxBundled() {
        return javaFxBundled;
    }

    /**
     * Retrieves the property indicating whether automatic download of the JDK is enabled.
     *
     * @return a {@link Property} containing a Boolean value that specifies if automatic downloading is enabled.
     */
    public Property<Boolean> getAutomaticDownload() {
        return automaticDownload;
    }

    /**
     * Read-only provider for the resolved JDK home directory.
     *
     * @return a {@code Provider<Directory>} representing the resolved JDK home directory
     */
    public Provider<Directory> getJdkHome() {
        return jdkHome;
    }

    /**
     * Read-only provider for the resolved JDK version specification.
     *
     * @return a {@code Provider<JdkSpec>} representing the resolved JDK specification
     */
    public Provider<JdkSpec> getJdkSpec() {
        return jdkSpec;
    }

    /**
     * Package private setter for the JDK home directory.
     * <p>
     * The plugin uses this method internally to populate the JDK home property with a
     * specific directory path representing the JDK home.
     *
     * @param jdkHome the {@link java.io.File} representing the JDK home directory to be set
     */
    void setJdkHome(java.io.File jdkHome) {
        this.jdkHome.fileValue(jdkHome);
    }

    /**
     * Package private setter for the JDK specification property for this extension.
     * <p>
     * The plugin uses this method internally to populate the JDK specification property that
     * provides information about the JDK used for the build.
     *
     * @param jdkSpec the JDK specification to be set, represented as a {@code JdkSpec} instance
     */
    void setJdkSpec(JdkSpec jdkSpec) {
        this.jdkSpec.set(jdkSpec);
    }
}
