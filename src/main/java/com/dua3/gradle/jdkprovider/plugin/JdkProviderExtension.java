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
public abstract class JdkProviderExtension {

    private final Property<OSFamily> os;
    private final Property<SystemArchitecture> arch;
    private final Property<String> version;
    private final Property<JvmVendorSpec> vendor;
    private final Property<Boolean> nativeImageCapable;
    private final Property<Boolean> javaFxBundled;
    private final Property<Boolean> automaticDownload;

    @Inject
    public JdkProviderExtension(ObjectFactory objects) {
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
