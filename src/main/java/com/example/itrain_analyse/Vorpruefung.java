package com.example.itrain_analyse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Vorprüfung im Programm (Anwender 18.11.2026, Weg 3, Variante A mit Prüfbericht B als Option): alles aus dem
 * Prüfkatalog des Prompts, was sich nach festen Regeln aus der Projektdatei rechnen lässt. Das Ergebnis geht als
 * {@code Vorpruefung.txt} an die KI (Regel P12 ab Prompt 2.7) und auf Wunsch als PDF an den Anwender
 * ({@link Pruefbericht}). Bewertung und Wortlaut folgen dem Prompt; die KI kann einen Befund mit Begründung anders
 * einstufen. Texte deutsch oder englisch nach der Sprachgruppe des Prompts.
 */
public final class Vorpruefung {

    public enum Art { FEHLER, HINWEIS, ANMERKUNG }

    /** stufe 1–4, 0 = Anmerkungen. */
    public record Befund(int stufe, Art art, String titel, String text, String quelle, boolean vermutlich,
            Tabelle tabelle) {
    }

    public record Tabelle(List<String> kopf, List<List<String>> zeilen) {
    }

    /** Ergebnis: Eingangscheck (Bezeichnung, Wert), Befunde, Sätze "ohne Befund", Zusätze. */
    public record Ergebnis(boolean deutsch, String datei, String version, List<String[]> eingangscheck,
            List<Befund> befunde, List<String> ohneBefund, Tabelle zugtabelle, List<String> zugHinweise,
            Tabelle bahnsteige, List<String> bahnsteigHinweise, int routenGeprueft) {

        public long anzahl(Art art) {
            return befunde.stream().filter(b -> b.art() == art).count();
        }
    }

    public static final String DATEINAME = "Vorpruefung.txt";
    public static final String DATEINAME_EN = "Pre-check.txt";

    private final Anlage a;
    private final boolean de;
    private final List<Befund> befunde = new ArrayList<>();
    private final List<String> ohne = new ArrayList<>();

    /** Schnittstellen vom Typ "demo": Objekte daran brauchen keine Adresse. */
    private final Set<String> demo = new HashSet<>();

    private Vorpruefung(Anlage a, boolean deutsch) {
        this.a = a;
        this.de = deutsch;
        a.schnittstellen.stream().filter(s -> "demo".equals(s.typ())).forEach(s -> demo.add(s.name()));
    }

    private boolean brauchtAdresse(String schnittstelle) {
        return schnittstelle != null && !demo.contains(schnittstelle);
    }

    /** Reaktionsverzögerung (ms): darüber Fehler bzw. Hinweis (Anwender 18.11.2026, Hinweis ab 1500 ms seit 20.11.2026). */
    static final int REAKTION_FEHLER = 2000;
    static final int REAKTION_HINWEIS = 1500;

    private static String ms(Integer w) {
        return w == null ? "–" : String.valueOf(w);
    }

    public static Ergebnis pruefe(Anlage a, boolean deutsch, boolean zugtabelle, boolean bahnsteige) {
        Vorpruefung v = new Vorpruefung(a, deutsch);
        v.verzoegerungen();
        v.melder();
        v.zubehoer();
        v.bloecke();
        v.fahrzeuge();
        v.signalfelder();
        v.weiterePruefungen();
        int routen = v.routen();
        Tabelle zt = null;
        List<String> zh = new ArrayList<>();
        if (zugtabelle) {
            zt = v.zugtabelle(zh);
        }
        Tabelle bt = null;
        List<String> bh = new ArrayList<>();
        if (bahnsteige) {
            bt = v.bahnsteige(bh);
        }
        List<Befund> sortiert = new ArrayList<>(v.befunde);
        sortiert.sort(Comparator.comparingInt((Befund b) -> b.stufe() == 0 ? 9 : b.stufe()));
        return new Ergebnis(deutsch, a.datei, a.version, v.eingangscheck(), sortiert, v.ohne, zt, zh, bt, bh,
                routen);
    }

    // --- Eingangscheck --------------------------------------------------------------------------------------------

    private List<String[]> eingangscheck() {
        List<String[]> l = new ArrayList<>();
        l.add(new String[] {t("Projektdatei", "Project file"), a.datei + ", iTrain " + a.version + ", "
                + t("Maßstab", "scale") + " 1:" + (a.massstab == null ? "?" : zahl(Anlage.dezimal(a.massstab)))});
        l.add(new String[] {t("Schnittstellen", "Interfaces"), a.schnittstellen.isEmpty() ? "–"
                : a.schnittstellen.stream().map(s -> s.name() + (s.typ() == null || s.typ().isEmpty() ? ""
                        : " (" + s.typ() + ")")).collect(Collectors.joining(", "))});
        long aktiveLoks = a.loks.values().stream().filter(Anlage.Lok::aktiv).count();
        long aktiveZuege = a.zuege.stream().filter(Anlage.Zug::aktiv).count();
        l.add(new String[] {t("Blöcke / Rückmelder / Zubehör", "Blocks / feedbacks / accessories"),
                a.bloecke.size() + " / " + a.melder.size() + " / " + a.zubehoer.size() + " ("
                        + t("davon Signale ", "signals ") + a.signale() + ")"});
        l.add(new String[] {t("Loks / Wagen / Züge", "Locomotives / wagons / trains"),
                a.loks.size() + " (" + t("aktiv ", "active ") + aktiveLoks + ") / " + a.wagen.size() + " / "
                        + a.zuege.size() + " (" + t("aktiv ", "active ") + aktiveZuege + ")"});
        l.add(new String[] {t("Bahnhöfe / Routen / Aktionen", "Stations / routes / actions"),
                a.bahnhoefe.size() + " / " + a.routen.size() + " / " + a.aktionen});
        String sp = a.spurweite == null ? "n" : a.spurweite;
        l.add(new String[] {t("Spurweite (Voreinstellung)", "Gauge (default)"), sp + " ("
                + t("n = Normalspur, nicht Spur N", "n = standard gauge, not N scale") + ")"});
        return l;
    }

    // --- Rückmelder-Verzögerungen ---------------------------------------------------------------------------------

    private void verzoegerungen() {
        Map<String, Anlage.Schnittstelle> si = new HashMap<>();
        a.schnittstellen.forEach(s -> si.put(s.name(), s));
        Map<List<String>, List<String>> kurz = new LinkedHashMap<>();
        Map<List<String>, List<String>> lang = new LinkedHashMap<>();
        Map<List<String>, List<String>> ein = new LinkedHashMap<>();
        Map<String, Integer> unbekannt = new LinkedHashMap<>();
        boolean einFehlt = false;
        int geprueft = 0;
        for (Anlage.Melder m : a.melder) {
            Anlage.Schnittstelle s = m.schnittstelle() == null ? null : si.get(m.schnittstelle());
            Integer e;
            Integer aus;
            if (m.eigeneVerzoegerung()) {
                e = m.ein();
                aus = m.aus() != null ? m.aus() : (s == null ? null : s.aus());
                einFehlt |= m.ein() == null;
            } else {
                e = s == null ? null : s.ein();
                aus = s == null ? null : s.aus();
                einFehlt |= s != null && s.ein() == null;
            }
            int einWert = e == null ? 0 : e;
            geprueft++;
            List<String> z = List.of(m.schnittstelle() == null ? "–" : m.schnittstelle(),
                    m.eigeneVerzoegerung() ? t("ja", "yes") : t("nein", "no"), String.valueOf(einWert),
                    aus == null ? "?" : String.valueOf(aus));
            if (aus == null) {
                unbekannt.merge(m.schnittstelle() == null ? "–" : m.schnittstelle(), 1, Integer::sum);
            } else if (aus < 250) {
                kurz.computeIfAbsent(z, k -> new ArrayList<>()).add(m.name());
            } else if (aus > 250) {
                lang.computeIfAbsent(z, k -> new ArrayList<>()).add(m.name());
            }
            if (einWert >= 50) {
                ein.computeIfAbsent(z, k -> new ArrayList<>()).add(m.name());
            }
        }
        List<String> kopf = List.of(t("Schnittstelle", "Interface"), t("eigener Wert", "own value"),
                t("Ein (ms)", "On (ms)"), t("Aus (ms)", "Off (ms)"), t("Anzahl", "Count"),
                t("Rückmelder", "Feedbacks"));
        String ort = t("Handlung: \"Bearbeiten\" > \"Schnittstellen\" > Schnittstelle, Abschnitt \"Rückmeldungen\" "
                + "(nicht \"Zubehör\"); eigener Wert im Rückmelder-Dialog.",
                "Action: \"Edit\" > \"Interfaces\" > interface, section \"Feedbacks\" (not \"Accessories\"); "
                        + "own value in the feedback dialog.");
        if (!kurz.isEmpty()) {
            befund(1, Art.FEHLER, t("Ausschaltverzögerung unter 250 ms", "Switch-off delay below 250 ms"),
                    t(anzahl(kurz) + " Rückmelder haben eine Ausschaltverzögerung unter dem Standardwert 250 ms. "
                            + "Das Handbuch nennt 100–500 ms als übliche Werte. ",
                            anzahl(kurz) + " feedbacks have a switch-off delay below the default 250 ms. The manual "
                                    + "gives 100–500 ms as usual values. ") + ort,
                    "S. 34, S. 94", false, new Tabelle(kopf, gruppen(kurz)));
        }
        if (!lang.isEmpty()) {
            befund(2, Art.HINWEIS, t("Ausschaltverzögerung über 250 ms", "Switch-off delay above 250 ms"),
                    t(anzahl(lang) + " Rückmelder haben eine Ausschaltverzögerung über 250 ms. Das Handbuch nennt "
                            + "100–500 ms als übliche Werte. ",
                            anzahl(lang) + " feedbacks have a switch-off delay above 250 ms. The manual gives "
                                    + "100–500 ms as usual values. ") + ort,
                    "S. 34, S. 94", false, new Tabelle(kopf, gruppen(lang)));
        }
        if (!ein.isEmpty()) {
            befund(2, Art.HINWEIS, t("Einschaltverzögerung ab 50 ms", "Switch-on delay of 50 ms or more"),
                    t(anzahl(ein) + " Rückmelder haben eine Einschaltverzögerung ab 50 ms; sie verzögert das "
                            + "Auslösen von Aktionen. Das Handbuch empfiehlt 0 oder unter 50 ms. ",
                            anzahl(ein) + " feedbacks have a switch-on delay of 50 ms or more; it delays the "
                                    + "triggering of actions. The manual recommends 0 or below 50 ms. ") + ort,
                    "S. 34", false, new Tabelle(kopf, gruppen(ein)));
        }
        if (!unbekannt.isEmpty()) {
            List<String> l = new ArrayList<>();
            unbekannt.forEach((s, n) -> l.add(s + " (" + n + t(" Rückmelder)", " feedbacks)")));
            befund(0, Art.ANMERKUNG, t("Ausschaltverzögerung nicht in der Datei",
                    "Switch-off delay not in the file"),
                    t("Bei diesen Schnittstellen steht keine Ausschaltverzögerung in der Datei; der Wert ist aus "
                            + "der Datei nicht sicher erkennbar (Standard 250 ms): ",
                            "For these interfaces the file contains no switch-off delay; the value cannot be "
                                    + "determined with certainty from the file (default 250 ms): ") + liste(l)
                            + t(". Im Dialog der Schnittstelle, Abschnitt \"Rückmeldungen\", nachsehen.",
                                    ". Check in the interface dialog, section \"Feedbacks\"."),
                    "S. 34", false, null);
        }
        ohne.add(t("Rückmelder-Verzögerungen: " + geprueft + " Rückmelder einzeln geprüft (wirksamer Wert = "
                + "eigener Wert, sonst Wert der Schnittstelle).",
                "Feedback delays: " + geprueft + " feedbacks checked individually (effective value = own value, "
                        + "otherwise the interface value).")
                + (einFehlt ? t(" Wo die Einschaltverzögerung in der Datei fehlt, gilt 0 ms (iTrain schreibt 0 "
                        + "nicht in jede Datei; Handbuch S. 34). Kein Befund.",
                        " Where the switch-on delay is missing in the file, it is 0 ms (iTrain does not write 0 "
                                + "into every file; manual p. 34). No finding.") : ""));
    }

    private static int anzahl(Map<List<String>, List<String>> m) {
        return m.values().stream().mapToInt(List::size).sum();
    }

    /** Gleiche Werte zusammenfassen: Werte, Anzahl, Namen der Rückmelder. */
    private static List<List<String>> gruppen(Map<List<String>, List<String>> m) {
        List<List<String>> z = new ArrayList<>();
        m.forEach((werte, namen) -> {
            List<String> zeile = new ArrayList<>(werte);
            zeile.add(String.valueOf(namen.size()));
            zeile.add(liste(namen));
            z.add(zeile);
        });
        return z;
    }

    // --- Rückmelder -----------------------------------------------------------------------------------------------

    private void melder() {
        List<String> ohneAdresse = new ArrayList<>();
        Map<String, List<String>> adressen = new LinkedHashMap<>();
        for (Anlage.Melder m : a.melder) {
            if (m.adresse() == null) {
                if (brauchtAdresse(m.schnittstelle())) {
                    ohneAdresse.add(m.name());
                }
            } else {
                adressen.computeIfAbsent(m.schnittstelle() + " | " + m.adresse(), k -> new ArrayList<>())
                        .add(m.name());
            }
        }
        if (!ohneAdresse.isEmpty()) {
            befund(2, Art.HINWEIS, t("Rückmelder ohne Adresse", "Feedbacks without address"),
                    t(ohneAdresse.size() + " Rückmelder haben eine Schnittstelle, aber keine Adresse: ",
                            ohneAdresse.size() + " feedbacks have an interface but no address: ")
                            + liste(ohneAdresse) + ".", "S. 93", false, null);
        }
        doppelt(adressen, 2, t("Doppelte Rückmelder-Adressen", "Duplicate feedback addresses"),
                t("Rückmelder", "Feedbacks"), "S. 93");
        // Typ der Blockmelder
        Map<String, Anlage.Melder> mi = new HashMap<>();
        a.melder.forEach(m -> mi.put(m.name(), m));
        List<String> falscherTyp = new ArrayList<>();
        Set<String> imBlock = new HashSet<>();
        Map<String, List<String>> bloeckeJeMelder = new LinkedHashMap<>();
        for (Anlage.Block b : a.bloecke.values()) {
            for (Anlage.BlockMelder bm : b.melder()) {
                imBlock.add(bm.name());
                List<String> bl = bloeckeJeMelder.computeIfAbsent(bm.name(), k -> new ArrayList<>());
                if (!bl.contains(b.name())) {
                    bl.add(b.name());
                }
                Anlage.Melder m = mi.get(bm.name());
                if (m != null && m.typ() != null && !m.typ().equals("occupancy")) {
                    falscherTyp.add(bm.name() + " (" + b.name() + ", " + m.typ() + ")");
                }
            }
        }
        if (!falscherTyp.isEmpty()) {
            befund(2, Art.HINWEIS, t("Blockmelder ist kein Belegtmelder", "Block feedback is not an occupancy type"),
                    t("Diese Rückmelder stehen in einem Block, sind aber nicht vom Typ Belegtmelder: ",
                            "These feedbacks are assigned to a block but are not of the occupancy type: ")
                            + liste(falscherTyp) + ".", "S. 93, S. 115", false, null);
        }
        // Nur iTrain 5: Melder-Tutorial von Norbert B. (Forum, "Tutorials und Anleitungen"); für 6 nicht mehr
        // maßgeblich (Anwender 20.11.2026).
        if (a.version != null && a.version.startsWith("5")) {
            List<String> nurPunkt = new ArrayList<>();
            for (Anlage.Block b : a.bloecke.values()) {
                if (!b.aktiv() || b.melder().isEmpty()) {
                    continue;
                }
                boolean belegt = b.melder().stream().map(bm -> mi.get(bm.name()))
                        .anyMatch(m -> m == null || m.typ() == null || m.typ().equals("occupancy"));
                if (!belegt) {
                    nurPunkt.add(b.name());
                }
            }
            if (!nurPunkt.isEmpty()) {
                befund(2, Art.HINWEIS, t("Blöcke nur mit Punktmeldern", "Blocks with point feedbacks only"),
                        t("In diesen Blöcken gibt es keinen Belegtmelder. Punktmelder (Reed, Hall, Lichtschranke) "
                                + "melden nur \"ich bin hier\"; ein abgekuppelter Wagen oder ein außerhalb liegen "
                                + "gebliebener Zug wird nicht erkannt. Punktmelder nur zusätzlich zu einem Belegtmelder "
                                + "einsetzen: ", "These blocks have no occupancy feedback. Point feedbacks (reed, "
                                + "Hall, light barrier) only report \"I am here\"; an uncoupled wagon or a train "
                                + "stopped elsewhere is not detected. Use point feedbacks only in addition to an "
                                + "occupancy feedback: ") + liste(nurPunkt) + ".",
                        t("Tutorial \"Melder in iTrain\" (Norbert B., iTrain 5)",
                                "Tutorial \"Melder in iTrain\" (Norbert B., iTrain 5)"), false, null);
            }
        }
        List<String> freieBelegtmelder = a.melder.stream()
                .filter(m -> !imBlock.contains(m.name()) && (m.typ() == null || m.typ().equals("occupancy")))
                .map(Anlage.Melder::name).toList();
        List<List<String>> mehrfach = new ArrayList<>();
        bloeckeJeMelder.forEach((m, bl) -> {
            if (bl.size() > 1) {
                mehrfach.add(List.of(m, String.join(", ", bl)));
            }
        });
        if (!mehrfach.isEmpty()) {
            befund(2, Art.FEHLER, t("Rückmelder in mehreren Blöcken", "Feedback in several blocks"),
                    t("Diese Rückmelder sind mehreren Blöcken zugeordnet. Wird der Rückmelder belegt, melden alle "
                            + "diese Blöcke belegt, und iTrain kann den Zug nicht eindeutig zuordnen. Vermutlich ist "
                            + "in einem der Blöcke der falsche Rückmelder eingetragen.",
                            "These feedbacks are assigned to several blocks. When the feedback is occupied, all "
                                    + "these blocks report occupied and iTrain cannot place the train. Probably the "
                                    + "wrong feedback is entered in one of the blocks.")
                            + (freieBelegtmelder.isEmpty() ? ""
                                    : t(" Keinem Block zugeordnete Belegtmelder (vielleicht der gemeinte): ",
                                            " Occupancy feedbacks not assigned to any block (perhaps the intended "
                                                    + "one): ") + liste(freieBelegtmelder) + "."),
                    "S. 115", false, new Tabelle(List.of(t("Rückmelder", "Feedback"), t("Blöcke", "Blocks")),
                            mehrfach));
        }
        long ohneBlock = a.melder.stream().filter(m -> !imBlock.contains(m.name())).count();
        ohne.add(t("Rückmelder: Adressen und doppelte Adressen je Schnittstelle geprüft; " + ohneBlock
                + " Rückmelder ohne Block (bei Tasten-, Weichen- oder Systemmeldern kein Fehler)"
                + (freieBelegtmelder.isEmpty() ? "" : ", davon Belegtmelder: " + liste(freieBelegtmelder)) + ".",
                "Feedbacks: addresses and duplicate addresses per interface checked; " + ohneBlock
                        + " feedbacks without block (no error for key, turnout or system feedbacks)"
                        + (freieBelegtmelder.isEmpty() ? "" : ", of which occupancy feedbacks: "
                                + liste(freieBelegtmelder)) + "."));
    }

    // --- Zubehör --------------------------------------------------------------------------------------------------

    private void zubehoer() {
        List<String> ohneAdresse = new ArrayList<>();
        List<String> relaisOhneAdresse = new ArrayList<>();
        List<String> handweichen = new ArrayList<>();
        Map<String, List<String>> adressen = new LinkedHashMap<>();
        Set<String> doppeltErlaubt = new HashSet<>();
        for (Anlage.Zubehoer z : a.zubehoer) {
            if (z.adresse() == null) {
                if (z.schnittstelle() == null) {
                    if (z.art().equals("turnout")) {
                        handweichen.add(z.name());
                    }
                } else if (brauchtAdresse(z.schnittstelle()) && !z.art().equals("signal")
                        && !z.art().equals("aspect")) {
                    // Relais ohne Adresse sind meist Merker für Aktionen (Anwender 20.11.2026)
                    (z.art().equals("relay") ? relaisOhneAdresse : ohneAdresse).add(z.name() + " (" + z.art() + ")");
                }
            } else {
                String k = z.schnittstelle() + " | " + z.adresse();
                adressen.computeIfAbsent(k, x -> new ArrayList<>()).add(z.name() + " (" + z.art() + ")");
                // Option "Doppelte Adresse erlaubt" gesetzt: Doppelbelegung ist gewollt (Anwender 20.11.2026)
                if (z.optionen().contains("addr_duplicate")) {
                    doppeltErlaubt.add(k);
                }
            }
        }
        doppeltErlaubt.forEach(adressen::remove);
        if (!relaisOhneAdresse.isEmpty()) {
            befund(0, Art.ANMERKUNG, t("Relais ohne Adresse", "Relays without address"),
                    t(relaisOhneAdresse.size() + " Relais haben eine Schnittstelle, aber keine Adresse. Meist sind "
                            + "das Merker für Aktionen; virtuelle Objekte brauchen keine Adresse: ",
                            relaisOhneAdresse.size() + " relays have an interface but no address. Usually these are "
                                    + "flags for actions; virtual objects need no address: ")
                            + liste(relaisOhneAdresse) + ".", "S. 95", false, null);
        }
        if (!ohneAdresse.isEmpty()) {
            befund(4, Art.HINWEIS, t("Zubehör ohne Adresse", "Accessories without address"),
                    t(ohneAdresse.size() + " Zubehörteile haben eine Schnittstelle, aber keine Adresse: ",
                            ohneAdresse.size() + " accessories have an interface but no address: ")
                            + liste(ohneAdresse) + ".", "S. 95", false, null);
        }
        if (!handweichen.isEmpty()) {
            befund(0, Art.ANMERKUNG, t("Weichen ohne Schnittstelle", "Turnouts without interface"),
                    t("Diese Weichen haben weder Schnittstelle noch Adresse (Handweichen?): ",
                            "These turnouts have neither interface nor address (manual turnouts?): ")
                            + liste(handweichen) + t(". Bei Handweichen die Optionen dafür prüfen.",
                                    ". For manual turnouts check the options for them."), "S. 95", true, null);
        }
        doppelt(adressen, 4, t("Doppelte Zubehör-Adressen", "Duplicate accessory addresses"),
                t("Zubehör", "Accessories"), "S. 95, S. 99");
        // Blockmelder als Weichen-Belegtmelder: in der Datei nicht sicher zuordenbar, bleibt bei der KI.
    }

    private void doppelt(Map<String, List<String>> adressen, int stufe, String titel, String was, String quelle) {
        List<List<String>> z = new ArrayList<>();
        adressen.forEach((k, v) -> {
            if (v.size() > 1) {
                String[] t = k.split(" \\| ", 2);
                z.add(List.of(t[0].equals("null") ? "–" : t[0], t[1], String.join(", ", v)));
            }
        });
        if (!z.isEmpty()) {
            befund(stufe, Art.HINWEIS, titel, t(z.size() + " Adressen sind je Schnittstelle mehrfach vergeben. "
                    + "Das kann gewollt sein (z. B. Vorsignal mit derselben Decoderadresse wie das Hauptsignal, "
                    + "Handbuch S. 99, oder mehrere Objekte an einem Ausgang); sonst Adresse korrigieren.",
                    z.size() + " addresses are used more than once per interface. This may be intended (e.g. a "
                            + "distant signal with the same decoder address as the home signal, manual p. 99, or "
                            + "several objects on one output); otherwise correct the address."), quelle, false,
                    new Tabelle(List.of(t("Schnittstelle", "Interface"), t("Adresse", "Address"), was), z));
        }
    }

    // --- Blöcke ---------------------------------------------------------------------------------------------------

    private void bloecke() {
        List<String> ohneLaenge = new ArrayList<>();
        List<Double> laengen = a.bloecke.values().stream().map(Anlage.Block::laenge)
                .filter(l -> l != null && l > 0).sorted().toList();
        double median = laengen.isEmpty() ? 0 : laengen.get(laengen.size() / 2);
        List<String> ausreisser = new ArrayList<>();
        List<List<String>> summen = new ArrayList<>();
        List<String> positionen = new ArrayList<>();
        List<List<String>> einseitig = new ArrayList<>();
        List<String> unbekannt = new ArrayList<>();
        List<String> haltepunkte = new ArrayList<>();
        List<String> bahnsteigTyp = new ArrayList<>();
        Map<String, Anlage.Melder> mi = new HashMap<>();
        a.melder.forEach(m -> mi.put(m.name(), m));
        for (Anlage.Block b : a.bloecke.values()) {
            Double l = b.laenge();
            if (l == null || l <= 0) {
                ohneLaenge.add(b.name());
            } else if (median > 0 && l >= 10 * median && l >= 500) {
                ausreisser.add(b.name() + " (" + zahl(l) + " cm)");
            }
            // Summe der Melderlängen
            if (l != null && l > 0 && !b.melder().isEmpty()) {
                double summe = 0;
                boolean alle = true;
                for (Anlage.BlockMelder bm : b.melder()) {
                    Anlage.Melder m = mi.get(bm.name());
                    if (m == null || m.laenge() == null) {
                        alle = false;
                    } else {
                        summe += m.laenge();
                    }
                }
                if (alle && Math.abs(summe - l) > 1.0) {
                    summen.add(List.of(b.name(), zahl(l), zahl(summe), b.melder().stream()
                            .map(Anlage.BlockMelder::name).collect(Collectors.joining(", "))));
                }
                if (b.melder().size() > 1) {
                    for (Anlage.BlockMelder bm : b.melder()) {
                        if (bm.position() == null) {
                            positionen.add(b.name() + ": " + bm.name() + " " + t("ohne Position", "without position"));
                        } else if (bm.position() > l + 0.5) {
                            positionen.add(b.name() + ": " + bm.name() + " " + t("bei", "at") + " "
                                    + zahl(bm.position()) + " cm > " + zahl(l) + " cm");
                        }
                    }
                }
            }
            for (Anlage.Richtung r : List.of(b.prev(), b.next())) {
                for (Anlage.Nachbar n : r.nachbarn()) {
                    Anlage.Block nb = a.bloecke.get(n.block());
                    if (nb == null) {
                        unbekannt.add(b.name() + " (" + r.seite() + ") → " + n.block());
                        continue;
                    }
                    boolean zurueck = false;
                    for (Anlage.Richtung nr : List.of(nb.prev(), nb.next())) {
                        if ((n.seite() == null || n.seite().equals("both") || n.seite().equals(nr.seite()))
                                && nr.nachbarn().stream().anyMatch(x -> x.block().equals(b.name()))) {
                            zurueck = true;
                        }
                    }
                    if (!zurueck) {
                        einseitig.add(List.of(b.name(), seite(r.seite()), n.block()
                                + (n.seite() == null ? t(" (ohne Seite)", " (no side)") : " (" + seite(n.seite()) + ")"),
                                n.seite() == null || n.seite().equals("both")
                                        ? t(n.block() + " nennt " + b.name() + " in keiner Richtung",
                                                n.block() + " does not name " + b.name() + " in any direction")
                                        : t(n.block() + " nennt " + b.name() + " in Richtung " + seite(n.seite())
                                                + " nicht", n.block() + " does not name " + b.name()
                                                        + " in direction " + seite(n.seite()))));
                    }
                }
                if (l != null && l > 0) {
                    if (r.stopp() != null && Math.abs(r.stopp()) > l + 0.5) {
                        haltepunkte.add(b.name() + " " + seite(r.seite()) + ": " + t("Stopp", "stop") + " "
                                + zahl(r.stopp()) + " cm, " + t("Block", "block") + " " + zahl(l) + " cm");
                    }
                    if (r.grenzzeichen() != null && r.grenzzeichen() > l + 0.5) {
                        haltepunkte.add(b.name() + " " + seite(r.seite()) + ": " + t("Grenzzeichen", "clearance")
                                + " " + zahl(r.grenzzeichen()) + " cm, " + t("Block", "block") + " " + zahl(l)
                                + " cm");
                    }
                }
                if (r.bahnsteig() != null && !"station".equals(b.typ())) {
                    bahnsteigTyp.add(b.name() + " (" + (b.typ() == null ? "free" : b.typ()) + ")");
                }
            }
        }
        if (!ohneLaenge.isEmpty()) {
            befund(2, Art.HINWEIS, t("Blöcke ohne Länge", "Blocks without length"),
                    t(ohneLaenge.size() + " Blöcke haben keine Länge: ", ohneLaenge.size()
                            + " blocks have no length: ") + liste(ohneLaenge) + ".", "S. 115", false, null);
        }
        if (!ausreisser.isEmpty()) {
            befund(2, Art.HINWEIS, t("Ungewöhnlich lange Blöcke", "Unusually long blocks"),
                    t("Diese Blöcke sind mindestens zehnmal so lang wie der typische Block der Anlage ("
                            + zahl(median) + " cm), vermutlich ein Tippfehler: ",
                            "These blocks are at least ten times as long as the typical block of the layout ("
                                    + zahl(median) + " cm), probably a typo: ") + liste(ausreisser) + ".",
                    "S. 115", true, null);
        }
        if (!summen.isEmpty()) {
            befund(2, Art.HINWEIS, t("Melderlängen passen nicht zur Blocklänge",
                    "Feedback lengths do not match the block length"),
                    t("Bei " + summen.size() + " Blöcken ist die Summe der Melderlängen nicht gleich der "
                            + "Blocklänge.", "For " + summen.size() + " blocks the sum of the feedback lengths "
                                    + "differs from the block length."), "S. 115–116", false,
                    new Tabelle(List.of(t("Block", "Block"), t("Block (cm)", "Block (cm)"),
                            t("Summe Melder (cm)", "Sum feedbacks (cm)"), t("Rückmelder", "Feedbacks")), summen));
        }
        if (!positionen.isEmpty()) {
            befund(2, Art.HINWEIS, t("Melderpositionen", "Feedback positions"),
                    t("In Blöcken mit mehreren Rückmeldern fehlt die Position oder liegt hinter dem Blockende: ",
                            "In blocks with several feedbacks the position is missing or lies beyond the block "
                                    + "end: ") + liste(positionen) + ".", "S. 115–116", false, null);
        }
        if (!unbekannt.isEmpty()) {
            befund(2, Art.HINWEIS, t("Nachbarblock existiert nicht", "Neighbouring block does not exist"),
                    t("Diese Blockverbindungen nennen einen Block, den es nicht gibt: ",
                            "These block connections name a block that does not exist: ") + liste(unbekannt) + ".",
                    "S. 120", false, null);
        }
        if (!einseitig.isEmpty()) {
            befund(2, Art.HINWEIS, t("Blockverbindung nur in einer Richtung", "Block connection in one direction only"),
                    t("Ein Block nennt in einer Richtung einen Nachbarblock, der Nachbarblock nennt ihn aber nicht "
                            + "zurück. Meist fehlt die Verbindung auf einer Seite oder führt zum falschen Block. Bei "
                            + "Drehscheiben vermutlich ohne Bedeutung.",
                            "A block names a neighbouring block in one direction, but the neighbour does not name it "
                                    + "back. Usually the connection is missing on one side or leads to the wrong "
                                    + "block. Probably irrelevant for turntables."), "S. 120", true,
                    new Tabelle(List.of(t("Block", "Block"), t("Richtung", "Direction"),
                            t("nennt als Nachbarn", "names as neighbour"), t("Gegenseite", "Other side")),
                            einseitig));
        } else {
            ohne.add(t("Blockverbindungen: alle in beiden Richtungen stimmig.",
                    "Block connections: all consistent in both directions."));
        }
        if (!haltepunkte.isEmpty()) {
            befund(2, Art.HINWEIS, t("Haltepunkte außerhalb des Blocks", "Stop points outside the block"),
                    t("Stopp-Position oder Grenzzeichen liegen hinter dem Blockende: ",
                            "Stop position or clearance point lie beyond the block end: ") + liste(haltepunkte)
                            + ".", "S. 117", false, null);
        }
        boolean irgendeinBahnsteig = a.bloecke.values().stream()
                .anyMatch(b -> b.prev().bahnsteig() != null || b.next().bahnsteig() != null);
        if (!irgendeinBahnsteig) {
            long bahnhofsbloecke = a.bloecke.values().stream().filter(b -> "station".equals(b.typ())).count();
            List<String> ausrichten = a.gattungen.values().stream()
                    .filter(g -> g.optionen().contains("platform_align")).map(Anlage.Gattung::name).toList();
            befund(2, Art.HINWEIS, t("Keine Bahnsteige eingetragen", "No platforms entered"),
                    t("In der Datei ist kein Bahnsteig eingetragen (" + bahnhofsbloecke + (bahnhofsbloecke == 1 ? " Bahnhofsblock). " : " Bahnhofsblöcke). ")
                            + "Ohne Bahnsteig halten Züge an der Stopp-Position, die Option \"Zug am Bahnsteig "
                            + "ausrichten\" der Zuggattung bleibt wirkungslos, und die Bahnsteigprüfung (Zusatz 2) "
                            + "kann keine Werte berechnen.",
                            "No platform is entered in the file (" + bahnhofsbloecke + (bahnhofsbloecke == 1 ? " station block). Without a " : " station blocks). Without a ")
                            + "platform trains stop at the stop position, the train type option \"Align train at "
                            + "platform\" has no effect, and the platform check (extra 2) cannot calculate values.")
                            + (ausrichten.isEmpty() ? ""
                                    : t(" Zuggattungen mit dieser Option: ", " Train types with this option: ")
                                            + liste(ausrichten) + "."),
                    "S. 55, S. 121", false, null);
        }
        if (!bahnsteigTyp.isEmpty()) {
            befund(2, Art.HINWEIS, t("Bahnsteig in einem Block, der kein Bahnhofsblock ist",
                    "Platform in a block that is not a station block"),
                    t("Ein Bahnsteig gehört in einen Block vom Typ \"Bahnhof\": ",
                            "A platform belongs in a block of type \"Station\": ") + liste(bahnsteigTyp) + ".",
                    "S. 121", false, null);
        }
    }

    // --- Fahrzeuge, Züge, Spurweite -------------------------------------------------------------------------------

    private void fahrzeuge() {
        String vorgabe = a.spurweite == null ? "n" : a.spurweite;
        List<String> ohneAdresse = new ArrayList<>();
        List<String> ohneLaenge = new ArrayList<>();
        List<String> ohneProfil = new ArrayList<>();
        List<String> inaktiv = new ArrayList<>();
        List<List<String>> reaktionFehler = new ArrayList<>();
        List<List<String>> reaktionHoch = new ArrayList<>();
        List<List<String>> traegheitKurz = new ArrayList<>();
        List<String> decoderAbv = new ArrayList<>();
        List<String> ohneOffset = new ArrayList<>();
        Map<String, List<String>> lokAdressen = new LinkedHashMap<>();
        for (Anlage.Lok l : a.loks.values()) {
            // Reaktionsverzögerung auch bei inaktiven Loks: Beim Aktivieren stimmt der Wert sonst nicht (Anwender
            // 20.11.2026, Befund aus einer KI-Analyse).
            Integer rm = l.reaktionMax();
            if (rm != null && rm > REAKTION_HINWEIS) {
                List<String> zeile = List.of(l.name() + (l.aktiv() ? "" : t(" (inaktiv)", " (inactive)")),
                        ms(l.reaktionVor()), ms(l.reaktionRueck()));
                (rm > REAKTION_FEHLER ? reaktionFehler : reaktionHoch).add(zeile);
            }
            if (!l.aktiv()) {
                inaktiv.add(l.name());
                continue;
            }
            // Hexadezimale Werte (BiDiB, IFnet) sind die Knotenkennung, nicht die Lokadresse.
            if (l.adresse() != null && l.schnittstelle() != null && !l.adresse().startsWith("0x")) {
                lokAdressen.computeIfAbsent(l.schnittstelle() + " | " + l.adresse(), k -> new ArrayList<>())
                        .add(l.name());
            }
            if (l.adresse() == null && brauchtAdresse(l.schnittstelle())) {
                ohneAdresse.add(l.name());
            }
            if (l.laenge() == null || l.laenge() <= 0) {
                ohneLaenge.add(l.name());
            }
            if (l.fahrstufen() == 0) {
                ohneProfil.add(l.name());
            }
            if (!l.offsetVorhanden()) {
                ohneOffset.add(l.name());
            }
            // Trägheitssimulation: unter 100 ms nicht brauchbar (S. 49). Die Stufenschrittweite prüfen wir nicht:
            // 1 oder 2 ist üblich, 4 oder 5 nur bei hoher Last durch viele Fahrzeuge (Anwender 20.11.2026).
            boolean anKurz = l.traegheitAnfahren() != null && l.traegheitAnfahren() > 0 && l.traegheitAnfahren() < 100;
            boolean brKurz = l.traegheitBremsen() != null && l.traegheitBremsen() > 0 && l.traegheitBremsen() < 100;
            if (anKurz || brKurz) {
                traegheitKurz.add(List.of(l.name(), ms(l.traegheitAnfahren()), ms(l.traegheitBremsen())));
            }
            // Anfahr-/Bremsverzögerung im Decoder (CV3/CV4) möglichst minimal; ESU-Sound 11 (Anwender 20.11.2026)
            if ((l.cv3() != null && l.cv3() > 11) || (l.cv4() != null && l.cv4() > 11)) {
                decoderAbv.add(l.name() + " (CV3=" + (l.cv3() == null ? "?" : l.cv3()) + ", CV4="
                        + (l.cv4() == null ? "?" : l.cv4()) + ")");
            }
        }
        if (!traegheitKurz.isEmpty()) {
            befund(3, Art.HINWEIS, t("Trägheitssimulation unter 100 ms", "Inertia simulation below 100 ms"),
                    t("Werte unter 100 ms sind laut Handbuch nicht brauchbar: Die Lok verarbeitet die einzelnen "
                            + "Fahrbefehle nicht, bevor der nächste kommt. Gute Werte liegen zwischen 100 und 500 ms. "
                            + "Im Lok-Editor unter \"Optionen\" ändern.",
                            "According to the manual values below 100 ms are not usable: the locomotive does not "
                                    + "process the individual speed commands before the next one arrives. Good "
                                    + "values are between 100 and 500 ms. Change them in the locomotive editor "
                                    + "under \"Options\"."),
                    "S. 49", false, new Tabelle(List.of(t("Lok", "Locomotive"), t("Anfahren (ms)",
                            "Accelerate (ms)"), t("Bremsen (ms)", "Brake (ms)")), traegheitKurz));
        }
        if (!decoderAbv.isEmpty()) {
            befund(0, Art.ANMERKUNG, t("Hohe Anfahr- und Bremsverzögerung im Decoder",
                    "High acceleration and deceleration in the decoder"),
                    t("Die Wegberechnung arbeitet am besten, wenn CV3 und CV4 im Decoder auf dem Minimum stehen und die "
                            + "Einmesskurve linear ist; bei ESU-Sounddecodern genügt 11. Wer die Herstellerwerte "
                            + "behält, gleicht die Haltepunkte mit der Reaktionsverzögerung und der Trägheitssimulation "
                            + "aus. Gespeicherte Werte: ",
                            "Position calculation works best when CV3 and CV4 are at their minimum and the speed curve "
                                    + "is linear; 11 is enough for ESU sound decoders. If you keep the manufacturer's "
                                    + "values, adjust the stop points with the reaction delay and the inertia "
                                    + "simulation. Stored values: ") + liste(decoderAbv) + ".",
                    "S. 49, S. 118", false, null);
        }
        if (!ohneOffset.isEmpty()) {
            befund(3, Art.FEHLER, t("Aktive Loks ohne Rückmeldungs-Offset", "Active locomotives without feedback offset"),
                    t("Bei diesen Loks ist kein Rückmeldungs-Offset eingetragen (alle Werte 0). Der Offset gibt den "
                            + "Abstand vom Puffer bis zur ersten bzw. letzten meldenden Achse an; ohne ihn stimmen "
                            + "Position und Haltepunkt nicht. Im Lok-Editor unter \"Optionen\" eintragen: ",
                            "No feedback offset is entered for these locomotives (all values 0). The offset is the "
                                    + "distance from the buffer to the first or last detecting axle; without it "
                                    + "position and stop point are wrong. Enter it in the locomotive editor under "
                                    + "\"Options\": ") + liste(ohneOffset) + ".", "S. 49–50", false, null);
        }
        if (!ohneAdresse.isEmpty()) {
            befund(3, Art.HINWEIS, t("Aktive Loks ohne Adresse", "Active locomotives without address"),
                    liste(ohneAdresse) + ".", "S. 42", false, null);
        }
        if (!ohneLaenge.isEmpty()) {
            befund(3, Art.HINWEIS, t("Aktive Loks ohne Länge", "Active locomotives without length"),
                    liste(ohneLaenge) + t(". Ohne Länge kann iTrain Zuglängen und Haltepunkte nicht richtig "
                            + "berechnen.", ". Without a length iTrain cannot calculate train lengths and stop "
                                    + "points correctly."), "S. 42", false, null);
        }
        if (!ohneProfil.isEmpty()) {
            befund(3, Art.HINWEIS, t("Aktive Loks ohne Geschwindigkeitsprofil",
                    "Active locomotives without speed profile"),
                    liste(ohneProfil) + t(". In der Datei steht keine Geschwindigkeitstabelle.",
                            ". The file contains no speed table."), "S. 46", true, null);
        }
        List<List<String>> doppelLok = new ArrayList<>();
        lokAdressen.forEach((k, v) -> {
            if (v.size() > 1) {
                String[] t0 = k.split(" \\| ", 2);
                doppelLok.add(List.of(t0[0], t0[1], String.join(", ", v)));
            }
        });
        if (!doppelLok.isEmpty()) {
            befund(3, Art.HINWEIS, t("Gleiche Decoderadresse bei mehreren aktiven Loks",
                    "Same decoder address on several active locomotives"),
                    t("Diese aktiven Loks haben an derselben Schnittstelle dieselbe Adresse. Fahren sie gleichzeitig, "
                            + "steuert iTrain beide Decoder gemeinsam. Gewollt ist das nur bei einem Decoder, den "
                            + "mehrere Lok-Einträge teilen; sonst Adresse korrigieren oder eine Lok deaktivieren.",
                            "These active locomotives have the same address on the same interface. If they run at "
                                    + "the same time, iTrain controls both decoders together. This is only intended "
                                    + "for one decoder shared by several locomotive entries; otherwise correct the "
                                    + "address or deactivate one locomotive."),
                    "S. 42", false, new Tabelle(List.of(t("Schnittstelle", "Interface"), t("Adresse", "Address"),
                            t("Loks", "Locomotives")), doppelLok));
        }
        List<String> reaktionKopf = List.of(t("Lok", "Locomotive"), t("vorwärts (ms)", "forward (ms)"),
                t("rückwärts (ms)", "backward (ms)"));
        if (!reaktionFehler.isEmpty()) {
            befund(3, Art.FEHLER, t("Reaktionsverzögerung über 2000 ms", "Reaction delay above 2000 ms"),
                    t("Die Reaktionsverzögerung korrigiert die Positionsberechnung im Block; voreingestellt sind "
                            + "200 ms. Werte über 2000 ms sind Eingabefehler: Die Lok hält deutlich zu früh, Halte- "
                            + "und Bremspositionen stimmen nicht. Im Lok-Editor unter \"Optionen\" korrigieren.",
                            "The reaction delay corrects the position calculation in the block; the default is "
                            + "200 ms. Values above 2000 ms are input errors: the locomotive stops far too early, "
                            + "stop and brake positions are wrong. Correct it in the locomotive editor under "
                            + "\"Options\"."),
                    "S. 50, S. 118", false, new Tabelle(reaktionKopf, reaktionFehler));
        }
        if (!reaktionHoch.isEmpty()) {
            befund(3, Art.HINWEIS, t("Reaktionsverzögerung über 1500 ms", "Reaction delay above 1500 ms"),
                    t("Voreingestellt sind 200 ms; der Wert wird durch Ausprobieren ermittelt und liegt in der Praxis "
                            + "höher, üblich sind 400 bis 1000 ms, bei schweren Loks bis 1500 ms. Je höher er ist, desto "
                            + "früher hält die Lok. Prüfen, ob die Lok an der gewünschten Position hält.",
                            "The default is 200 ms; the value is found by trial and is higher in practice, usually "
                            + "400 to 1000 ms, up to 1500 ms for heavy locomotives. The higher it is, the earlier the "
                            + "locomotive stops. Check whether it stops at the intended position."),
                    "S. 50, S. 118", false, new Tabelle(reaktionKopf, reaktionHoch));
        }
        if (!inaktiv.isEmpty()) {
            befund(0, Art.ANMERKUNG, t("Inaktive Loks", "Inactive locomotives"),
                    inaktiv.size() + ": " + liste(inaktiv) + t(". Kein Fehler.", ". Not an error."), "S. 42",
                    false, null);
        }
        // Züge
        List<String> unbekannt = new ArrayList<>();
        List<String> spurweite = new ArrayList<>();
        List<String> leer = new ArrayList<>();
        List<String> wagenOhneLaenge = new ArrayList<>();
        for (Anlage.Zug z : a.zuege) {
            String zs = z.spurweite() == null ? vorgabe : z.spurweite();
            if (z.teile().isEmpty()) {
                leer.add(z.name());
            }
            for (Anlage.Teil teil : z.teile()) {
                String fs = null;
                if (teil.art().equals("LOK")) {
                    Anlage.Lok l = a.loks.get(teil.name());
                    if (l == null) {
                        unbekannt.add(z.name() + ": " + teil.name());
                    } else {
                        fs = l.spurweite();
                    }
                } else if (teil.art().equals("WAGEN")) {
                    Anlage.Wagen w = a.wagen.get(teil.name());
                    if (w == null) {
                        unbekannt.add(z.name() + ": " + teil.name());
                    } else {
                        fs = w.spurweite();
                        if (z.aktiv() && (w.laenge() == null || w.laenge() <= 0)) {
                            wagenOhneLaenge.add(w.name());
                        }
                    }
                }
                if (fs != null && !fs.equals(zs)) {
                    spurweite.add(z.name() + " (" + zs + "): " + teil.name() + " (" + fs + ")");
                }
            }
        }
        if (!unbekannt.isEmpty()) {
            befund(3, Art.FEHLER, t("Zug nennt ein Fahrzeug, das es nicht gibt",
                    "Train names a vehicle that does not exist"), liste(unbekannt) + ".", "S. 57", false, null);
        }
        if (!spurweite.isEmpty()) {
            befund(3, Art.HINWEIS, t("Spurweite von Zug und Fahrzeug verschieden",
                    "Gauge of train and vehicle differs"), liste(spurweite) + ".", "S. 27, S. 57", false, null);
        }
        Set<String> wol = new TreeSet<>(wagenOhneLaenge);
        if (!wol.isEmpty()) {
            befund(3, Art.HINWEIS, t("Wagen in aktiven Zügen ohne Länge", "Wagons in active trains without length"),
                    liste(new ArrayList<>(wol)) + ".", "S. 52", false, null);
        }
        if (!leer.isEmpty()) {
            ohne.add(t("Leere Züge oder Wagenzüge ohne Lok sind kein Fehler: ",
                    "Empty trains or wagon trains without locomotive are not an error: ") + liste(leer) + ".");
        }
        // Spurweite Blöcke
        Set<String> sp = new TreeSet<>();
        a.bloecke.values().forEach(b -> sp.add(b.spurweite() == null ? vorgabe : b.spurweite()));
        ohne.add(t("Spurweiten der Blöcke: ", "Gauges of the blocks: ") + String.join(", ", sp) + ".");
    }

    // --- Routen ---------------------------------------------------------------------------------------------------

    private record Knoten(String block, String seite) {
    }

    private int routen() {
        List<List<String>> zeilen = new ArrayList<>();
        List<List<String>> vermutlich = new ArrayList<>();
        List<List<String>> erlaubnisFehler = new ArrayList<>();
        List<List<String>> erlaubnisTeil = new ArrayList<>();
        List<String> wiederholung = new ArrayList<>();
        List<String> reservierung = new ArrayList<>();
        List<String> marker = new ArrayList<>();
        List<String> kritischWarten = new ArrayList<>();
        List<String> spurweite = new ArrayList<>();
        String vorgabe = a.spurweite == null ? "n" : a.spurweite;
        int geprueft = 0;
        List<String> inaktiveRouten = new ArrayList<>();
        for (Anlage.Route r : a.routen.values()) {
            if (!r.aktiv()) {
                inaktiveRouten.add(r.name());
                continue;
            }
            if (r.eintraege().isEmpty()) {
                continue;
            }
            geprueft++;
            List<Anlage.Eintrag> e = r.eintraege();
            // (a) Blöcke vorhanden
            for (Anlage.Eintrag x : e) {
                for (Anlage.BlockSeite bs : x.bloecke()) {
                    if (!a.bloecke.containsKey(bs.block())) {
                        zeilen.add(List.of(r.name(), x.name(), t("Block existiert nicht", "block does not exist"),
                                bs.block()));
                    }
                }
                if (x.bloecke().isEmpty()) {
                    zeilen.add(List.of(r.name(), x.name(), t("Eintrag ohne Block", "item without block"), "–"));
                }
            }
            // (b), (c)
            boolean wechsel = r.rangieren() || !"forbidden".equals(r.richtungswechsel());
            for (int i = 0; i + 1 < e.size(); i++) {
                List<Anlage.BlockSeite> von = vorhanden(e.get(i).bloecke());
                List<Anlage.BlockSeite> nach = vorhanden(e.get(i + 1).bloecke());
                if (von.isEmpty() || nach.isEmpty()) {
                    continue;
                }
                boolean strikt = e.get(i + 1).optionen().contains("strict");
                if (strikt) {
                    boolean direkt = von.stream().anyMatch(v -> nach.stream()
                            .anyMatch(n -> benachbart(v.block(), n.block())));
                    if (!direkt) {
                        List<String> weg = wegUngerichtet(blocke(von), blocke(nach));
                        zeilen.add(List.of(r.name(), e.get(i + 1).name(), t("\"Strikt\", aber nicht direkt "
                                + "benachbart", "\"Strict\", but not directly adjacent"),
                                weg == null ? "–" : String.join(" → ", weg)));
                    }
                }
                if (wegUngerichtet(blocke(von), blocke(nach)) == null) {
                    zeilen.add(List.of(r.name(), e.get(i).name() + " → " + e.get(i + 1).name(),
                            t("keine Verbindung im Gleisplan", "no connection in the track plan"), "–"));
                } else if (!erreichbar(r, von, nach, wechsel)) {
                    vermutlich.add(List.of(r.name(), e.get(i).name() + " → " + e.get(i + 1).name(),
                            wechsel ? t("kein Weg in der angegebenen Richtung",
                                    "no path in the given direction")
                                    : t("kein Weg ohne Richtungswechsel", "no path without direction change"),
                            "–"));
                }
            }
            // (d)
            if (r.wiederholung() != 1) {
                boolean mitMarker = e.stream().anyMatch(x -> x.marker() != null);
                if (!mitMarker) {
                    Set<String> erst = blocke(e.get(0).bloecke());
                    Set<String> letzt = blocke(e.get(e.size() - 1).bloecke());
                    if (erst.stream().noneMatch(letzt::contains)) {
                        wiederholung.add(r.name() + " (" + e.get(0).name() + " … " + e.get(e.size() - 1).name()
                                + ")");
                    }
                }
            }
            if (r.reservierung() != null && r.reservierung() > e.size()
                    || r.reservierungStart() != null && r.reservierungStart() > e.size()) {
                reservierung.add(r.name() + " (" + e.size() + t(" Einträge, Reservierung ", " items, reservation ")
                        + r.reservierung() + "/" + r.reservierungStart() + ")");
            }
            // "Kritisch" an einem Eintrag mit Wartezeit ist wirkungslos (Handbuch S. 158; bestätigt im Forum,
            // LudwigR 18.11.2026)
            for (Anlage.Eintrag x : e) {
                if (x.warten() && x.optionen().contains("critical")) {
                    kritischWarten.add(r.name() + ": " + x.name());
                }
            }
            // Markierungen
            boolean startErst = "start".equals(e.get(0).marker());
            boolean endeLetzt = "finish".equals(e.get(e.size() - 1).marker());
            boolean hatMarker = e.stream().anyMatch(x -> x.marker() != null);
            if (hatMarker && (r.wiederholung() == 1 || startErst && endeLetzt)) {
                marker.add(r.name());
            }
            // (f)
            Set<String> sp = new TreeSet<>();
            e.forEach(x -> vorhanden(x.bloecke()).forEach(bs -> {
                String g = a.bloecke.get(bs.block()).spurweite();
                sp.add(g == null ? vorgabe : g);
            }));
            if (sp.size() > 1) {
                spurweite.add(r.name() + " (" + String.join(", ", sp) + ")");
            }
            // (g) Erlaubnisse
            erlaubnisse(r, erlaubnisFehler, erlaubnisTeil);
        }
        List<String> kopf = List.of(t("Route", "Route"), t("Eintrag", "Item"), t("Problem", "Problem"),
                t("dazwischen / fehlend", "in between / missing"));
        if (!zeilen.isEmpty()) {
            befund(1, Art.FEHLER, t("Routen nicht durchführbar", "Routes not runnable"),
                    t("Diese Routen sind nach der Routenprüfung (a)–(c) sicher nicht durchführbar.",
                            "According to the route check (a)–(c) these routes are certainly not runnable."),
                    "S. 155, S. 158", false, new Tabelle(kopf, zeilen));
        }
        if (!vermutlich.isEmpty()) {
            befund(4, Art.HINWEIS, t("Routen vermutlich nicht durchführbar", "Routes probably not runnable"),
                    t("Die Blöcke sind im Gleisplan verbunden, aber nicht in der Richtung, die die Route angibt "
                            + "(side = Seite, über die der Zug den Block verlässt; die Bedeutung ist aus der Datei "
                            + "nicht sicher erkennbar).",
                            "The blocks are connected in the track plan, but not in the direction given by the route "
                                    + "(side = side by which the train leaves the block; the meaning cannot be "
                                    + "determined with certainty from the file)."),
                    "S. 155, S. 159, S. 161", true, new Tabelle(kopf, vermutlich));
        }
        List<String> ekopf = List.of(t("Route", "Route"), t("Zug", "Train"), t("Eintrag", "Item"),
                t("gesperrter Block", "blocked block"), t("frei bleibt", "remains free"),
                t("Erlaubnis von", "Permission of"));
        erlaubnisFehler = new ArrayList<>(new LinkedHashSet<>(erlaubnisFehler));
        erlaubnisTeil = new ArrayList<>(new LinkedHashSet<>(erlaubnisTeil));
        boolean nurEiner = erlaubnisTeil.stream().anyMatch(z -> !z.get(4).contains(","));
        if (!erlaubnisFehler.isEmpty()) {
            befund(1, Art.FEHLER, t("Route durch Erlaubnis gesperrt", "Route blocked by permission"),
                    t("Für diese Züge sind alle Blöcke eines Routeneintrags durch Erlaubnisse gesperrt. Die Route "
                            + "findet keinen Weg. Erlaubnisse, die Gleise nur für eine Richtung freigeben, führen "
                            + "leicht dazu, dass sich Züge gegenseitig blockieren. Handlung: Erlaubnis im Dialog des "
                            + "genannten Objekts auf der Registerkarte \"Erlaubnis\" anpassen oder die Route ändern.",
                            "For these trains all blocks of a route item are blocked by permissions. The route finds "
                                    + "no path. Permissions that open tracks for one direction only easily cause "
                                    + "trains to block each other. Action: adjust the permission in the dialog of the "
                                    + "named object on the tab \"Permission\", or change the route."),
                    "S. 50, S. 54, S. 56, S. 60, S. 159, S. 162", false, new Tabelle(ekopf, erlaubnisFehler));
        }
        if (!erlaubnisTeil.isEmpty()) {
            befund(4, Art.HINWEIS, t("Routenauswahl durch Erlaubnis eingeschränkt",
                    "Route selection restricted by permission"),
                    t("Ein Teil der Alternativen eines Routeneintrags ist für diese Züge gesperrt.",
                            "Some of the alternatives of a route item are blocked for these trains.")
                            + (nurEiner ? t(" Bleibt nur ein Block frei, wartet der Zug, solange dieser Block "
                                    + "belegt ist, und startet die Route scheinbar nicht. Die Blocklänge spielt dabei "
                                    + "keine Rolle; bei der Auswahl \"Reihenfolge\" prüft iTrain keine Längen.",
                                    " If only one block remains free, the train waits as long as this block is "
                                            + "occupied and the route seems not to start. The block length plays no "
                                            + "role; with the selection \"Order\" iTrain does not check lengths.")
                                    : ""),
                    "S. 50, S. 54, S. 56, S. 60, S. 159, S. 162", false, new Tabelle(ekopf, erlaubnisTeil));
        }
        if (!wiederholung.isEmpty()) {
            befund(4, Art.HINWEIS, t("Wiederholte Route endet nicht am Anfang", "Repeated route does not end at its start"),
                    liste(wiederholung) + ".", "S. 155", false, null);
        }
        if (!reservierung.isEmpty()) {
            befund(4, Art.HINWEIS, t("Reservierungsanzahl größer als die Zahl der Einträge",
                    "Reservation count greater than the number of items"), liste(reservierung) + ".", "S. 161",
                    false, null);
        }
        if (!spurweite.isEmpty()) {
            befund(4, Art.HINWEIS, t("Routenblöcke mit verschiedener Spurweite", "Route blocks with different gauges"),
                    liste(spurweite) + ".", "S. 111", false, null);
        }
        if (!kritischWarten.isEmpty()) {
            befund(4, Art.HINWEIS, t("\"Kritisch\" an Routeneinträgen mit Wartezeit ist wirkungslos",
                    "\"Critical\" on route items with a waiting time has no effect"),
                    t("\"Kritisch\" ist nur an Einträgen ohne Wartezeit möglich; hier läuft die Route, die Option "
                            + "bewirkt aber nichts: ", "\"Critical\" is only possible on items without waiting "
                                    + "time; the route runs here, but the option does nothing: ")
                            + liste(kritischWarten) + t(". Handlung: \"Kritisch\" dort entfernen und, falls die "
                                    + "Reservierung gebraucht wird, an einen Eintrag ohne Wartezeit davor oder im "
                                    + "Blockeditor setzen.", ". Action: remove \"Critical\" there and, if the "
                                    + "reservation is needed, set it on an item without waiting time before it or in "
                                    + "the block editor."), "S. 158, S. 119", false, null);
        }
        if (!marker.isEmpty()) {
            befund(0, Art.ANMERKUNG, t("Überflüssige Markierungen in Routen", "Superfluous markers in routes"),
                    t(marker.size() + " Routen haben Markierungen, obwohl sie nicht wiederholt werden oder die "
                            + "Markierungen die ganze Route umfassen. Markierungen braucht man nur, wenn ein Teil der "
                            + "Route wiederholt wird: ",
                            marker.size() + " routes have markers although they are not repeated or the markers "
                                    + "cover the whole route. Markers are only needed if part of the route is "
                                    + "repeated: ") + liste(marker) + ".", "S. 158, S. 161", false, null);
        }
        if (zeilen.isEmpty() && vermutlich.isEmpty() && erlaubnisFehler.isEmpty()) {
            ohne.add(t("Routen überprüft (" + geprueft + " Routen), keine Befunde.",
                    "Routes checked (" + geprueft + " routes), no findings."));
        }
        if (!inaktiveRouten.isEmpty()) {
            // Regel P10: nicht geprüfte Routen mit Namen nennen (Anwender 20.11.2026)
            ohne.add(t(inaktiveRouten.size() + " inaktive Routen nicht geprüft: ",
                    inaktiveRouten.size() + " inactive routes not checked: ") + liste(inaktiveRouten) + ".");
        }
        return geprueft;
    }

    /** (g): Erlaubnisse von Zug, Loks, Wagen und ggf. Zuggattung für die Züge, die die Route fahren. */
    private void erlaubnisse(Anlage.Route r, List<List<String>> fehler, List<List<String>> teil) {
        boolean gattung = r.optionen().contains("use_type_permissions");
        for (Anlage.Zug z : a.zuege) {
            if (!z.aktiv() || !z.routen().contains(r.name())) {
                continue;
            }
            Map<String, Anlage.Erlaubnis> erl = new LinkedHashMap<>();
            if (z.erlaubnis() != null) {
                erl.put(t("Zug ", "train ") + z.name(), z.erlaubnis());
            }
            for (Anlage.Teil teil0 : z.teile()) {
                Anlage.Erlaubnis e = teil0.art().equals("LOK")
                        ? (a.loks.get(teil0.name()) == null ? null : a.loks.get(teil0.name()).erlaubnis())
                        : teil0.art().equals("WAGEN")
                                ? (a.wagen.get(teil0.name()) == null ? null : a.wagen.get(teil0.name()).erlaubnis())
                                : null;
                if (e != null) {
                    erl.put((teil0.art().equals("LOK") ? t("Lok ", "loco ") : t("Wagen ", "wagon ")) + teil0.name(),
                            e);
                }
            }
            if (gattung && z.gattung() != null && a.gattungen.get(z.gattung()) != null
                    && a.gattungen.get(z.gattung()).erlaubnis() != null) {
                erl.put(t("Zuggattung ", "train type ") + z.gattung(), a.gattungen.get(z.gattung()).erlaubnis());
            }
            if (erl.isEmpty()) {
                continue;
            }
            for (Anlage.Eintrag x : r.eintraege()) {
                List<Anlage.BlockSeite> bl = vorhanden(x.bloecke());
                if (bl.isEmpty()) {
                    continue;
                }
                List<String> gesperrt = new ArrayList<>();
                Set<String> von = new LinkedHashSet<>();
                for (Anlage.BlockSeite bs : bl) {
                    // Ohne Seite in der Route ist der Block frei, wenn eine der beiden Richtungen erlaubt ist.
                    boolean frei = false;
                    Set<String> sperrend = new LinkedHashSet<>();
                    for (String seite : seiten(bs.seite())) {
                        boolean richtungFrei = true;
                        for (var en : erl.entrySet()) {
                            if (!en.getValue().erlaubt(bs.block(), seite)) {
                                richtungFrei = false;
                                sperrend.add(en.getKey());
                            }
                        }
                        frei |= richtungFrei;
                    }
                    if (!frei) {
                        gesperrt.add(bs.block());
                        von.addAll(sperrend);
                    }
                }
                if (gesperrt.isEmpty()) {
                    continue;
                }
                List<String> frei = bl.stream().map(Anlage.BlockSeite::block).filter(b -> !gesperrt.contains(b))
                        .distinct().toList();
                List<String> zeile = List.of(r.name(), z.name(), x.name(), String.join(", ", gesperrt),
                        frei.isEmpty() ? "–" : String.join(", ", frei), String.join(", ", von));
                (gesperrt.size() == bl.size() ? fehler : teil).add(zeile);
            }
        }
    }

    // --- Weitere Prüfungen aus dem Vergleich mit KI-Analysen (Anwender 20.11.2026) -----------------------------------

    /** Funktionen, die iTrain 6 beim Anlegen eines Zuges in Start und Stopp einträgt (Korpus 20.11.2026). */
    private static final Set<String> STANDARD_ZUGFUNKTIONEN = Set.of("Licht vorn", "Licht hinten",
            "Licht hinten/vorn Innenseite aus", "Innenbeleuchtung", "Nummernschild", "Zuglaufschild");

    void weiterePruefungen() {
        // Funktionen in Zug- und Routenaktionen, die kein Fahrzeug des Zuges hat (Handbuch S. 58)
        List<List<String>> fehlend = new ArrayList<>();
        for (Anlage.Zug z : a.zuege) {
            if (!z.aktiv() || z.teile().isEmpty()) {
                continue;
            }
            Set<String> hat = new HashSet<>();
            boolean bekannt = true;
            for (Anlage.Teil t0 : z.teile()) {
                Set<String> f = t0.name() == null ? null : a.fahrzeugFunktionen.get(t0.name());
                if (f == null) {
                    bekannt = t0.art().equals("ANONYM") && bekannt;
                    continue;
                }
                hat.addAll(f);
            }
            if (!bekannt || hat.isEmpty()) {
                continue;
            }
            Map<String, Set<String>> stellen = new LinkedHashMap<>();
            for (String f : a.zugFunktionen.getOrDefault(z.name(), List.of())) {
                // Die Standardaktionen, die iTrain in jeden neuen Zug schreibt, sind ohne Wirkung, aber harmlos.
                if (!hat.contains(f) && !STANDARD_ZUGFUNKTIONEN.contains(f)) {
                    stellen.computeIfAbsent(f, k -> new LinkedHashSet<>()).add(t("Start/Stopp des Zuges",
                            "train start/stop"));
                }
            }
            for (String r : z.routen()) {
                Anlage.Route ro = a.routen.get(r);
                if (ro == null || !ro.aktiv()) {
                    continue;
                }
                for (String f : a.routenFunktionen.getOrDefault(r, List.of())) {
                    if (!hat.contains(f)) {
                        stellen.computeIfAbsent(f, k -> new LinkedHashSet<>()).add(t("Route ", "route ") + r);
                    }
                }
            }
            stellen.forEach((f, st) -> fehlend.add(List.of(z.name(),
                    f.isEmpty() ? t("(ohne Namen)", "(no name)") : f, String.join(", ", st))));
        }
        if (!fehlend.isEmpty()) {
            befund(3, Art.HINWEIS, t("Funktionen in Aktionen, die kein Fahrzeug des Zuges hat",
                    "Functions in actions that no vehicle of the train has"),
                    t("Aktionen mit Funktionen, die in den Loks und Wagen des Zuges nicht vorhanden sind, führt iTrain "
                            + "nicht aus. Handlung: im Zug- bzw. Routeneditor die passende Funktion wählen oder sie "
                            + "der Lok bzw. dem Wagen zuweisen.",
                            "Actions with functions that the train's locomotives and wagons do not have are not "
                                    + "carried out. Action: choose the matching function in the train or route "
                                    + "editor, or assign it to the locomotive or wagon."),
                    "S. 58", false, new Tabelle(List.of(t("Zug", "Train"), t("Funktion", "Function"),
                            t("Stelle", "Place")), fehlend));
        }
        // Bremsposition hinter oder auf der Stopp-Position (Handbuch S. 117)
        List<List<String>> bremse = new ArrayList<>();
        for (Anlage.Block b : a.bloecke.values()) {
            if (b.laenge() == null || b.laenge() <= 0) {
                continue;
            }
            for (Anlage.Richtung r : List.of(b.prev(), b.next())) {
                if (r.bremsen() == null || r.stopp() == null || r.bremsen() == 0 || r.stopp() == 0) {
                    continue;
                }
                double br = r.bremsen() > 0 ? r.bremsen() : b.laenge() + r.bremsen();
                double st = r.stopp() > 0 ? r.stopp() : b.laenge() + r.stopp();
                if (br > st - 0.5) {
                    bremse.add(List.of(b.name(), seite(r.seite()), zahl(br), zahl(st),
                            br > st + 0.5 ? t("Bremsen hinter Stopp", "brake beyond stop")
                                    : t("Bremsen = Stopp", "brake = stop")));
                }
            }
        }
        if (!bremse.isEmpty()) {
            befund(2, Art.HINWEIS, t("Bremsposition hinter oder auf der Stopp-Position",
                    "Brake position beyond or at the stop position"),
                    t("Die Bremsposition muss vor der Stopp-Position liegen, sonst bremst der Zug erst am Haltepunkt "
                            + "oder danach. Werte ab Blockanfang gerechnet.",
                            "The brake position must lie before the stop position, otherwise the train only brakes "
                                    + "at or after the stop point. Values measured from the block start."),
                    "S. 117", false, new Tabelle(List.of(t("Block", "Block"), t("Richtung", "Direction"),
                            t("Bremsen (cm)", "Brake (cm)"), t("Stopp (cm)", "Stop (cm)"),
                            t("Problem", "Problem")), bremse));
        }
        // Routen mit inaktiven Blöcken (Release Notes 6.0.0: inaktive Blöcke gelten als nicht vorhanden)
        List<List<String>> inaktiv = new ArrayList<>();
        for (Anlage.Route r : a.routen.values()) {
            if (!r.aktiv()) {
                continue;
            }
            for (Anlage.Eintrag x : r.eintraege()) {
                List<String> ina = vorhanden(x.bloecke()).stream().map(Anlage.BlockSeite::block).distinct()
                        .filter(n -> !a.bloecke.get(n).aktiv()).toList();
                if (!ina.isEmpty()) {
                    long alle = vorhanden(x.bloecke()).stream().map(Anlage.BlockSeite::block).distinct().count();
                    inaktiv.add(List.of(r.name(), x.name(), String.join(", ", ina),
                            ina.size() == alle ? t("kein Block übrig", "no block left")
                                    : t("Alternativen bleiben", "alternatives remain")));
                }
            }
        }
        if (!inaktiv.isEmpty()) {
            boolean sperrt = inaktiv.stream().anyMatch(z -> z.get(3).equals(t("kein Block übrig", "no block left")));
            befund(sperrt ? 1 : 4, sperrt ? Art.FEHLER : Art.HINWEIS, t("Routen mit inaktiven Blöcken",
                    "Routes with inactive blocks"),
                    t("Seit iTrain 6 werden inaktive Blöcke in Routen ignoriert, als gäbe es sie nicht. Bleibt für einen "
                            + "Eintrag kein Block übrig, findet die Route keinen Weg. Handlung: Block aktivieren oder "
                            + "die Route ändern.",
                            "Since iTrain 6 inactive blocks in routes are ignored as if they did not exist. If no block "
                                    + "is left for an item, the route finds no path. Action: activate the block or "
                                    + "change the route."),
                    "Release Notes 6.0.0", false, new Tabelle(List.of(t("Route", "Route"), t("Eintrag", "Item"),
                            t("inaktiv", "inactive"), t("Folge", "Effect")), inaktiv));
        }
        // Bahnhofsblöcke außerhalb eines Bahnhofs, Blöcke ohne jede Verbindung
        Set<String> imBahnhof = new HashSet<>();
        a.bahnhoefe.forEach(bh -> bh.bloecke().forEach(bs -> imBahnhof.add(bs.block())));
        Set<String> inRoute = new HashSet<>();
        a.routen.values().forEach(r -> r.eintraege().forEach(x -> x.bloecke().forEach(bs -> inRoute.add(bs.block()))));
        Set<String> genannt = new HashSet<>();
        a.bloecke.values().forEach(b -> List.of(b.prev(), b.next())
                .forEach(r -> r.nachbarn().forEach(n -> genannt.add(n.block()))));
        List<String> bahnhofsblock = new ArrayList<>();
        List<String> isoliert = new ArrayList<>();
        for (Anlage.Block b : a.bloecke.values()) {
            if ("station".equals(b.typ()) && !imBahnhof.contains(b.name()) && !a.bahnhoefe.isEmpty()) {
                bahnhofsblock.add(b.name());
            }
            if (b.prev().nachbarn().isEmpty() && b.next().nachbarn().isEmpty() && !genannt.contains(b.name())
                    && !inRoute.contains(b.name())) {
                isoliert.add(b.name());
            }
        }
        if (!bahnhofsblock.isEmpty()) {
            befund(0, Art.ANMERKUNG, t("Bahnhofsblöcke in keinem Bahnhof", "Station blocks in no station"),
                    t("Diese Blöcke haben den Typ \"Bahnhof\", gehören aber zu keinem Bahnhof: ",
                            "These blocks have the type \"Station\" but belong to no station: ")
                            + liste(bahnhofsblock) + t(". Routen mit Bahnhofseinträgen finden sie nicht.",
                                    ". Routes with station items do not find them."), "S. 111, S. 121", false, null);
        }
        if (!isoliert.isEmpty()) {
            befund(0, Art.ANMERKUNG, t("Blöcke ohne jede Verbindung", "Blocks without any connection"),
                    t("Diese Blöcke haben keinen Nachbarn, werden von keinem Block genannt und kommen in keiner Route "
                            + "vor (Rest einer Umplanung?): ", "These blocks have no neighbour, are named by no block "
                            + "and appear in no route (left over from a redesign?): ") + liste(isoliert) + ".",
                    "S. 120", true, null);
        }
        // Option "Kurzer Block" ohne NS-/NMBS-Signale (Handbuch S. 112)
        List<String> kurz = a.bloecke.values().stream().filter(b -> b.optionen().contains("short"))
                .map(Anlage.Block::name).toList();
        boolean nlBe = a.zubehoer.stream().anyMatch(z -> z.art().equals("signal") && z.typ() != null
                && (z.typ().startsWith("nl") || z.typ().startsWith("be")));
        if (!kurz.isEmpty() && !nlBe) {
            befund(0, Art.ANMERKUNG, t("Option \"Kurzer Block\" ohne NS- oder NMBS-Signale",
                    "Option \"Short block\" without NS or NMBS signals"),
                    t("Die Option wirkt nur bei den Signalsystemen NS und NMBS; hier bewirkt sie nichts (unschädlich): ",
                            "The option only has an effect with the NS and NMBS signal systems; here it does nothing "
                                    + "(harmless): ") + liste(kurz) + ".", "S. 112", false, null);
        }
        // Namen mit Leerzeichen am Anfang oder Ende
        if (!a.leerzeichenNamen.isEmpty()) {
            befund(0, Art.ANMERKUNG, t("Namen mit Leerzeichen am Anfang oder Ende",
                    "Names with spaces at the start or end"),
                    t("Solche Namen sind in Listen kaum zu unterscheiden und führen leicht zu Verwechslungen: ",
                            "Such names are hard to tell apart in lists and easily lead to mix-ups: ")
                            + liste(a.leerzeichenNamen) + ".", "S. 116", false, null);
        }
    }

    // --- Signale im Blockeditor (Signalregeln V01, Regeln 2B–2D, 3A) -------------------------------------------------

    private enum Rolle { HAUPT, MEHRFACH, VOR, RANGIER }

    /** Rolle deutscher Signaltypen; unbekannte Typen (andere Länder) bleiben ungeprüft. */
    private static Rolle rolle(String typ) {
        if (typ == null) {
            return null;
        }
        String t0 = typ.endsWith("f") ? typ.substring(0, typ.length() - 1) : typ;
        return switch (t0) {
            case "de_hp0_1", "de_hp0_2", "de_hp0_1_2" -> Rolle.HAUPT;
            case "de_hp0_1_2_sh1", "de_ks_h", "de_ks_m", "de_hl_m" -> Rolle.MEHRFACH;
            case "de_vr0_1_2", "de_ks_v" -> Rolle.VOR;
            case "de_sh0_1" -> Rolle.RANGIER;
            default -> null;
        };
    }

    /** Signal im falschen Feld des Blockeditors, z. B. Mehrfach-Signal zusätzlich im Feld Rangiersignal. */
    void signalfelder() {
        Map<String, String> typ = new HashMap<>();
        a.zubehoer.stream().filter(z -> z.art().equals("signal")).forEach(z -> typ.put(z.name(), z.typ()));
        List<List<String>> z = new ArrayList<>();
        Set<String> ungeprueft = new TreeSet<>();
        Map<String, List<String>> hauptIn = new LinkedHashMap<>();
        for (Anlage.Block b : a.bloecke.values()) {
            for (Anlage.Richtung r : List.of(b.prev(), b.next())) {
                for (String s : r.signale()) {
                    String[] f = s.split(":", 2);
                    String feld = f[0];
                    String name = f[1];
                    if (feld.equals("signal")) {
                        hauptIn.computeIfAbsent(name, k -> new ArrayList<>()).add(b.name() + " (" + seite(r.seite()) + ")");
                    }
                    Rolle ro = rolle(typ.get(name));
                    if (ro == null) {
                        if (typ.containsKey(name)) {
                            ungeprueft.add(typ.get(name));
                        }
                        continue;
                    }
                    String problem = switch (feld) {
                        case "signal" -> ro == Rolle.VOR || ro == Rolle.RANGIER
                                ? t("im Feld Hauptsignal steht kein Hauptsignal", "no home signal in the home field")
                                : null;
                        case "shunt-signal" -> ro == Rolle.MEHRFACH
                                ? t("Mehrfach-Signal gehört nur in das Feld Hauptsignal",
                                        "a multiple signal belongs only in the home field")
                                : ro != Rolle.RANGIER ? t("im Feld Rangiersignal steht kein Rangiersignal",
                                        "no shunting signal in the shunting field") : null;
                        case "warning-signal", "side-warning-signal" -> ro != Rolle.VOR
                                ? t("im Feld Vorsignal steht kein Vorsignal", "no distant signal in the distant field")
                                : null;
                        default -> null;
                    };
                    if (problem != null) {
                        z.add(List.of(name, typ.get(name), b.name(), seite(r.seite()), feldName(feld), problem));
                    }
                }
            }
        }
        if (!z.isEmpty()) {
            befund(4, Art.FEHLER, t("Signal im falschen Feld des Blockeditors", "Signal in the wrong block field"),
                    t("Diese Signale stehen in einem Feld, das nicht zu ihrer Rolle passt. Ein Mehrfach-Signal "
                            + "(z. B. Ausfahrsignal mit Sh1, Ks-Signal) gehört nur in das Feld \"Hauptsignal\"; "
                            + "seine Rangier- und Vorsignalrolle ordnet iTrain selbst zu. Handlung: im Blockeditor "
                            + "auf dem Richtungs-Tab das falsche Feld leeren bzw. das Signal in das richtige Feld "
                            + "eintragen. Für die KI: gehört bei gewähltem Zusatz 3 in die Signalprüfung.",
                            "These signals are in a field that does not match their role. A multiple signal (e.g. "
                                    + "exit signal with Sh1, Ks signal) belongs only in the field \"Home signal\"; "
                                    + "iTrain assigns its shunting and distant role itself. Action: in the block "
                                    + "editor on the direction tab clear the wrong field or enter the signal in the "
                                    + "right field. For the AI: belongs in the signal check if extra 3 is chosen."),
                    "S. 118–119", false, new Tabelle(List.of(t("Signal", "Signal"), t("Typ", "Type"),
                            t("Block", "Block"), t("Richtung", "Direction"), t("Feld", "Field"),
                            t("Problem", "Problem")), z));
        } else if (!typ.isEmpty()) {
            long n = typ.values().stream().filter(x -> rolle(x) != null).count();
            if (n > 0) {
                ohne.add(t("Signale: Feld im Blockeditor passt zur Rolle (" + n + " Signale).",
                        "Signals: block field matches the role (" + n + " signals)."));
            }
        }
        List<String> mehrfach = new ArrayList<>();
        hauptIn.forEach((n, l) -> {
            if (l.size() > 1) {
                mehrfach.add(n + ": " + String.join(", ", l));
            }
        });
        if (!mehrfach.isEmpty()) {
            befund(0, Art.ANMERKUNG, t("Hauptsignal in mehreren Blöcken oder Richtungen",
                    "Home signal in several blocks or directions"),
                    t("Dasselbe Signal steht im Feld \"Hauptsignal\" mehrerer Blockrichtungen (z. B. "
                            + "Gruppenausfahrsignal; die Signalregeln behandeln das nicht): ",
                            "The same signal is in the field \"Home signal\" of several block directions (e.g. group "
                                    + "exit signal; the signal rules do not cover this): ") + String.join("; ", mehrfach)
                            + ".", "S. 118", false, null);
        }
        if (!ungeprueft.isEmpty()) {
            ohne.add(t("Signaltypen ohne Feldprüfung (Rolle nicht hinterlegt): ",
                    "Signal types without field check (role not known): ") + String.join(", ", ungeprueft) + ".");
        }
    }

    private String feldName(String feld) {
        return switch (feld) {
            case "signal" -> t("Hauptsignal", "Home signal");
            case "shunt-signal" -> t("Rangiersignal", "Shunting signal");
            case "warning-signal" -> t("Vorsignal (Signal)", "Distant signal (Signal)");
            case "side-warning-signal" -> t("Vorsignal (Block)", "Distant signal (Block)");
            default -> feld;
        };
    }

    private List<Anlage.BlockSeite> vorhanden(List<Anlage.BlockSeite> l) {
        return l.stream().filter(b -> a.bloecke.containsKey(b.block())).toList();
    }

    private static Set<String> blocke(List<Anlage.BlockSeite> l) {
        return l.stream().map(Anlage.BlockSeite::block).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean benachbart(String x, String y) {
        Anlage.Block b = a.bloecke.get(x);
        Anlage.Block c = a.bloecke.get(y);
        return b != null && (nennt(b, y) || c != null && nennt(c, x));
    }

    private static boolean nennt(Anlage.Block b, String name) {
        return b.prev().nachbarn().stream().anyMatch(n -> n.block().equals(name))
                || b.next().nachbarn().stream().anyMatch(n -> n.block().equals(name));
    }

    /** Kürzester Weg im ungerichteten Blockgraphen (Liste der Blöcke) oder null. */
    private List<String> wegUngerichtet(Set<String> von, Set<String> nach) {
        Map<String, String> vor = new HashMap<>();
        Deque<String> q = new ArrayDeque<>();
        for (String v : von) {
            vor.put(v, null);
            q.add(v);
        }
        Map<String, Set<String>> adj = adjazenz();
        while (!q.isEmpty()) {
            String x = q.poll();
            if (nach.contains(x)) {
                List<String> w = new ArrayList<>();
                for (String y = x; y != null; y = vor.get(y)) {
                    w.add(0, y);
                }
                return w;
            }
            for (String n : adj.getOrDefault(x, Set.of())) {
                if (!vor.containsKey(n)) {
                    vor.put(n, x);
                    q.add(n);
                }
            }
        }
        return null;
    }

    private Map<String, Set<String>> adjazenzCache;

    private Map<String, Set<String>> adjazenz() {
        if (adjazenzCache == null) {
            adjazenzCache = new HashMap<>();
            for (Anlage.Block b : a.bloecke.values()) {
                for (Anlage.Richtung r : List.of(b.prev(), b.next())) {
                    for (Anlage.Nachbar n : r.nachbarn()) {
                        if (a.bloecke.containsKey(n.block())) {
                            adjazenzCache.computeIfAbsent(b.name(), k -> new LinkedHashSet<>()).add(n.block());
                            adjazenzCache.computeIfAbsent(n.block(), k -> new LinkedHashSet<>()).add(b.name());
                        }
                    }
                }
            }
        }
        return adjazenzCache;
    }

    /**
     * Gerichtete Erreichbarkeit: Knoten = (Block, Seite, über die der Zug den Block verlässt). Kante von (A, s) zu
     * jedem Nachbarn N in A.s; der Zug betritt N auf der Seite, die bei N angegeben ist, und verlässt ihn auf der
     * anderen. Richtungswechsel im Block, wenn die Route ihn erlaubt und der Block ihn nicht verbietet.
     */
    private boolean erreichbar(Anlage.Route r, List<Anlage.BlockSeite> von, List<Anlage.BlockSeite> nach,
            boolean wechsel) {
        Set<Knoten> ziel = new HashSet<>();
        for (Anlage.BlockSeite n : nach) {
            for (String s : seiten(n.seite())) {
                ziel.add(new Knoten(n.block(), s));
            }
        }
        Set<Knoten> gesehen = new HashSet<>();
        Deque<Knoten> q = new ArrayDeque<>();
        for (Anlage.BlockSeite v : von) {
            for (String s : seiten(v.seite())) {
                Knoten k = new Knoten(v.block(), s);
                if (gesehen.add(k)) {
                    q.add(k);
                }
            }
        }
        while (!q.isEmpty()) {
            Knoten k = q.poll();
            Anlage.Block b = a.bloecke.get(k.block());
            if (b == null) {
                continue;
            }
            List<Knoten> weiter = new ArrayList<>();
            for (Anlage.Nachbar n : b.richtung(k.seite()).nachbarn()) {
                if (!a.bloecke.containsKey(n.block())) {
                    continue;
                }
                if (n.seite() == null || n.seite().equals("both")) {
                    weiter.add(new Knoten(n.block(), "prev"));
                    weiter.add(new Knoten(n.block(), "next"));
                } else {
                    weiter.add(new Knoten(n.block(), andere(n.seite())));
                }
            }
            for (Knoten w : weiter) {
                if (ziel.contains(w)) {
                    return true;
                }
                Anlage.Block wb = a.bloecke.get(w.block());
                List<Knoten> mit = new ArrayList<>(List.of(w));
                if (wechsel && (r.rangieren() || !wb.richtungswechselVerboten())) {
                    Knoten u = new Knoten(w.block(), andere(w.seite()));
                    if (ziel.contains(u)) {
                        return true;
                    }
                    mit.add(u);
                }
                for (Knoten m : mit) {
                    if (gesehen.add(m)) {
                        q.add(m);
                    }
                }
            }
        }
        return false;
    }

    private static List<String> seiten(String s) {
        return s == null || s.equals("both") ? List.of("prev", "next") : List.of(s);
    }

    private static String andere(String s) {
        return "prev".equals(s) ? "next" : "prev";
    }

    // --- Zusatz 1: Zugtabelle -------------------------------------------------------------------------------------

    private Tabelle zugtabelle(List<String> hinweise) {
        List<List<String>> z = new ArrayList<>();
        List<String> unsicher = new ArrayList<>();
        List<Anlage.Zug> zs = new ArrayList<>(a.zuege);
        zs.sort(Comparator.comparing((Anlage.Zug x) -> !x.aktiv()).thenComparing(x -> x.name().toLowerCase(Locale.ROOT)));
        for (Anlage.Zug x : zs) {
            String name = x.name() + (x.aktiv() ? "" : " (" + t("inaktiv", "inactive") + ")");
            String g = x.gattung() == null ? "–" : x.gattung();
            if (x.teile().isEmpty()) {
                z.add(List.of(name, g, t("leer", "empty"), "", "", "0"));
                continue;
            }
            double lok = 0;
            double wag = 0;
            boolean lokFehlt = false;
            boolean wagFehlt = false;
            for (Anlage.Teil teil : x.teile()) {
                boolean istLok = teil.art().equals("LOK");
                if (teil.laenge() == null || teil.laenge() <= 0) {
                    if (istLok) {
                        lokFehlt = true;
                    } else {
                        wagFehlt = true;
                    }
                } else if (istLok) {
                    lok += teil.laenge();
                } else {
                    wag += teil.laenge();
                }
            }
            String fehlt = t("fehlt", "missing");
            boolean fehlend = lokFehlt || wagFehlt;
            if (fehlend) {
                unsicher.add(x.name());
            }
            z.add(List.of(name, g, lokFehlt ? fehlt : zahl(lok), wagFehlt ? fehlt : zahl(wag),
                    (fehlend ? "≥ " : "") + zahl(lok + wag), String.valueOf(x.teile().size())));
        }
        if (!unsicher.isEmpty()) {
            hinweise.add(t("Wegen fehlender Längen nicht sicher bewertbar: ",
                    "Cannot be assessed with certainty because of missing lengths: ") + liste(unsicher) + ".");
        }
        hinweise.add(t("Längen über Puffer. Quellen: Handbuch S. 42, S. 52, S. 57.",
                "Lengths over buffers. Sources: manual p. 42, p. 52, p. 57."));
        return new Tabelle(List.of(t("Zug", "Train"), t("Zuggattung", "Train type"), t("Loks (cm)", "Locos (cm)"),
                t("Wagen (cm)", "Wagons (cm)"), t("Gesamt (cm)", "Total (cm)"), t("Fahrzeuge", "Vehicles")), z);
    }

    // --- Zusatz 2: Bahnsteige -------------------------------------------------------------------------------------

    private Tabelle bahnsteige(List<String> hinweise) {
        Map<String, String> bahnhof = new HashMap<>();
        for (Anlage.Bahnhof bh : a.bahnhoefe) {
            for (Anlage.BlockSeite bs : bh.bloecke()) {
                bahnhof.putIfAbsent(bs.block(), bh.name());
            }
        }
        boolean irgendeiner = a.bloecke.values().stream()
                .anyMatch(b -> b.prev().bahnsteig() != null || b.next().bahnsteig() != null);
        List<List<String>> z = new ArrayList<>();
        double maxGanz = 0;
        for (Anlage.Block b : a.bloecke.values()) {
            if (b.laenge() == null || b.laenge() <= 0) {
                continue;
            }
            for (Anlage.Richtung r : List.of(b.prev(), b.next())) {
                if (irgendeiner ? r.bahnsteig() == null : !"station".equals(b.typ())) {
                    continue;
                }
                double l = b.laenge();
                double gz = r.grenzzeichen() == null ? 0 : r.grenzzeichen();
                boolean stoppGesetzt = r.stopp() != null && r.stopp() != 0;
                double stopp = !stoppGesetzt ? l : r.stopp() > 0 ? r.stopp() : l + r.stopp();
                double nutzbar = stopp - gz;
                List<String> h = new ArrayList<>();
                if (!stoppGesetzt) {
                    h.add(t("Stopp nicht gesetzt", "stop not set"));
                }
                String bh = bahnhof.getOrDefault(b.name(), "–");
                if (!irgendeiner) {
                    z.add(List.of(bh, b.name(), seite(r.seite()), zahl(l), zahl(nutzbar)));
                    continue;
                }
                double ps = r.bahnsteig().start();
                double pe = ps + r.bahnsteig().laenge();
                double ganz = Math.max(0, Math.min(pe, stopp) - Math.max(ps, gz));
                maxGanz = Math.max(maxGanz, ganz);
                if (pe > l + 0.5) {
                    h.add(t("ragt über das Blockende", "extends beyond the block end"));
                }
                if (ps < gz - 0.5) {
                    h.add(t("beginnt vor dem Grenzzeichen", "starts before the clearance point"));
                }
                if (pe > stopp + 0.5) {
                    h.add(t("endet hinter der Stopp-Position", "ends beyond the stop position"));
                }
                if (r.bahnsteig().laenge() > nutzbar + 0.5) {
                    h.add(t("länger als nutzbar", "longer than usable"));
                }
                z.add(List.of(bh, b.name(), seite(r.seite()), zahl(l), zahl(nutzbar), zahl(ps) + "–" + zahl(pe),
                        zahl(ganz), zahl(nutzbar), String.join("; ", h)));
            }
        }
        List<String> ohneAusrichtung = a.gattungen.values().stream()
                .filter(g -> !g.optionen().contains("platform_align")).map(Anlage.Gattung::name).toList();
        if (!ohneAusrichtung.isEmpty()) {
            hinweise.add(t("Zuggattungen ohne \"Zug am Bahnsteig ausrichten\" (Züge halten am Blockende): ",
                    "Train types without \"Align train at platform\" (trains stop at the block end): ")
                    + liste(ohneAusrichtung) + t(" (Handbuch S. 55).", " (manual p. 55)."));
        }
        if (!irgendeiner) {
            hinweise.add(0, t("Keine Bahnsteige eingetragen. Die Tabelle nennt je Bahnhofsblock die nutzbare Länge.",
                    "No platforms entered. The table gives the usable length per station block."));
            return new Tabelle(List.of(t("Bahnhof", "Station"), t("Block", "Block"), t("Richtung", "Direction"),
                    t("Block (cm)", "Block (cm)"), t("max. im Block (cm)", "max. in block (cm)")), z);
        }
        if (maxGanz > 0) {
            double mg = maxGanz;
            List<String> lang = new ArrayList<>();
            for (Anlage.Zug x : a.zuege) {
                double s = x.teile().stream().filter(tt -> tt.laenge() != null).mapToDouble(Anlage.Teil::laenge).sum();
                if (x.aktiv() && s > mg + 0.5) {
                    lang.add(x.name() + " (" + zahl(s) + " cm)");
                }
            }
            if (!lang.isEmpty()) {
                hinweise.add(t("Aktive Züge, die länger sind als der längste Bahnsteigbereich (" + zahl(mg)
                        + " cm, ganz am Bahnsteig); sie können trotzdem halten, wenn sie in den Block passen: ",
                        "Active trains longer than the longest platform area (" + zahl(mg) + " cm, fully at the "
                                + "platform); they can still stop if they fit into the block: ") + liste(lang) + ".");
            }
        }
        hinweise.add(t("Quellen: Handbuch S. 117, S. 121, S. 55.", "Sources: manual p. 117, p. 121, p. 55."));
        return new Tabelle(List.of(t("Bahnhof", "Station"), t("Block", "Block"), t("Richtung", "Direction"),
                t("Block (cm)", "Block (cm)"), t("nutzbar (cm)", "usable (cm)"),
                t("Bahnsteig von–bis (cm)", "Platform from–to (cm)"),
                t("max. ganz am Bahnsteig (cm)", "max. fully at platform (cm)"),
                t("max. im Block (cm)", "max. in block (cm)"), t("Hinweis", "Note")), z);
    }

    // --- Ausgabe --------------------------------------------------------------------------------------------------

    /** Schreibt die Vorprüfung als Text für die KI. */
    public static Path schreibe(Ergebnis e, Path ordner, String programmVersion) throws IOException {
        Path ziel = ordner.resolve(e.deutsch() ? DATEINAME : DATEINAME_EN);
        Files.writeString(ziel, alsText(e, programmVersion), StandardCharsets.UTF_8);
        return ziel;
    }

    public static String alsText(Ergebnis e, String programmVersion) {
        boolean de = e.deutsch();
        StringBuilder sb = new StringBuilder();
        sb.append(de ? "VORPRÜFUNG DURCH DAS PROGRAMM iTrain-Analyse " : "PRE-CHECK BY THE PROGRAM iTrain-Analyse ")
                .append(programmVersion).append('\n');
        sb.append(de ? "Projektdatei: " : "Project file: ").append(e.datei()).append('\n');
        sb.append(de ? "Für die KI: Regel P12 des Prompts. Die Werte stammen aus der Projektdatei und sind nach dem "
                + "Prüfkatalog des Prompts berechnet. Übernimm sie, prüfe drei Befunde stichprobenhaft an den "
                + "Rohdaten und ergänze die Prüfungen, die hier nicht stehen."
                : "For the AI: rule P12 of the prompt. The values come from the project file and are calculated "
                        + "according to the checklist of the prompt. Adopt them, spot-check three findings against "
                        + "the raw data and add the checks that are not listed here.").append("\n\n");
        sb.append(de ? "EINGANGSCHECK\n" : "ENTRY CHECK\n");
        for (String[] z : e.eingangscheck()) {
            sb.append(z[0]).append(": ").append(z[1]).append('\n');
        }
        sb.append('\n').append(de ? "BEFUNDE\n" : "FINDINGS\n");
        if (e.befunde().isEmpty()) {
            sb.append(de ? "Keine.\n" : "None.\n");
        }
        int nr = 0;
        for (Befund b : e.befunde()) {
            nr++;
            sb.append('V').append(nr).append(" | ").append(b.stufe() == 0 ? (de ? "Anmerkungen" : "Notes")
                    : (de ? "Stufe " : "Stage ") + b.stufe()).append(" | ").append(art(b.art(), de))
                    .append(b.vermutlich() ? (de ? " (vermutlich)" : " (probably)") : "").append(" | ")
                    .append(b.titel()).append('\n');
            sb.append("   ").append(b.text()).append('\n');
            if (b.tabelle() != null) {
                tabelle(sb, b.tabelle(), "   ");
            }
            sb.append("   ").append(de ? "(Quelle: Handbuch " : "(Source: manual ")
                    .append(de ? b.quelle() : b.quelle().replace("S.", "p.")).append(")\n");
        }
        sb.append('\n').append(de ? "OHNE BEFUND\n" : "NO FINDINGS\n");
        for (String o : e.ohneBefund()) {
            sb.append("- ").append(o).append('\n');
        }
        if (e.zugtabelle() != null) {
            sb.append('\n').append(de ? "ZUSATZ 1: ZUGTABELLE\n" : "EXTRA 1: TRAIN TABLE\n");
            tabelle(sb, e.zugtabelle(), "");
            e.zugHinweise().forEach(h -> sb.append(h).append('\n'));
        }
        if (e.bahnsteige() != null) {
            sb.append('\n').append(de ? "ZUSATZ 2: BAHNSTEIGE, BLOCKLÄNGEN UND HALTEPUNKTE\n"
                    : "EXTRA 2: PLATFORMS, BLOCK LENGTHS AND STOP POINTS\n");
            tabelle(sb, e.bahnsteige(), "");
            e.bahnsteigHinweise().forEach(h -> sb.append(h).append('\n'));
        }
        sb.append('\n').append(de ? "NICHT IM PROGRAMM GEPRÜFT (Aufgabe der KI): Zugoptionen und Eventintervalle, "
                + "Release Notes, Aktionen, \"Kritisch\" an Blöcken, Blocktyp und Blockoptionen im Zusammenhang, Weichen-"
                + "Belegtmelder, Geschwindigkeitsprofile fachlich, Zusatz 3 Signale (außer Feld im Blockeditor), Zusatzauftrag.\n"
                : "NOT CHECKED BY THE PROGRAM (task of the AI): train options and event intervals, release notes, "
                        + "actions, \"Critical\" on blocks, block type and block options in context, turnout occupancy "
                        + "feedbacks, speed profiles, extra 3 signals (except block field), additional task.\n");
        return sb.toString();
    }

    /** Lesefassung für das Fenster der Kurzprüfung: Befunde nach Stufen, ohne Hinweise an die KI. */
    public static String uebersicht(Ergebnis e) {
        boolean de = e.deutsch();
        StringBuilder sb = new StringBuilder();
        if (e.befunde().isEmpty()) {
            sb.append(de ? "Keine Befunde.\n" : "No findings.\n");
        }
        int nr = 0;
        for (Befund b : e.befunde()) {
            nr++;
            sb.append(nr).append(". ").append(b.titel()).append("  (")
                    .append(b.stufe() == 0 ? (de ? "Anmerkung" : "note") : (de ? "Stufe " : "stage ") + b.stufe())
                    .append(", ").append(art(b.art(), de)).append(b.vermutlich() ? (de ? ", vermutlich" : ", probably")
                            : "").append(")\n");
            sb.append("   ").append(b.text()).append('\n');
            if (b.tabelle() != null) {
                tabelle(sb, b.tabelle(), "   ");
            }
            sb.append("   ").append(de ? "Quelle: Handbuch " : "Source: manual ")
                    .append(de ? b.quelle() : b.quelle().replace("S.", "p.")).append("\n\n");
        }
        sb.append(de ? "OHNE BEFUND\n" : "NO FINDINGS\n");
        for (String o : e.ohneBefund()) {
            sb.append("- ").append(o).append('\n');
        }
        return sb.toString();
    }

    private static void tabelle(StringBuilder sb, Tabelle t, String einzug) {
        sb.append(einzug).append(String.join(" | ", t.kopf())).append('\n');
        for (List<String> z : t.zeilen()) {
            sb.append(einzug).append(String.join(" | ", z)).append('\n');
        }
    }

    static String art(Art a, boolean de) {
        return switch (a) {
            case FEHLER -> de ? "Fehler" : "Error";
            case HINWEIS -> de ? "Hinweis" : "Warning";
            case ANMERKUNG -> de ? "Anmerkung" : "Note";
        };
    }

    /** Alle Handbuchseiten, die in den Befunden als Quelle stehen. */
    public static Set<Integer> seiten(Ergebnis e) {
        Set<Integer> s = new TreeSet<>();
        for (Befund b : e.befunde()) {
            s.addAll(HandbuchAuszug.seitenAus(b.quelle()));
        }
        return s;
    }

    // --- Hilfen ---------------------------------------------------------------------------------------------------

    private void befund(int stufe, Art art, String titel, String text, String quelle, boolean vermutlich,
            Tabelle tabelle) {
        befunde.add(new Befund(stufe, art, titel, text, quelle, vermutlich, tabelle));
    }

    private String t(String deutsch, String englisch) {
        return de ? deutsch : englisch;
    }

    private String seite(String s) {
        if (s == null) {
            return "–";
        }
        return switch (s) {
            case "prev" -> t("Vorherige", "Previous");
            case "next" -> t("Nächste", "Next");
            default -> s;
        };
    }

    private static String liste(List<String> l) {
        if (l.size() <= 40) {
            return String.join(", ", l);
        }
        return String.join(", ", l.subList(0, 40)) + " … (+" + (l.size() - 40) + ")";
    }

    static String zahl(Double d) {
        if (d == null) {
            return "?";
        }
        if (Math.abs(d - Math.rint(d)) < 0.05) {
            return String.valueOf((long) Math.rint(d));
        }
        return String.format(Locale.ROOT, "%.1f", d);
    }

    private static String zahl(double d) {
        return zahl(Double.valueOf(d));
    }
}
