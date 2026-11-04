package com.example.itrain_analyse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;

/**
 * Prüft, ob es eine neuere Programmversion gibt.
 *
 * <p>Quelle ist das neueste veröffentlichte GitHub-Release. Abgefragt wird die Release-Seite
 * {@code github.com/…/releases/latest}, die auf {@code …/releases/tag/v1.1} weiterleitet; die Versionsnummer steht im
 * letzten Teil dieser Adresse. Die GitHub-API wird bewusst nicht benutzt, weil sie ohne Anmeldung nur 60 Abfragen
 * je Stunde und Internetadresse erlaubt und danach mit HTTP 403 antwortet (Anwender 04.11.2026). Entwürfe und
 * Vorabversionen zählen nicht. Dem Anwender wird nur die Webseite itrainplus.de angezeigt, nicht GitHub.
 */
public final class ProgrammUpdate {

    static final URI LATEST = URI.create("https://github.com/aruff100/iTrain-Analyse/releases/latest");
    static final URI WEBSEITE = URI.create("https://itrainplus.de/analyse.html");

    private static final String TAG_PFAD = "/releases/tag/";

    /** Ergebnis der Prüfung. {@code neu} ist {@code null}, wenn die installierte Version aktuell ist. */
    public record Stand(Version installiert, Version neu) {
    }

    private ProgrammUpdate() {
    }

    /** Fragt das neueste Release ab und vergleicht es mit der installierten Version. */
    public static Stand pruefe() throws IOException {
        Version installiert = Version.aus(AppVersion.text());
        HttpResponse<Void> a = Http.kopf(LATEST);
        if (a.statusCode() != 200) {
            throw new IOException("HTTP " + a.statusCode() + " " + LATEST);
        }
        Version online = ausAdresse(a.uri());
        if (online == null) {
            throw new IOException("keine Version in " + a.uri());
        }
        boolean neuer = installiert == null || online.compareTo(installiert) > 0;
        return new Stand(installiert, neuer ? online : null);
    }

    /** Liest die Versionsnummer aus einer Adresse wie {@code …/releases/tag/v1.1}; sonst {@code null}. */
    static Version ausAdresse(URI uri) {
        if (uri == null || uri.getPath() == null) {
            return null;
        }
        String pfad = uri.getPath();
        int i = pfad.indexOf(TAG_PFAD);
        return i < 0 ? null : Version.aus(pfad.substring(i + TAG_PFAD.length()));
    }
}
