package com.dua3.gradle.jdkprovider.disco;

import com.dua3.gradle.jdkprovider.types.JdkSpec;
import com.dua3.gradle.jdkprovider.types.OSFamily;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jspecify.annotations.NullMarked;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@NullMarked
public class DiscoApiSelector {
    private static final Logger LOGGER = Logging.getLogger(DiscoApiSelector.class);

    private static final int CONNETION_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int RETRIES = 2;

    private final DiscoApiClient client;

    public DiscoApiSelector() {
        client = new DiscoApiClient();
    }

    public DiscoApiSelector(String baseUrl) {
        client = new DiscoApiClient(baseUrl);
    }

    public Optional<DiscoPackage> findPackage(JdkSpec jdkSpec) {

        URI uri = client.buildPackagesQueryUrl(jdkSpec);
        LOGGER.debug("[Java Resolver] Querying DiscoAPI: {}", uri);
        try {
            JSONArray arr = getJsonArray(uri);
            Optional<DiscoPackage> selected = selectFromArray(arr, jdkSpec.os(), LOGGER);
            if (selected.isPresent()) return selected;
        } catch (Exception e) {
            LOGGER.debug("[Java Resolver] DiscoAPI query failed for {}: {}", uri, e.toString());
        }

        return Optional.empty();
    }

    private static JSONArray getJsonArray(URI uri) throws Exception {
        try (HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(CONNETION_TIMEOUT_MS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()) {
            int attempts = 0;
            Exception last = null;
            while (attempts++ <= Math.max(0, RETRIES)) {
                try {
                    HttpRequest req = HttpRequest.newBuilder(uri)
                            .timeout(Duration.ofMillis(READ_TIMEOUT_MS))
                            .GET().build();
                    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                    int code = resp.statusCode();
                    if (code >= 200 && code < 300) {
                        String body = resp.body();
                        // Per DiscoAPI docs the body is either null or an object: { "result": [ ... ], "message": "..." }
                        if (body == null || body.isBlank()) {
                            return new JSONArray();
                        }
                        JSONObject obj = new JSONObject(body);
                        JSONArray result = obj.optJSONArray("result");
                        return result != null ? result : new JSONArray();
                    }
                    if (code < 500 || code >= 600) {
                        throw new RuntimeException("HTTP " + code + " for " + uri);
                    }
                } catch (Exception e) {
                    last = e;
                }
            }
            throw last != null ? last : new RuntimeException("Failed to fetch " + uri);
        }
    }

    private static Optional<DiscoPackage> selectFromArray(JSONArray arr, OSFamily os, Logger logger) {
        // Preference order limited to archive types we can extract
        boolean windows = os == OSFamily.WINDOWS.WINDOWS;
        String[] preferredArchiveOrder = windows
                ? new String[]{"zip", "tar.gz", "tgz"}
                : new String[]{"tar.gz", "tgz", "zip"};

        // First pass: build candidates
        List<JSONObject> candidates = getCandidates(arr);
        if (candidates.isEmpty()) {
            logger.debug("[Java Resolver] DiscoAPI returned no suitable packages.");
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
            logger.debug("[Java Resolver] DiscoAPI returned no suitable packages for preferred archive order, returning first candidate: {}", Arrays.toString(preferredArchiveOrder));
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
        logger.debug("[Java Resolver] No candidates with supported archive types were found.");
        return Optional.empty();
    }

    private static List<JSONObject> getCandidates(JSONArray arr) {
        List<JSONObject> candidates = new ArrayList<>();
        for (Object item : arr) {
            if (!(item instanceof JSONObject jsonObject)) {
                LOGGER.debug("[Java Resolver] DiscoAPI returned non-object in result array, ignoring: {}", item);
                continue;
            }

            // Must be JDK (not JRE) and directly downloadable. The actual download link is provided in the "link" object
            // as per DiscoAPI docs.
            String pkgType = jsonObject.optString("package_type", "");
            if (!pkgType.equalsIgnoreCase("jdk")) {
                LOGGER.debug("[Java Resolver] DiscoAPI returned non-JDK package: {}", jsonObject);
                continue;
            }

            boolean directlyDownloadable = jsonObject.optBoolean("directly_downloadable", false);
            if (!directlyDownloadable) {
                LOGGER.debug("[Java Resolver] DiscoAPI returned package that is not directly downloadable: {}", jsonObject);
                continue;
            }

            String uri = extractDownloadUri(jsonObject);
            if (uri == null || uri.isBlank()) {
                LOGGER.debug("[Java Resolver] DiscoAPI returned package without download link in 'link' object: {}", jsonObject);
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
        // Fallback to legacy fields if present
        return firstNonBlank(
                jsonObject.optString("direct_download_uri", null),
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
        if (uri == null) return "";
        return guessArchiveName(uri.toString());
    }

    private static String guessArchiveName(String filename) {
        if (filename == null || filename.isBlank()) return "";
        String fn = filename.toLowerCase(Locale.ROOT);
        if (fn.endsWith(".tar.gz") || fn.endsWith(".tgz")) return "tar.gz";
        if (fn.endsWith(".zip")) return "zip";
        if (fn.endsWith(".msi")) return "msi";
        if (fn.endsWith(".exe")) return "exe";
        if (fn.endsWith(".dmg")) return "dmg";
        if (fn.endsWith(".pkg")) return "pkg";
        return "";
    }

    private static String normalizeArchiveType(String t) {
        String s = t == null ? "" : t.toLowerCase(Locale.ROOT);
        if (s.equals("tgz")) return "tar.gz";
        return s;
    }

    private static boolean isSupportedArchiveType(String t) {
        String s = normalizeArchiveType(t);
        return s.equals("zip") || s.equals("tar.gz");
    }
}
