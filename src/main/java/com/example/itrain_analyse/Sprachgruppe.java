package com.example.itrain_analyse;

import java.util.List;

/**
 * Die Prompt-Dateien und was im Chat dazu geschrieben wird. Je Sprachgruppe gibt es den Analyse-Prompt
 * (Fortgeschrittene) und den Prompt für Anfänger, der in Runden arbeitet (Anwender 04.11.2026); der Anfänger-Prompt
 * kennt weder Direktmodus noch Zusätze, Forumsbeitrag oder Release Notes.
 */
public enum Sprachgruppe {

    DE_NL("Deutsch / Nederlands", "Prompt_Analyse_DE_NL", List.of("Deutsch", "Nederlands"),
            "Führe den Prompt aus.", "Beziehe diesen Beitrag aus dem iTrain-Forum in die Analyse ein: %s",
            "Der Text dieses Beitrags liegt als Datei %s bei.",
            "Direktmodus: Sprache %s, Zusätze %s, Format %s", "Zugtabelle", "Bahnsteige", "Signale", "und", "Nein",
            "Hinweis: Diese Dateien wurden vom Anwender bearbeitet: %s",
            "--- Zusatzauftrag ---", "--- Ende Zusatzauftrag ---", false),
    EN_FR_ES_IT("English / Français / Español / Italiano", "Prompt_Analysis_EN_FR_ES_IT",
            List.of("English", "Français", "Español", "Italiano"),
            "Run the prompt.", "Include this post from the iTrain forum in the analysis: %s",
            "The text of this post is attached as the file %s.",
            "Direct mode: Language %s, Extras %s, Format %s", "Train table", "Platforms", "Signals", "and", "No",
            "Note: these files were edited by the user: %s",
            "--- Additional task ---", "--- End of additional task ---", false),
    ANFAENGER_DE_NL("Deutsch / Nederlands", "Prompt_Anfaenger_DE_NL", List.of("Deutsch", "Nederlands"),
            "Führe den Prompt aus.", "", "", "", "", "", "", "", "",
            "Hinweis: Diese Dateien wurden vom Anwender bearbeitet: %s", "", "", true),
    ANFAENGER_EN_FR_ES_IT("English / Français / Español / Italiano", "Prompt_Beginners_EN_FR_ES_IT",
            List.of("English", "Français", "Español", "Italiano"),
            "Run the prompt.", "", "", "", "", "", "", "", "",
            "Note: these files were edited by the user: %s", "", "", true);

    public static final List<String> FORMATE = List.of("Text", "BBCode", "PDF", "RTF");

    private final String anzeige;
    private final String dateiPraefix;
    private final List<String> sprachen;
    private final String aufforderung;
    private final String forumZeile;
    private final String forumDateiZeile;
    private final String direktZeile;
    private final String zugtabelle;
    private final String bahnsteige;
    private final String signale;
    private final String und;
    private final String nein;
    private final String bearbeitetZeile;
    private final String auftragAnfang;
    private final String auftragEnde;
    private final boolean anfaenger;

    Sprachgruppe(String anzeige, String dateiPraefix, List<String> sprachen, String aufforderung,
            String forumZeile, String forumDateiZeile, String direktZeile, String zugtabelle, String bahnsteige,
            String signale, String und, String nein, String bearbeitetZeile, String auftragAnfang, String auftragEnde,
            boolean anfaenger) {
        this.anzeige = anzeige;
        this.dateiPraefix = dateiPraefix;
        this.sprachen = sprachen;
        this.aufforderung = aufforderung;
        this.forumZeile = forumZeile;
        this.forumDateiZeile = forumDateiZeile;
        this.direktZeile = direktZeile;
        this.zugtabelle = zugtabelle;
        this.bahnsteige = bahnsteige;
        this.signale = signale;
        this.und = und;
        this.nein = nein;
        this.bearbeitetZeile = bearbeitetZeile;
        this.auftragAnfang = auftragAnfang;
        this.auftragEnde = auftragEnde;
        this.anfaenger = anfaenger;
    }

    /** True für den Prompt für Anfänger (Runden, ohne Direktmodus, Zusätze, Forum und Release Notes). */
    public boolean anfaenger() {
        return anfaenger;
    }

    /** True für die Gruppe Deutsch/Niederländisch (Handbuch DE). */
    public boolean deutsch() {
        return this == DE_NL || this == ANFAENGER_DE_NL;
    }

    /** Die Gruppe derselben Sprachen für den anderen Kenntnisstand. */
    public Sprachgruppe fuer(boolean alsAnfaenger) {
        if (alsAnfaenger) {
            return deutsch() ? ANFAENGER_DE_NL : ANFAENGER_EN_FR_ES_IT;
        }
        return deutsch() ? DE_NL : EN_FR_ES_IT;
    }

    /** Die Gruppen eines Kenntnisstands. */
    public static List<Sprachgruppe> von(boolean alsAnfaenger) {
        return java.util.Arrays.stream(values()).filter(g -> g.anfaenger == alsAnfaenger).toList();
    }

    public String dateiPraefix() {
        return dateiPraefix;
    }

    public List<String> sprachen() {
        return sprachen;
    }

    /** Wert für "Zusätze" in der Direktmodus-Zeile (Prompt ab 2.3: Zugtabelle und/oder Bahnsteige, sonst Nein). */
    public String zusaetze(boolean mitZugtabelle, boolean mitBahnsteigen) {
        return zusaetze(mitZugtabelle, mitBahnsteigen, false);
    }

    /** Wie oben, ab Prompt 2.5 auch mit Signale (Zusatz 3): "Zugtabelle, Bahnsteige und Signale". */
    public String zusaetze(boolean mitZugtabelle, boolean mitBahnsteigen, boolean mitSignalen) {
        List<String> l = new java.util.ArrayList<>();
        if (mitZugtabelle) {
            l.add(zugtabelle);
        }
        if (mitBahnsteigen) {
            l.add(bahnsteige);
        }
        if (mitSignalen) {
            l.add(signale);
        }
        if (l.isEmpty()) {
            return nein;
        }
        if (l.size() == 1) {
            return l.get(0);
        }
        return String.join(", ", l.subList(0, l.size() - 1)) + " " + und + " " + l.get(l.size() - 1);
    }

    /**
     * Text für das Nachrichtenfeld: Aufforderung, optional der Forumsbeitrag, optional der Hinweis auf bearbeitete
     * Dateien, optional der Zusatzauftrag zwischen zwei Markierungszeilen (Prompt ab 2.5, Regel P11), im Direktmodus
     * zuletzt die Zeile "Direktmodus: …" (Regel P0 des Prompts erwartet sie am Ende).
     */
    public String nachricht(boolean direkt, String sprache, boolean mitZugtabelle, boolean mitBahnsteigen,
            boolean mitSignalen, String format, String forumUrl, String forumDatei, java.util.List<String> bearbeitet,
            String zusatzauftrag) {
        StringBuilder sb = new StringBuilder(aufforderung);
        if (anfaenger) {
            if (bearbeitet != null && !bearbeitet.isEmpty()) {
                sb.append('\n').append(String.format(bearbeitetZeile, String.join(", ", bearbeitet)));
            }
            return sb.toString();
        }
        if (forumUrl != null && !forumUrl.isBlank()) {
            sb.append('\n').append(String.format(forumZeile, forumUrl.trim()));
        }
        if (forumDatei != null && !forumDatei.isBlank()) {
            sb.append('\n').append(String.format(forumDateiZeile, forumDatei));
        }
        if (bearbeitet != null && !bearbeitet.isEmpty()) {
            sb.append('\n').append(String.format(bearbeitetZeile, String.join(", ", bearbeitet)));
        }
        if (zusatzauftrag != null && !zusatzauftrag.isBlank()) {
            sb.append('\n').append(auftragAnfang).append('\n').append(zusatzauftrag.strip()).append('\n')
                    .append(auftragEnde);
        }
        if (direkt) {
            sb.append('\n').append(String.format(direktZeile, sprache,
                    zusaetze(mitZugtabelle, mitBahnsteigen, mitSignalen), format));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return anzeige;
    }
}
