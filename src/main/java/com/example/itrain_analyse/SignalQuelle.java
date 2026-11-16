package com.example.itrain_analyse;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prüft, ob Berros die "Regeln für Signale" geändert hat, auf denen eine Signal-Zusatzdatei beruht (Anwender
 * 16.11.2026). Die Zusatzdatei nennt im Kopf die Adresse der PDF und deren Stand beim Erstellen:
 * <pre>
 * Quelle: https://berros.eu/download/tutorial/Rules_For_iTrain_Signals_v01_DE.pdf
 * Quelle-Stand: 2024-10-13T12:14:03Z, 2264076 Bytes
 * </pre>
 * (englisch "Source:" und "Source-Date:"). Verglichen werden "Last-Modified" und Größe der PDF (HEAD); außerdem
 * sucht die Prüfung im Verzeichnis-Listing nach einer höheren Fassung desselben Namens (…_v02_DE.pdf). Die
 * Zusatzdatei wird nicht automatisch angepasst: Sie wird von Hand überarbeitet und als neue Version hochgeladen.
 */
public final class SignalQuelle {

    public enum Zustand { AKTUELL, GEAENDERT, NEUE_FASSUNG, ENTFERNT, UNBEKANNT }

    /** Ergebnis: Zustand, Adresse und Stand laut Zusatzdatei, Name einer neueren Fassung (sonst null). */
    public record Stand(Zustand zustand, String adresse, Instant stand, String neueDatei) {
    }

    /** Kopfangaben der Zusatzdatei. */
    record Kopf(String adresse, Instant stand, long groesse) {
    }

    private static final Pattern ADRESSE = Pattern.compile("^(?:Quelle|Source):\\s*(https?://\\S+)\\s*$",
            Pattern.MULTILINE);
    private static final Pattern STAND = Pattern.compile(
            "^(?:Quelle-Stand|Source-Date):\\s*(\\S+?),\\s*(\\d+)\\s*Bytes\\s*$", Pattern.MULTILINE);
    private static final Pattern FASSUNG = Pattern.compile("^(.*_v)(\\d+)((?:_[A-Za-z]+)?\\.pdf)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HREF = Pattern.compile("href=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    private SignalQuelle() {
    }

    static Kopf kopf(String text) {
        Matcher a = ADRESSE.matcher(text);
        Matcher s = STAND.matcher(text);
        if (!a.find() || !s.find()) {
            return null;
        }
        try {
            return new Kopf(a.group(1), Instant.parse(s.group(1)), Long.parseLong(s.group(2)));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Sucht in einem Verzeichnis-Listing (HTML) eine höhere Fassung derselben Datei: gleicher Anfang bis "_v",
     * gleiche Endung (z. B. "_DE.pdf"), größere Nummer. Liefert den Dateinamen oder null.
     */
    static String neuereFassung(String adresse, String html) {
        String name = adresse.substring(adresse.lastIndexOf('/') + 1);
        Matcher m = FASSUNG.matcher(name);
        if (!m.matches()) {
            return null;
        }
        int eigene = Integer.parseInt(m.group(2));
        String beste = null;
        int besteNr = eigene;
        Matcher h = HREF.matcher(html);
        while (h.find()) {
            String ziel = URLDecoder.decode(h.group(1), StandardCharsets.UTF_8);
            ziel = ziel.substring(ziel.lastIndexOf('/') + 1);
            Matcher z = FASSUNG.matcher(ziel);
            if (z.matches() && z.group(1).equalsIgnoreCase(m.group(1)) && z.group(3).equalsIgnoreCase(m.group(3))) {
                int nr = Integer.parseInt(z.group(2));
                if (nr > besteNr) {
                    besteNr = nr;
                    beste = ziel;
                }
            }
        }
        return beste;
    }

    /** Vergleicht die Quelle der Zusatzdatei mit dem Server. Wirft IOException, wenn Berros nicht erreichbar ist. */
    public static Stand pruefe(Path zusatz) throws IOException {
        Kopf k = kopf(Files.readString(zusatz, StandardCharsets.UTF_8));
        if (k == null) {
            return new Stand(Zustand.UNBEKANNT, null, null, null);
        }
        URI uri = URI.create(k.adresse());
        String neu = null;
        try {
            neu = neuereFassung(k.adresse(), Http.text(uri.resolve(".")));
        } catch (IOException ex) {
            // Ohne Verzeichnis-Listing bleibt es beim Vergleich der Datei selbst.
        }
        if (neu != null) {
            return new Stand(Zustand.NEUE_FASSUNG, k.adresse(), k.stand(), neu);
        }
        HttpResponse<Void> a = Http.kopf(uri);
        if (a.statusCode() == 404 || a.statusCode() == 410) {
            return new Stand(Zustand.ENTFERNT, k.adresse(), k.stand(), null);
        }
        if (a.statusCode() != 200) {
            throw new IOException("HTTP " + a.statusCode() + " " + uri);
        }
        OptionalLong laenge = a.headers().firstValueAsLong("Content-Length");
        Instant server = a.headers().firstValue("Last-Modified").map(SignalQuelle::zeit).orElse(null);
        boolean geaendert = (laenge.isPresent() && laenge.getAsLong() != k.groesse())
                || (server != null && !server.equals(k.stand()));
        return new Stand(geaendert ? Zustand.GEAENDERT : Zustand.AKTUELL, k.adresse(), k.stand(), null);
    }

    /** Meldung für das Feld "Fortschritt", bei AKTUELL null. */
    public static String meldung(Stand s) {
        String datei = s.adresse() == null ? "" : s.adresse().substring(s.adresse().lastIndexOf('/') + 1);
        String datum = s.stand() == null ? "?"
                : DateTimeFormatter.ofPattern("dd.MM.yyyy").format(s.stand().atZone(ZoneId.systemDefault()));
        return switch (s.zustand()) {
            case AKTUELL -> null;
            case GEAENDERT -> I18n.t("status.signaleQuelleGeaendert", datei, datum);
            case NEUE_FASSUNG -> I18n.t("status.signaleQuelleNeu", s.neueDatei(), datei);
            case ENTFERNT -> I18n.t("status.signaleQuelleWeg", s.adresse());
            case UNBEKANNT -> I18n.t("status.signaleQuelleUnbekannt");
        };
    }

    private static Instant zeit(String http) {
        try {
            return ZonedDateTime.parse(http, DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH))
                    .toInstant();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
