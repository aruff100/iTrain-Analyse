package com.example.itrain_analyse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PromptQuelleTest {

    @Test
    void aeltereServerfassungWirdErkannt() {
        assertTrue(PromptQuelle.aelter("Prompt_Analyse_DE_NL_v2.2.txt", "Prompt_Analyse_DE_NL_v2.3.txt"));
        assertFalse(PromptQuelle.aelter("Prompt_Analyse_DE_NL_v2.3.txt", "Prompt_Analyse_DE_NL_v2.3.txt"));
        assertFalse(PromptQuelle.aelter("Prompt_Analyse_DE_NL_v2.10.txt", "Prompt_Analyse_DE_NL_v2.9.txt"));
        assertFalse(PromptQuelle.aelter("Prompt_ohne_Version.txt", "Prompt_Analyse_DE_NL_v2.3.txt"));
    }

    @Test
    void zusaetzeInDerDirektmodusZeile() {
        assertEquals("Zugtabelle und Bahnsteige", Sprachgruppe.DE_NL.zusaetze(true, true));
        assertEquals("Bahnsteige", Sprachgruppe.DE_NL.zusaetze(false, true));
        assertEquals("Nein", Sprachgruppe.DE_NL.zusaetze(false, false));
        assertEquals("Train table", Sprachgruppe.EN_FR_ES_IT.zusaetze(true, false));
        assertEquals("Zugtabelle, Bahnsteige und Signale", Sprachgruppe.DE_NL.zusaetze(true, true, true));
        assertEquals("Signale", Sprachgruppe.DE_NL.zusaetze(false, false, true));
        assertEquals("Platforms and Signals", Sprachgruppe.EN_FR_ES_IT.zusaetze(false, true, true));
    }

    @Test
    void zusatzauftragStehtVorDerDirektmodusZeile() {
        String n = Sprachgruppe.DE_NL.nachricht(true, "Deutsch", false, false, true, "PDF", "", null, List.of(),
                "Prüfe Relais R12.");
        assertEquals("Führe den Prompt aus.\n--- Zusatzauftrag ---\nPrüfe Relais R12.\n--- Ende Zusatzauftrag ---\n"
                + "Direktmodus: Sprache Deutsch, Zusätze Signale, Format PDF", n);
        assertFalse(Sprachgruppe.DE_NL.nachricht(false, "Deutsch", false, false, false, "Text", "", null, List.of(),
                "  ").contains("Zusatzauftrag"));
    }

    @Test
    void signalQuelleKopfUndNeuereFassung() {
        SignalQuelle.Kopf k = SignalQuelle.kopf("ZUSATZ\nQuelle: https://berros.eu/download/tutorial/"
                + "Rules_For_iTrain_Signals_v01_DE.pdf\nQuelle-Stand: 2024-10-13T12:14:03Z, 2264076 Bytes\n");
        assertEquals("https://berros.eu/download/tutorial/Rules_For_iTrain_Signals_v01_DE.pdf", k.adresse());
        assertEquals(2264076L, k.groesse());
        assertEquals(java.time.Instant.parse("2024-10-13T12:14:03Z"), k.stand());
        String html = "<a href=\"Rules_For_iTrain_Signals_v01_UK.pdf\"></a><a href=\"Rules_For_iTrain_Signals_v01_DE.pdf\">"
                + "<a href=\"Rules_For_iTrain_Signals_v02_NL.pdf\"><a href=\"Video_07.pdf\">";
        assertNull(SignalQuelle.neuereFassung(k.adresse(), html));
        assertEquals("Rules_For_iTrain_Signals_v02_DE.pdf", SignalQuelle.neuereFassung(k.adresse(),
                html + "<a href=\"Rules_For_iTrain_Signals_v02_DE.pdf\">"));
        assertNull(SignalQuelle.kopf("ohne Kopf"));
    }

    @Test
    void zusatzdateienImManifest() {
        assertTrue(PromptQuelle.eintraege().stream().anyMatch(e -> e.schluessel().equals("SIGNALE_DE_NL")
                && e.praefix().equals("Zusatz_Signale_DE_NL")));
        assertEquals(Zusatzdatei.SIGNALE_EN_FR_ES_IT, Zusatzdatei.signale(Sprachgruppe.EN_FR_ES_IT));
    }
}
