package com.dua3.gradle.jdkprovider.types;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiPredicate;

@NullMarked
public record JdkSpec(
        OSFamily os,
        SystemArchitecture arch,
        VersionSpec versionSpec,
        @Nullable JvmVendorSpec vendor,
        @Nullable Boolean nativeImageCapable,
        @Nullable Boolean javaFxBundled
) {
    private static final Logger LOG = Logging.getLogger(JdkSpec.class);

    public static Builder builder() {
        return new Builder();
    }

    public boolean isCompatibleWith(JdkSpec requested) {
        return compatibleOrLog("Operating System", os, requested.os)
                && compatibleOrLog("Architecture", arch, requested.arch)
                && compatibleOrLog("Version", versionSpec, requested.versionSpec, JdkSpec::isCompatible)
                && compatibleOrLog("Vendor", vendor, requested.vendor, JdkSpec::isCompatible)
                && compatibleOrLog("Native Image Capable", nativeImageCapable, requested.nativeImageCapable)
                && compatibleOrLog("JavaFX Bundled", javaFxBundled, requested.javaFxBundled);
    }

    private static <T> boolean compatibleOrLog(String feature, @Nullable T actual, @Nullable T requested) {
        return compatibleOrLog(feature, actual, requested, (act, req) -> req == null || Objects.equals(act, req));
    }

    private static <T> boolean compatibleOrLog(String feature, @Nullable T actual, @Nullable T requested, BiPredicate<@Nullable T, @Nullable T> predicate) {
        boolean compatible = predicate.test(actual, requested);
        if (!compatible) {
            LOG.debug("incompatible {}: actual={}, requested={}", feature, actual, requested);
        }
        return compatible;
    }

    private static boolean isCompatible(@Nullable JvmVendorSpec actual, @Nullable JvmVendorSpec requested) {
        if (requested == null || Objects.equals(actual, requested)) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        return requested.matches(actual.toString());
    }

    private static boolean isCompatible(@Nullable VersionSpec actual, @Nullable VersionSpec requested) {
        return requested == null || requested.matches(actual);
    }

    @Override
    public String toString() {
        return "jdk"
                + "-" + Objects.toString(versionSpec, "any")
                + "-" + Objects.toString(os, "any")
                + "-" + Objects.toString(arch, "any")
                + "-" + Objects.toString(vendor, "any")
                + "-" + Objects.toString(nativeImageCapable, "any")
                + "-" + Objects.toString(javaFxBundled, "any");
    }

    public static final class Builder {
        private @Nullable OSFamily os;
        private @Nullable SystemArchitecture arch;
        private @Nullable VersionSpec versionSpec;
        private @Nullable JvmVendorSpec vendor;
        private @Nullable Boolean nativeImageCapable;
        private @Nullable Boolean javaFxBundled;

        public Builder os(@Nullable OSFamily os) {
            this.os = os;
            return this;
        }

        public Builder arch(@Nullable SystemArchitecture arch) {
            this.arch = arch;
            return this;
        }

        public Builder version(@Nullable VersionSpec versionSpec) {
            this.versionSpec = versionSpec;
            return this;
        }

        public Builder vendor(@Nullable JvmVendorSpec vendor) {
            this.vendor = vendor;
            return this;
        }

        public Builder nativeImageCapable(@Nullable Boolean nativeImageCapable) {
            this.nativeImageCapable = nativeImageCapable;
            return this;
        }

        public Builder javaFxBundled(@Nullable Boolean javaFxBundled) {
            this.javaFxBundled = javaFxBundled;
            return this;
        }

        public JdkSpec build() {
            OSFamily resolvedOs = Objects.requireNonNullElseGet(os, OSFamily::current);
            SystemArchitecture resolvedArch = Objects.requireNonNullElseGet(arch, SystemArchitecture::current);
            VersionSpec resolvedVersionSpec = Objects.requireNonNullElseGet(versionSpec, VersionSpec::current);

            return new JdkSpec(
                    resolvedOs,
                    resolvedArch,
                    resolvedVersionSpec,
                    vendor,
                    nativeImageCapable,
                    javaFxBundled
            );
        }
    }
}
