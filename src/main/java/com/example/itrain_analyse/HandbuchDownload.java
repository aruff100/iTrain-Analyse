package com.example.itrain_analyse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handbuch-PDF von der Download-Seite (Vorgabe https://berros.eu/download/6-0/). Die Datei liegt im Zwischenspeicher
 * {@code .cache/handbuch}; ihr Änderungsdatum entspricht dem "Last-Modified" des Servers. {@link #pruefe} fragt nur
 * den Dateikopf ab (HEAD) - geladen wird erst nach Rückfrage mit {@link #lade}.
 */
public final class HandbuchDownload {

    /** Ergebnis der Prüfung. {@code groesse} = Größe auf dem Server in Byte, wenn bekannt. */
    public record Stand(Zustand zustand, Path datei, long groesse) {
    }

    public enum Zustand {
        /** Lokal aktuell. */
        AKTUELL,
        /** Lokal vorhanden, auf dem Server neuer. */
        NEUER,
        /** Lokal nicht vorhanden, auf dem Server verfügbar. */
        FEHLT,
        /** Server nicht erreichbar, lokale Fassung vorhanden. */
        OFFLINE,
        /** Weder lokal noch auf dem Server verfügbar. */
        NICHT_VERFUEGBAR
    }

    private static final Pattern VERSION_IN_ADRESSE = Pattern.compile("/(\\d+)-(\\d+)/?$");

    private HandbuchDownload() {
    }

    /** "https://berros.eu/download/6-0/" ergibt 6.0; {@code null}, wenn die Adresse anders aufgebaut ist. */
    public static Version versionAus(String adresse) {
        Matcher m = VERSION_IN_ADRESSE.matcher(adresse.trim());
        return m.find() ? Version.aus(m.group(1) + "." + m.group(2)) : null;
    }

    static String dateiname(String adresse, HandbuchSprache sprache) {
        Version v = versionAus(adresse);
        return sprache.dateiname(v == null ? 6 : v.teile().get(0));
    }

    public static Path lokal(String adresse, HandbuchSprache sprache) {
        return Einstellungen.cache().resolve("handbuch").resolve(dateiname(adresse, sprache));
    }

    public static Stand pruefe(String adresse, HandbuchSprache sprache) {
        Path lokal = lokal(adresse, sprache);
        boolean da = Files.isRegularFile(lokal);
        URI uri = Http.datei(adresse, dateiname(adresse, sprache));
        try {
            HttpResponse<Void> a = Http.kopf(uri);
            if (a.statusCode() != 200) {
                return new Stand(da ? Zustand.OFFLINE : Zustand.NICHT_VERFUEGBAR, lokal, -1);
            }
            OptionalLong laenge = a.headers().firstValueAsLong("Content-Length");
            long groesse = laenge.isPresent() ? laenge.getAsLong() : -1;
            if (!da) {
                return new Stand(Zustand.FEHLT, lokal, groesse);
            }
            Instant server = a.headers().firstValue("Last-Modified").map(HandbuchDownload::zeit).orElse(null);
            boolean neuer = (server != null && server.isAfter(Files.getLastModifiedTime(lokal).toInstant()))
                    || (groesse > 0 && groesse != Files.size(lokal));
            return new Stand(neuer ? Zustand.NEUER : Zustand.AKTUELL, lokal, groesse);
        } catch (IOException ex) {
            return new Stand(da ? Zustand.OFFLINE : Zustand.NICHT_VERFUEGBAR, lokal, -1);
        }
    }

    public static Path lade(String adresse, HandbuchSprache sprache) throws IOException {
        Path ziel = lokal(adresse, sprache);
        HttpResponse<Path> a = Http.laden(Http.datei(adresse, dateiname(adresse, sprache)), ziel);
        a.headers().firstValue("Last-Modified").map(HandbuchDownload::zeit).ifPresent(t -> {
            try {
                Files.setLastModifiedTime(ziel, FileTime.from(t));
            } catch (IOException ex) {
                // nur für die nächste Prüfung
            }
        });
        return ziel;
    }

    private static Instant zeit(String http) {
        try {
            return ZonedDateTime.parse(http, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
