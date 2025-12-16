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

package com.dua3.gradle.jdkprovider.provision;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChecksumUtilTest {
    @Test
    void sha256AndVerify() throws IOException {
        Path tmp = Files.createTempFile("checksum-", ".txt");
        Files.writeString(tmp, "hello-javafx\n");

        String sum = ChecksumUtil.sha256Hex(tmp);
        assertNotNull(sum);
        assertEquals(64, sum.length());

        // should not throw
        ChecksumUtil.verifySha256(tmp, sum);

        // mismatch should throw
        assertThrows(IOException.class, () -> ChecksumUtil.verifySha256(tmp, "deadbeef"));
    }
}
