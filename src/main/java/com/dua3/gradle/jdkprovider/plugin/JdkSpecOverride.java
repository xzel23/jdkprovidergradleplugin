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

    public String getName() {
        return name;
    }

    public Property<OSFamily> getOs() {
        return os;
    }

    public Property<SystemArchitecture> getArch() {
        return arch;
    }

    public Property<Object> getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version.set(version);
    }

    public void setVersion(String version) {
        this.version.set(version);
    }

    public Property<JvmVendorSpec> getVendor() {
        return vendor;
    }

    public Property<Boolean> getNativeImageCapable() {
        return nativeImageCapable;
    }

    public Property<Boolean> getJavaFxBundled() {
        return javaFxBundled;
    }
}
