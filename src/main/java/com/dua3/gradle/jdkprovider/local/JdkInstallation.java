package com.dua3.gradle.jdkprovider.local;

import com.dua3.gradle.jdkprovider.types.JdkSpec;
import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;

/**
 * Represents the specification of a JDK installation,consisting of the installation path
 * and the JDK specification.
 *
 * @param jdkHome       the filesystem path to the JDK installation home directory.
 * @param jdkSpec       the JDK specification.
 */
@NullMarked
public record JdkInstallation(Path jdkHome, JdkSpec jdkSpec) {}
