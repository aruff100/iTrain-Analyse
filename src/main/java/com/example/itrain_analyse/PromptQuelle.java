package com.example.itrain_analyse;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Woher die Prompt-Dateien kommen.
 *
 * <p>Server (Vorgabe): Im Ordner {@link Einstellungen#promptAdresse()} liegt {@code manifest.properties} mit
 * Dateiname und SHA-256 je Gruppe ({@code DE_NL.datei}, {@code DE_NL.sha256} …, erzeugt von
 * {@code gradlew promptManifest}). Das Programm hält eine Kopie im Zwischenspeicher {@code .cache/prompt}. Beim ersten
 * Start wird dort die mitgelieferte Fassung abgelegt; bei jedem Start vergleicht das Fenster die Prüfsummen und fragt
 * vor einem Download nach.
 *
 * <p>Ordner: wie bisher die neueste Datei aus einem lokalen Ordner (z. B. Proton Drive).
 *
 * <p>Dasselbe gilt für die {@link Zusatzdatei Zusatzdateien} (Zusatz 3 Signale, Anwender 16.11.2026): eigener
 * Schlüssel im Manifest ({@code SIGNALE_DE_NL.datei} …), Prüfung, Rückfrage und Download wie beim Prompt.
 */
public final class PromptQuelle {

    /** Neuere Fassung auf dem Server; {@code schluessel} ist der Name im Manifest (z. B. DE_NL, SIGNALE_DE_NL). */
    public record Neu(String schluessel, String datei, String sha256) {
    }

    /** Eine Datei auf dem Server: Schlüssel im Manifest und Anfang des Dateinamens. */
    record Eintrag(String schluessel, String praefix) {
    }

    /** Alle Prompt- und Zusatzdateien. */
    static List<Eintrag> eintraege() {
        List<Eintrag> l = new ArrayList<>();
        for (Sprachgruppe g : Sprachgruppe.values()) {
            l.add(new Eintrag(g.name(), g.dateiPraefix()));
        }
        for (Zusatzdatei z : Zusatzdatei.values()) {
            l.add(new Eintrag(z.name(), z.dateiPraefix()));
        }
        return l;
    }

    private static final String MANIFEST = "manifest.properties";

    private PromptQuelle() {
    }

    static Path cacheOrdner() {
        return Einstellungen.cache().resolve("prompt");
    }

    /** Die Prompt-Datei, die für die Zusammenstellung verwendet wird. */
    public static Path aktuell(Sprachgruppe g) throws IOException {
        return aktuell(new Eintrag(g.name(), g.dateiPraefix()));
    }

    /** Die Zusatzdatei, die für die Zusammenstellung verwendet wird. */
    public static Path aktuell(Zusatzdatei z) throws IOException {
        return aktuell(new Eintrag(z.name(), z.dateiPraefix()));
    }

    private static Path aktuell(Eintrag e) throws IOException {
        if (!Einstellungen.promptVomServer()) {
            Path ordner = Einstellungen.pfad(Einstellungen.PROMPT_ORDNER, "");
            if (ordner == null || !Files.isDirectory(ordner)) {
                throw new IOException(I18n.t("fehler.promptOrdner"));
            }
            return Quellordner.prompt(ordner, e.praefix());
        }
        Properties lokal = lokalesManifest();
        String name = lokal.getProperty(e.schluessel() + ".datei");
        if (name == null || !Files.isRegularFile(cacheOrdner().resolve(name))) {
            mitgelieferteInstallieren();
            lokal = lokalesManifest();
            name = lokal.getProperty(e.schluessel() + ".datei");
        }
        if (name == null) {
            throw new IOException(I18n.t("fehler.promptFehlt", e.praefix()));
        }
        return cacheOrdner().resolve(name);
    }

    /** Fragt das Manifest auf dem Server ab und liefert die Gruppen, deren Datei sich geändert hat. */
    public static List<Neu> pruefeServer() throws IOException {
        Properties server = new Properties();
        server.load(new StringReader(Http.text(Http.datei(Einstellungen.promptAdresse(), MANIFEST))));
        List<Neu> neu = new ArrayList<>();
        for (Eintrag e : eintraege()) {
            String datei = server.getProperty(e.schluessel() + ".datei");
            String sha = server.getProperty(e.schluessel() + ".sha256");
            if (datei == null || sha == null) {
                continue;
            }
            Path lokal;
            try {
                lokal = aktuell(e);
            } catch (IOException ex) {
                lokal = null;
            }
            // Eine ältere Fassung auf dem Server (z. B. bevor ein neuer Prompt hochgeladen ist) wird nie angeboten.
            if (lokal != null && aelter(datei, lokal.getFileName().toString())) {
                continue;
            }
            if (lokal == null || !sha.equalsIgnoreCase(sha256(lokal))) {
                neu.add(new Neu(e.schluessel(), datei, sha));
            }
        }
        return neu;
    }

    private static final java.util.regex.Pattern DATEI_VERSION =
            java.util.regex.Pattern.compile("_v(\\d+(?:\\.\\d+)*)\\.txt$", java.util.regex.Pattern.CASE_INSENSITIVE);

    /** True, wenn die Versionsnummer im Namen {@code a} kleiner ist als die in {@code b} (…_v2.2.txt < …_v2.3.txt). */
    static boolean aelter(String a, String b) {
        java.util.regex.Matcher ma = DATEI_VERSION.matcher(a);
        java.util.regex.Matcher mb = DATEI_VERSION.matcher(b);
        if (!ma.find() || !mb.find()) {
            return false;
        }
        Version va = Version.aus(ma.group(1));
        Version vb = Version.aus(mb.group(1));
        return va != null && vb != null && va.compareTo(vb) < 0;
    }

    /** Lädt eine neuere Fassung, prüft die Prüfsumme und ersetzt die Datei im Zwischenspeicher. */
    public static Path lade(Neu n) throws IOException {
        Path ziel = cacheOrdner().resolve(n.datei());
        Path tmp = cacheOrdner().resolve(n.datei() + ".neu");
        Http.laden(Http.datei(Einstellungen.promptAdresse(), n.datei()), tmp);
        if (!n.sha256().equalsIgnoreCase(sha256(tmp))) {
            Files.deleteIfExists(tmp);
            throw new IOException(I18n.t("fehler.pruefsumme", n.datei()));
        }
        Properties lokal = lokalesManifest();
        String alt = lokal.getProperty(n.schluessel() + ".datei");
        Files.move(tmp, ziel, StandardCopyOption.REPLACE_EXISTING);
        if (alt != null && !alt.equals(n.datei())) {
            Files.deleteIfExists(cacheOrdner().resolve(alt));
        }
        lokal.setProperty(n.schluessel() + ".datei", n.datei());
        speichern(lokal);
        return ziel;
    }

    /** Legt die mit dem Programm ausgelieferte Fassung im Zwischenspeicher ab (nur fehlende Gruppen). */
    static void mitgelieferteInstallieren() throws IOException {
        Properties mit = new Properties();
        try (InputStream in = PromptQuelle.class.getResourceAsStream("prompt-manifest.properties")) {
            if (in == null) {
                return;
            }
            mit.load(in);
        }
        Files.createDirectories(cacheOrdner());
        Properties lokal = lokalesManifest();
        for (Eintrag e : eintraege()) {
            String datei = mit.getProperty(e.schluessel() + ".datei");
            if (datei == null) {
                continue;
            }
            // Vorhandene Fassung behalten, außer das Programm bringt eine neuere mit (z. B. Prompt 2.5 mit Programm
            // 1.3, solange der Server noch 2.4 anbietet).
            String vorhanden = lokal.getProperty(e.schluessel() + ".datei");
            if (vorhanden != null && Files.isRegularFile(cacheOrdner().resolve(vorhanden))
                    && !aelter(vorhanden, datei)) {
                continue;
            }
            try (InputStream in = PromptQuelle.class.getResourceAsStream(datei)) {
                if (in == null) {
                    continue;
                }
                Files.copy(in, cacheOrdner().resolve(datei), StandardCopyOption.REPLACE_EXISTING);
            }
            lokal.setProperty(e.schluessel() + ".datei", datei);
        }
        speichern(lokal);
    }

    private static Properties lokalesManifest() throws IOException {
        Properties p = new Properties();
        Path f = cacheOrdner().resolve("lokal.properties");
        if (Files.isRegularFile(f)) {
            try (InputStream in = Files.newInputStream(f)) {
                p.load(in);
            }
        }
        return p;
    }

    private static void speichern(Properties p) throws IOException {
        Files.createDirectories(cacheOrdner());
        try (var out = Files.newOutputStream(cacheOrdner().resolve("lokal.properties"))) {
            p.store(out, "Prompt-Dateien im Zwischenspeicher");
        }
    }

    static String sha256(Path datei) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(datei)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    md.update(buf, 0, n);
                }
            }
            return java.util.HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException(ex);
        }
    }

}
