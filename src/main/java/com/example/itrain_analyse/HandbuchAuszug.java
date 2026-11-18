package com.example.itrain_analyse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auszug aus der Textfassung des Handbuchs (Anwender 18.11.2026, Weg 3): nur die Seiten, die Prompt, Zusatzdateien
 * und Vorprüfung als Quelle nennen. Spart den größten Teil der Tokens. Die Seitenmarken "===== Seite x von N ====="
 * bleiben unverändert, damit Seitenangaben und die Seitenzahl N im Eingangscheck stimmen. Nur für das deutsche
 * Handbuch mit 252 Seiten: Für andere Fassungen gelten die Seitenangaben im Prompt nicht.
 */
public final class HandbuchAuszug {

    /** Seitenzahl des Handbuchs, auf das sich die geprüften Seitenangaben im Prompt beziehen. */
    public static final int SEITEN_GEPRUEFT = 252;

    private static final Pattern MARKE = Pattern.compile("^===== Seite (\\d+) von (\\d+) =====$", Pattern.MULTILINE);
    private static final Pattern ANGABE = Pattern.compile(
            "(?:S\\.|p\\.)\\s*(\\d{1,3}(?:\\s*[–-]\\s*\\d{1,3})?(?:\\s*,\\s*\\d{1,3}(?:\\s*[–-]\\s*\\d{1,3})?)*)");

    /** Ergebnis: Datei, Zahl der Seiten im Auszug, Seitenzahl des Handbuchs. */
    public record Ergebnis(Path datei, int seiten, int gesamt) {
    }

    private HandbuchAuszug() {
    }

    /** Alle Seitenzahlen aus Angaben wie "S. 34", "S. 115–116", "S. 50, 54, 56" oder "p. 158". */
    public static Set<Integer> seitenAus(String text) {
        Set<Integer> s = new TreeSet<>();
        if (text == null) {
            return s;
        }
        Matcher m = ANGABE.matcher(text);
        while (m.find()) {
            for (String teil : m.group(1).split(",")) {
                String[] vb = teil.strip().split("\\s*[–-]\\s*");
                int von = Integer.parseInt(vb[0].strip());
                int bis = vb.length > 1 ? Integer.parseInt(vb[1].strip()) : von;
                if (bis < von || bis - von > 20) {
                    bis = von;
                }
                for (int i = von; i <= bis; i++) {
                    s.add(i);
                }
            }
        }
        return s;
    }

    /** Seiten des Handbuchtexts: Seitenzahl -> Text der Seite (ohne Marke). Vorspann unter Schlüssel 0. */
    static Map<Integer, String> seiten(String handbuch) {
        Map<Integer, String> m = new LinkedHashMap<>();
        Matcher mk = MARKE.matcher(handbuch);
        int start = 0;
        int nr = 0;
        while (mk.find()) {
            m.put(nr, handbuch.substring(start, mk.start()));
            nr = Integer.parseInt(mk.group(1));
            start = mk.end();
        }
        m.put(nr, handbuch.substring(start));
        return m;
    }

    static int gesamt(String handbuch) {
        Matcher mk = MARKE.matcher(handbuch);
        return mk.find() ? Integer.parseInt(mk.group(2)) : 0;
    }

    /** Ist ein Auszug möglich (Handbuch mit den Seitenmarken und der geprüften Seitenzahl)? */
    public static boolean moeglich(Path handbuchText) throws IOException {
        return gesamt(Files.readString(handbuchText, StandardCharsets.UTF_8)) == SEITEN_GEPRUEFT;
    }

    /**
     * Schreibt {@code <Name>_Auszug.txt} neben die Textfassung. {@code seiten}: gewünschte Seiten; Seite 1
     * (Titel und Version) kommt immer dazu.
     */
    public static Ergebnis schreibe(Path handbuchText, Set<Integer> seiten, boolean deutsch) throws IOException {
        return schreibe(handbuchText, seiten, deutsch, handbuchText.getParent());
    }

    /** Wie oben, aber in einen anderen Ordner. */
    public static Ergebnis schreibe(Path handbuchText, Set<Integer> seiten, boolean deutsch, Path zielOrdner)
            throws IOException {
        String text = Files.readString(handbuchText, StandardCharsets.UTF_8);
        int n = gesamt(text);
        Map<Integer, String> alle = seiten(text);
        Set<Integer> wahl = new TreeSet<>(seiten);
        wahl.add(1);
        wahl.removeIf(p -> p < 1 || p > n);
        StringBuilder sb = new StringBuilder();
        String vorspann = alle.getOrDefault(0, "").strip();
        if (!vorspann.isEmpty()) {
            sb.append(vorspann).append('\n');
        }
        String liste = String.join(", ", wahl.stream().map(String::valueOf).toList());
        sb.append(deutsch
                ? "AUSZUG: Diese Datei enthält nur die " + wahl.size() + " von " + n + " Seiten, die Prompt, "
                        + "Zusatzdateien und Vorprüfung als Quelle nennen (Seiten " + liste + "). Die Seitenmarken "
                        + "und die Seitenzahl " + n + " sind die des vollständigen Handbuchs. Fehlende Seiten sind "
                        + "kein Fehler der Datei.\n"
                : "EXCERPT: this file contains only the " + wahl.size() + " of " + n + " pages that the prompt, the "
                        + "add-on files and the pre-check cite as sources (pages " + liste + "). The page markers and "
                        + "the page count " + n + " are those of the complete manual. Missing pages are not an "
                        + "error of the file.\n");
        for (int p : wahl) {
            sb.append("\n===== Seite ").append(p).append(" von ").append(n).append(" =====\n");
            sb.append(alle.getOrDefault(p, "").strip()).append('\n');
        }
        String name = handbuchText.getFileName().toString().replaceFirst("(?i)\\.txt$", "") + "_Auszug.txt";
        Path ziel = zielOrdner.resolve(name);
        Files.writeString(ziel, sb.toString(), StandardCharsets.UTF_8);
        return new Ergebnis(ziel, wahl.size(), n);
    }
}
