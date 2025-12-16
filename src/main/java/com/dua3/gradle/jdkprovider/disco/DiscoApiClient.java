package com.dua3.gradle.jdkprovider.disco;

import com.dua3.gradle.jdkprovider.types.JdkSpec;
import com.dua3.gradle.jdkprovider.types.VersionSpec;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Minimal DiscoAPI client for building query URLs.
 */
public final class DiscoApiClient {
    private static final String DEFAULT_BASE = "https://api.foojay.io/disco/v3.0/packages";

    private final String baseUrl;

    public DiscoApiClient() {
        this(DEFAULT_BASE);
    }

    public DiscoApiClient(String baseUrl) {
        this.baseUrl = Objects.requireNonNull(baseUrl);
    }


    public URI buildPackagesQueryUrl(JdkSpec query) {
        List<String> params = new ArrayList<>();
        params.add(toQueryParam(query.versionSpec()));
        addIfNonNull(params, "operating_system", query.os());
        addIfNonNull(params, "architecture", query.arch());
        addIfNonNull(params, "distribution", toQueryArg(query.vendor()));
        addIfNonNull(params, "javafx_bundled", query.javaFxBundled());
        addIfNonNull(params, "release_status", "ga");
        return URI.create(baseUrl + "?" + String.join("&", params));
    }

    private static String toQueryParam(VersionSpec versionSpec) {
        if (versionSpec == null) {
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

    private static String toQueryArg(JvmVendorSpec jvmVendorSpec) {
        return switch (String.valueOf(jvmVendorSpec).toLowerCase(Locale.ROOT)) {
            case "azul zulu" -> "zulu";
            case "bellsoft liberica" -> "liberica";
            default -> "";
        };
    }

    private static <T> void addIfNonNull(List<String> params, String name, @Nullable T value) {
        if (value != null) {
            params.add(param(name, value.toString()));
        }
    }

    private static String param(String name, String value) {
        return url(name) + '=' + url(value);
    }

    private static String url(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
