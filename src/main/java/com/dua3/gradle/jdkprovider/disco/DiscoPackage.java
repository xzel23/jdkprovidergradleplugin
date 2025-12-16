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
