package com.example.itrain_analyse;

/**
 * Zusatzdateien zum Analyse-Prompt, die wie die Prompts auf dem Webserver liegen ({@code manifest.properties}) und
 * als eigene Datei in den Chat gehen. Zusatz 3 "Signale prüfen" beruht auf den "Regeln für Signale" von Berros
 * (Anwender 16.11.2026); die Datei nennt im Kopf ihre Quelle, siehe {@link SignalQuelle}.
 */
public enum Zusatzdatei {

    SIGNALE_DE_NL("Zusatz_Signale_DE_NL"),
    SIGNALE_EN_FR_ES_IT("Addon_Signals_EN_FR_ES_IT");

    private final String dateiPraefix;

    Zusatzdatei(String dateiPraefix) {
        this.dateiPraefix = dateiPraefix;
    }

    public String dateiPraefix() {
        return dateiPraefix;
    }

    /** Die Signal-Zusatzdatei zur Sprachgruppe (beide Kenntnisstände; Anfänger verwenden sie nicht). */
    public static Zusatzdatei signale(Sprachgruppe g) {
        return g.deutsch() ? SIGNALE_DE_NL : SIGNALE_EN_FR_ES_IT;
    }
}
