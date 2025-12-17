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

import com.dua3.gradle.jdkprovider.types.OSFamily;
import com.dua3.gradle.jdkprovider.types.SystemArchitecture;
import com.dua3.gradle.jdkprovider.types.VersionSpec;

import java.net.URI;

/**
 * Represents a package containing information about a downloadable asset, such as
 * its URI, checksum, distribution details, archive type, and compatibility with a
 * specific operating system and architecture.
 * <p>
 * Instances of this record can be used to encapsulate metadata for software packages
 * or other distributable artifacts.
 *
 * @param downloadUri   the URI from which the package can be downloaded
 * @param sha256        the SHA-256 checksum of the package, useful for verifying file integrity
 * @param distribution  the name of the distribution or source associated with the package
 * @param archiveType   the type of archive (e.g., "zip", "tar.gz") that the package is stored in
 * @param filename      the filename of the package as it would appear when downloaded
 * @param os            the operating system family for which this package is designed
 * @param archticture   the system architecture that this package is compatible with
 * @param version       the version specification of the package
 */
public record DiscoPackage(
        URI downloadUri,
        String sha256,
        String distribution,
        String archiveType,
        String filename,
        OSFamily os,
        SystemArchitecture archticture,
        VersionSpec version) {}
