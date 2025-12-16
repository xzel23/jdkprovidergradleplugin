package com.dua3.gradle.jdkprovider.types;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class VersionSpecTest {

    @ParameterizedTest
    @DisplayName("Round-trip parse/toString compatibility for supported formats")
    @ValueSource(strings = {
            "any",
            "latest",
            "21",
            "21+",
            "21.2",
            "21.2+",
            "21.2.3",
            // accept case insensitivity and whitespace, canonicalize via toString
            "  AnY  ",
            "LATEST\t"
    })
    void roundTrip(String input) {
        VersionSpec spec = VersionSpec.parse(input);
        String canonical = spec.toString();

        // All canonical outputs should be one of the supported normalized forms
        assertTrue(
                canonical.equals("any")
                        || canonical.equals("latest")
                        || canonical.matches("\\d+")
                        || canonical.matches("\\d+\\+")
                        || canonical.matches("\\d+\\.\\d+")
                        || canonical.matches("\\d+\\.\\d+\\+")
                        || canonical.matches("\\d+\\.\\d+\\.\\d+")
        );

        // Ensure parse(canonical).toString() is stable
        assertEquals(canonical, VersionSpec.parse(canonical).toString());
    }

    @ParameterizedTest
    @DisplayName("Parse rejects invalid formats")
    @ValueSource(strings = {
            "",
            " ",
            "abc",
            "21..2",
            "21.",
            ".2",
            "21.+",
            "21.2.+",
            "-1",
            "21.-1",
            "21.2.-3",
            "21.2.3+",
            "1.2.3.4"
    })
    void parseInvalid(String input) {
        assertThrows(IllegalArgumentException.class, () -> VersionSpec.parse(input));
    }
}
