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

import com.dua3.gradle.jdkprovider.types.DiscoPackage;
import com.dua3.gradle.jdkprovider.types.JdkQuery;
import com.dua3.gradle.jdkprovider.types.JdkSpec;
import com.dua3.gradle.jdkprovider.types.OSFamily;
import com.dua3.gradle.jdkprovider.types.SystemArchitecture;
import com.dua3.gradle.jdkprovider.types.VersionSpec;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * DiscoAPI client to discover Disco packages.
 */
public final class DiscoApiClient {
    private static final Logger LOGGER = Logging.getLogger(DiscoApiClient.class);
    /**
     * The default base URL used by the {@code DiscoApiClient} for connecting to the Disco API.
     * This URL points to the endpoint for querying JDK packages and related metadata.
     * It serves as the root URL for building API requests unless explicitly overridden
     * by providing a custom base URL during the initialization of a {@code DiscoApiClient}.
     */
    private static final String DEFAULT_BASE = "https://api.foojay.io/disco/v3.0/packages";

    private static final int CONNETION_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int RETRIES = 2;

    private static final Map<String, String> VENDOR_MAP = Map.ofEntries(
             Map.entry("aoj", "adoptopenjdk"),
             Map.entry("dragonwell", "alibaba"),
             Map.entry("corretto", "amazon"),
             Map.entry("zulu", "azul"),
             Map.entry("liberica", "bellsoft"),
             Map.entry("ojdk_build", "community"),
             Map.entry("trava", "community"),
             Map.entry("temurin", "eclipse"),
             Map.entry("gluon", "gluon"),
             Map.entry("bisheng", "huawei"),
             Map.entry("semeru", "ibm"),
             Map.entry("jetbrains", "jetbrains"),
             Map.entry("jbr", "jetbrains"),
             Map.entry("microsoft", "microsoft"),
             Map.entry("open_logic", "openlogic"),
             Map.entry("graalvm", "oracle"),
             Map.entry("oracle", "oracle"),
             Map.entry("mandrel", "redhat"),
             Map.entry("redhat", "red_hat"),
             Map.entry("sap_machine", "sap"),
             Map.entry("kona", "tencent")
    );

    /**
     * The base URL used as the root for constructing query URLs in the Disco API client.
     */
    private final String baseUrl;

    /**
     * Constructs a new instance of DiscoApiClient with the default base URL.
     * The default base URL points to the Disco API's package query endpoint.
     * This constructor is designed for quick initialization using the preset configuration.
     */
    public DiscoApiClient() {
        this(DEFAULT_BASE);
    }

    /**
     * Constructs a new instance of DiscoApiClient with the specified base URL.
     * The base URL is used as the root for building query URLs for the Disco API.
     * <p>
     * This constructor is used in unit tests.
     *
     * @param baseUrl the base URL to use for API queries, must not be null
     * @throws NullPointerException if the provided base URL is null
     */
    public DiscoApiClient(String baseUrl) {
        this.baseUrl = Objects.requireNonNull(baseUrl);
    }

    /**
     * Builds a query URL for fetching JDK package details based on the specified query parameters.
     * The method constructs a parameterized URL by appending query parameters derived from the given
     * {@link JdkSpec} instance to the client's base URL.
     *
     * @param query the {@link JdkSpec} containing the parameters for constructing the query URL.
     *              It includes details such as operating system, architecture, version specification,
     *              vendor, and additional attributes like whether JavaFX is bundled.
     * @return a {@link URI} representing the complete query URL with the specified parameters.
     */
    URI buildPackagesQueryUrl(JdkQuery query) {
        List<String> params = new ArrayList<>();

        // package type
        params.add("package_type=jdk");
        // download options
        params.add("directly_downloadable=true");
        params.add("archive_type=tar.gz");
        params.add("archive_type=tgz");
        params.add("archive_type=zip");
        // filter results
        addIfNonBlank(params, query.stableReleaseOnly() != Boolean.FALSE ? "release_status=ga" : "");
        addIfNonBlank(params, query.longTermSupportOnly() != Boolean.FALSE ? "term_of_support=lts" : "");
        addIfNonBlank(params, query.freeForProductionUseOnly() != Boolean.FALSE ? "free_to_use_in_production=true" : "");
        // operating system, system architecture, etc.
        addIfNonBlank(params, toQueryParam(query.versionSpec()));
        params.add("operating_system=" + query.os().toString());
        addIfNonBlank(params, toQueryArg(query.arch()));
        addIfNonBlank(params, "libc_type", query.libcType());
        // features
        addIfNonBlank(params, query.javaFxBundled() == Boolean.TRUE ? "javafx_bundled=true" : "");

        return URI.create(baseUrl + "?" + String.join("&", params));
    }

    /**
     * Finds a suitable {@link DiscoPackage} based on the provided {@link JdkSpec}.
     * <p>
     * This method queries the Disco API using parameters derived from the given JdkSpec 
     * to retrieve matching JDK package details. If a package compatible with the specified 
     * operating system and other criteria is found, it is returned as an {@code Optional}.
     * If no matching package is found or an error occurs during the API query, an empty 
     * {@code Optional} is returned.
     *
     * @param jdkQuery the {@link JdkSpec} instance containing parameters used to query the Disco API.
     *                This includes fields like operating system, architecture, version preferences, 
     *                and vendor specifications.
     * @return an {@link Optional} containing the selected {@link DiscoPackage} if a matching 
     *         package is found; otherwise, an empty {@code Optional}.
     */
    public Optional<DiscoPackage> findPackage(JdkQuery jdkQuery) {
        URI uri = buildPackagesQueryUrl(jdkQuery);
        LOGGER.info("[JDK Provider - DiscoAPI Client] Querying: {}", uri);
        try {
            JSONArray arr = getJsonArray(uri);
            return getDiscoPackages(arr).stream()
                    .filter(pkg -> filterQueryResult(jdkQuery, pkg))
                    .max(Comparator.comparing(DiscoPackage::libcType, Comparator.reverseOrder())
                            .thenComparing(DiscoPackage::version)
                            .thenComparing(DiscoApiClient::archiveProprity)
                    );
        } catch (IOException e) {
            LOGGER.info("[JDK Provider - DiscoAPI Client] query failed for {}: {}", uri, e.toString());
            return Optional.empty();
        }
    }

    private static boolean filterQueryResult(JdkQuery jdkQuery, DiscoPackage pkg) {
        if (!isSupportedArchiveType(pkg.archiveType())) {
            LOGGER.debug("pkg {} is invalid because archive type does not match query: requested=supported, actual={}", pkg.filename(), pkg.archiveType());
            return false;
        }

        if (pkg.os() != jdkQuery.os()) {
            LOGGER.debug("pkg {} is invalid because os does not match query: requested={}, actual={}", pkg.filename(), jdkQuery.os(), pkg.os());
            return false;
        }

        if (pkg.archticture() != jdkQuery.arch()) {
            LOGGER.debug("pkg {} is invalid because architecture does not match query: requested={}, actual={}", pkg.filename(), jdkQuery.arch(), pkg.archticture());
            return false;
        }

        if (pkg.os() == OSFamily.LINUX && !pkg.libcType().isBlank() && !pkg.libcType().equals(jdkQuery.libcType())) {
            LOGGER.debug("pkg {} is invalid because libc type does not match query: requested={}, actual={}", pkg.filename(), jdkQuery.libcType(), pkg.libcType());
            return false;
        }

        String vendor = getVendorFromDistribution(pkg);
        if (!jdkQuery.vendorSpec().matches(vendor)) {
            LOGGER.debug("pkg {} is invalid because vendor does not match query: requested={}, actual={}", pkg.filename(), jdkQuery.vendorSpec(), vendor);
            return false;
        }

        if (jdkQuery.nativeImageCapable() != null) {
            boolean pkgNativeCapable = pkg.distribution().contains("graalvm") || pkg.distribution().contains("liberica_native");
            if (jdkQuery.nativeImageCapable() != pkgNativeCapable) {
                LOGGER.debug("pkg {} is invalid because native image capability does not match query: requested={}, actual={}", pkg.filename(), jdkQuery.nativeImageCapable(), pkgNativeCapable);
                return false;
            }
        }

        LOGGER.debug("pkg {} is valid", pkg.filename());
        return true;
    }

    private static String getVendorFromDistribution(DiscoPackage pkg) {
        String vs = VENDOR_MAP.get(pkg.distribution());
        if (vs != null) {
            return vs;
        }

        return VENDOR_MAP.entrySet().stream()
                .filter(entry -> pkg.distribution().contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("unknown");
    }

    /**
     * Determines the archive priority of a given package based on its archive type
     * and operating system family.
     *
     * @param pkg the DiscoPackage object containing archive type and OS information.
     * @return an integer representing the priority; higher values indicate higher priority.
     */
    private static int archiveProprity(DiscoPackage pkg) {
        int p = switch (pkg.archiveType()) {
            case "tar.gz" -> 3;
            case "tgz" -> 2;
            case "zip" -> 1;
            default -> 0;
        };
        return pkg.os() == OSFamily.WINDOWS ? 3 - p : p;
    }

    /**
     * Converts a given {@link SystemArchitecture} instance into a query argument string.
     * This method constructs a URL query parameter string by iterating over the aliases
     * associated with the {@link SystemArchitecture} and combining them into a single
     * parameterized string.
     *
     * @param arch the {@link SystemArchitecture} instance containing architecture details.
     *             If the input is null, an empty string is returned.
     * @return a string representing the architecture aliases as query arguments.
     *         Returns an empty string if {@code arch} is null.
     */
    private static String toQueryArg(SystemArchitecture arch) {
        if (arch == null) return "";
        return arch.aliases().stream()
                .map(alias -> param("architecture", alias))
                .collect(Collectors.joining("&"));
    }

    /**
     * Converts a given {@link VersionSpec} instance into a query parameter string.
     * The query parameter string is used to specify version-related constraints
     * in API queries. Different specifications of the {@link VersionSpec} result
     * in different query formats.
     *
     * @param versionSpec the {@link VersionSpec} object containing version details such as
     *                    major, minor, and patch versions. A null value or certain
     *                    property values in {@link VersionSpec} may result in
     *                    default query parameters being used.
     * @return a string representing the version specification as a query parameter.
     *         Returns "version_by_definition=latest_lts" if the major version is null.
     *         Returns "version_by_definition=latest" if the major version is {@link Integer#MAX_VALUE}.
     *         Returns specific formats based on the presence or absence of minor
     *         and patch versions in the given {@link VersionSpec}.
     */
    private static String toQueryParam(VersionSpec versionSpec) {
        if (versionSpec.major() == null) {
            return "version_by_definition=latest_lts";
        }
        if (versionSpec.major() == Integer.MAX_VALUE) {
            return "version_by_definition=latest";
        }
        if (versionSpec.minor() == null) {
            return "jdk_version=" + versionSpec.major();
        }
        if (versionSpec.minor() == Integer.MAX_VALUE) {
            return "version=" + versionSpec.major() + "..<" + (versionSpec.major() + 1);
        }
        if (versionSpec.patch() == null || versionSpec.patch() == Integer.MAX_VALUE) {
            return "version=" + versionSpec.major() + "." + versionSpec.minor() + "..<" + versionSpec.major() + "." + (versionSpec.minor() + 1);
        }
        return "version=" + versionSpec.major() + "." + versionSpec.minor() + "." + versionSpec.patch();
    }

    private static JSONArray getJsonArray(URI uri) throws IOException {
        try (HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(CONNETION_TIMEOUT_MS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()) {
            int attempts = 0;
            IOException last = null;
            while (attempts++ <= Math.max(0, RETRIES)) {
                try {
                    HttpRequest req = HttpRequest.newBuilder(uri)
                            .timeout(Duration.ofMillis(READ_TIMEOUT_MS))
                            .GET().build();
                    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                    int code = resp.statusCode();
                    if (code >= 200 && code < 300) {
                        String body = resp.body();
                        // Per DiscoAPI docs the body is either null or an object
                        if (body == null || body.isBlank()) {
                            return new JSONArray();
                        }
                        JSONObject obj = new JSONObject(body);
                        JSONArray result = obj.optJSONArray("result");
                        return result != null ? result : new JSONArray();
                    }
                    if (code < 500 || code >= 600) {
                        throw new IOException("HTTP " + code + " for " + uri);
                    }
                } catch (IOException e) {
                    last = e;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while fetching " + uri, e);
                }
            }
            throw last != null ? last : new IOException("Failed to fetch " + uri);
        }
    }

    private static List<DiscoPackage> getDiscoPackages(JSONArray arr) {
        return getCandidates(arr)
                .stream()
                .map(o -> {
                    String uri = extractDownloadUri(o);
                    String filename = o.optString("filename", null);
                    String sha256 = o.optString("sha256", "");
                    String distribution = o.optString("distribution", "");
                    // Per DiscoAPI behavior the filename is authoritative for determining archive type
                    String archiveType = normalizeArchiveType(
                            firstNonBlank(
                                    guessArchiveName(filename),
                                    o.optString("archive_type", null),
                                    guessArchiveFromUri(uri)
                            )
                    );

                    VersionSpec version = VersionSpec.parse(o.optString("java_version", "").replaceAll("\\+.*", ""));
                    OSFamily os = OSFamily.parse(o.optString("operating_system", ""));
                    SystemArchitecture architecture = SystemArchitecture.parse(o.optString("architecture", ""));
                    String libcType = o.optString("libc_type", "");

                    return new DiscoPackage(
                            URI.create(uri),
                            sha256,
                            distribution,
                            archiveType,
                            filename,
                            version, os,
                            architecture,
                            libcType);
                })
                .toList();
    }

    private static List<JSONObject> getCandidates(JSONArray arr) {
        List<JSONObject> candidates = new ArrayList<>();
        for (Object item : arr) {
            if (!(item instanceof JSONObject jsonObject)) {
                LOGGER.debug("[JDK Provider - DiscoAPI Client] non-object in result array, ignoring: {}", item);
                continue;
            }

            // Must be JDK (not JRE) and directly downloadable. The actual download link is provided in the "link" object
            // as per DiscoAPI docs.
            String pkgType = jsonObject.optString("package_type", "");
            if (!pkgType.equalsIgnoreCase("jdk")) {
                LOGGER.debug("[JDK Provider - DiscoAPI Client] non-JDK package: {}", jsonObject);
                continue;
            }

            boolean directlyDownloadable = jsonObject.optBoolean("directly_downloadable", false);
            if (!directlyDownloadable) {
                LOGGER.debug("[JDK Provider - DiscoAPI Client] package is not directly downloadable: {}", jsonObject);
                continue;
            }

            String uri = extractDownloadUri(jsonObject);
            if (uri == null || uri.isBlank()) {
                LOGGER.debug("[JDK Provider - DiscoAPI Client] package without download link in 'link' object: {}", jsonObject);
                continue;
            }

            candidates.add(jsonObject);
        }
        return candidates;
    }

    private static String extractDownloadUri(JSONObject jsonObject) {
        // New API: download link is inside "link" (or sometimes "links") object.
        JSONObject link = jsonObject.optJSONObject("link");
        if (link == null) {
            link = jsonObject.optJSONObject("links");
        }
        String fromLink = null;
        if (link != null) {
            // Try common keys in order of likelihood
            fromLink = firstNonBlank(
                    link.optString("pkg_download_redirect", null),
                    link.optString("pkg_download_uri", null),
                    link.optString("pkg_download_url", null),
                    link.optString("download_uri", null),
                    link.optString("download_url", null)
            );
        }
        if (fromLink != null && !fromLink.isBlank()) {
            return fromLink;
        }
        // Some distros may provide the URL directly at top-level for backward compatibility
        return firstNonBlank(
                jsonObject.optString("pkg_download_redirect", null),
                jsonObject.optString("pkg_download_uri", null),
                jsonObject.optString("pkg_download_url", null),
                jsonObject.optString("download_uri", null),
                jsonObject.optString("download_url", null)
        );
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static String guessArchiveFromUri(String uri) {
        if (uri == null) return null;
        int q = uri.indexOf('?');
        String path = q >= 0 ? uri.substring(0, q) : uri;
        int slash = path.lastIndexOf('/');
        String filename = slash >= 0 ? path.substring(slash + 1) : path;
        return guessArchiveName(filename);
    }

    private static String guessArchiveName(String filename) {
        if (filename == null) return null;
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".tar.gz")) return "tar.gz";
        if (lower.endsWith(".tgz")) return "tgz";
        if (lower.endsWith(".zip")) return "zip";
        if (lower.endsWith(".tar")) return "tar";
        return null;
    }

    private static String normalizeArchiveType(String t) {
        if (t == null) return "";
        return switch (t.toLowerCase(Locale.ROOT)) {
            case "tar.gz", "tgz" -> t.toLowerCase(Locale.ROOT);
            case "zip" -> "zip";
            case "tar" -> "tar";
            default -> "";
        };
    }

    private static boolean isSupportedArchiveType(String t) {
        if (t == null || t.isBlank()) return false;
        String normalized = normalizeArchiveType(t);
        return normalized.equals("zip") || normalized.equals("tar.gz") || normalized.equals("tgz");
    }

    /**
     * Adds the specified name-value pair to the given list of parameters if the value is non-null.
     * The method creates a query parameter string using the name and the string representation of the value,
     * and appends it to the parameter list.
     *
     * @param params the list of query parameters to which the name-value pair should be added
     * @param name   the name of the query parameter
     * @param value  the value to be associated with the name; the value is added only if it is non-null
     */
    private static void addIfNonBlank(List<String> params, String name, String value) {
        if (!value.isBlank()) {
            params.add(param(name, value));
        }
    }

    /**
     * Adds the specified query parameter to the provided list of parameters if the query parameter
     * is non-null and not blank. A query parameter is considered blank if it is empty or contains
     * only whitespace characters.
     *
     * @param params     the list of query parameters to which the query parameter should be added;
     *                   must not be null
     * @param queryParam the query parameter to be added to the list; it will be added only if it
     *                   is non-null and not blank
     */
    private static void addIfNonBlank(List<String> params, String queryParam) {
        if (queryParam != null && !queryParam.isBlank()) {
            params.add(queryParam);
        }
    }

    /**
     * Constructs a query parameter string by combining the given name and value,
     * encoding them properly for inclusion in a URL.
     *
     * @param name  the name of the query parameter, must not be null
     * @param value the value of the query parameter, must not be null
     * @return a string in the format "name=value" where both the name and value are URL-encoded
     */
    private static String param(String name, String value) {
        return urlEncode(name) + '=' + urlEncode(value);
    }

    /**
     * Encodes the given string as a URL-encoded string using the UTF-8 character set.
     * This method ensures the input string is properly encoded for safe inclusion in a URL.
     *
     * @param s the string to be URL-encoded; must not be null
     * @return the URL-encoded string, encoded using the UTF-8 character set
     */
    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
