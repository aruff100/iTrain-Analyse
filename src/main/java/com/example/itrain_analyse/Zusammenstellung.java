package com.example.itrain_analyse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Sucht alle Dateien für den Chat zusammen und legt sie im Arbeitsordner ab
 * ({@code <Arbeitsordner>/<Name der Layout-Datei>/}). Handbuch-PDF, Handbuch-Text und Prompt kommen aus dem
 * Zwischenspeicher {@code <Arbeitsordner>/.cache} und werden bei der nächsten Analyse wiederverwendet.
 * Fortschritt und Ergebnis jedes Schritts gehen als Zeile mit Symbol an {@code meldung}.
 */
public final class Zusammenstellung {

    /** Rückfragen an den Anwender (das Fenster zeigt einen Dialog und wartet auf die Antwort). */
    public interface Rueckfrage {
        boolean frage(String titel, String text, String ja, String nein);
    }

    /** Vom Anwender abgebrochen. */
    public static final class Abbruch extends IOException {
        public Abbruch(String text) {
            super(text);
        }
    }

    /** Eingaben aus Fenster und Voreinstellungen. */
    public record Auftrag(Path projektdatei, Sprachgruppe gruppe, Path docsOrdner, Path arbeitsordner,
            boolean tcdAlsXml, boolean direkt, String sprache, boolean zugtabelle, boolean bahnsteige,
            boolean signale, String format, String forumUrl, String forumText, String zusatzauftrag,
            String handbuchAdresse, HandbuchSprache handbuchSprache, boolean pruefbericht, boolean handbuchAuszug) {
    }

    /** Ein Eintrag der Statusliste: Symbol (✔, ⚠, ✖) und Text. */
    public record Status(String symbol, String text) {
    }

    /**
     * Ein ansehbarer Text (Menü "Ansichten"): Art, Schlüssel für die bearbeitete Fassung, Anzeigename, das
     * unbearbeitete Original und die Datei im Arbeitsordner, die in den Chat geht.
     */
    public record Text(Bearbeitet.Art art, String schluessel, String bezeichnung, Path original, Path datei,
            boolean bearbeitet) {
    }

    /** Dateien in der Reihenfolge für den Chat, Nachrichtentext, Ordner, Statusliste, Texte, Auftrag. */
    public record Ergebnis(List<Path> dateien, String nachricht, Path ordner, List<Status> status, List<Text> texte,
            Auftrag auftrag, String forumDatei, Path pruefbericht) {

        public java.util.Optional<Text> text(Bearbeitet.Art art) {
            return texte.stream().filter(x -> x.art() == art).findFirst();
        }

        /** Anzeigenamen der bearbeiteten Texte. */
        public List<String> bearbeitet() {
            return texte.stream().filter(Text::bearbeitet).map(Text::bezeichnung).toList();
        }
    }

    public static final String OK = "✔";
    public static final String WARNUNG = "⚠";
    public static final String FEHLT = "✖";
    public static final String LAEUFT = "…";

    /** Nachrichtentext zusätzlich als Datei im Arbeitsordner (wird nicht in den Chat gezogen). */
    public static final String NACHRICHT_DATEI = "Nachricht_fuer_den_Chat.txt";

    private static final Version HANDBUCH_VORGABE = Version.aus("6.0");

    private Zusammenstellung() {
    }

    public static Ergebnis ausfuehren(Auftrag a, Rueckfrage rf, Consumer<String> meldung) throws IOException {
        List<Status> status = new ArrayList<>();
        Consumer<Status> melde = s -> {
            status.add(s);
            meldung.accept(s.symbol() + " " + s.text());
        };

        // 1. Layout-Datei
        if (a.projektdatei() == null || !Files.isRegularFile(a.projektdatei())) {
            melde.accept(new Status(FEHLT, I18n.t("status.layoutFehlt")));
            throw new IOException(I18n.t("status.layoutFehlt"));
        }
        String basis = a.projektdatei().getFileName().toString().replaceFirst("(?i)\\.tcdz?$", "");
        Path ordner = a.arbeitsordner().resolve(basis);
        Files.createDirectories(ordner);
        leeren(ordner);
        List<Path> dateien = new ArrayList<>();
        List<Text> texte = new ArrayList<>();

        // 2. Prompt
        prompt(a, rf, melde, meldung, ordner, dateien, texte);

        // 2a. Zusatz 3 "Signale prüfen": eigene Datei im Ordner des Laufs neben dem Prompt (Anwender 16.11.2026).
        if (a.signale() && !a.gruppe().anfaenger()) {
            signale(a, rf, melde, ordner, dateien, texte);
        }

        // 3. Layout entpacken
        Projektdatei.Ergebnis tcd = Projektdatei.entpacke(a.projektdatei(), ordner, a.tcdAlsXml());
        dateien.add(tcd.tcd());
        melde.accept(new Status(OK, I18n.t("status.layout", tcd.tcd().getFileName(),
                tcd.itrainVersion() == null ? I18n.t("status.nichtGelesen") : tcd.itrainVersion())));

        // 3a. Vorprüfung im Programm (Anwender 18.11.2026, Weg 3): Ermittlungen nach festen Regeln als Datei für die
        // KI, auf Wunsch zusätzlich als Prüfbericht (PDF) ohne KI. Der Prompt für Anfänger verwendet sie nicht.
        Vorpruefung.Ergebnis vp = null;
        Path pruefbericht = null;
        if (!a.gruppe().anfaenger()) {
            meldung.accept(LAEUFT + " " + I18n.t("status.vorpruefungLaeuft"));
            try {
                Anlage anlage = Anlage.lies(tcd.tcd());
                // Im Dialogmodus fragt die KI erst später nach den Zusätzen; dann liegen beide Tabellen bereit.
                vp = Vorpruefung.pruefe(anlage, a.gruppe().deutsch(), !a.direkt() || a.zugtabelle(),
                        !a.direkt() || a.bahnsteige());
                Path vpDatei = Vorpruefung.schreibe(vp, ordner, AppVersion.text());
                dateien.add(vpDatei);
                melde.accept(new Status(OK, I18n.t("status.vorpruefung", vpDatei.getFileName(),
                        vp.anzahl(Vorpruefung.Art.FEHLER), vp.anzahl(Vorpruefung.Art.HINWEIS),
                        vp.anzahl(Vorpruefung.Art.ANMERKUNG), vp.routenGeprueft())));
                if (a.pruefbericht()) {
                    pruefbericht = Pruefbericht.schreibe(vp, ordner, AppVersion.text());
                    melde.accept(new Status(OK, I18n.t("status.pruefbericht", pruefbericht.getFileName())));
                }
            } catch (IOException | RuntimeException ex) {
                vp = null;
                melde.accept(new Status(WARNUNG, I18n.t("status.vorpruefungFehler",
                        ex.getMessage() == null ? ex.toString() : ex.getMessage())));
            }
        }

        // 4. Handbuch
        Version hbVersion = handbuch(a, rf, melde, meldung, ordner, dateien, texte, true);
        if (!a.gruppe().anfaenger()) {
            auszug(a, melde, dateien, texte, vp);
        }

        // 5. Release Notes (nur lokal). Der Prompt für Anfänger braucht sie nicht (Anwender 04.11.2026).
        if (a.gruppe().anfaenger()) {
            melde.accept(new Status(OK, I18n.t("status.releaseAnfaenger")));
        } else {
            ReleaseNotes.Ergebnis rn = release(a, rf, melde, ordner, dateien, texte, hbVersion, true);
            Version datei = Version.aus(tcd.itrainVersion());
            if (rn != null && datei != null && rn.installiert() != null && datei.compareTo(rn.installiert()) > 0) {
                melde.accept(new Status(WARNUNG, I18n.t("status.releaseAelter", datei, rn.installiert())));
            }
        }
        Version datei = Version.aus(tcd.itrainVersion());
        if (datei != null && datei.teile().get(0) < hbVersion.teile().get(0)) {
            melde.accept(new Status(WARNUNG, I18n.t("status.versionAlt", tcd.itrainVersion(), hbVersion)));
        }

        // 6. Forumsbeitrag: Das Forum verlangt eine Anmeldung. Ohne eingefügten Text kann die KI den Beitrag nur
        // über einen angemeldeten Browser lesen (Anwender 03.11.2026).
        String forumDatei = null;
        if (a.forumText() != null && !a.forumText().isBlank()) {
            Path f = ordner.resolve(forumDateiname(a.forumUrl()));
            String kopf = a.forumUrl() == null || a.forumUrl().isBlank() ? "" : a.forumUrl().trim() + "\n\n";
            Files.writeString(f, kopf + a.forumText().strip() + "\n", StandardCharsets.UTF_8);
            dateien.add(f);
            forumDatei = f.getFileName().toString();
            melde.accept(new Status(OK, I18n.t("status.forumDatei", forumDatei)));
        } else if (a.forumUrl() != null && !a.forumUrl().isBlank()) {
            melde.accept(new Status(WARNUNG, I18n.t("status.forumAnmeldung", a.forumUrl().trim())));
        }

        // 7. Nachricht
        String nachricht = nachricht(a, texte, forumDatei);
        Files.writeString(ordner.resolve(NACHRICHT_DATEI), nachricht + "\n", StandardCharsets.UTF_8);
        melde.accept(new Status(OK, I18n.t("status.fertig", dateien.size(), groesse(dateien) / 1024, ordner)));
        return new Ergebnis(dateien, nachricht, ordner, status, texte, a, forumDatei, pruefbericht);
    }

    /**
     * Ersetzt das Handbuch in der Dateiliste durch den Auszug mit den Seiten, die Prompt, Signal-Datei und Vorprüfung
     * nennen (Anwender 18.11.2026). Nur für das deutsche Handbuch mit 252 Seiten; bei einem eigenen Auftrag an die KI
     * oder einem Forumsbeitrag bleibt das vollständige Handbuch, weil die KI dafür andere Seiten brauchen kann.
     */
    private static void auszug(Auftrag a, Consumer<Status> melde, List<Path> dateien, List<Text> texte,
            Vorpruefung.Ergebnis vp) throws IOException {
        if (!a.handbuchAuszug()) {
            return;
        }
        var hb = texte.stream().filter(x -> x.art() == Bearbeitet.Art.HANDBUCH).findFirst();
        if (hb.isEmpty() || !dateien.contains(hb.get().datei())) {
            return;
        }
        boolean eigeneFrage = a.zusatzauftrag() != null && !a.zusatzauftrag().isBlank()
                || a.forumUrl() != null && !a.forumUrl().isBlank()
                || a.forumText() != null && !a.forumText().isBlank();
        if (eigeneFrage) {
            melde.accept(new Status(OK, I18n.t("status.auszugVollstaendig")));
            return;
        }
        Path voll = hb.get().datei();
        if (!HandbuchAuszug.moeglich(voll)) {
            melde.accept(new Status(OK, I18n.t("status.auszugNichtMoeglich")));
            return;
        }
        java.util.Set<Integer> seiten = new java.util.TreeSet<>();
        for (Text t : texte) {
            if (t.art() == Bearbeitet.Art.PROMPT || t.art() == Bearbeitet.Art.SIGNALE) {
                seiten.addAll(HandbuchAuszug.seitenAus(Files.readString(t.datei(), StandardCharsets.UTF_8)));
            }
        }
        if (vp != null) {
            seiten.addAll(Vorpruefung.seiten(vp));
        }
        HandbuchAuszug.Ergebnis aus = HandbuchAuszug.schreibe(voll, seiten, a.gruppe().deutsch());
        // Die vollständige Fassung bleibt im Ordner (Menü "Ansichten"), geht aber nicht in den Chat.
        dateien.set(dateien.indexOf(voll), aus.datei());
        melde.accept(new Status(OK, I18n.t("status.auszug", aus.datei().getFileName(), aus.seiten(), aus.gesamt())));
        // Ersparnis gegenüber dem Paket ohne Kurzprüfung und mit vollständigem Handbuch (Anwender 18.11.2026)
        long mit = groesse(dateien);
        long ohne = mit - Files.size(aus.datei()) + Files.size(voll);
        for (Path p : dateien) {
            String n = p.getFileName().toString();
            if (n.equals(Vorpruefung.DATEINAME) || n.equals(Vorpruefung.DATEINAME_EN)) {
                ohne -= Files.size(p);
            }
        }
        if (ohne > mit) {
            melde.accept(new Status(OK, I18n.t("status.ersparnis", prozent(ohne, mit), mit / 1024, ohne / 1024)));
        }
    }

    static int prozent(long ohne, long mit) {
        return ohne <= 0 ? 0 : (int) Math.round((ohne - mit) * 100.0 / ohne);
    }

    /** Ergebnis der Kurzprüfung ohne KI: Prüfbericht, Vorprüfung, Ersparnis für die KI in % (-1 = unbekannt). */
    public record Kurz(Path pruefbericht, Path vorpruefung, Vorpruefung.Ergebnis ergebnis, int ersparnis,
            long mitKb, long ohneKb) {
    }

    /** Unterordner des Laufs für die Kurzprüfung; das Zusammenstellen leert ihn nicht. */
    public static final String KURZ_ORDNER = "Kurzpruefung";

    /**
     * Kurzprüfung ohne KI (Anwender 18.11.2026): Vorprüfung und Prüfbericht für die gewählte Layout-Datei in
     * {@code <Arbeitsordner>/<Layout>/Kurzpruefung}. Schätzt außerdem, um wie viel kleiner das Paket für die KI
     * durch Vorprüfung und Handbuch-Auszug wird (nur wenn Prompt und Handbuch-Text schon im Zwischenspeicher liegen).
     */
    public static Kurz kurzpruefung(Auftrag a, Consumer<String> meldung) throws IOException {
        if (a.projektdatei() == null || !Files.isRegularFile(a.projektdatei())) {
            throw new IOException(I18n.t("status.layoutFehlt"));
        }
        String basis = a.projektdatei().getFileName().toString().replaceFirst("(?i)\\.tcdz?$", "");
        Path ordner = a.arbeitsordner().resolve(basis).resolve(KURZ_ORDNER);
        Files.createDirectories(ordner);
        leeren(ordner);
        meldung.accept(LAEUFT + " " + I18n.t("status.kurzLaeuft"));
        Projektdatei.Ergebnis tcd = Projektdatei.entpacke(a.projektdatei(), ordner, false);
        boolean deutsch = a.gruppe().deutsch();
        Vorpruefung.Ergebnis vp = Vorpruefung.pruefe(Anlage.lies(tcd.tcd()), deutsch, true, true);
        Path vpDatei = Vorpruefung.schreibe(vp, ordner, AppVersion.text());
        Path bericht = Pruefbericht.schreibe(vp, ordner, AppVersion.text());
        int ersparnis = -1;
        long mit = 0;
        long ohne = 0;
        try {
            HandbuchSprache hs = a.handbuchSprache().aufloesen(a.gruppe(), a.direkt(), a.sprache());
            Path pdf = HandbuchDownload.lokal(a.handbuchAdresse(), hs);
            Path cache = Einstellungen.cache().resolve("handbuch-text");
            if (a.handbuchAuszug() && Files.isRegularFile(pdf) && Handbuch.istZwischengespeichert(pdf, cache)) {
                Path voll = Handbuch.cacheDatei(pdf, cache);
                if (HandbuchAuszug.moeglich(voll)) {
                    Path prompt = PromptQuelle.aktuell(a.gruppe());
                    java.util.Set<Integer> seiten = new java.util.TreeSet<>(
                            HandbuchAuszug.seitenAus(Files.readString(prompt, StandardCharsets.UTF_8)));
                    seiten.addAll(Vorpruefung.seiten(vp));
                    HandbuchAuszug.Ergebnis aus = HandbuchAuszug.schreibe(voll, seiten, deutsch, ordner);
                    long gemeinsam = Files.size(prompt) + Files.size(tcd.tcd());
                    ohne = gemeinsam + Files.size(voll);
                    mit = gemeinsam + Files.size(aus.datei()) + Files.size(vpDatei);
                    Files.deleteIfExists(aus.datei());
                    ersparnis = prozent(ohne, mit);
                }
            }
        } catch (IOException | RuntimeException ex) {
            ersparnis = -1;
        }
        meldung.accept(OK + " " + I18n.t("status.kurzFertig", bericht.getFileName(),
                vp.anzahl(Vorpruefung.Art.FEHLER), vp.anzahl(Vorpruefung.Art.HINWEIS),
                vp.anzahl(Vorpruefung.Art.ANMERKUNG), vp.routenGeprueft()));
        if (ersparnis > 0) {
            meldung.accept(OK + " " + I18n.t("status.ersparnis", ersparnis, mit / 1024, ohne / 1024));
        }
        return new Kurz(bericht, vpDatei, vp, ersparnis, mit / 1024, ohne / 1024);
    }

    /** Ordner der Dokumente für das Menü "Ansichten" nach "Aktualisieren" (im Arbeitsordner). */
    public static final String DOKUMENTE_ORDNER = "Dokumente";

    /**
     * Nur die Dokumente für das Menü "Ansichten" (Anwender 16.11.2026): Prompt, Signale, Handbuch und Release Notes
     * in {@code <Arbeitsordner>/Dokumente}, ohne Layout-Datei, Zwischenablage und Nachricht. Gibt es eine bearbeitete
     * Fassung, wird sie ohne Rückfrage gezeigt; ein fehlendes Handbuch oder fehlende Release Notes brechen nicht ab.
     */
    public static Ergebnis dokumente(Auftrag a, Rueckfrage rf, Consumer<String> meldung) throws IOException {
        List<Status> status = new ArrayList<>();
        Consumer<Status> melde = s -> {
            status.add(s);
            meldung.accept(s.symbol() + " " + s.text());
        };
        Path ordner = a.arbeitsordner().resolve(DOKUMENTE_ORDNER);
        Files.createDirectories(ordner);
        leeren(ordner);
        List<Path> dateien = new ArrayList<>();
        List<Text> texte = new ArrayList<>();
        Rueckfrage bearbeitete = (titel, text, ja, nein) -> true;
        prompt(a, bearbeitete, melde, meldung, ordner, dateien, texte);
        if (!a.gruppe().anfaenger()) {
            signale(a, bearbeitete, melde, ordner, dateien, texte);
        }
        Rueckfrage handbuchFrage = (titel, text, ja, nein) -> rf.frage(titel, text, ja, nein);
        Version hbVersion = handbuch(a, handbuchFrage, melde, meldung, ordner, dateien, texte, false);
        if (!a.gruppe().anfaenger()) {
            release(a, bearbeitete, melde, ordner, dateien, texte, hbVersion == null ? HANDBUCH_VORGABE : hbVersion,
                    false);
        }
        melde.accept(new Status(OK, I18n.t("status.dokumenteFertig", texte.size(), ordner)));
        return new Ergebnis(dateien, "", ordner, status, texte, a, null, null);
    }

    /** Ergebnis von {@link #dokumente} (nicht von einer Zusammenstellung). */
    public static boolean nurDokumente(Ergebnis e) {
        return e.ordner().getFileName().toString().equals(DOKUMENTE_ORDNER) && e.nachricht().isEmpty();
    }

    private static void prompt(Auftrag a, Rueckfrage rf, Consumer<Status> melde, Consumer<String> meldung, Path ordner,
            List<Path> dateien, List<Text> texte) throws IOException {
        meldung.accept(LAEUFT + " " + I18n.t("status.promptSuche"));
        Path prompt = PromptQuelle.aktuell(a.gruppe());
        Path promptZiel = ordner.resolve(prompt.getFileName());
        Files.copy(prompt, promptZiel, StandardCopyOption.REPLACE_EXISTING);
        dateien.add(promptZiel);
        melde.accept(new Status(OK, I18n.t("status.prompt", prompt.getFileName())));
        waehle(a, rf, melde, dateien, texte, new Text(Bearbeitet.Art.PROMPT,
                Bearbeitet.schluessel(Bearbeitet.Art.PROMPT, a.gruppe().name()), I18n.t("ansicht.prompt"),
                prompt, promptZiel, false));
    }

    /** Zusatzdatei Signale; hat Berros die Signalregeln seitdem geändert, steht das als Warnung im Fortschritt. */
    private static void signale(Auftrag a, Rueckfrage rf, Consumer<Status> melde, Path ordner, List<Path> dateien,
            List<Text> texte) throws IOException {
        Zusatzdatei z = Zusatzdatei.signale(a.gruppe());
        Path zusatz = PromptQuelle.aktuell(z);
        Path zusatzZiel = ordner.resolve(zusatz.getFileName());
        Files.copy(zusatz, zusatzZiel, StandardCopyOption.REPLACE_EXISTING);
        dateien.add(zusatzZiel);
        melde.accept(new Status(OK, I18n.t("status.signale", zusatz.getFileName())));
        try {
            String m = SignalQuelle.meldung(SignalQuelle.pruefe(zusatz));
            if (m != null) {
                melde.accept(new Status(WARNUNG, m));
            }
        } catch (IOException ex) {
            // Berros nicht erreichbar: ohne Vergleich weiter.
        }
        waehle(a, rf, melde, dateien, texte, new Text(Bearbeitet.Art.SIGNALE,
                Bearbeitet.schluessel(Bearbeitet.Art.SIGNALE, z.name().replace("SIGNALE_", "")), I18n.t("ansicht.signale"), zusatz,
                zusatzZiel, false));
    }

    /**
     * Handbuch als Text; liefert die Version des Handbuchs. Fehlt das Handbuch und lädt der Anwender es nicht, bricht
     * die Zusammenstellung ab ({@code pflicht}); sonst Meldung und Ergebnis null.
     */
    private static Version handbuch(Auftrag a, Rueckfrage rf, Consumer<Status> melde, Consumer<String> meldung,
            Path ordner, List<Path> dateien, List<Text> texte, boolean pflicht) throws IOException {
        HandbuchSprache hs = a.handbuchSprache().aufloesen(a.gruppe(), a.direkt(), a.sprache());
        Path pdf = HandbuchDownload.lokal(a.handbuchAdresse(), hs);
        if (!Files.isRegularFile(pdf)) {

            HandbuchDownload.Stand st = HandbuchDownload.pruefe(a.handbuchAdresse(), hs);
            if (st.zustand() == HandbuchDownload.Zustand.FEHLT
                    && rf.frage(I18n.t("dialog.handbuch.titel"),
                            I18n.t("dialog.handbuch.fehlt", pdf.getFileName(), mb(st.groesse())),
                            I18n.t("knopf.herunterladen"), I18n.t("knopf.abbrechen"))) {
                meldung.accept(LAEUFT + " " + I18n.t("status.handbuchLaedt", pdf.getFileName()));
                pdf = HandbuchDownload.lade(a.handbuchAdresse(), hs);
            } else {
                melde.accept(new Status(FEHLT, I18n.t("status.handbuchFehlt", pdf.getFileName())));
                if (pflicht) {
                    throw new Abbruch(I18n.t("status.handbuchFehlt", pdf.getFileName()));
                }
                return null;
            }
        }
        Path cacheHandbuch = Einstellungen.cache().resolve("handbuch-text");
        boolean neuUmwandeln = !Handbuch.istZwischengespeichert(pdf, cacheHandbuch);
        if (neuUmwandeln) {
            meldung.accept(LAEUFT + " " + I18n.t("status.handbuchWandelt"));
        }
        Version adressVersion = HandbuchDownload.versionAus(a.handbuchAdresse());
        Handbuch.Ergebnis hb = Handbuch.alsText(pdf, cacheHandbuch, ordner, hs.kuerzel(), adressVersion, m -> { });
        dateien.add(hb.text());
        Path hbOriginal = cacheHandbuch.resolve(hb.text().getFileName());
        Version hbVersion = hb.version() == null ? HANDBUCH_VORGABE : hb.version();
        melde.accept(new Status(OK, I18n.t("status.handbuch", hb.text().getFileName(), hb.seiten(),
                neuUmwandeln ? I18n.t("status.neuErstellt") : I18n.t("status.wiederverwendet"))));
        if (hs != HandbuchSprache.DE) {
            melde.accept(new Status(WARNUNG, I18n.t("status.handbuchSeiten")));
        }
        waehle(a, rf, melde, dateien, texte, new Text(Bearbeitet.Art.HANDBUCH,
                Bearbeitet.schluessel(Bearbeitet.Art.HANDBUCH, hs.kuerzel()), I18n.t("ansicht.handbuch"),
                Files.isRegularFile(hbOriginal) ? hbOriginal : hb.text(), hb.text(), false));
        return hbVersion;
    }

    /**
     * Auszug aus den Release Notes ab der Hauptversion des Handbuchs; null, wenn es keinen gibt. Fehlt die Datei,
     * fragt die Zusammenstellung, ob sie ohne weitermachen soll ({@code pflicht}); sonst nur eine Warnung.
     */
    private static ReleaseNotes.Ergebnis release(Auftrag a, Rueckfrage rf, Consumer<Status> melde, Path ordner,
            List<Path> dateien, List<Text> texte, Version hbVersion, boolean pflicht) throws IOException {
        Version ab = Version.aus(String.valueOf(hbVersion.teile().get(0)));
        Path docs = a.docsOrdner();
        if (docs != null && Files.isRegularFile(docs.resolve(ReleaseNotes.DATEINAME))) {
            ReleaseNotes.Ergebnis rn = ReleaseNotes.auszug(docs, ab, ordner);
            if (rn.datei() == null) {
                melde.accept(new Status(WARNUNG, I18n.t("status.releaseLeer", ab)));
                return null;
            }
            dateien.add(rn.datei());
            melde.accept(new Status(OK, I18n.t("status.release", rn.datei().getFileName(),
                    rn.abschnitte().size(), rn.installiert())));
            // Original ist der frisch erzeugte Auszug; er wird neben der bearbeiteten Fassung aufbewahrt.
            Path rnOriginal = Einstellungen.cache().resolve("release").resolve(rn.datei().getFileName());
            Files.createDirectories(rnOriginal.getParent());
            Files.copy(rn.datei(), rnOriginal, StandardCopyOption.REPLACE_EXISTING);
            waehle(a, rf, melde, dateien, texte, new Text(Bearbeitet.Art.RELEASE,
                    Bearbeitet.schluessel(Bearbeitet.Art.RELEASE, null), I18n.t("ansicht.release"),
                    rnOriginal, rn.datei(), false));
            return rn;
        }
        if (pflicht && !rf.frage(I18n.t("dialog.release.titel"), I18n.t("dialog.release.text"),
                I18n.t("knopf.weiter"), I18n.t("knopf.abbrechen"))) {
            melde.accept(new Status(FEHLT, I18n.t("status.releaseFehlt")));
            throw new Abbruch(I18n.t("status.abgebrochen"));
        }
        melde.accept(new Status(WARNUNG, I18n.t("status.releaseFehlt")));
        return null;
    }

    /** Forum_Beitrag_<Beitragsnummer>.txt, ohne Nummer in der Adresse Forum_Beitrag.txt. */
    static String forumDateiname(String url) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[?&#]p=?(\\d+)").matcher(url == null ? "" : url);
        return m.find() ? "Forum_Beitrag_" + m.group(1) + ".txt" : "Forum_Beitrag.txt";
    }

    private static String nachricht(Auftrag a, List<Text> texte, String forumDatei) {
        List<String> namen = texte.stream().filter(Text::bearbeitet).map(x -> x.datei().getFileName().toString())
                .toList();
        return a.gruppe().nachricht(a.direkt(), a.sprache(), a.zugtabelle(), a.bahnsteige(), a.signale(),
                a.format(), a.forumUrl(), forumDatei, namen, a.zusatzauftrag());
    }

    /**
     * Gibt es eine bearbeitete Fassung, fragt der Anwender, ob sie oder das Original verwendet wird. Bei der
     * bearbeiteten Fassung ersetzt {@code Name_bearbeitet.txt} das Original im Arbeitsordner und in der Dateiliste.
     */
    private static void waehle(Auftrag a, Rueckfrage rf, Consumer<Status> melde, List<Path> dateien,
            List<Text> texte, Text t) throws IOException {
        var e = Bearbeitet.finde(a.arbeitsordner(), t.schluessel());
        if (e.isEmpty()) {
            texte.add(t);
            return;
        }
        String text = I18n.t("dialog.bearbeitet.text", t.bezeichnung(), e.get().datei().getFileName(),
                e.get().zeit());
        if (Bearbeitet.originalGeaendert(e.get(), t.original())) {
            text += "\n\n" + I18n.t("dialog.bearbeitet.neuesOriginal", e.get().basis(), t.original().getFileName());
        }
        if (!rf.frage(I18n.t("dialog.bearbeitet.titel"), text, I18n.t("knopf.bearbeitete"),
                I18n.t("knopf.original"))) {
            texte.add(t);
            melde.accept(new Status(OK, I18n.t("status.originalVerwendet", t.bezeichnung())));
            return;
        }
        Text neu = einsetzen(dateien, t, e.get().datei());
        texte.add(neu);
        melde.accept(new Status(WARNUNG, I18n.t("status.bearbeitetVerwendet", t.bezeichnung(),
                neu.datei().getFileName())));
    }

    /** Kopiert die bearbeitete Fassung in den Ordner des Laufs und ersetzt damit die bisherige Datei. */
    private static Text einsetzen(List<Path> dateien, Text t, Path bearbeitet) throws IOException {
        Path ordner = t.datei().getParent();
        Path ziel = ordner.resolve(Bearbeitet.dateiname(t.original()));
        if (!ziel.equals(bearbeitet)) {
            Files.copy(bearbeitet, ziel, StandardCopyOption.REPLACE_EXISTING);
        }
        if (!t.datei().equals(ziel)) {
            Files.deleteIfExists(t.datei());
        }
        int i = dateien.indexOf(t.datei());
        if (i < 0 && t.art() == Bearbeitet.Art.HANDBUCH) {
            // Statt des Auszugs geht dann die bearbeitete vollständige Fassung in den Chat.
            for (int j = 0; j < dateien.size(); j++) {
                if (dateien.get(j).getFileName().toString().endsWith("_Auszug.txt")) {
                    i = j;
                }
            }
        }
        if (i >= 0) {
            dateien.set(i, ziel);
        } else {
            dateien.add(ziel);
        }
        return new Text(t.art(), t.schluessel(), t.bezeichnung(), t.original(), ziel, true);
    }

    /**
     * Nach dem Speichern im Ansichtsfenster: bearbeitete Fassung ablegen, in den Ordner des Laufs übernehmen und
     * Dateiliste sowie Nachrichtentext neu bilden. {@code text == null} stellt das Original wieder her.
     */
    public static Ergebnis aendere(Ergebnis e, Text t, String text) throws IOException {
        List<Path> dateien = new ArrayList<>(e.dateien());
        List<Text> texte = new ArrayList<>(e.texte());
        List<Status> status = new ArrayList<>(e.status());
        Text neu;
        if (text == null) {
            Bearbeitet.verwerfen(e.auftrag().arbeitsordner(), t.schluessel());
            Path ziel = e.ordner().resolve(t.original().getFileName());
            Files.copy(t.original(), ziel, StandardCopyOption.REPLACE_EXISTING);
            if (!t.datei().equals(ziel)) {
                Files.deleteIfExists(t.datei());
            }
            int i = dateien.indexOf(t.datei());
            if (i >= 0) {
                dateien.set(i, ziel);
            }
            neu = new Text(t.art(), t.schluessel(), t.bezeichnung(), t.original(), ziel, false);
            status.add(new Status(OK, I18n.t("status.originalVerwendet", t.bezeichnung())));
        } else {
            Path gespeichert = Bearbeitet.speichern(e.auftrag().arbeitsordner(), t.schluessel(), t.original(), text);
            neu = einsetzen(dateien, t, gespeichert);
            status.add(new Status(WARNUNG, I18n.t("status.bearbeitetGespeichert", t.bezeichnung(),
                    neu.datei().getFileName())));
        }
        texte.set(texte.indexOf(t), neu);
        if (nurDokumente(e)) {
            return new Ergebnis(dateien, "", e.ordner(), status, texte, e.auftrag(), e.forumDatei(), e.pruefbericht());
        }
        String nachricht = nachricht(e.auftrag(), texte, e.forumDatei());
        Files.writeString(e.ordner().resolve(NACHRICHT_DATEI), nachricht + "\n", StandardCharsets.UTF_8);
        return new Ergebnis(dateien, nachricht, e.ordner(), status, texte, e.auftrag(), e.forumDatei(),
                e.pruefbericht());
    }

    private static String mb(long bytes) {
        return bytes > 0 ? String.valueOf(Math.round(bytes / (1024.0 * 1024.0))) : "?";
    }

    /** Entfernt Dateien eines früheren Laufs (nur Dateien, keine Unterordner). */
    private static void leeren(Path ordner) throws IOException {
        try (Stream<Path> s = Files.list(ordner)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                Files.delete(p);
            }
        }
    }

    /** Gesamtgröße der Dateien in Byte. */
    public static long groesse(List<Path> dateien) {
        long summe = 0;
        for (Path p : dateien) {
            try {
                summe += Files.size(p);
            } catch (IOException ex) {
                // nur für die Anzeige
            }
        }
        return summe;
    }
}
