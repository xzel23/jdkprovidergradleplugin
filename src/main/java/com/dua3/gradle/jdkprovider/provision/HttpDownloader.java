package com.dua3.gradle.jdkprovider.provision;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Simple HTTP downloader with timeouts and basic retry for transient failures.
 */
public final class HttpDownloader {
    private final HttpClient client;
    private final Duration timeout;
    private final int retries;

    public HttpDownloader(int connectTimeoutMs, int readTimeoutMs, int retries) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.timeout = Duration.ofMillis(readTimeoutMs);
        this.retries = Math.max(0, retries);
    }

    public Path downloadTo(URI uri, Path targetFile) throws IOException, InterruptedException {
        Files.createDirectories(targetFile.getParent());
        int attempts = 0;
        IOException lastIo = null;
        InterruptedException lastInterrupted = null;
        while (attempts <= retries) {
            attempts++;
            try {
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(timeout)
                        .GET()
                        .build();
                HttpResponse<InputStream> resp = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int code = resp.statusCode();
                if (code >= 200 && code < 300) {
                    try (InputStream in = resp.body()) {
                        Files.copy(in, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    return targetFile;
                } else if (code < 500 || code >= 600) {
                    throw new IOException("HTTP " + code + " when downloading: " + uri);
                }
                // else retry on server errors
            } catch (IOException e) {
                lastIo = e;
            } catch (InterruptedException e) {
                lastInterrupted = e;
                break;
            }
        }
        if (lastInterrupted != null) throw lastInterrupted;
        if (lastIo != null) throw lastIo;
        throw new IOException("Download failed: " + uri);
    }
}
