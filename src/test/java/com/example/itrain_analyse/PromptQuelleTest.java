package com.example.itrain_analyse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    }
}
