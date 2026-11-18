package com.example.itrain_analyse;

import java.nio.file.Path;
import java.util.prefs.Preferences;

/** Dauerhafte Einstellungen über die Java-Preferences-API (unter Windows in der Registry des Anwenders). */
public final class Einstellungen {

    public static final String PROMPT_QUELLE = "promptQuelle";
    public static final String PROMPT_ADRESSE = "promptAdresse";
    public static final String PROMPT_ORDNER = "quellordner";
    public static final String LAYOUT_ORDNER = "layoutOrdner";
    public static final String LETZTES_LAYOUT = "projektdatei";
    public static final String DOCS_ORDNER = "docsOrdner";
    public static final String ARBEITSORDNER = "arbeitsordner";
    public static final String HANDBUCH_ADRESSE = "handbuchAdresse";
    public static final String HANDBUCH_SPRACHE = "handbuchSprache";
    /** Nur die benötigten Handbuchseiten in den Chat geben (Anwender 18.11.2026); Vorgabe ja. */
    public static final String HANDBUCH_AUSZUG = "handbuchAuszug";
    /** Prüfbericht des Programms als PDF ohne KI (Variante B, Anwender 18.11.2026). */
    public static final String PRUEFBERICHT = "pruefbericht";
    public static final String FARBEN = "farben";
    public static final String MODUS = "modus";
    public static final String UI_SPRACHE = "uiSprache";
    public static final String FORUM_URL = "forumUrl";

    /** Prompt-Quelle: Webserver (Vorgabe) oder lokaler Ordner. */
    public static final String QUELLE_SERVER = "server";
    public static final String QUELLE_ORDNER = "ordner";

    /** Ordner mit Prompts und manifest.properties auf itrainplus.de (Anwender 03.11.2026). */
    public static final String STANDARD_PROMPT_ADRESSE = "https://itrainplus.de/analyse-daten/";

    /** Download-Seite des aktuellen Handbuchs (Anwender 02.11.2026). */
    public static final String STANDARD_HANDBUCH_ADRESSE = "https://berros.eu/download/6-0/";

    private static final Preferences P = Preferences.userNodeForPackage(Einstellungen.class);

    private Einstellungen() {
    }

    public static String get(String schluessel, String vorgabe) {
        return P.get(schluessel, vorgabe);
    }

    public static void set(String schluessel, String wert) {
        P.put(schluessel, wert == null ? "" : wert);
    }

    public static boolean getBool(String schluessel, boolean vorgabe) {
        return P.getBoolean(schluessel, vorgabe);
    }

    public static void setBool(String schluessel, boolean wert) {
        P.putBoolean(schluessel, wert);
    }

    /** Vorgabe für den Arbeitsordner: Benutzerordner/iTrain-Analyse. */
    public static String standardArbeitsordner() {
        return Path.of(System.getProperty("user.home"), "iTrain-Analyse").toString();
    }

    /** Vorgabe für die Layout-Dateien: Benutzerordner/iTrain/layouts (unter Windows C:\Users\<Name>\iTrain\layouts). */
    public static String standardLayoutOrdner() {
        return Path.of(System.getProperty("user.home"), "iTrain", "layouts").toString();
    }

    public static Path pfad(String schluessel, String vorgabe) {
        String t = get(schluessel, vorgabe).trim();
        return t.isEmpty() ? null : Path.of(t);
    }

    public static Path arbeitsordner() {
        Path p = pfad(ARBEITSORDNER, standardArbeitsordner());
        return p == null ? Path.of(standardArbeitsordner()) : p;
    }

    public static Path cache() {
        return arbeitsordner().resolve(".cache");
    }

    public static Path layoutOrdner() {
        return pfad(LAYOUT_ORDNER, standardLayoutOrdner());
    }

    /** iTrain-docs-Ordner aus den Einstellungen, sonst automatisch gesucht. */
    public static Path docsOrdner() {
        Path p = pfad(DOCS_ORDNER, "");
        return p != null ? p : ReleaseNotes.findeDocsOrdner();
    }

    public static boolean promptVomServer() {
        return !QUELLE_ORDNER.equals(get(PROMPT_QUELLE, QUELLE_SERVER));
    }

    public static String promptAdresse() {
        String a = get(PROMPT_ADRESSE, STANDARD_PROMPT_ADRESSE).trim();
        return a.isEmpty() ? STANDARD_PROMPT_ADRESSE : a;
    }

    public static String handbuchAdresse() {
        String a = get(HANDBUCH_ADRESSE, STANDARD_HANDBUCH_ADRESSE).trim();
        return a.isEmpty() ? STANDARD_HANDBUCH_ADRESSE : a;
    }

    public static boolean dunkel() {
        return "dunkel".equals(get(FARBEN, "hell"));
    }
}
