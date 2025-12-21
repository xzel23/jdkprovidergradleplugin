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
import com.dua3.gradle.jdkprovider.types.JdkQuery;
import com.dua3.gradle.jdkprovider.types.JdkSpec;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.IOException;
import java.nio.file.Path;
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

        return new LocalJdkScanner()
                // first try to find an existing installation
                .getCompatibleInstalledJdks(jdkQuery)
                .stream()
                .findFirst()
                // then try downloading using DiscoAPI
                .or(() -> {
                    // No com.dua3.gradle.jdkprovider.local JDK found — try DiscoAPI-based resolution if not offline
                    if (offlineMode) {
                        throw new GradleException("Offline mode detected or automatic download disabled, cannot resolve toolchain");
                    }

                    // use DiscoAPI to look up and provision a suitable JDK
                    LOGGER.debug("[JDK Provider - Resolver] No matching local JDK found, querying DiscoAPI");

                    return new DiscoApiClient().findPackage(jdkQuery)
                            .map(pkg -> {
                                try {
                                    Path jdkHome = new JdkProvisioner()
                                            .provision(pkg.downloadUri(), pkg.sha256(), pkg.filename(), pkg.archiveType());
                                    return LocalJdkScanner.readJdkSpec(jdkHome).orElse(null);
                                } catch (InterruptedException e) {
                                    LOGGER.debug("[JDK Provider - Resolver] Provisioning interrupted, aborting");
                                    Thread.currentThread().interrupt();
                                    return null;
                                } catch (IOException e) {
                                    throw new GradleException("Failed to download and provision JDK from DiscoAPI: " + e.getMessage(), e);
                                }
                            });
                });
    }
}
