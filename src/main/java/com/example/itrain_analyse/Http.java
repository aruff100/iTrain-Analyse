package com.example.itrain_analyse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/** Gemeinsamer HTTP-Zugriff (Prompt-Server, Handbuch-Download). */
final class Http {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private Http() {
    }

    static HttpRequest.Builder anfrage(URI uri) {
        return HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(5))
                .header("User-Agent", "iTrain-Analyse/" + AppVersion.text());
    }

    static String text(URI uri) throws IOException {
        HttpResponse<String> a = senden(anfrage(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (a.statusCode() != 200) {
            throw new IOException("HTTP " + a.statusCode() + " " + uri);
        }
        return a.body();
    }

    static HttpResponse<Void> kopf(URI uri) throws IOException {
        return senden(anfrage(uri).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.discarding());
    }

    /** Lädt in eine temporäre Datei neben dem Ziel und verschiebt erst nach vollständigem Download. */
    static HttpResponse<Path> laden(URI uri, Path ziel) throws IOException {
        Files.createDirectories(ziel.getParent());
        Path tmp = ziel.resolveSibling(ziel.getFileName() + ".part");
        try {
            HttpResponse<Path> a = senden(anfrage(uri).GET().build(), HttpResponse.BodyHandlers.ofFile(tmp));
            if (a.statusCode() != 200) {
                throw new IOException("HTTP " + a.statusCode() + " " + uri);
            }
            Files.move(tmp, ziel, StandardCopyOption.REPLACE_EXISTING);
            return a;
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static <T> HttpResponse<T> senden(HttpRequest r, HttpResponse.BodyHandler<T> h) throws IOException {
        try {
            return CLIENT.send(r, h);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("abgebrochen", ex);
        }
    }

    /** Hängt einen Dateinamen URL-kodiert an eine Ordner-Adresse an. */
    static URI datei(String ordner, String name) {
        String basis = ordner.trim().endsWith("/") ? ordner.trim() : ordner.trim() + "/";
        return URI.create(basis + java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20"));
    }
}
