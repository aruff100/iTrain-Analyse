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
    void verzoegerungUnter100IstFehlerOhneEinIstKeinBefund(@TempDir Path dir) throws IOException {
        // 200 ms liegt im üblichen Bereich 100–500 ms: kein Befund
        Vorpruefung.Ergebnis e = pruefe(dir);
        assertTrue(e.befunde().stream().noneMatch(x -> x.titel().startsWith("Ausschaltverzögerung")));
        assertTrue(e.befunde().stream().noneMatch(x -> x.titel().startsWith("Einschaltverzögerung")));
        Path f = dir.resolve("Kurz.tcd");
        Files.writeString(f, TCD.replace("<feedback-delay off=\"200\" unit=\"ms\"/>", "<feedback-delay off=\"50\" unit=\"ms\"/>")
                .replace("<feedback name=\"M-B\" type=\"occupancy\">",
                        "<feedback name=\"M-B\" type=\"occupancy\"><delay off=\"600\" unit=\"ms\"/>"), StandardCharsets.UTF_8);
        e = Vorpruefung.pruefe(Anlage.lies(f), true, true, true);
        Vorpruefung.Befund b = befund(e, "Ausschaltverzögerung unter 100 ms");
        assertEquals(1, b.stufe());
        assertEquals(Vorpruefung.Art.FEHLER, b.art());
        assertEquals("0", b.tabelle().zeilen().get(0).get(2));
        assertEquals("600", befund(e, "Ausschaltverzögerung über 500 ms").tabelle().zeilen().get(0).get(3));
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
        assertEquals("–", g.tabelle().zeilen().get(0).get(4));
        assertEquals("Lok V100", g.tabelle().zeilen().get(0).get(5));
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
    void boosterBeimSpeichernAusgeschaltet(@TempDir Path dir) throws IOException {
        String boosters = "<boosters count=\"2\"><booster name=\"Booster 1\" type=\"lodi\" state=\"off\"/>"
                + "<booster name=\"Booster 2\" type=\"lodi\" state=\"on\"/></boosters>";
        int i = TCD.indexOf("<blocks");
        String tcd = TCD.substring(0, i) + boosters + TCD.substring(i);
        int b = tcd.indexOf(">", tcd.indexOf("<block name=", i)) + 1;
        tcd = tcd.substring(0, b) + "<booster name=\"Booster 1\"/>" + tcd.substring(b);
        Path f = dir.resolve("Test.tcd");
        Files.writeString(f, tcd, StandardCharsets.UTF_8);
        Vorpruefung.Befund bo = befund(Vorpruefung.pruefe(Anlage.lies(f), true, true, true),
                "Booster beim Speichern ausgeschaltet");
        assertEquals(Vorpruefung.Art.ANMERKUNG, bo.art());
        assertEquals("Booster 1", bo.tabelle().zeilen().get(0).get(0));
        assertEquals("1", bo.tabelle().zeilen().get(0).get(2));
        // Alle Booster aus: ganze Anlage abgeschaltet, kein Befund
        Files.writeString(f, tcd.replace("state=\"on\"", "state=\"off\""), StandardCharsets.UTF_8);
        assertTrue(befund(Vorpruefung.pruefe(Anlage.lies(f), true, true, true),
                "Booster beim Speichern ausgeschaltet") == null);
        assertTrue(befund(pruefe(dir), "Booster beim Speichern ausgeschaltet") == null);
    }

    @Test
    void reaktionsverzoegerungUndKeineBahnsteige(@TempDir Path dir) throws IOException {
        String tcd = TCD.replace("<platform start=\"10.0\" length=\"95.0\" side=\"right\"/>", "")
                .replace("<length unit=\"cm\">12.0</length>",
                        "<length unit=\"cm\">12.0</length><delay value=\"20000\" unit=\"ms\"/>")
                .replace("<locomotives count=\"1\">", "<locomotives count=\"3\">"
                        + "<locomotive name=\"BR 44\"><delay forward=\"1800\" backward=\"100\" unit=\"ms\"/></locomotive>"
                        + "<locomotive name=\"V60\"><delay value=\"1500\" unit=\"ms\"/></locomotive>");
        Path f = dir.resolve("Test.tcd");
        Files.writeString(f, tcd, StandardCharsets.UTF_8);
        Vorpruefung.Ergebnis e = Vorpruefung.pruefe(Anlage.lies(f), true, true, true);
        Vorpruefung.Befund fehler = befund(e, "Reaktionsverzögerung über 2000 ms");
        assertEquals(Vorpruefung.Art.FEHLER, fehler.art());
        assertEquals(java.util.List.of("V100", "20000", "20000"), fehler.tabelle().zeilen().get(0));
        assertTrue(fehler.text().contains("möglicherweise Eingabefehler, bitte manuell prüfen"));
        Vorpruefung.Befund hoch = befund(e, "Reaktionsverzögerung über 1500 ms");
        assertEquals(Vorpruefung.Art.HINWEIS, hoch.art());
        assertEquals(1, hoch.tabelle().zeilen().size());
        assertEquals(java.util.List.of("BR 44", "1800", "100"), hoch.tabelle().zeilen().get(0));
        Vorpruefung.Befund bs = befund(e, "Keine Bahnsteige eingetragen");
        assertEquals(2, bs.stufe());
        assertTrue(bs.text().contains("1 Bahnhofsblock)"));
        assertTrue(befund(pruefe(dir), "Keine Bahnsteige eingetragen") == null);
    }

    @Test
    void melderInZweiBloeckenUndEinseitigeVerbindung(@TempDir Path dir) throws IOException {
        String tcd = TCD.replace("<feedbacks count=\"2\">", "<feedbacks count=\"3\">"
                        + "<feedback name=\"M-C\"><interface name=\"Z21\" address=\"5\"/></feedback>")
                .replace("<block name=\"C\"><length unit=\"cm\">100.0</length>",
                        "<block name=\"C\"><length unit=\"cm\">100.0</length><feedbacks count=\"1\" unit=\"cm\">"
                                + "<feedback name=\"M-B\" position=\"0.0\"/></feedbacks>")
                .replace("<next><side-block/></next>", "<next><side-block name=\"A\"/></next>");
        Path f = dir.resolve("Test.tcd");
        Files.writeString(f, tcd, StandardCharsets.UTF_8);
        Vorpruefung.Ergebnis e = Vorpruefung.pruefe(Anlage.lies(f), true, true, true);
        Vorpruefung.Befund m = befund(e, "Rückmelder in mehreren Blöcken");
        assertEquals(Vorpruefung.Art.FEHLER, m.art());
        assertEquals(java.util.List.of("M-B", "B, C"), m.tabelle().zeilen().get(0));
        assertTrue(m.text().contains("M-C"));
        Vorpruefung.Befund v = befund(e, "Blockverbindung nur in einer Richtung");
        assertEquals(java.util.List.of("D", "Nächste", "A (ohne Seite)", "A nennt D in keiner Richtung"),
                v.tabelle().zeilen().get(0));
        assertFalse(v.text().contains("Block A nennt B"));
    }

    @Test
    void dinamoAnalogLokWeichenblockUndTaste(@TempDir Path dir) throws IOException {
        String tcd = TCD.replace("<feedbacks count=\"2\">",
                        "<feedbacks count=\"3\"><feedback name=\"T1\" type=\"button\"><interface name=\"Z21\"/></feedback>")
                .replace("<blocks count=\"4\">", "<blocks count=\"5\"><block name=\"w18\" type=\"turnout\"><prev/><next/></block>")
                .replace("<locomotive name=\"V100\" gauge=\"n\"><interface name=\"Z21\" address=\"3\"/>",
                        "<locomotive name=\"V100\" gauge=\"n\"><interface name=\"Z21\"/><decoder protocol=\"analog\" steps=\"63\"/>");
        Path f = dir.resolve("Test.tcd");
        Files.writeString(f, tcd, StandardCharsets.UTF_8);
        Vorpruefung.Ergebnis e = Vorpruefung.pruefe(Anlage.lies(f), true, true, true);
        assertTrue(befund(e, "Aktive Loks ohne Adresse") == null);
        assertTrue(befund(e, "Blöcke ohne Länge") == null);
        assertTrue(befund(e, "Rückmelder ohne Adresse") == null);
        assertEquals(Vorpruefung.Art.ANMERKUNG, befund(e, "Tasten und Sensoren ohne Adresse").art());
    }

    @Test
    void bidibKnotenkennungUndZubehoerOhneAdresse(@TempDir Path dir) throws IOException {
        String tcd = TCD.replace("<blocks count=\"4\">", "<accessories count=\"6\">"
                        + "<relay name=\"R_KSM Wa\"><id>0xED1D00680D00DA00</id><interface name=\"BIDIB\" address=\"197\"/></relay>"
                        + "<relay name=\"R_KSM Wi\"><id>0xED1D00680D00DA00</id><interface name=\"BIDIB\" address=\"193\"/></relay>"
                        + "<sound name=\"Sound 1\" file=\"welcome.wav\"><interface name=\"Z21\"/></sound>"
                        + "<turnout name=\"Kreuzung\" type=\"cross-fixed\"><interface name=\"Z21\"/></turnout>"
                        + "<turnout name=\"W9\" type=\"left\"><interface name=\"Z21\"/></turnout>"
                        + "<turnout name=\"W10\" type=\"left\"><interface name=\"Z21\"><address>70</address><address>71</address></interface></turnout>"
                        + "</accessories><blocks count=\"4\">");
        Path f = dir.resolve("Test.tcd");
        Files.writeString(f, tcd, StandardCharsets.UTF_8);
        Vorpruefung.Ergebnis e = Vorpruefung.pruefe(Anlage.lies(f), true, true, true);
        assertTrue(befund(e, "Doppelte Zubehör-Adressen") == null);
        Vorpruefung.Befund oa = befund(e, "Zubehör ohne Adresse");
        assertEquals(Vorpruefung.Art.ANMERKUNG, oa.art());
        assertTrue(oa.text().endsWith(": W9 (turnout)."));
        assertEquals("70+71", Anlage.lies(f).zubehoer.stream().filter(z -> z.name().equals("W10")).findFirst()
                .orElseThrow().adresse());
    }

    @Test
    void signalfelderRelaisDoppelteAdresseInaktiveRoute(@TempDir Path dir) throws IOException {
        String tcd = TCD.replace("<blocks count=\"4\">", "<accessories count=\"4\">"
                        + "<signal name=\"S1\" type=\"de_hp0_1_2_sh1\"><interface name=\"Z21\" address=\"20\"/></signal>"
                        + "<relay name=\"Merker\"><interface name=\"Z21\"/></relay>"
                        + "<turnout name=\"W1\"><interface name=\"Z21\" address=\"7\"/><options/></turnout>"
                        + "<turnout name=\"W2\"><interface name=\"Z21\" address=\"7\"/><options><option>addr_duplicate</option></options></turnout>"
                        + "</accessories><blocks count=\"4\">")
                .replace("<next><positions unit=\"cm\" clearance=\"0.0\" brake=\"10.0\" stop=\"-5.0\"><platform",
                        "<next><shunt-signal name=\"S1\"/><signal name=\"S1\"/><positions unit=\"cm\" clearance=\"0.0\" brake=\"10.0\" stop=\"-5.0\"><platform")
                .replace("<route name=\"R2\">", "<route name=\"R9\" active=\"false\"><items count=\"0\"/></route><route name=\"R2\">");
        Path f = dir.resolve("Test.tcd");
        Files.writeString(f, tcd, StandardCharsets.UTF_8);
        Vorpruefung.Ergebnis e = Vorpruefung.pruefe(Anlage.lies(f), true, true, true);
        Vorpruefung.Befund sf = befund(e, "Signal im falschen Feld des Blockeditors");
        assertEquals(java.util.List.of("S1", "de_hp0_1_2_sh1", "A", "Nächste", "Rangiersignal",
                "Mehrfach-Signal gehört nur in das Feld Hauptsignal"), sf.tabelle().zeilen().get(0));
        assertEquals(1, sf.tabelle().zeilen().size());
        assertEquals(0, befund(e, "Relais ohne Adresse").stufe());
        assertTrue(befund(e, "Doppelte Zubehör-Adressen") == null);
        assertTrue(e.ohneBefund().stream().anyMatch(o -> o.startsWith("1 inaktive Routen nicht geprüft: R9")));
    }

    @Test
    void weiterePruefungenAusKiVergleich(@TempDir Path dir) throws IOException {
        String tcd = TCD.replace("<prev><side-block name=\"A\" side=\"next\"/></prev>\n",
                        "<prev><side-block name=\"A\" side=\"next\"/></prev>\n<options><option>short</option></options>\n")
                .replace("<next><side-block><turnout name=\"W1\">",
                        "<next><positions unit=\"cm\" clearance=\"0.0\" brake=\"80.0\" stop=\"40.0\"/><side-block><turnout name=\"W1\">")
                .replace("<block name=\"D\" type=\"siding\">", "<block name=\"D\" type=\"siding\" active=\"false\">")
                .replace("</blocks>", "<block name=\"E \"><length unit=\"cm\">50.0</length><prev/><next/></block></blocks>")
                .replace("<permissions access-type", "<functions><function name=\"Licht\"/></functions><permissions access-type")
                .replace("<routes count=\"1\"><route name=\"R2\"/></routes></train>",
                        "<routes count=\"1\"><route name=\"R2\"/></routes><actions><start>"
                                + "<train-function name=\"Pfeife\" state=\"on\"/><train-function name=\"Licht vorn\" state=\"on\"/>"
                                + "<train-function name=\"Glocke\" state=\"on\" active=\"false\"/>"
                                + "</start></actions></train>");
        Path f = dir.resolve("Test.tcd");
        Files.writeString(f, tcd, StandardCharsets.UTF_8);
        Vorpruefung.Ergebnis e = Vorpruefung.pruefe(Anlage.lies(f), true, true, true);
        Vorpruefung.Befund fn = befund(e, "Funktionen in Aktionen, die kein Fahrzeug des Zuges hat");
        assertEquals(java.util.List.of(java.util.List.of("Zug 1", "Pfeife", "Start/Stopp des Zuges")), fn.tabelle().zeilen());
        Vorpruefung.Befund br = befund(e, "Bremsposition hinter oder auf der Stopp-Position");
        assertEquals("B", br.tabelle().zeilen().get(0).get(0));
        assertEquals(1, br.tabelle().zeilen().size());
        Vorpruefung.Befund in = befund(e, "Routen mit inaktiven Blöcken");
        assertEquals(1, in.stufe());
        assertEquals("R2", in.tabelle().zeilen().get(0).get(0));
        assertTrue(befund(e, "Blöcke ohne jede Verbindung").text().contains("E "));
        assertTrue(befund(e, "Option \"Kurzer Block\" ohne NS- oder NMBS-Signale").text().contains("B"));
        assertTrue(befund(e, "Namen mit Leerzeichen am Anfang oder Ende").text().contains("Block 'E '"));
    }

    @Test
    void traegheitSchrittweiteOffset(@TempDir Path dir) throws IOException {
        String tcd = TCD.replace("<length unit=\"cm\">12.0</length>",
                "<decoder protocol=\"dcc\" steps=\"126\"/><configuration count=\"2\"><parameter nr=\"3\" value=\"60\" type=\"acceleration\"/>"
                        + "<parameter nr=\"4\" value=\"11\" type=\"deceleration\"/></configuration><length unit=\"cm\">12.0</length>"
                        + "<feedback count=\"1\" unit=\"cm\"><offset type=\"occupancy\" front=\"0.0\" rear=\"0.0\"/></feedback>"
                        + "<acceleration delay=\"50\" unit=\"ms\" step=\"1\"/><deceleration delay=\"200\" unit=\"ms\" step=\"1\"/>"
                        + "<speed-control count=\"1\" unit=\"km_h\"><speed step=\"1\" value=\"5\"/></speed-control>");
        Path f = dir.resolve("Test.tcd");
        Files.writeString(f, tcd, StandardCharsets.UTF_8);
        Vorpruefung.Ergebnis e = Vorpruefung.pruefe(Anlage.lies(f), true, true, true);
        assertEquals(java.util.List.of(java.util.List.of("V100", "50", "200")),
                befund(e, "Trägheitssimulation unter 100 ms").tabelle().zeilen());
        assertTrue(befund(e, "Kleine Stufenschrittweite bei 126 Fahrstufen") == null);
        assertEquals(Vorpruefung.Art.FEHLER, befund(e, "Aktive Loks ohne Rückmeldungs-Offset").art());
        assertTrue(befund(e, "Hohe Anfahr- und Bremsverzögerung im Decoder").text().contains("V100 (CV3=60, CV4=11)"));
    }

    @Test
    void nurPunktmelderNurBeiITrain5(@TempDir Path dir) throws IOException {
        String tcd = TCD.replace("<feedback name=\"M-B\" type=\"occupancy\">", "<feedback name=\"M-B\" type=\"reed\">");
        Path f = dir.resolve("Test.tcd");
        Files.writeString(f, tcd, StandardCharsets.UTF_8);
        assertTrue(befund(Vorpruefung.pruefe(Anlage.lies(f), true, true, true), "Blöcke nur mit Punktmeldern") == null);
        Files.writeString(f, tcd.replace("version=\"6.0.4-b300\"", "version=\"5.1.6\""), StandardCharsets.UTF_8);
        Vorpruefung.Befund b = befund(Vorpruefung.pruefe(Anlage.lies(f), true, true, true), "Blöcke nur mit Punktmeldern");
        assertTrue(b.text().endsWith(": B."));
    }

    @Test
    void pruefungenAusKatoAnalyse(@TempDir Path dir) throws IOException {
        StringBuilder bloecke = new StringBuilder();
        for (int i = 1; i <= 6; i++) {
            bloecke.append("<block name=\"E").append(i).append("\" polarity=\"normal\"/>");
        }
        bloecke.append("<block name=\"F\" polarity=\"unknown\"/><block name=\"Kehrschleife\" polarity=\"unknown\"/>")
                .append("<block name=\"G\" type=\"station\"/>");
        String tcd = TCD.replace("<train gauge=\"n\"/>",
                        "<train gauge=\"n\"><options><option>correct_with_railcom</option></options></train>")
                .replace("</blocks>", bloecke + "</blocks>")
                .replace("<block name=\"D\" type=\"siding\"><length unit=\"cm\">60.0</length>",
                        "<block name=\"D\" type=\"siding\"><length unit=\"cm\">60.0</length>"
                                + "<options><option>no_dir_change</option></options>")
                .replace("<permissions access-type", "<functions><function name=\"Licht\"/></functions><permissions access-type")
                .replace("</control-items>", "<actions count=\"3\">"
                        + "<action name=\"Mühle\"><condition><random percentage=\"50\"/></condition><items/></action>"
                        + "<action name=\"Burg\"><items><action name=\"Burg\"/></items></action>"
                        + "<action name=\"Pfiff\"><items><locomotive-function name=\"Pfeife\" state=\"on\">"
                        + "<locomotive name=\"V100\"/></locomotive-function></items></action>"
                        + "</actions></control-items>");
        Path f = dir.resolve("Test.tcd");
        Files.writeString(f, tcd, StandardCharsets.UTF_8);
        Vorpruefung.Ergebnis e = Vorpruefung.pruefe(Anlage.lies(f), true, true, true);
        assertTrue(befund(e, "„Zufällig“ als einzige Bedingung").text().endsWith(": Mühle."));
        assertTrue(befund(e, "Aktion ruft sich selbst auf").text().endsWith(": Burg."));
        assertTrue(befund(e, "Polarität „Unbekannt“ bei aktiver RailCom-Korrektur").text().endsWith(": F."));
        assertTrue(befund(e, "Stumpfgleis mit „Richtungswechsel verboten“").text().endsWith(": D."));
        Vorpruefung.Befund bs = befund(e, "Bahnhofsblöcke ohne Bahnsteig");
        assertEquals(Vorpruefung.Art.ANMERKUNG, bs.art());
        assertTrue(bs.text().endsWith(": G."));
        assertTrue(befund(e, "Funktionen in Aktionen, die kein Fahrzeug des Zuges hat").tabelle().zeilen()
                .contains(java.util.List.of("Lok V100", "Pfeife", "Aktion „Pfiff“")));
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
