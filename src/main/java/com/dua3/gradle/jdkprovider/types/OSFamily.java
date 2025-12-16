package com.dua3.gradle.jdkprovider.types;

import org.jspecify.annotations.NullMarked;

import java.util.Locale;

@NullMarked
public enum OSFamily {
    AIX,
    FREE_BSD,
    LINUX,
    MACOS,
    QNX,
    SOLARIS,
    WINDOWS;

    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static OSFamily current() {
        return parse(System.getProperty("os.name").toLowerCase(Locale.ROOT));
    }

    public static OSFamily parse(String osName) {
        osName = osName.toLowerCase(Locale.ROOT);
        if (osName.contains("mac") || osName.contains("darwin")) {
            return MACOS;
        } else if (osName.contains("win")) {
            return WINDOWS;
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            if (osName.contains("aix")) {
                return AIX;
            }
            return LINUX;
        } else if (osName.contains("sunos") || osName.contains("solaris")) {
            return SOLARIS;
        } else if (osName.contains("freebsd")) {
            return FREE_BSD;
        } else if (osName.contains("qnx")) {
            return QNX;
        }

        throw new IllegalStateException("Unknown operating system: " + osName);
    }
}
