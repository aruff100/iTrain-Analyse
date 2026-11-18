package com.example.itrain_analyse;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Prüfbericht als PDF ohne KI (Anwender 18.11.2026, Variante B als Option): die Vorprüfung in der Gliederung des
 * Analysedokuments. A4 hochkant, Ränder 2 cm, Fußzeile mit Seitenzahl und Projektdatei. Schrift Helvetica
 * (WinAnsi); Zeichen außerhalb davon werden ersetzt.
 */
public final class Pruefbericht {

    private static final float RAND = 56.7f;
    private static final PDRectangle A4 = PDRectangle.A4;
    private static final float BREITE = A4.getWidth() - 2 * RAND;

    private final PDDocument doc = new PDDocument();
    private final PDFont normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont fett = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final PDFont kursiv = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
    private PDPageContentStream cs;
    private float y;

    private Pruefbericht() {
    }

    public static Path schreibe(Vorpruefung.Ergebnis e, Path ordner, String programmVersion) throws IOException {
        String basis = e.datei().replaceFirst("(?i)\\.tcd(\\.xml)?$", "");
        Path ziel = ordner.resolve((e.deutsch() ? "Pruefbericht_" : "Check_report_") + basis + ".pdf");
        Pruefbericht p = new Pruefbericht();
        try {
            p.inhalt(e, programmVersion);
            p.fusszeilen(e);
            p.doc.save(ziel.toFile());
        } finally {
            p.doc.close();
        }
        return ziel;
    }

    private void inhalt(Vorpruefung.Ergebnis e, String version) throws IOException {
        boolean de = e.deutsch();
        neueSeite();
        absatz(de ? "Prüfbericht iTrain-Analyse" : "iTrain-Analyse check report", fett, 18, 6);
        absatz((de ? "Projektdatei: " : "Project file: ") + e.datei() + " · iTrain " + e.version() + " · "
                + DateTimeFormatter.ofPattern(de ? "dd.MM.yyyy" : "yyyy-MM-dd").format(LocalDate.now()) + " · "
                + (de ? "Programm " : "program ") + version, normal, 9, 8);
        kasten(de ? "Diesen Bericht hat das Programm iTrain-Analyse ohne KI aus der Projektdatei berechnet. Er "
                + "enthält die Prüfungen nach festen Regeln. Aktionen, Zusammenhänge, Zusatz 3 Signale und "
                + "Erklärungen liefert die erweiterte Analyse durch die KI. Die Seitenangaben beziehen sich auf das "
                + "deutsche PDF-Handbuch iTrain 6.0 (252 Seiten)."
                : "The program iTrain-Analyse calculated this report from the project file without AI. It contains "
                        + "the rule-based checks. Actions, relationships, extra 3 signals and explanations come "
                        + "from the extended analysis by the AI. Page references refer to the German PDF manual "
                        + "iTrain 6.0 (252 pages).");
        ueberschrift(de ? "Eingangscheck" : "Entry check");
        List<List<String>> ec = new ArrayList<>();
        for (String[] z : e.eingangscheck()) {
            ec.add(List.of(z[0], z[1]));
        }
        tabelle(new Vorpruefung.Tabelle(List.of(de ? "Angabe" : "Item", de ? "Wert" : "Value"), ec));
        String[] stufen = de
                ? new String[] {"Stufe 1: Damit kann der Betrieb nicht oder nur gestört laufen",
                        "Stufe 2: Blockgeometrie und Rückmelder", "Stufe 3: Fahrzeuge und Züge",
                        "Stufe 4: Routen, Aktionen und Kleinigkeiten"}
                : new String[] {"Stage 1: Operation cannot run or only with disruptions",
                        "Stage 2: Block geometry and feedbacks", "Stage 3: Vehicles and trains",
                        "Stage 4: Routes, actions and minor issues"};
        int nr = 0;
        for (int s = 1; s <= 4; s++) {
            ueberschrift(stufen[s - 1]);
            nr = befunde(e, s, nr);
        }
        ueberschrift(de ? "Anmerkungen (keine Fehler)" : "Notes (not errors)");
        nr = befunde(e, 0, nr);
        ueberschrift(de ? "Ohne Befund" : "No findings");
        for (String o : e.ohneBefund()) {
            absatz("– " + o, normal, 10, 2);
        }
        if (e.zugtabelle() != null) {
            ueberschrift(de ? "Zusatz 1: Zugtabelle" : "Extra 1: Train table");
            tabelle(e.zugtabelle());
            e.zugHinweise().forEach(h -> absatzQuiet(h));
        }
        if (e.bahnsteige() != null) {
            ueberschrift(de ? "Zusatz 2: Bahnsteige, Blocklängen und Haltepunkte"
                    : "Extra 2: Platforms, block lengths and stop points");
            tabelle(e.bahnsteige());
            e.bahnsteigHinweise().forEach(h -> absatzQuiet(h));
        }
        ueberschrift(de ? "Nicht im Programm geprüft" : "Not checked by the program");
        absatz(de ? "Zugoptionen und Eventintervalle, Release Notes, Aktionen, \"Kritisch\" an Blöcken, Blocktyp und "
                + "Blockoptionen im Zusammenhang, Weichen-Belegtmelder, Geschwindigkeitsprofile fachlich, Zusatz 3 "
                + "Signale und ein eigener Auftrag an die KI. Dafür das Analyse-Paket in den Chat geben."
                : "Train options and event intervals, release notes, actions, \"Critical\" on blocks, block type and block "
                        + "options in context, turnout occupancy feedbacks, speed profiles, extra 3 signals and "
                        + "an additional task for the AI. For these, put the analysis package into the chat.",
                normal, 10, 4);
        cs.close();
    }

    /**
     * Druckt den Prüfbericht: Seiten mit PDFBox als Bild rendern und über den JavaFX-Druckdialog ausgeben (ohne
     * zusätzliches Modul javafx.swing).
     */
    public static void drucken(javafx.stage.Window owner, Path pdf) {
        javafx.print.PrinterJob job = javafx.print.PrinterJob.createPrinterJob();
        if (job == null || !job.showPrintDialog(owner)) {
            return;
        }
        javafx.print.PageLayout pl = job.getJobSettings().getPageLayout();
        try (PDDocument d = org.apache.pdfbox.Loader.loadPDF(pdf.toFile())) {
            org.apache.pdfbox.rendering.PDFRenderer r = new org.apache.pdfbox.rendering.PDFRenderer(d);
            for (int i = 0; i < d.getNumberOfPages(); i++) {
                java.awt.image.BufferedImage b = r.renderImageWithDPI(i, 150);
                javafx.scene.image.WritableImage w = new javafx.scene.image.WritableImage(b.getWidth(), b.getHeight());
                javafx.scene.image.PixelWriter pw = w.getPixelWriter();
                for (int y = 0; y < b.getHeight(); y++) {
                    for (int x = 0; x < b.getWidth(); x++) {
                        pw.setArgb(x, y, b.getRGB(x, y));
                    }
                }
                javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(w);
                iv.setPreserveRatio(true);
                iv.setFitWidth(pl.getPrintableWidth());
                iv.setFitHeight(pl.getPrintableHeight());
                if (!job.printPage(pl, iv)) {
                    break;
                }
            }
            job.endJob();
        } catch (IOException ex) {
            job.cancelJob();
        }
    }

    private int befunde(Vorpruefung.Ergebnis e, int stufe, int nr) throws IOException {
        boolean de = e.deutsch();
        boolean keiner = true;
        for (Vorpruefung.Befund b : e.befunde()) {
            if (b.stufe() != stufe) {
                continue;
            }
            keiner = false;
            nr++;
            platz(60);
            absatz(nr + ". " + b.titel() + " (" + Vorpruefung.art(b.art(), de)
                    + (b.vermutlich() ? (de ? ", vermutlich" : ", probably") : "") + ")", fett, 10.5f, 2);
            absatz(b.text(), normal, 10, 3);
            if (b.tabelle() != null) {
                tabelle(b.tabelle());
            }
            absatz((de ? "(Quelle: Handbuch " : "(Source: manual ")
                    + (de ? b.quelle() : b.quelle().replace("S.", "p.")) + ")", kursiv, 9, 8);
        }
        if (keiner) {
            absatz(stufe == 0 ? (de ? "Keine Anmerkungen." : "No notes.")
                    : (de ? "Alles in Ordnung, ohne Befund." : "All in order, no findings."), normal, 10, 6);
        }
        return nr;
    }

    // --- Satz ---------------------------------------------------------------------------------------------------

    private void neueSeite() throws IOException {
        if (cs != null) {
            cs.close();
        }
        PDPage p = new PDPage(A4);
        doc.addPage(p);
        cs = new PDPageContentStream(doc, p);
        y = A4.getHeight() - RAND;
    }

    private void platz(float hoehe) throws IOException {
        if (y - hoehe < RAND + 20) {
            neueSeite();
        }
    }

    private void ueberschrift(String text) throws IOException {
        platz(50);
        y -= 8;
        absatz(text, fett, 13, 5);
    }

    private void absatzQuiet(String text) {
        try {
            absatz(text, normal, 9.5f, 3);
        } catch (IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
    }

    private void absatz(String text, PDFont f, float groesse, float danach) throws IOException {
        for (String zeile : umbrechen(sicher(text, f), f, groesse, BREITE)) {
            platz(groesse * 1.3f);
            schreib(zeile, f, groesse, RAND, y - groesse);
            y -= groesse * 1.3f;
        }
        y -= danach;
    }

    private void kasten(String text) throws IOException {
        List<String> z = umbrechen(sicher(text, normal), normal, 9.5f, BREITE - 12);
        float h = z.size() * 9.5f * 1.3f + 10;
        platz(h);
        cs.setNonStrokingColor(0.94f, 0.96f, 0.99f);
        cs.addRect(RAND, y - h, BREITE, h);
        cs.fill();
        cs.setStrokingColor(0.6f, 0.7f, 0.85f);
        cs.addRect(RAND, y - h, BREITE, h);
        cs.stroke();
        cs.setNonStrokingColor(0f, 0f, 0f);
        float yy = y - 5;
        for (String s : z) {
            schreib(s, normal, 9.5f, RAND + 6, yy - 9.5f);
            yy -= 9.5f * 1.3f;
        }
        y -= h + 8;
    }

    private void tabelle(Vorpruefung.Tabelle t) throws IOException {
        int n = t.kopf().size();
        float groesse = n > 6 ? 7.5f : 8.5f;
        float[] w = spalten(t, n, groesse);
        zeile(t.kopf(), w, fett, groesse, true);
        for (List<String> z : t.zeilen()) {
            if (zeile(z, w, normal, groesse, false)) {
                zeile(t.kopf(), w, fett, groesse, true);
                zeile(z, w, normal, groesse, false);
            }
        }
        y -= 6;
    }

    /** Spaltenbreiten nach Textlänge, mindestens 40 pt. */
    private float[] spalten(Vorpruefung.Tabelle t, int n, float groesse) throws IOException {
        float[] max = new float[n];
        for (int i = 0; i < n; i++) {
            max[i] = Math.max(40, Math.min(220, breite(sicher(t.kopf().get(i), fett), fett, groesse) + 8));
        }
        for (List<String> z : t.zeilen()) {
            for (int i = 0; i < n && i < z.size(); i++) {
                max[i] = Math.max(max[i], Math.min(220, breite(sicher(z.get(i), normal), normal, groesse) + 8));
            }
        }
        float summe = 0;
        for (float m : max) {
            summe += m;
        }
        float[] w = new float[n];
        for (int i = 0; i < n; i++) {
            w[i] = max[i] * BREITE / summe;
        }
        return w;
    }

    /** Schreibt eine Tabellenzeile; true, wenn dafür eine neue Seite nötig war (Kopf wiederholen). */
    private boolean zeile(List<String> zellen, float[] w, PDFont f, float groesse, boolean kopf) throws IOException {
        List<List<String>> teile = new ArrayList<>();
        int zeilen = 1;
        for (int i = 0; i < w.length; i++) {
            String s = i < zellen.size() ? zellen.get(i) : "";
            List<String> u = umbrechen(sicher(s, f), f, groesse, w[i] - 6);
            teile.add(u);
            zeilen = Math.max(zeilen, u.size());
        }
        float h = zeilen * groesse * 1.25f + 4;
        boolean neu = false;
        if (y - h < RAND + 20) {
            neueSeite();
            neu = !kopf;
            if (neu) {
                return true;
            }
        }
        float x = RAND;
        if (kopf) {
            cs.setNonStrokingColor(0.9f, 0.9f, 0.9f);
            cs.addRect(RAND, y - h, BREITE, h);
            cs.fill();
            cs.setNonStrokingColor(0f, 0f, 0f);
        }
        cs.setStrokingColor(0.55f, 0.55f, 0.55f);
        cs.setLineWidth(0.5f);
        for (int i = 0; i < w.length; i++) {
            cs.addRect(x, y - h, w[i], h);
            cs.stroke();
            float yy = y - 2;
            for (String s : teile.get(i)) {
                schreib(s, f, groesse, x + 3, yy - groesse);
                yy -= groesse * 1.25f;
            }
            x += w[i];
        }
        y -= h;
        return false;
    }

    private void fusszeilen(Vorpruefung.Ergebnis e) throws IOException {
        int n = doc.getNumberOfPages();
        for (int i = 0; i < n; i++) {
            try (PDPageContentStream c = new PDPageContentStream(doc, doc.getPage(i),
                    PDPageContentStream.AppendMode.APPEND, true)) {
                String s = sicher((e.deutsch() ? "Seite " : "Page ") + (i + 1) + (e.deutsch() ? " von " : " of ") + n
                        + " · " + e.datei(), normal);
                c.beginText();
                c.setFont(normal, 8);
                c.newLineAtOffset(RAND, RAND - 20);
                c.showText(s);
                c.endText();
            }
        }
    }

    private void schreib(String s, PDFont f, float groesse, float x, float yy) throws IOException {
        cs.beginText();
        cs.setFont(f, groesse);
        cs.newLineAtOffset(x, yy);
        cs.showText(s);
        cs.endText();
    }

    private static float breite(String s, PDFont f, float groesse) throws IOException {
        return f.getStringWidth(s) / 1000 * groesse;
    }

    static List<String> umbrechen(String text, PDFont f, float groesse, float max) throws IOException {
        List<String> l = new ArrayList<>();
        for (String absatz : text.split("\n")) {
            StringBuilder z = new StringBuilder();
            for (String wort : absatz.split(" ")) {
                String probe = z.isEmpty() ? wort : z + " " + wort;
                if (breite(probe, f, groesse) <= max) {
                    z.setLength(0);
                    z.append(probe);
                    continue;
                }
                if (!z.isEmpty()) {
                    l.add(z.toString());
                    z.setLength(0);
                }
                // überlanges Wort hart trennen
                String rest = wort;
                while (breite(rest, f, groesse) > max && rest.length() > 1) {
                    int i = rest.length() - 1;
                    while (i > 1 && breite(rest.substring(0, i), f, groesse) > max) {
                        i--;
                    }
                    l.add(rest.substring(0, i));
                    rest = rest.substring(i);
                }
                z.append(rest);
            }
            l.add(z.toString());
        }
        return l;
    }

    /** Ersetzt Zeichen, die die Schrift nicht kennt. */
    static String sicher(String s, PDFont f) {
        StringBuilder sb = new StringBuilder();
        s.codePoints().forEach(cp -> {
            String c = new String(Character.toChars(cp));
            String ersatz = switch (cp) {
                case 0x2265 -> ">=";
                case 0x2264 -> "<=";
                case 0x2192 -> "->";
                case 0x2714, 0x2713 -> "OK";
                case 0x26A0 -> "!";
                case '\t' -> " ";
                default -> null;
            };
            if (ersatz != null) {
                sb.append(ersatz);
                return;
            }
            try {
                f.encode(c);
                sb.append(c);
            } catch (IOException | IllegalArgumentException ex) {
                sb.append('?');
            }
        });
        return sb.toString();
    }
}
