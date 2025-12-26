package com.dua3.gradle.jdkprovider.types;

import io.soabase.recordbuilder.core.RecordBuilder;

import org.gradle.api.logging.Logging;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Represents a query interface for specifying requirements or characteristics of a JDK in terms
 * of operating system, architecture, vendor, and additional configurations such as support for
 * native images and JavaFX.
 *
 * @param os                       the target operating system family; defaults to the current OS if null
 * @param arch                     the target system architecture; defaults to the current architecture if null
 * @param nativeImageCapable       indicates whether the JDK should support native image compilation
 * @param javaFxBundled            indicates whether the JDK should include JavaFX libraries
 * @param versionSpec              the version specification for the JDK; defaults to the latest version if null
 * @param stableReleaseOnly        indicates whether only stable releases should be considered
 * @param longTermSupportOnly      indicates whether only long-term support (LTS) versions should be considered
 * @param freeForProductionUseOnly indicates whether only JDKs free for production use should be considered
 * @param vendorSpec               the JVM vendor specification; defaults to matching any vendor if null
 * @param libcType                 the type of C standard library (e.g., "glibc", "musl"); defaults to detected libc type if null
 */
@RecordBuilder
public record JdkQuery(
        OSFamily os,
        SystemArchitecture arch,
        Boolean nativeImageCapable,
        Boolean javaFxBundled,
        VersionSpec versionSpec,
        Boolean stableReleaseOnly,
        Boolean longTermSupportOnly,
        Boolean freeForProductionUseOnly,
        JvmVendorSpec vendorSpec,
        String libcType
) {
    /**
     * Initializes the query with defaults for unspecified attributes.
     */
    public JdkQuery {
        os = Objects.requireNonNullElse(os, OSFamily.current());
        arch = Objects.requireNonNullElse(arch, SystemArchitecture.current());
        nativeImageCapable = Objects.requireNonNullElse(nativeImageCapable, Boolean.FALSE);
        javaFxBundled = Objects.requireNonNullElse(javaFxBundled, Boolean.FALSE);
        versionSpec = Objects.requireNonNullElse(versionSpec, VersionSpec.latest());
        stableReleaseOnly = Objects.requireNonNullElse(stableReleaseOnly, Boolean.TRUE);
        longTermSupportOnly = Objects.requireNonNullElse(longTermSupportOnly, Boolean.FALSE);
        freeForProductionUseOnly = Objects.requireNonNullElse(freeForProductionUseOnly, Boolean.TRUE);
        vendorSpec = Objects.requireNonNullElse(vendorSpec, DefaultJvmVendorSpec.any());
        libcType = Objects.requireNonNullElse(libcType, getLibcType(os));
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
        return compatibleOrLog("Operating System", jdkSpec.os(), jdkQuery.os(), (act, req) -> req == null || act == req)
                && compatibleOrLog("Architecture", jdkSpec.arch(), jdkQuery.arch(), (act, req) -> req == null || act == req)
                && compatibleOrLog("Native Image Capable", jdkSpec.nativeImageCapable(), jdkQuery.nativeImageCapable())
                && compatibleOrLog("JavaFX Bundled", jdkSpec.javaFxBundled(), jdkQuery.javaFxBundled())
                && compatibleOrLog("Version", jdkSpec.version(), jdkQuery.versionSpec(), (act, req) -> req == null || req.matches(act))
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
    private static String getLibcType(OSFamily os) {
        if (os != OSFamily.LINUX) {
            return "";
        }

        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("alpine")) {
            return "musl";
        } else {
            return "";
        }
    }
}
