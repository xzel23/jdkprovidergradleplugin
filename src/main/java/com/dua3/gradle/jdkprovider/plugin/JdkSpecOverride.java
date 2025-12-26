// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 Axel Howind

package com.dua3.gradle.jdkprovider.plugin;

import com.dua3.gradle.jdkprovider.types.OSFamily;
import com.dua3.gradle.jdkprovider.types.SystemArchitecture;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.jvm.toolchain.JvmVendorSpec;

import javax.inject.Inject;

/**
 * Configuration for a specific JDK.
 */
public abstract class JdkSpecOverride {
    private final String name;
    private final Property<OSFamily> os;
    private final Property<SystemArchitecture> arch;
    private final Property<Object> version;
    private final Property<JvmVendorSpec> vendor;
    private final Property<Boolean> nativeImageCapable;
    private final Property<Boolean> javaFxBundled;

    /**
     * Constructs an instance of {@code JdkSpecOverride} with the specified name and object factory.
     *
     * @param name the name of the JDK specification override, used to uniquely identify this configuration
     * @param objects the object factory used to create property instances for this configuration
     */
    @Inject
    public JdkSpecOverride(String name, ObjectFactory objects) {
        this.name = name;
        this.os = objects.property(OSFamily.class);
        this.arch = objects.property(SystemArchitecture.class);
        this.version = objects.property(Object.class);
        this.vendor = objects.property(JvmVendorSpec.class);
        this.nativeImageCapable = objects.property(Boolean.class);
        this.javaFxBundled = objects.property(Boolean.class);
    }

    /**
     * Retrieves the name of this JDK specification override configuration.
     *
     * @return the name of the JDK specification override
     */
    public String getName() {
        return name;
    }

    /**
     * Retrieves the operating system family property for this configuration.
     *
     * @return the {@code Property} representing the operating system family associated with this configuration.
     */
    public Property<OSFamily> getOs() {
        return os;
    }

    /**
     * Retrieves the property representing the system architecture configuration.
     *
     * @return a {@code Property<SystemArchitecture>} instance that holds the value of the system architecture
     */
    public Property<SystemArchitecture> getArch() {
        return arch;
    }

    /**
     * Retrieves the version property of this JDK specification override.
     *
     * @return the {@link Property} representing the version, which can hold various types of values such as an integer or a string.
     */
    public Property<Object> getVersion() {
        return version;
    }

    /**
     * Sets the version of the JDK specification override.
     *
     * @param version the version of the JDK specification as an integer
     */
    public void setVersion(int version) {
        this.version.set(version);
    }

    /**
     * Sets the version property for the JDK specification override.
     *
     * @param version the version value to be set, represented as a {@code String}
     */
    public void setVersion(String version) {
        this.version.set(version);
    }

    /**
     * Returns the property representing the JDK vendor specification.
     *
     * @return a property encapsulating the vendor specification for the JDK
     */
    public Property<JvmVendorSpec> getVendor() {
        return vendor;
    }

    /**
     * Retrieves the property indicating whether native image capabilities are supported.
     *
     * @return a {@code Property} of type {@code Boolean} representing the native image capability status.
     */
    public Property<Boolean> getNativeImageCapable() {
        return nativeImageCapable;
    }

    /**
     * Retrieves the property indicating whether JavaFX is bundled with the JDK.
     *
     * @return a property that contains a boolean value, where {@code true} indicates that JavaFX is bundled,
     *         and {@code false} indicates it is not bundled.
     */
    public Property<Boolean> getJavaFxBundled() {
        return javaFxBundled;
    }
}
