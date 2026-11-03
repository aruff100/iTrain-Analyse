package com.example.itrain_analyse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Wandelt das PDF-Handbuch seitenweise in reinen Text um. Jede Seite beginnt mit
 * "===== Seite x von N =====" - dasselbe Format wie iTrain_6_Handbuch.txt, auf das sich die Seitenangaben im
 * Prompt beziehen. Das Ergebnis wird zwischengespeichert, weil die Umwandlung einige Sekunden dauert.
 */
public final class Handbuch {

    /** Textfassung, Handbuchversion von Seite 1 ("Version: 6.0") oder {@code null}, Seitenzahl. */
    public record Ergebnis(Path text, Version version, int seiten) {
    }

    private static final Pattern VERSION = Pattern.compile("(?:Version|Versie|Versión)\\s*:?\\s*(\\d+\\.\\d+(?:\\.\\d+)*)");

    private Handbuch() {
    }

    /**
     * {@code versionVorgabe}: Handbuchversion aus der Download-Adresse (z. B. 6.0), sonst {@code null} - dann gilt
     * die Angabe "Version: x.y" auf Seite 1. {@code kuerzel}: Sprache für den Dateinamen (DE, NL, …) oder {@code null}.
     */
    public static Ergebnis alsText(Path pdf, Path cacheOrdner, Path zielordner, String kuerzel,
            Version versionVorgabe, Consumer<String> meldung) throws IOException {
        Files.createDirectories(cacheOrdner);
        Path cache = cacheDatei(pdf, cacheOrdner);
        if (!Files.exists(cache)) {
            meldung.accept("Handbuch wird in Text umgewandelt (einmalig, dauert etwas) …");
            Path tmp = cache.resolveSibling(cache.getFileName() + ".part");
            Files.writeString(tmp, umwandeln(pdf), StandardCharsets.UTF_8);
            Files.move(tmp, cache, StandardCopyOption.REPLACE_EXISTING);
        } else {
            meldung.accept("Handbuch-Text aus dem Zwischenspeicher.");
        }
        String text = Files.readString(cache, StandardCharsets.UTF_8);
        Version version = versionVorgabe != null ? versionVorgabe : versionAus(text);
        int seiten = seitenAus(text);
        String name = "iTrain_" + (version == null ? "" : version + "_") + "Handbuch"
                + (kuerzel == null ? "" : "_" + kuerzel) + ".txt";
        Path ziel = zielordner.resolve(name);
        Files.copy(cache, ziel, StandardCopyOption.REPLACE_EXISTING);
        return new Ergebnis(ziel, version, seiten);
    }

    /** Name der Textfassung im Zwischenspeicher: hängt an Größe und Änderungsdatum des PDFs. */
    static Path cacheDatei(Path pdf, Path cacheOrdner) throws IOException {
        String basis = pdf.getFileName().toString().replaceFirst("(?i)\\.pdf$", "");
        return cacheOrdner.resolve(basis + "-" + Files.size(pdf) + "-"
                + Files.getLastModifiedTime(pdf).toMillis() + ".txt");
    }

    /** Gibt es zu diesem PDF schon eine Textfassung (dann wird sie wiederverwendet)? */
    public static boolean istZwischengespeichert(Path pdf, Path cacheOrdner) throws IOException {
        return Files.exists(cacheDatei(pdf, cacheOrdner));
    }

    static String umwandeln(Path pdf) throws IOException {
        List<String> seiten = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            int n = doc.getNumberOfPages();
            for (int i = 1; i <= n; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                seiten.add(stripper.getText(doc).strip());
            }
        }
        int n = seiten.size();
        Version v = seiten.isEmpty() ? null : versionAus(seiten.get(0));
        StringBuilder sb = new StringBuilder();
        sb.append("iTrain ").append(v == null ? "" : v + " ").append("– Handbuch, Textfassung des PDF-Handbuchs \"")
                .append(pdf.getFileName()).append("\" mit ").append(n).append(" Seiten.\n");
        sb.append("Jede Seite beginnt mit einer Zeile \"===== Seite x von ").append(n)
                .append(" =====\"; die Seitenzahl entspricht der PDF-Seite.\n");
        sb.append("Bilder und Bildschirmfotos sind in der Textfassung nicht enthalten.\n");
        for (int i = 0; i < n; i++) {
            sb.append("\n===== Seite ").append(i + 1).append(" von ").append(n).append(" =====\n");
            sb.append(seiten.get(i)).append('\n');
        }
        return sb.toString();
    }

    static Version versionAus(String text) {
        Matcher m = VERSION.matcher(text);
        return m.find() ? Version.aus(m.group(1)) : null;
    }

    private static int seitenAus(String text) {
        Matcher m = Pattern.compile("===== Seite \\d+ von (\\d+) =====").matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }
}
