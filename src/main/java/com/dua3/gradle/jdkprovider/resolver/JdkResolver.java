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

package com.dua3.gradle.jdkprovider.resolver;

import com.dua3.gradle.jdkprovider.disco.DiscoApiClient;
import com.dua3.gradle.jdkprovider.local.JdkInstallation;
import com.dua3.gradle.jdkprovider.local.LocalJdkScanner;
import com.dua3.gradle.jdkprovider.provision.JdkProvisioner;
import com.dua3.gradle.jdkprovider.types.DiscoPackage;
import com.dua3.gradle.jdkprovider.types.JdkQuery;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Resolves a JDK installation that matches the specified requirements. The class checks for
 * compatible local installations or provisions a new JDK using the DiscoAPI when necessary.
 */
public class JdkResolver {

    private static final Logger LOGGER = Logging.getLogger(JdkResolver.class);

    /**
     * Constructs a new instance of the JdkResolver.
     */
    public JdkResolver() {
        // Default constructor
    }

    /**
     * Resolves a JDK installation that matches the specified requirements. This method checks
     * for compatible local JDK installations and, if none are found, attempts to provision a
     * JDK using the DiscoAPI, provided offline mode is disabled.
     *
     * @param jdkQuery      the specification of the JDK version and features required.
     * @param offlineMode  a flag indicating whether the resolution process should avoid
     *                     network communication and rely only on local installations.
     * @return an {@code Optional} containing a {@link JdkInstallation} that meets the
     *         requirements, or an empty {@code Optional} if no suitable JDK could be resolved.
     * @throws GradleException if the toolchain cannot be resolved due to offline mode or failure
     *                         in provisioning a JDK using the DiscoAPI.
     */
    public Optional<JdkInstallation> resolve(JdkQuery jdkQuery, boolean offlineMode) {
        LOGGER.debug("[JDK Provider - Resolver] Resolving toolchain for {}", jdkQuery);

        List<DiscoPackage> remotePackages = offlineMode ? List.of() : findDiscoPackagesSafely(jdkQuery);

        List<JdkInstallation> localJdks = getCompatibleInstalledJdks(jdkQuery);
        Optional<JdkInstallation> localCandidate = newestByVersion(localJdks);

        if (offlineMode) {
            return localCandidate.or(() -> {
                throw new GradleException("Offline mode detected or automatic download disabled, cannot resolve toolchain");
            });
        }

        if (localCandidate.isPresent() && localVersionAtLeastRemoteVersion(localCandidate.get(), remotePackages)) {
            return localCandidate;
        }

        try {
            Optional<JdkInstallation> remoteCandidate = provisionFirstAvailableJdk(jdkQuery, remotePackages);
            if (remoteCandidate.isPresent()) {
                return remoteCandidate;
            }
        } catch (GradleException e) {
            if (localCandidate.isPresent()) {
                LOGGER.warn("[JDK Provider - Resolver] Failed to provision newer remote JDK, falling back to compatible local JDK: {}", e.toString());
                return localCandidate;
            }
            throw e;
        }

        return localCandidate;
    }

    private static Optional<JdkInstallation> newestByVersion(List<JdkInstallation> installations) {
        return installations.stream()
                .max(Comparator.comparing(installation -> installation.jdkSpec().version()));
    }

    private static boolean localVersionAtLeastRemoteVersion(JdkInstallation localCandidate, List<DiscoPackage> remotePackages) {
        return remotePackages.stream()
                .map(DiscoPackage::version)
                .max(Comparator.naturalOrder())
                .map(remoteVersion -> localCandidate.jdkSpec().version().compareTo(remoteVersion) >= 0)
                .orElse(true);
    }

    private List<DiscoPackage> findDiscoPackagesSafely(JdkQuery jdkQuery) {
        try {
            return findDiscoPackages(jdkQuery);
        } catch (RuntimeException e) {
            LOGGER.warn("[JDK Provider - Resolver] Error querying DiscoAPI for {}: {}", jdkQuery, e.toString());
            return List.of();
        }
    }

    protected List<JdkInstallation> getCompatibleInstalledJdks(JdkQuery jdkQuery) {
        return createLocalJdkScanner().getCompatibleInstalledJdks(jdkQuery);
    }

    protected List<DiscoPackage> findDiscoPackages(JdkQuery jdkQuery) {
        JdkProvisioner provisioner = createJdkProvisioner();
        return createDiscoApiClient().findPackages(jdkQuery).stream()
                .filter(pkg -> !provisioner.isBlacklisted(pkg.downloadUri()))
                .toList();
    }

    /**
     * Creates and returns a new instance of {@link LocalJdkScanner} to facilitate scanning
     * and identification of locally installed JDKs.
     *
     * @return a newly instantiated {@code LocalJdkScanner} object for discovering local JDK installations.
     */
    protected LocalJdkScanner createLocalJdkScanner() {
        return new LocalJdkScanner();
    }

    /**
     * Creates and returns a new instance of the {@link DiscoApiClient}.
     *
     * @return a {@link DiscoApiClient} instance, used for interacting with the DiscoAPI.
     */
    protected DiscoApiClient createDiscoApiClient() {
        return new DiscoApiClient();
    }

    /**
     * Creates and returns an instance of {@link JdkProvisioner}, which is responsible
     * for provisioning JDKs based on specified requirements.
     *
     * @return a new instance of {@link JdkProvisioner} to handle JDK provisioning.
     */
    protected JdkProvisioner createJdkProvisioner() {
        return new JdkProvisioner();
    }

    /**
     * Reads the JDK specification from the specified JDK home directory.
     *
     * @param jdkHome the filesystem path to the JDK home directory.
     * @return an {@code Optional} containing a {@link JdkInstallation} if the JDK
     *         specification is successfully read, or an empty {@code Optional} if
     *         the JDK home directory does not contain a valid JDK specification.
     */
    protected Optional<JdkInstallation> readJdkSpec(Path jdkHome) {
        return LocalJdkScanner.readJdkSpec(jdkHome);
    }

    /**
     * Provisions a package by downloading, verifying, and extracting the specified package archive.
     * This method delegates the operation to a {@link JdkProvisioner} instance to handle the actual
     * provisioning logic, including downloading the archive, checking its integrity with the provided
     * SHA-256 checksum, and extracting it to a specified location.
     *
     * @param pkg the {@link DiscoPackage} containing metadata about the package to be provisioned,
     *            including its download URI, checksum, filename, and archive type.
     * @return the {@link Path} to the root directory of the provisioned package.
     * @throws IOException          if an error occurs during downloading, verifying, or extracting the package.
     * @throws InterruptedException if the thread is interrupted while waiting for an operation to complete.
     */
    protected Path provisionPackage(DiscoPackage pkg) throws IOException, InterruptedException {
        return createJdkProvisioner().provision(pkg.downloadUri(), pkg.sha256(), pkg.filename(), pkg.archiveType());
    }

    private Optional<JdkInstallation> provisionFirstAvailableJdk(JdkQuery query, List<DiscoPackage> packages) {
        IOException lastException = null;
        for (DiscoPackage pkg : packages) {
            try {
                Path jdkHome = provisionPackage(pkg);
                Optional<JdkInstallation> installation = readJdkSpec(jdkHome);

                if (installation.isPresent()) {
                    JdkInstallation inst = installation.get();
                    if (JdkQuery.isCompatible(inst.jdkSpec(), query)) {
                        return installation;
                    } else {
                        LOGGER.warn("[JDK Provider - Resolver] Provisioned JDK at {} is not compatible with query {}. Actual: {}", jdkHome, query, inst.jdkSpec());
                        JdkProvisioner provisioner = createJdkProvisioner();
                        provisioner.blacklist(pkg.downloadUri());
                        provisioner.removeExtractedJdk(jdkHome);
                    }
                }
            } catch (InterruptedException e) {
                LOGGER.debug("[JDK Provider - Resolver] Provisioning interrupted, aborting");
                Thread.currentThread().interrupt();
                return Optional.empty();
            } catch (IOException e) {
                lastException = e;
                LOGGER.warn(
                        "[JDK Provider - Resolver] Failed to provision JDK from {} ({}): {}. Trying next matching JDK.",
                        pkg.downloadUri(),
                        pkg.distribution(),
                        e.toString()
                );
            }
        }

        if (lastException != null) {
            throw new GradleException("Failed to download and provision JDK from DiscoAPI: " + lastException.getMessage(), lastException);
        }
        return Optional.empty();
    }
}
