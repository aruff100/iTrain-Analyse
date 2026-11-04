package com.example.itrain_analyse;

import java.io.IOException;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prüft, ob es eine neuere Programmversion gibt.
 *
 * <p>Quelle ist das neueste veröffentlichte GitHub-Release (Feld {@code tag_name}, z. B. "v1.1"). Entwürfe und
 * Vorabversionen liefert {@code releases/latest} nicht. Dem Anwender wird nur die Webseite itrainplus.de angezeigt,
 * nicht GitHub (Anwender 04.11.2026).
 */
public final class ProgrammUpdate {

    static final URI LATEST = URI.create("https://api.github.com/repos/aruff100/iTrain-Analyse/releases/latest");
    static final URI WEBSEITE = URI.create("https://itrainplus.de/analyse.html");

    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");

    /** Ergebnis der Prüfung. {@code neu} ist {@code null}, wenn die installierte Version aktuell ist. */
    public record Stand(Version installiert, Version neu) {
    }

    private ProgrammUpdate() {
    }

    /** Fragt das neueste Release ab und vergleicht es mit der installierten Version. */
    public static Stand pruefe() throws IOException {
        Version installiert = Version.aus(AppVersion.text());
        Version online = neuesteAus(Http.text(LATEST));
        if (online == null) {
            throw new IOException("tag_name fehlt");
        }
        boolean neuer = installiert == null || online.compareTo(installiert) > 0;
        return new Stand(installiert, neuer ? online : null);
    }

    /** Liest die Versionsnummer aus dem Feld {@code tag_name} der GitHub-Antwort; {@code null}, wenn es fehlt. */
    static Version neuesteAus(String json) {
        if (json == null) {
            return null;
        }
        Matcher m = TAG.matcher(json);
        return m.find() ? Version.aus(m.group(1)) : null;
    }
}
