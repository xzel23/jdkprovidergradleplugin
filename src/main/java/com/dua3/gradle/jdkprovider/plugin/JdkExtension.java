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

import com.dua3.gradle.jdkprovider.types.OSFamily;
import com.dua3.gradle.jdkprovider.types.SystemArchitecture;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.jvm.toolchain.JvmVendorSpec;

import javax.inject.Inject;

/**
 * Extension for configuring the JDK selection.
 */
public abstract class JdkExtension {

    private final Property<OSFamily> os;
    private final Property<SystemArchitecture> arch;
    private final Property<String> version;
    private final Property<JvmVendorSpec> vendor;
    private final Property<Boolean> nativeImageCapable;
    private final Property<Boolean> javaFxBundled;
    private final Property<Boolean> automaticDownload;

    @Inject
    public JdkExtension(ObjectFactory objects) {
        this.os = objects.property(OSFamily.class);
        this.arch = objects.property(SystemArchitecture.class);
        this.version = objects.property(String.class);
        this.vendor = objects.property(JvmVendorSpec.class);
        this.nativeImageCapable = objects.property(Boolean.class);
        this.javaFxBundled = objects.property(Boolean.class);
        this.automaticDownload = objects.property(Boolean.class);
    }

    public Property<OSFamily> getOs() {
        return os;
    }

    public Property<SystemArchitecture> getArch() {
        return arch;
    }

    public Property<String> getVersion() {
        return version;
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

    public Property<Boolean> getAutomaticDownload() {
        return automaticDownload;
    }
}
