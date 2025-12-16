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

package com.dua3.gradle.jdkprovider.disco;

import java.net.URI;

/**
 * Represents a downloadable JDK package obtained from the Disco API.
 * This class encapsulates metadata necessary to locate and verify the package.
 *
 * @param downloadUri The URI from which the package can be downloaded.
 * @param sha256      The SHA-256 checksum of the package for verifying integrity.
 * @param vendor      The vendor or distribution of the package (e.g., Zulu, Liberica).
 * @param archiveType The type of archive (e.g., zip, tar.gz).
 * @param filename    The name of the file for the downloaded package.
 */
public record DiscoPackage(URI downloadUri, String sha256, String vendor, String archiveType, String filename) {
}
