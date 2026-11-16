package com.example.itrain_analyse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Sucht im Ordner mit Prompt und Handbuch (z. B. dem Proton-Drive-Ordner ki-analyse) die passenden Dateien.
 * Nur die oberste Ebene - Unterordner wie "Alte Versionen" bleiben außen vor.
 */
public final class Quellordner {

    private static final Pattern DATEI_VERSION = Pattern.compile("_v(\\d+(?:\\.\\d+)*)\\.txt$",
            Pattern.CASE_INSENSITIVE);

    private Quellordner() {
    }

    /**
     * Neueste Datei mit diesem Namensanfang (Prompt oder Zusatzdatei): höchste Versionsnummer im Namen (…_v2.2.txt),
     * bei Gleichstand die jüngere.
     */
    public static Path prompt(Path ordner, String dateiPraefix) throws IOException {
        String praefix = dateiPraefix.toLowerCase(Locale.ROOT);
        try (Stream<Path> s = Files.list(ordner)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.startsWith(praefix) && n.endsWith(".txt");
                    })
                    .max(Comparator.comparing(Quellordner::dateiVersion).thenComparing(Quellordner::geaendert))
                    .orElseThrow(() -> new IOException(I18n.t("fehler.promptFehlt", dateiPraefix + "….txt (" + ordner + ")")));
        }
    }

    /** Das PDF-Handbuch: eine .pdf mit "handbuch" oder "manual" im Namen; bei mehreren die jüngste. */
    public static Path handbuchPdf(Path ordner) throws IOException {
        try (Stream<Path> s = Files.list(ordner)) {
            List<Path> pdfs = s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
                    .toList();
            return pdfs.stream()
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.contains("handbuch") || n.contains("manual");
                    })
                    .max(Comparator.comparing(Quellordner::geaendert))
                    .orElseThrow(() -> new IOException("Kein PDF-Handbuch (Name mit \"Handbuch\" oder \"Manual\") in "
                            + ordner));
        }
    }

    static Version dateiVersion(Path p) {
        Matcher m = DATEI_VERSION.matcher(p.getFileName().toString());
        return m.find() ? Version.aus(m.group(1)) : Version.aus("0");
    }

    private static Long geaendert(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException ex) {
            return 0L;
        }
    }
}
