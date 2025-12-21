package com.dua3.gradle.jdkprovider.types;

import io.soabase.recordbuilder.core.RecordBuilder;

import org.gradle.api.logging.Logging;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Represents a query interface for specifying requirements or characteristics of a JDK in terms
 * of operating system, architecture, vendor, and additional configurations such as support for
 * native images and JavaFX.
 */
@RecordBuilder
public record JdkQuery(
        OSFamily os,
        SystemArchitecture arch,
        @RecordBuilder.Initializer(value = "FALSE", source = Boolean.class)
        Boolean nativeImageCapable,
        @RecordBuilder.Initializer(value = "FALSE", source = Boolean.class)
        Boolean javaFxBundled,
        VersionSpec versionSpec,
        @RecordBuilder.Initializer(value = "TRUE", source = Boolean.class)
        Boolean stableReleaseOnly,
        @RecordBuilder.Initializer(value = "FALSE", source = Boolean.class)
        Boolean longTermSupportOnly,
        @RecordBuilder.Initializer(value = "TRUE", source = Boolean.class)
        Boolean freeForProductionUseOnly,
        JvmVendorSpec vendorSpec,
        String libcType
) {
    public JdkQuery {
        os = os != null ? os : OSFamily.current();
        arch = arch != null ? arch : SystemArchitecture.current();
        versionSpec = versionSpec != null ? versionSpec : VersionSpec.latest();
        vendorSpec = vendorSpec != null ? vendorSpec : JvmVendorSpec.matching("");
        libcType = libcType != null ? libcType : getLicCType();
    }

    /**
     * Determines if the specified {@code JdkSpec} is compatible with the constraints
     * defined in the {@code JdkQuery}.
     * Compatibility is assessed based on a series of criteria such as operating system,
     * system architecture, native image support, JavaFX inclusion, version, and vendor.
     * If any of these criteria are not met, the method will return {@code false}.
     *
     * @param jdkSpec the specification of the JDK to be checked for compatibility.
     * @param jdkQuery the query defining the desired requirements for compatibility.
     * @return {@code true} if the {@code JdkSpec} is compatible with the {@code JdkQuery},
     * otherwise {@code false}.
     */
    public static boolean isCompatible(JdkSpec jdkSpec, JdkQuery jdkQuery) {
        return compatibleOrLog("Operating System", jdkSpec.os(), jdkQuery.os(), (act, req) -> req == null || Objects.equals(act, req))
                && compatibleOrLog("Architecture", jdkSpec.arch(), jdkQuery.arch(), (act, req) -> req == null || Objects.equals(act, req))
                && compatibleOrLog("Native Image Capable", jdkSpec.nativeImageCapable(), jdkQuery.nativeImageCapable())
                && compatibleOrLog("JavaFX Bundled", jdkSpec.javaFxBundled(), jdkQuery.javaFxBundled())
                && compatibleOrLog("Version", VersionSpec.parse(jdkSpec.version()), jdkQuery.versionSpec(), (act, req) -> req == null || req.matches(act))
                && compatibleOrLog("Vendor", jdkSpec.vendor(), jdkQuery.vendorSpec(), (act, req) -> req == null || req.matches(act));
    }

    /**
     * Determines if the actual value is compatible with the requested value for a specific feature.
     * If the values are not compatible, it logs a debug message indicating the incompatibility.
     *
     * @param <T>       the type of the values being compared
     * @param feature   the name of the feature for which compatibility is being checked
     * @param actual    the actual value of the feature
     * @param requested the requested value of the feature
     * @return {@code true} if the actual value is compatible with the requested value,
     * otherwise {@code false}
     */
    private static <T> boolean compatibleOrLog(String feature, @Nullable T actual, @Nullable T requested) {
        return compatibleOrLog(feature, actual, requested, Objects::equals);
    }


    /**
     * Checks if the given values are compatible based on the provided predicate. Logs a debug message if the values are incompatible.
     *
     * @param <T>       the type of the values being compared
     * @param feature   the name of the feature for logging purposes
     * @param actual    the actual value
     * @param requested the requested value
     * @param predicate the predicate to determine compatibility between the actual and requested values
     * @return true if the values are compatible; false otherwise
     */
    private static <T, U> boolean compatibleOrLog(String feature, @Nullable T actual, @Nullable U requested, BiPredicate<@Nullable T, @Nullable U> predicate) {
        boolean compatible = predicate.test(actual, requested);
        if (!compatible) {
            Logging.getLogger(JdkQuery.class).debug("incompatible {}: actual={}, requested={}", feature, actual, requested);
        }
        return compatible;
    }


    /**
     * Determines the type of libc used by the operating system of the current environment.
     * The method checks the system property "os.name" to determine if the operating system is based on Alpine Linux,
     * which uses musl libc, or other operating systems that generally use glibc.
     *
     * @return "musl" if the operating system is determined to be Alpine Linux, otherwise "glibc".
     */
    private static String getLicCType() {
        if (OSFamily.current() != OSFamily.LINUX) {
            return "";
        }

        String libcType = System.getProperty("os.name").toLowerCase();
        if (libcType.contains("alpine")) {
            return "musl";
        } else {
            return "glibc";
        }
    }

}
