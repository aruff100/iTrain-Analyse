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
 */
public final class PromptQuelle {

    /** Neuere Fassung auf dem Server. */
    public record Neu(Sprachgruppe gruppe, String datei, String sha256) {
    }

    private static final String MANIFEST = "manifest.properties";

    private PromptQuelle() {
    }

    static Path cacheOrdner() {
        return Einstellungen.cache().resolve("prompt");
    }

    /** Die Prompt-Datei, die für die Zusammenstellung verwendet wird. */
    public static Path aktuell(Sprachgruppe g) throws IOException {
        if (!Einstellungen.promptVomServer()) {
            Path ordner = Einstellungen.pfad(Einstellungen.PROMPT_ORDNER, "");
            if (ordner == null || !Files.isDirectory(ordner)) {
                throw new IOException(I18n.t("fehler.promptOrdner"));
            }
            return Quellordner.prompt(ordner, g);
        }
        Properties lokal = lokalesManifest();
        String name = lokal.getProperty(g.name() + ".datei");
        if (name == null || !Files.isRegularFile(cacheOrdner().resolve(name))) {
            mitgelieferteInstallieren();
            lokal = lokalesManifest();
            name = lokal.getProperty(g.name() + ".datei");
        }
        if (name == null) {
            throw new IOException(I18n.t("fehler.promptFehlt", g.dateiPraefix()));
        }
        return cacheOrdner().resolve(name);
    }

    /** Fragt das Manifest auf dem Server ab und liefert die Gruppen, deren Datei sich geändert hat. */
    public static List<Neu> pruefeServer() throws IOException {
        Properties server = new Properties();
        server.load(new StringReader(Http.text(Http.datei(Einstellungen.promptAdresse(), MANIFEST))));
        List<Neu> neu = new ArrayList<>();
        for (Sprachgruppe g : Sprachgruppe.values()) {
            String datei = server.getProperty(g.name() + ".datei");
            String sha = server.getProperty(g.name() + ".sha256");
            if (datei == null || sha == null) {
                continue;
            }
            Path lokal;
            try {
                lokal = aktuell(g);
            } catch (IOException ex) {
                lokal = null;
            }
            // Eine ältere Fassung auf dem Server (z. B. bevor ein neuer Prompt hochgeladen ist) wird nie angeboten.
            if (lokal != null && aelter(datei, lokal.getFileName().toString())) {
                continue;
            }
            if (lokal == null || !sha.equalsIgnoreCase(sha256(lokal))) {
                neu.add(new Neu(g, datei, sha));
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
        String alt = lokal.getProperty(n.gruppe().name() + ".datei");
        Files.move(tmp, ziel, StandardCopyOption.REPLACE_EXISTING);
        if (alt != null && !alt.equals(n.datei())) {
            Files.deleteIfExists(cacheOrdner().resolve(alt));
        }
        lokal.setProperty(n.gruppe().name() + ".datei", n.datei());
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
        for (Sprachgruppe g : Sprachgruppe.values()) {
            String vorhanden = lokal.getProperty(g.name() + ".datei");
            if (vorhanden != null && Files.isRegularFile(cacheOrdner().resolve(vorhanden))) {
                continue;
            }
            String datei = mit.getProperty(g.name() + ".datei");
            if (datei == null) {
                continue;
            }
            try (InputStream in = PromptQuelle.class.getResourceAsStream(datei)) {
                if (in == null) {
                    continue;
                }
                Files.copy(in, cacheOrdner().resolve(datei), StandardCopyOption.REPLACE_EXISTING);
            }
            lokal.setProperty(g.name() + ".datei", datei);
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
