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
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class VersionSpecTest {

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
