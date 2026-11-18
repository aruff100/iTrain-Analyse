package com.example.itrain_analyse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VorpruefungTest {

    /** Kleine Anlage: Strecke A - B - C, Abstellgleis D an C. Rückmelder mit 200 ms Ausschaltverzögerung. */
    private static final String TCD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <train-control xmlns="http://www.berros.eu/xml/train" version="6.0.4-b300">
              <settings><scale>87.0</scale><train gauge="n"/></settings>
              <control-items>
                <interfaces count="1">
                  <interface name="Z21" type="z21"><feedback-delay off="200" unit="ms"/></interface>
                </interfaces>
                <feedbacks count="2">
                  <feedback name="M-A" type="occupancy"><interface name="Z21" address="1"/><length unit="cm">100.0</length></feedback>
                  <feedback name="M-B" type="occupancy"><interface name="Z21" address="1"/><length unit="cm">80.0</length></feedback>
                </feedbacks>
                <blocks count="4">
                  <block name="A" type="station"><length unit="cm">100.0</length>
                    <feedbacks count="1" unit="cm"><feedback name="M-A" position="0.0"/></feedbacks>
                    <prev><positions unit="cm" clearance="0.0" brake="10.0" stop="-5.0"/><side-block/></prev>
                    <next><positions unit="cm" clearance="0.0" brake="10.0" stop="-5.0"><platform start="10.0" length="95.0" side="right"/></positions>
                      <side-block name="B" side="prev"/></next>
                  </block>
                  <block name="B"><length unit="cm">100.0</length>
                    <feedbacks count="1" unit="cm"><feedback name="M-B" position="0.0"/></feedbacks>
                    <prev><side-block name="A" side="next"/></prev>
                    <next><side-block><turnout name="W1"><block name="C" side="prev"/></turnout></side-block></next>
                  </block>
                  <block name="C"><length unit="cm">100.0</length>
                    <prev><side-block name="B" side="next"/></prev>
                    <next><side-block name="D" side="prev"/></next>
                  </block>
                  <block name="D" type="siding"><length unit="cm">60.0</length>
                    <prev><side-block name="C" side="next"/></prev>
                    <next><side-block/></next>
                  </block>
                </blocks>
                <train-routes count="2">
                  <route name="R1">
                    <items count="2">
                      <block name="A" side="next" marker="start"><options/></block>
                      <block name="C" side="next" marker="finish"><options><option>strict</option></options></block>
                    </items>
                    <repeat count="1"/><direction-change>forbidden</direction-change><options/>
                  </route>
                  <route name="R2">
                    <items count="2">
                      <block name="A" side="next"><options/></block>
                      <block name="D" side="next"><options/></block>
                    </items>
                    <repeat count="1"/><direction-change>forbidden</direction-change><options/>
                  </route>
                </train-routes>
                <locomotives count="1">
                  <locomotive name="V100" gauge="n"><interface name="Z21" address="3"/><length unit="cm">12.0</length>
                    <permissions access-type="forbidden"><block name="D"/></permissions></locomotive>
                </locomotives>
                <trains count="1">
                  <train name="Zug 1"><composition count="1"><locomotive name="V100"/><item length="20.0" unit="cm"/></composition>
                    <routes count="1"><route name="R2"/></routes></train>
                </trains>
              </control-items>
            </train-control>
            """;

    private Vorpruefung.Ergebnis pruefe(Path dir) throws IOException {
        Path f = dir.resolve("Test.tcd");
        Files.writeString(f, TCD, StandardCharsets.UTF_8);
        return Vorpruefung.pruefe(Anlage.lies(f), true, true, true);
    }

    private static Vorpruefung.Befund befund(Vorpruefung.Ergebnis e, String titel) {
        return e.befunde().stream().filter(b -> b.titel().equals(titel)).findFirst().orElse(null);
    }

    @Test
    void verzoegerungUnter250IstFehlerOhneEinIstKeinBefund(@TempDir Path dir) throws IOException {
        Vorpruefung.Ergebnis e = pruefe(dir);
        Vorpruefung.Befund b = befund(e, "Ausschaltverzögerung unter 250 ms");
        assertEquals(1, b.stufe());
        assertEquals(Vorpruefung.Art.FEHLER, b.art());
        assertEquals("0", b.tabelle().zeilen().get(0).get(2));
        assertTrue(e.befunde().stream().noneMatch(x -> x.titel().startsWith("Einschaltverzögerung")));
    }

    @Test
    void doppelteAdresseUndMelderlaenge(@TempDir Path dir) throws IOException {
        Vorpruefung.Ergebnis e = pruefe(dir);
        assertTrue(befund(e, "Doppelte Rückmelder-Adressen").tabelle().zeilen().get(0).get(2).contains("M-A"));
        assertEquals("B", befund(e, "Melderlängen passen nicht zur Blocklänge").tabelle().zeilen().get(0).get(0));
    }

    @Test
    void striktOhneDirektenNachbarnUndErlaubnis(@TempDir Path dir) throws IOException {
        Vorpruefung.Ergebnis e = pruefe(dir);
        Vorpruefung.Befund r = befund(e, "Routen nicht durchführbar");
        assertEquals("R1", r.tabelle().zeilen().get(0).get(0));
        assertEquals("A → B → C", r.tabelle().zeilen().get(0).get(3));
        Vorpruefung.Befund g = befund(e, "Route durch Erlaubnis gesperrt");
        assertEquals("R2", g.tabelle().zeilen().get(0).get(0));
        assertEquals("Lok V100", g.tabelle().zeilen().get(0).get(4));
        assertTrue(befund(e, "Überflüssige Markierungen in Routen").text().contains("R1"));
        assertEquals(2, e.routenGeprueft());
    }

    @Test
    void verbindungenUeberWeichenUndZusaetze(@TempDir Path dir) throws IOException {
        Vorpruefung.Ergebnis e = pruefe(dir);
        assertTrue(e.befunde().stream().noneMatch(b -> b.titel().startsWith("Blockverbindung")));
        assertEquals(java.util.List.of("Zug 1", "–", "12", "20", "32", "2"), e.zugtabelle().zeilen().get(0));
        // Bahnsteig 10–105 cm, Block 100 cm, Stopp -5 = 95 cm: ragt über das Blockende, ganz am Bahnsteig 85 cm
        var z = e.bahnsteige().zeilen().get(0);
        assertEquals("85", z.get(6));
        assertTrue(z.get(8).contains("ragt über das Blockende"));
        String text = Vorpruefung.alsText(e, "1.4");
        assertTrue(text.contains("V1 | Stufe 1 | Fehler"));
        assertFalse(text.contains("null"));
    }

    @Test
    void reaktionsverzoegerungUndKeineBahnsteige(@TempDir Path dir) throws IOException {
        String tcd = TCD.replace("<platform start=\"10.0\" length=\"95.0\" side=\"right\"/>", "")
                .replace("<length unit=\"cm\">12.0</length>",
                        "<length unit=\"cm\">12.0</length><delay value=\"20000\" unit=\"ms\"/>")
                .replace("<locomotives count=\"1\">", "<locomotives count=\"3\">"
                        + "<locomotive name=\"BR 44\"><delay forward=\"1000\" backward=\"100\" unit=\"ms\"/></locomotive>"
                        + "<locomotive name=\"V60\"><delay value=\"500\" unit=\"ms\"/></locomotive>");
        Path f = dir.resolve("Test.tcd");
        Files.writeString(f, tcd, StandardCharsets.UTF_8);
        Vorpruefung.Ergebnis e = Vorpruefung.pruefe(Anlage.lies(f), true, true, true);
        Vorpruefung.Befund fehler = befund(e, "Reaktionsverzögerung über 2000 ms");
        assertEquals(Vorpruefung.Art.FEHLER, fehler.art());
        assertEquals(java.util.List.of("V100", "20000", "20000"), fehler.tabelle().zeilen().get(0));
        Vorpruefung.Befund hoch = befund(e, "Reaktionsverzögerung über 500 ms");
        assertEquals(Vorpruefung.Art.HINWEIS, hoch.art());
        assertEquals(1, hoch.tabelle().zeilen().size());
        assertEquals(java.util.List.of("BR 44", "1000", "100"), hoch.tabelle().zeilen().get(0));
        Vorpruefung.Befund bs = befund(e, "Keine Bahnsteige eingetragen");
        assertEquals(2, bs.stufe());
        assertTrue(bs.text().contains("1 Bahnhofsblöcke"));
        assertTrue(befund(pruefe(dir), "Keine Bahnsteige eingetragen") == null);
    }

    @Test
    void pruefberichtWirdGeschrieben(@TempDir Path dir) throws IOException {
        Path pdf = Pruefbericht.schreibe(pruefe(dir), dir, "1.4");
        assertTrue(Files.size(pdf) > 1000);
        assertTrue(pdf.getFileName().toString().startsWith("Pruefbericht_Test"));
    }

    @Test
    void seitenangabenUndAuszug(@TempDir Path dir) throws IOException {
        assertEquals(Set.of(34, 94, 115, 116, 50, 54, 158), HandbuchAuszug.seitenAus(
                "(Quelle: Handbuch S. 34, S. 94) S. 115–116; S. 50, 54 und p. 158"));
        StringBuilder hb = new StringBuilder("Vorspann\n");
        for (int i = 1; i <= 252; i++) {
            hb.append("\n===== Seite ").append(i).append(" von 252 =====\nInhalt ").append(i).append('\n');
        }
        Path voll = dir.resolve("iTrain_6.0_Handbuch_DE.txt");
        Files.writeString(voll, hb.toString(), StandardCharsets.UTF_8);
        assertTrue(HandbuchAuszug.moeglich(voll));
        HandbuchAuszug.Ergebnis a = HandbuchAuszug.schreibe(voll, Set.of(34, 117), true);
        String t = Files.readString(a.datei(), StandardCharsets.UTF_8);
        assertEquals(3, a.seiten());
        assertTrue(t.contains("===== Seite 34 von 252 =====\nInhalt 34"));
        assertTrue(t.contains("===== Seite 1 von 252 ====="));
        assertFalse(t.contains("Inhalt 35"));
    }
}
