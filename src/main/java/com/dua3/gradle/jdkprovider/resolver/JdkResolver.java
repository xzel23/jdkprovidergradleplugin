package com.dua3.gradle.jdkprovider.resolver;

import com.dua3.gradle.jdkprovider.disco.DiscoApiSelector;
import com.dua3.gradle.jdkprovider.local.JdkInstallation;
import com.dua3.gradle.jdkprovider.local.LocalJdkScanner;
import com.dua3.gradle.jdkprovider.provision.JdkProvisioner;
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

    public JdkResolver() {
        LOGGER.debug("[Java Resolver] Initializing");
    }

    public Optional<JdkInstallation> resolve(JdkSpec jdkSpec, boolean offlineMode) {
        LOGGER.debug("[Java Resolver] Resolving toolchain for {}", jdkSpec);

        return new LocalJdkScanner()
                // first try to find an existing installation
                .getCompatibleInstalledJdks(jdkSpec)
                .stream()
                .findFirst()
                // then try downloading using DiscoAPI
                .or(() -> {
                    // No com.dua3.gradle.jdkprovider.local JDK found — try DiscoAPI-based resolution if not offline
                    if (offlineMode) {
                        throw new GradleException("Offline mode detected or automatic download disabled, cannot resolve toolchain");
                    }

                    // use DiscoAPI to lookup and provision a suitable JDK
                    LOGGER.debug("[Java Resolver] No com.dua3.gradle.jdkprovider.local JDK found, querying DiscoAPI");

                    return new DiscoApiSelector().findPackage(jdkSpec)
                            .map(pkg -> {
                                try {
                                    Path jdkHome = new JdkProvisioner()
                                            .provision(pkg.downloadUri(), pkg.sha256(), pkg.filename(), pkg.archiveType());
                                    return LocalJdkScanner.readJdkSpec(jdkHome).orElse(null);
                                } catch (InterruptedException e) {
                                    LOGGER.debug("[Java Resolver] Provisioning interrupted, aborting");
                                    Thread.currentThread().interrupt();
                                    return null;
                                } catch (IOException e) {
                                    throw new GradleException("Failed to download and provision JDK from DiscoAPI: " + e.getMessage(), e);
                                }
                            });
                });
    }
}
