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

package com.dua3.gradle.jdkprovider.types;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents specifications for a JDK.
 * <p>
 * Note:
 * Default values for `os`, `arch`, and `versionSpec` are resolved to the current system's 
 * operating system, architecture, and version when not explicitly specified during construction.
 *
 * @param os the operating system family for the JDK
 * @param arch the system architecture for the JDK
 * @param versionSpec the version specification for the JDK
 * @param vendor the vendor specification for the JDK, or null for any vendor
 * @param nativeImageCapable whether the JDK should support native image compilation, or null for any
 * @param javaFxBundled whether the JDK should have JavaFX bundled, or null for any
 */
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

    /**
     * Creates a new instance of the {@code Builder} class for constructing {@code JdkSpec} objects.
     *
     * @return a new {@code Builder} instance for constructing and customizing {@code JdkSpec} objects.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Determines if the current JdkSpec instance is compatible with the requested JdkSpec instance.
     * Compatibility is evaluated based on multiple criteria, including operating system, architecture,
     * version specification, vendor, native image capability, and whether JavaFX is bundled.
     *
     * @param requested the JdkSpec instance to compare against the current specification
     * @return {@code true} if the current JdkSpec instance is compatible with the requested JdkSpec;
     *         {@code false} otherwise
     */
    public boolean isCompatibleWith(JdkSpec requested) {
        return compatibleOrLog("Operating System", os, requested.os)
                && compatibleOrLog("Architecture", arch, requested.arch)
                && compatibleOrLog("Version", versionSpec, requested.versionSpec, JdkSpec::isCompatible)
                && compatibleOrLog("Vendor", vendor, requested.vendor, JdkSpec::isCompatible)
                && compatibleOrLog("Native Image Capable", nativeImageCapable, requested.nativeImageCapable)
                && compatibleOrLog("JavaFX Bundled", javaFxBundled, requested.javaFxBundled);
    }

    /**
     * Determines if the actual value is compatible with the requested value for a specific feature.
     * If the values are not compatible, it logs a debug message indicating the incompatibility.
     *
     * @param <T> the type of the values being compared
     * @param feature the name of the feature for which compatibility is being checked
     * @param actual the actual value of the feature
     * @param requested the requested value of the feature
     * @return {@code true} if the actual value is compatible with the requested value,
     *         otherwise {@code false}
     */
    private static <T> boolean compatibleOrLog(String feature, @Nullable T actual, @Nullable T requested) {
        return compatibleOrLog(feature, actual, requested, (act, req) -> req == null || Objects.equals(act, req));
    }

    /**
     * Checks if the given values are compatible based on the provided predicate. Logs a debug message if the values are incompatible.
     *
     * @param <T> the type of the values being compared
     * @param feature the name of the feature for logging purposes
     * @param actual the actual value
     * @param requested the requested value
     * @param predicate the predicate to determine compatibility between the actual and requested values
     * @return true if the values are compatible; false otherwise
     */
    private static <T> boolean compatibleOrLog(String feature, @Nullable T actual, @Nullable T requested, BiPredicate<@Nullable T, @Nullable T> predicate) {
        boolean compatible = predicate.test(actual, requested);
        if (!compatible) {
            LOG.debug("incompatible {}: actual={}, requested={}", feature, actual, requested);
        }
        return compatible;
    }

    /**
     * Determines whether the actual JVM vendor specification is compatible
     * with the requested JVM vendor specification.
     *
     * @param actual   the actual {@code JvmVendorSpec}, may be {@code null}
     * @param requested the requested {@code JvmVendorSpec}, may be {@code null}
     * @return {@code true} if the actual specification is compatible with the
     *         requested one, or if the requested specification is {@code null};
     *         {@code false} otherwise
     */
    private static boolean isCompatible(@Nullable JvmVendorSpec actual, @Nullable JvmVendorSpec requested) {
        if (requested == null || Objects.equals(actual, requested)) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        return requested.matches(actual.toString());
    }

    /**
     * Determines whether the actual version specification is compatible with the requested version specification.
     *
     * @param actual    the actual version specification, which may be null
     * @param requested the requested version specification, which may be null
     * @return true if the requested version is null or if it matches the actual version specification; false otherwise
     */
    private static boolean isCompatible(@Nullable VersionSpec actual, @Nullable VersionSpec requested) {
        return requested == null || requested.matches(actual);
    }

    /**
     * Generates a string representation of the {@code JdkSpec} object detailing its
     * characteristics, including version specification, operating system, architecture,
     * vendor, native image capability, and whether JavaFX is bundled. Each attribute is
     * included in the resulting string, separating individual components with a dash ('-').
     * <p>
     * Note: The string representation was chosen in a format that can be used as a filename.
     *
     * @return a string representation of the {@code JdkSpec} object, formatted as:
     *         "jdk-{versionSpec}-{os}-{arch}-{vendor}-{nativeImageCapable}-{javaFxBundled}".
     *         If any attribute is {@code null}, it is represented as "any" in the string.
     */
    @Override
    public String toString() {
        return Stream.of(
                        vendorName(),
                        versionSpec,
                        os,
                        arch,
                        nativeImageCapable == Boolean.TRUE ? "native_image" : null,
                        javaFxBundled == Boolean.TRUE ? "javafx" : null
                )
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.joining("-"));
    }

    private String vendorName() {
        if (vendor == null) {
            return "any";
        }
        return vendor.toString()
                .replaceFirst("^vendor matching\\('(.*)'\\)", "$1")
                .replaceAll("[/\\\\:*?<>\"|-]", "_");
    }

    /**
     * A builder class for constructing instances of {@code JdkSpec} using a fluent interface.
     */
    public static final class Builder {
        private @Nullable OSFamily os;
        private @Nullable SystemArchitecture arch;
        private @Nullable VersionSpec versionSpec;
        private @Nullable JvmVendorSpec vendor;
        private @Nullable Boolean nativeImageCapable;
        private @Nullable Boolean javaFxBundled;

        /**
         * Sets the operating system family for the {@code JdkSpec} being constructed.
         *
         * @param os the {@link OSFamily} to set, or {@code null} to use the default operating system.
         * @return the current {@code Builder} instance for method chaining.
         */
        public Builder os(@Nullable OSFamily os) {
            this.os = os;
            return this;
        }

        /**
         * Sets the system architecture for the builder.
         *
         * @param arch the desired system architecture, or {@code null} to use the default architecture
         * @return the updated builder instance
         */
        public Builder arch(@Nullable SystemArchitecture arch) {
            this.arch = arch;
            return this;
        }

        /**
         * Sets the version specification for the builder.
         *
         * @param versionSpec the version specification to set, can be null to represent no specific version
         * @return the builder instance for chaining further method calls
         */
        public Builder version(@Nullable VersionSpec versionSpec) {
            this.versionSpec = versionSpec;
            return this;
        }

        /**
         * Specifies the JVM vendor for the JDK specification being built.
         *
         * @param vendor the JVM vendor specification, or null if no specific vendor is required
         * @return the builder instance for chaining additional configuration
         */
        public Builder vendor(@Nullable JvmVendorSpec vendor) {
            this.vendor = vendor;
            return this;
        }

        /**
         * Sets whether the JDK supports native image capability.
         *
         * @param nativeImageCapable a Boolean indicating if native image capability is supported;
         *                           can be {@code null} to represent an unknown state.
         * @return the current {@code Builder} instance for method chaining.
         */
        public Builder nativeImageCapable(@Nullable Boolean nativeImageCapable) {
            this.nativeImageCapable = nativeImageCapable;
            return this;
        }

        /**
         * Sets whether the JavaFX runtime is bundled with the JDK being specified.
         *
         * @param javaFxBundled a Boolean value indicating whether JavaFX is included (true),
         *                      not included (false), or unspecified (null).
         * @return the current {@code Builder} instance for method chaining.
         */
        public Builder javaFxBundled(@Nullable Boolean javaFxBundled) {
            this.javaFxBundled = javaFxBundled;
            return this;
        }

        /**
         * Builds and returns a {@code JdkSpec} instance using the specified or default
         * configuration values from the {@code Builder}.
         *
         * @return a {@code JdkSpec} object containing the resolved operating system family,
         *         system architecture, version specification, JVM vendor, native image capability,
         *         and JavaFX bundling status.
         */
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
