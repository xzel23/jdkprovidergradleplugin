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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class VersionSpecTest {

    static Stream<Arguments> matchesProvider() {
        return Stream.of(
                // spec, actual, expected result
                Arguments.of("any", "21.0.1", true),
                Arguments.of("any", "17", true),
                Arguments.of("latest", "21.0.1", true),
                Arguments.of("latest", "17", true),
                Arguments.of("21", "21", true),
                Arguments.of("21", "21.0.1", true),
                Arguments.of("21", "21.1.2", true),
                Arguments.of("21", "17", false),
                Arguments.of("21", "17.1", false),
                Arguments.of("21", "22", false),
                Arguments.of("21.2", "17", false),
                Arguments.of("21.2", "21", false),
                Arguments.of("21.2", "21.1", false),
                Arguments.of("21.2", "21.2", true),
                Arguments.of("21.2", "21.3", false),
                Arguments.of("21.2", "21.2.1", true),
                Arguments.of("21.0.1", "21.0.1", true),
                Arguments.of("21.0.1", "21.0.2", false),
                Arguments.of("21+", "21.0.1", true),
                Arguments.of("21+", "21.2.3", true),
                Arguments.of("21+", "17", false),
                Arguments.of("21+", "25", true),
                Arguments.of("21.2+", "21.2.1", true),
                Arguments.of("21.2+", "21.3.3", true),
                Arguments.of("21.2+", "17", false),
                Arguments.of("21.2+", "25", false),
                Arguments.of("25", "25.0.1", true),
                Arguments.of("21.3+", "21.3.1", true),
                Arguments.of("21.3+", "21.3.5", true),
                Arguments.of("21.3+", "21.4", true),
                Arguments.of("21.*", "21.0.1", true),
                Arguments.of("21.*", "21.0.5", true),
                Arguments.of("21.*", "21.1", true),
                // edge cases
                Arguments.of("21.0.1", "21", false),
                Arguments.of("21.1", "21", false),
                Arguments.of("21.1.1", "21.1.0.1", false),
                Arguments.of("21.0.1.7", "21.0.1.7", true),
                Arguments.of("21.0.1.7", "21.0.1.8", false),
                Arguments.of("21.0.1+", "21.0.1.7", true),
                Arguments.of("21.0.1.7", "21.0.1", false)
        );
    }

    @ParameterizedTest
    @DisplayName("VersionSpec.matches() tests")
    @MethodSource("matchesProvider")
    void matches(String specStr, String actualStr, boolean expected) {
        VersionSpec spec = VersionSpec.parse(specStr);
        Runtime.Version actual = actualStr == null ? null : Runtime.Version.parse(actualStr);
        assertEquals(expected, spec.matches(actual), () -> specStr + " should " + (expected ? "" : "not ") + "match " + actualStr);
    }

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
            "21.2.3.4",
            "21.2.3+"
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
                        || canonical.matches("\\d+\\.\\d+\\.\\d+\\+")
                        || canonical.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")
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
            "21.2.-3"
    })
    void parseInvalid(String input) {
        assertThrows(IllegalArgumentException.class, () -> VersionSpec.parse(input));
    }
}
