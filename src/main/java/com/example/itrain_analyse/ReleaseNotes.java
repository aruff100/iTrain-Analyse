package com.example.itrain_analyse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Liest "Release Notes.txt" aus dem docs-Ordner der iTrain-Installation und übernimmt nur die Abschnitte, die
 * zur Hauptversion des Handbuchs gehören (Anwender 03.11.2026: "ab Version 6.x"). Ältere Versionen
 * kosten in der Analyse nur Platz.
 *
 * <p>Aufbau der Datei: Versionszeile ("6.0.4"), darunter eine Zeile aus Bindestrichen, dann die Punkte.
 */
public final class ReleaseNotes {

    public static final String DATEINAME = "Release Notes.txt";

    /** Ein Versionsabschnitt mit seinem Text (ohne Kopfzeilen). */
    public record Abschnitt(Version version, String text) {
    }

    /** Erzeugte Datei (oder {@code null}, wenn es nichts Neueres gibt), übernommene Abschnitte, installierte Version. */
    public record Ergebnis(Path datei, List<Abschnitt> abschnitte, Version installiert) {
    }

    private ReleaseNotes() {
    }

    /**
     * Mögliche docs-Ordner je Betriebssystem. Windows ist geprüft (C:\Program Files\iTrain\docs); die Pfade für
     * macOS und Linux sind Vermutungen und müssen auf einem solchen Rechner geprüft werden.
     */
    public static List<Path> kandidaten() {
        List<Path> k = new ArrayList<>();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            for (String var : new String[] {"ProgramFiles", "ProgramFiles(x86)", "ProgramW6432"}) {
                String pf = System.getenv(var);
                if (pf != null && !pf.isBlank()) {
                    k.add(Path.of(pf, "iTrain", "docs"));
                }
            }
            k.add(Path.of("C:\\Program Files\\iTrain\\docs"));
        } else if (os.contains("mac")) {
            k.add(Path.of("/Applications/iTrain.app/Contents/app/docs"));
            k.add(Path.of("/Applications/iTrain.app/Contents/Resources/docs"));
            k.add(Path.of("/Applications/iTrain/docs"));
        } else {
            k.add(Path.of("/opt/itrain/docs"));
            k.add(Path.of("/opt/iTrain/docs"));
            k.add(Path.of("/opt/itrain/lib/app/docs"));
            k.add(Path.of(System.getProperty("user.home"), "iTrain", "docs"));
        }
        return k;
    }

    /** Erster Kandidat, in dem "Release Notes.txt" liegt; {@code null}, wenn keiner passt. */
    public static Path findeDocsOrdner() {
        for (Path p : kandidaten()) {
            if (Files.isRegularFile(p.resolve(DATEINAME))) {
                return p;
            }
        }
        return null;
    }

    public static List<Abschnitt> zerlege(List<String> zeilen) {
        List<Abschnitt> liste = new ArrayList<>();
        Version aktuell = null;
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < zeilen.size(); i++) {
            String z = zeilen.get(i).strip();
            boolean kopf = z.matches("\\d+\\.\\d+(\\.\\d+)*") && i + 1 < zeilen.size()
                    && zeilen.get(i + 1).strip().matches("-{3,}");
            if (kopf) {
                Version v = Version.aus(z);
                if (aktuell != null && !aktuell.equals(v)) {
                    liste.add(new Abschnitt(aktuell, text.toString().strip()));
                    text.setLength(0);
                }
                // Doppelte Kopfzeile (in der Datei steht "6.0.0" zweimal) gehört zum selben Abschnitt.
                aktuell = v;
                i++;
                continue;
            }
            if (aktuell != null) {
                text.append(zeilen.get(i)).append('\n');
            }
        }
        if (aktuell != null) {
            liste.add(new Abschnitt(aktuell, text.toString().strip()));
        }
        return liste;
    }

    /**
     * Schreibt den Auszug: alle Abschnitte ab Version {@code ab} (einschließlich, z. B. 6 = ab 6.0.0). Die neueste
     * Version in der Datei gilt als installierte Version.
     */
    public static Ergebnis auszug(Path docsOrdner, Version ab, Path zielordner) throws IOException {
        List<String> zeilen = Files.readAllLines(docsOrdner.resolve(DATEINAME), StandardCharsets.UTF_8);
        List<Abschnitt> alle = zerlege(zeilen);
        Version installiert = alle.stream().map(Abschnitt::version).max(Version::compareTo).orElse(null);
        List<Abschnitt> neu = alle.stream().filter(a -> a.version().compareTo(ab) >= 0)
                .sorted((a, b) -> b.version().compareTo(a.version())).toList();
        if (neu.isEmpty()) {
            return new Ergebnis(null, neu, installiert);
        }
        Version von = neu.get(neu.size() - 1).version();
        Version bis = neu.get(0).version();
        StringBuilder sb = new StringBuilder();
        sb.append("iTrain Release Notes, Auszug ").append(von).append(" bis ").append(bis).append('\n');
        sb.append("Nur Version ").append(ab).append(" und neuer; ältere Versionen sind im Handbuch beschrieben.\n");
        sb.append("Only version ").append(ab).append(" and newer; older versions are described in the manual.\n");
        for (Abschnitt a : neu) {
            String v = a.version().toString();
            sb.append('\n').append(v).append('\n').append("-".repeat(v.length())).append('\n');
            sb.append(a.text()).append('\n');
        }
        Path ziel = zielordner.resolve("Release_Notes_" + von + "-" + bis + ".txt");
        Files.writeString(ziel, sb.toString(), StandardCharsets.UTF_8);
        return new Ergebnis(ziel, neu, installiert);
    }
}
