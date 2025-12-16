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

    /**
     * Constructs an instance of the HttpDownloader class with specified configuration parameters.
     *
     * @param connectTimeoutMs the timeout in milliseconds for establishing the HTTP connection
     * @param readTimeoutMs the timeout in milliseconds for waiting to read data from the HTTP connection
     * @param retries the maximum number of retries to perform for transient failures; must be zero or greater
     */
    public HttpDownloader(int connectTimeoutMs, int readTimeoutMs, int retries) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.timeout = Duration.ofMillis(readTimeoutMs);
        this.retries = Math.max(0, retries);
    }

    /**
     * Downloads the content from the specified URI to the target file path.
     * If the target file's parent directory does not exist, it will be created.
     * The method retries the download up to the configured number of retries in case of transient failures.
     *
     * @param uri the URI from which the content will be downloaded
     * @param targetFile the path to the file where the downloaded content will be saved
     * @return the path to the target file containing the downloaded content
     * @throws IOException if an I/O error occurs during the download, including HTTP errors
     * @throws InterruptedException if the operation is interrupted
     */
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
