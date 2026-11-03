package com.example.itrain_analyse;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Properties;

/**
 * Vom Anwender bearbeitete Fassungen von Prompt-, Handbuch- und Release-Text (Menü "Ansichten").
 * Sie liegen dauerhaft in {@code <Arbeitsordner>/Bearbeitet/} und werden bei jeder Zusammenstellung angeboten
 * (Rückfrage: bearbeitete Fassung oder Original). In {@code bearbeitet.properties} steht je Text, von welchem
 * Original die Fassung stammt (Dateiname und SHA-256), damit eine neuere Originalfassung erkannt wird.
 */
public final class Bearbeitet {

    /** Welcher Text. */
    public enum Art {
        PROMPT, RELEASE, HANDBUCH
    }

    /** Eine gespeicherte Fassung. */
    public record Eintrag(Path datei, String basis, String basisSha, String zeit) {
    }

    public static final String ORDNER = "Bearbeitet";
    public static final String ENDUNG = "_bearbeitet.txt";
    private static final String INDEX = "bearbeitet.properties";

    private Bearbeitet() {
    }

    public static Path ordner(Path arbeitsordner) {
        return arbeitsordner.resolve(ORDNER);
    }

    /** Schlüssel je Text: Art plus Prompt-Gruppe bzw. Handbuchsprache, damit DE- und EN-Fassung getrennt bleiben. */
    public static String schluessel(Art art, String zusatz) {
        return zusatz == null || zusatz.isBlank() ? art.name() : art.name() + "_" + zusatz;
    }

    /** Dateiname der bearbeiteten Fassung zu einem Original: {@code Name_bearbeitet.txt}. */
    public static String dateiname(Path original) {
        String n = original.getFileName().toString();
        if (n.endsWith(ENDUNG)) {
            return n;
        }
        return n.replaceFirst("(?i)\\.txt$", "") + ENDUNG;
    }

    public static Optional<Eintrag> finde(Path arbeitsordner, String schluessel) {
        Properties p = lies(arbeitsordner);
        String datei = p.getProperty(schluessel + ".datei");
        if (datei == null) {
            return Optional.empty();
        }
        Path d = ordner(arbeitsordner).resolve(datei);
        if (!Files.isRegularFile(d)) {
            return Optional.empty();
        }
        return Optional.of(new Eintrag(d, p.getProperty(schluessel + ".basis", ""),
                p.getProperty(schluessel + ".sha256", ""), p.getProperty(schluessel + ".zeit", "")));
    }

    /** Speichert den Text als bearbeitete Fassung des Originals und gibt die Datei zurück. */
    public static Path speichern(Path arbeitsordner, String schluessel, Path original, String text)
            throws IOException {
        Path o = ordner(arbeitsordner);
        Files.createDirectories(o);
        Properties p = lies(arbeitsordner);
        // Basis ist immer das unbearbeitete Original, auch wenn eine bearbeitete Fassung erneut bearbeitet wird.
        String basis = original.getFileName().toString();
        String sha = Files.isRegularFile(original) ? PromptQuelle.sha256(original) : "";
        if (basis.endsWith(ENDUNG)) {
            basis = p.getProperty(schluessel + ".basis", basis);
            sha = p.getProperty(schluessel + ".sha256", "");
        }
        String alt = p.getProperty(schluessel + ".datei");
        String neu = dateiname(Path.of(basis));
        if (alt != null && !alt.equals(neu)) {
            Files.deleteIfExists(o.resolve(alt));
        }
        Path ziel = o.resolve(neu);
        Path tmp = o.resolve(neu + ".part");
        Files.writeString(tmp, text, StandardCharsets.UTF_8);
        Files.move(tmp, ziel, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        p.setProperty(schluessel + ".datei", neu);
        p.setProperty(schluessel + ".basis", basis);
        p.setProperty(schluessel + ".sha256", sha);
        p.setProperty(schluessel + ".zeit",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
        schreib(arbeitsordner, p);
        return ziel;
    }

    /** Verwirft die bearbeitete Fassung (die Datei wird gelöscht). */
    public static void verwerfen(Path arbeitsordner, String schluessel) throws IOException {
        Properties p = lies(arbeitsordner);
        String datei = p.getProperty(schluessel + ".datei");
        if (datei != null) {
            Files.deleteIfExists(ordner(arbeitsordner).resolve(datei));
        }
        for (String k : new String[] {".datei", ".basis", ".sha256", ".zeit"}) {
            p.remove(schluessel + k);
        }
        schreib(arbeitsordner, p);
    }

    /** True, wenn das aktuelle Original nicht mehr das ist, von dem die Fassung abgeleitet wurde. */
    public static boolean originalGeaendert(Eintrag e, Path original) {
        try {
            return !e.basisSha().isEmpty() && Files.isRegularFile(original)
                    && !e.basisSha().equals(PromptQuelle.sha256(original));
        } catch (IOException ex) {
            return false;
        }
    }

    private static Properties lies(Path arbeitsordner) {
        Properties p = new Properties();
        Path f = ordner(arbeitsordner).resolve(INDEX);
        if (Files.isRegularFile(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException ex) {
                // leer weiter
            }
        }
        return p;
    }

    private static void schreib(Path arbeitsordner, Properties p) throws IOException {
        Path o = ordner(arbeitsordner);
        Files.createDirectories(o);
        try (Writer w = Files.newBufferedWriter(o.resolve(INDEX), StandardCharsets.UTF_8)) {
            p.store(w, "Bearbeitete Texte (iTrain-Analyse)");
        }
    }
}
