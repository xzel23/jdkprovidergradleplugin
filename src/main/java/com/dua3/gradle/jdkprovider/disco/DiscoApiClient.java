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

import com.dua3.gradle.jdkprovider.types.JdkSpec;
import com.dua3.gradle.jdkprovider.types.OSFamily;
import com.dua3.gradle.jdkprovider.types.SystemArchitecture;
import com.dua3.gradle.jdkprovider.types.VersionSpec;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
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

    /**
     * A mapping of predicates to lists of vendor aliases used for identifying and transforming
     * vendor-related information in the Disco API client. Each entry in the map consists of a
     * predicate, generated from a regular expression, and a corresponding list of
     * distribution identifiers that are used by that vendor.
     * <p>
     * The map is statically initialized and immutable, providing a predetermined set of
     * vendor mappings at runtime. Each predicate is applied to a candidate vendor string to
     * determine if any of the associated aliases match.
     */
    private static final Map<Predicate<String>, List<String>> VENDOR_MAP = Map.ofEntries(
            Map.entry(Pattern.compile("aoj|adoptopenjdk").asPredicate(), List.of("aoj_openj9", "aoj")),
            Map.entry(Pattern.compile("alibaba").asPredicate(), List.of("dragonwell")),
            Map.entry(Pattern.compile("amazon|corretto").asPredicate(), List.of("corretto")),
            Map.entry(Pattern.compile("azul|zulu").asPredicate(), List.of("zulu")),
            Map.entry(Pattern.compile("bellsoft|liberica").asPredicate(), List.of("liberica_native", "liberica")),
            Map.entry(Pattern.compile("community").asPredicate(), List.of("ojdk_build", "trava")),
            Map.entry(Pattern.compile("temurin|adoptium|eclipse[ _]foundation").asPredicate(), List.of("temurin")),
            Map.entry(Pattern.compile("gluon").asPredicate(), List.of("gluon_graalvm")),
            Map.entry(Pattern.compile("huawei").asPredicate(), List.of("bisheng")),
            Map.entry(Pattern.compile("ibm|semeru|international business machines corporation").asPredicate(), List.of("semeru_certified", "semeru")),
            Map.entry(Pattern.compile("jbr|jetbrains").asPredicate(), List.of("jetbrains")),
            Map.entry(Pattern.compile("microsoft").asPredicate(), List.of("microsoft")),
            Map.entry(Pattern.compile("open_logic").asPredicate(), List.of("openlogic")),
            Map.entry(Pattern.compile("oracle").asPredicate(), List.of("graalvm_ce11", "graalvm_ce16", "graalvm_ce17", "graalvm_ce19", "graalvm_ce8", "graalvm_community", "graalvm", "oracle_open_jdk", "oracle")),
            Map.entry(Pattern.compile("red_hat").asPredicate(), List.of("mandrel", "redhat")),
            Map.entry(Pattern.compile("sap").asPredicate(), List.of("sap_machine")),
            Map.entry(Pattern.compile("tencent|kona").asPredicate(), List.of("kona"))
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
    URI buildPackagesQueryUrl(JdkSpec query) {
        List<String> params = new ArrayList<>();
        params.add("directly_downloadable=true");
        addIfNonNullOrBlank(params, toQueryParam(query.versionSpec()));
        addIfNonNull(params, "operating_system", query.os());
        addIfNonNullOrBlank(params, toQueryArg(query.arch()));
        addIfNonNullOrBlank(params, toQueryParam(query.vendor()));
        addIfNonNull(params, "javafx_bundled", query.javaFxBundled());
        addIfNonNull(params, "release_status", "ga");
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
     * @param jdkSpec the {@link JdkSpec} instance containing parameters used to query the Disco API.
     *                This includes fields like operating system, architecture, version preferences, 
     *                and vendor specifications.
     * @return an {@link Optional} containing the selected {@link DiscoPackage} if a matching 
     *         package is found; otherwise, an empty {@code Optional}.
     */
    public Optional<DiscoPackage> findPackage(JdkSpec jdkSpec) {
        URI uri = buildPackagesQueryUrl(jdkSpec);
        LOGGER.debug("Querying DiscoAPI: {}", uri);
        try {
            JSONArray arr = getJsonArray(uri);
            Optional<DiscoPackage> selected = selectFromArray(arr, jdkSpec.os(), LOGGER);
            if (selected.isPresent()) return selected;
        } catch (IOException e) {
            LOGGER.debug("DiscoAPI query failed for {}: {}", uri, e.toString());
        }

        return Optional.empty();
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
    private String toQueryArg(SystemArchitecture arch) {
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
    private static String toQueryParam(@Nullable VersionSpec versionSpec) {
        if (versionSpec == null || versionSpec.major() == null) {
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

    /**
     * Converts the given {@link JvmVendorSpec} instance into a query parameter string.
     * This method maps the vendor specification to a set of predefined distributions
     * and constructs a query parameter string using those distributions.
     *
     * @param jvmVendorSpec the {@link JvmVendorSpec} instance representing the vendor specification.
     *                      If null, the method returns an empty string.
     * @return a string representing the vendor distributions as query parameters.
     *         Returns an empty string if {@code jvmVendorSpec} is null or no mapping is found.
     */
    private static String toQueryParam(@Nullable JvmVendorSpec jvmVendorSpec) {
        if (jvmVendorSpec == null) return "";

        String vendorString = String.valueOf(jvmVendorSpec).toLowerCase(Locale.ROOT);
        return VENDOR_MAP.entrySet().stream()
                .filter(e -> e.getKey().test(vendorString))
                .findFirst()
                .map(Map.Entry::getValue)
                .map(distributions ->
                        distributions.stream()
                                .map(dist -> param("distribution", dist))
                                .collect(Collectors.joining("&")))
                .orElse("");
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

    private static Optional<DiscoPackage> selectFromArray(JSONArray arr, OSFamily os, Logger logger) {
        // Preference order limited to archive types we can extract
        boolean windows = os == OSFamily.WINDOWS;
        String[] preferredArchiveOrder = windows
                ? new String[]{"zip", "tar.gz", "tgz"}
                : new String[]{"tar.gz", "tgz", "zip"};

        // First pass: build candidates
        List<JSONObject> candidates = getCandidates(arr);
        if (candidates.isEmpty()) {
            logger.debug("DiscoAPI returned no suitable packages.");
            return Optional.empty();
        }

        // Choose by archive preference
        for (String pref : preferredArchiveOrder) {
            for (JSONObject o : candidates) {
                String uri = extractDownloadUri(o);
                String filename = o.optString("filename", null);
                // Per DiscoAPI behavior the filename is authoritative for determining archive type
                String archiveType = normalizeArchiveType(
                        firstNonBlank(
                                guessArchiveName(filename),
                                o.optString("archive_type", null),
                                guessArchiveFromUri(uri)
                        )
                );
                if (archiveType.equals(pref) && isSupportedArchiveType(archiveType)) {
                    String sha256 = o.optString("sha256", "");
                    String vendor = o.optString("distribution", "");
                    return Optional.of(new DiscoPackage(URI.create(uri), sha256, vendor, archiveType, filename));
                }
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("DiscoAPI returned no suitable packages for preferred archive order, returning first candidate: {}", Arrays.toString(preferredArchiveOrder));
        }

        // Fallback: first candidate that has a supported archive type
        for (JSONObject cand : candidates) {
            String uri = extractDownloadUri(cand);
            String sha256 = cand.optString("sha256", "");
            String vendor = cand.optString("distribution", "");
            String filename = cand.optString("filename", null);
            String archiveType = normalizeArchiveType(
                    firstNonBlank(
                            guessArchiveName(filename),
                            cand.optString("archive_type", null),
                            guessArchiveFromUri(uri)
                    )
            );
            if (isSupportedArchiveType(archiveType)) {
                return Optional.of(new DiscoPackage(URI.create(uri), sha256, vendor, archiveType, filename));
            }
        }
        logger.debug("No candidates with supported archive types were found.");
        return Optional.empty();
    }

    private static List<JSONObject> getCandidates(JSONArray arr) {
        List<JSONObject> candidates = new ArrayList<>();
        for (Object item : arr) {
            if (!(item instanceof JSONObject jsonObject)) {
                LOGGER.debug("DiscoAPI returned non-object in result array, ignoring: {}", item);
                continue;
            }

            // Must be JDK (not JRE) and directly downloadable. The actual download link is provided in the "link" object
            // as per DiscoAPI docs.
            String pkgType = jsonObject.optString("package_type", "");
            if (!pkgType.equalsIgnoreCase("jdk")) {
                LOGGER.debug("DiscoAPI returned non-JDK package: {}", jsonObject);
                continue;
            }

            boolean directlyDownloadable = jsonObject.optBoolean("directly_downloadable", false);
            if (!directlyDownloadable) {
                LOGGER.debug("DiscoAPI returned package that is not directly downloadable: {}", jsonObject);
                continue;
            }

            String uri = extractDownloadUri(jsonObject);
            if (uri == null || uri.isBlank()) {
                LOGGER.debug("DiscoAPI returned package without download link in 'link' object: {}", jsonObject);
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
        String n = normalizeArchiveType(t);
        return n.equals("zip") || n.equals("tar.gz") || n.equals("tgz");
    }

    /**
     * Adds the specified name-value pair to the given list of parameters if the value is non-null.
     * The method creates a query parameter string using the name and the string representation of the value,
     * and appends it to the parameter list.
     *
     * @param <T>   the type of the value to be checked for null and converted to a string
     * @param params the list of query parameters to which the name-value pair should be added
     * @param name   the name of the query parameter
     * @param value  the value to be associated with the name; the value is added only if it is non-null
     */
    private static <T> void addIfNonNull(List<String> params, String name, @Nullable T value) {
        if (value != null) {
            params.add(param(name, value.toString()));
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
    private static void addIfNonNullOrBlank(List<String> params, String queryParam) {
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
