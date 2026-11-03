package com.example.itrain_analyse;

/**
 * Sprachfassungen des iTrain-Handbuchs auf der Download-Seite (berros.eu/download/6-0, Stand 02.11.2026).
 * Ein italienisches Handbuch gibt es dort nicht; für Italienisch wird das englische genommen.
 * Die Seitenangaben im Prompt sind nur für das deutsche Handbuch geprüft; bei anderen Fassungen sucht die KI
 * die Seiten selbst (siehe NOTES im Prompt).
 */
public enum HandbuchSprache {

    AUTOMATISCH("Passend zur Analysesprache", null, null),
    DE("Deutsch", "Handbuch", "DE"),
    NL("Nederlands", "handleiding", "NL"),
    EN("English", "manual", "EN"),
    FR("Français", "manuel", "FR"),
    ES("Español", "manual es", "ES");

    private final String anzeige;
    private final String wort;
    private final String kuerzel;

    HandbuchSprache(String anzeige, String wort, String kuerzel) {
        this.anzeige = anzeige;
        this.wort = wort;
        this.kuerzel = kuerzel;
    }

    /** Dateiname auf der Download-Seite, z. B. "iTrain 6 Handbuch.pdf". */
    public String dateiname(int hauptversion) {
        return "iTrain " + hauptversion + " " + wort + ".pdf";
    }

    public String kuerzel() {
        return kuerzel;
    }

    /** Löst AUTOMATISCH auf: im Direktmodus nach der gewählten Sprache, sonst nach der Prompt-Datei. */
    public HandbuchSprache aufloesen(Sprachgruppe gruppe, boolean direkt, String sprache) {
        if (this != AUTOMATISCH) {
            return this;
        }
        if (direkt && sprache != null) {
            return switch (sprache) {
                case "Deutsch" -> DE;
                case "Nederlands" -> NL;
                case "Français" -> FR;
                case "Español" -> ES;
                default -> EN;
            };
        }
        return gruppe == Sprachgruppe.DE_NL ? DE : EN;
    }

    public static HandbuchSprache aus(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException | NullPointerException ex) {
            return AUTOMATISCH;
        }
    }

    @Override
    public String toString() {
        return this == AUTOMATISCH ? I18n.t("handbuch.automatisch") : anzeige;
    }
}
