package com.example.itrain_analyse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Hauptfenster: Kenntnisstand, Layout-Datei, Prompt-Sprache, Direktmodus mit Sprache/Format/Zusätzen,
 * Forumsbeitrag, Fortschritt. Im Kenntnisstand "Anfänger" (Prompt für Anfänger, Runden) entfallen Direktmodus,
 * Zusätze, Forumsbeitrag und Release Notes; beim Wählen erklärt {@link AnfaengerDialog} den Ablauf. Beim Start prüft es Prompt (Webserver) und Handbuch auf neue Fassungen und fragt vor
 * jedem Download. Nach dem Zusammenstellen öffnet sich das {@link ErgebnisFenster}.
 */
public class AnalyseApplication extends Application {

    private Stage stage;
    private Scene scene;

    private RadioButton fortgeschritten;
    private RadioButton anfaenger;
    private ComboBox<Path> layout;
    private ComboBox<Sprachgruppe> gruppe;
    private CheckBox alsXml;
    private CheckBox direkt;
    private ComboBox<String> sprache;
    private ComboBox<String> format;
    private CheckBox zugtabelle;
    private CheckBox bahnsteige;
    private CheckBox forum;
    private TextField forumUrl;
    private Label forumUrlLabel;
    private Label forumHinweis;
    private Label forumTextLabel;
    private TextArea forumText;
    private GridPane gitter;
    private Label anfHinweis;
    private Button anleitung;
    private Button andereKnopf;
    private Button neuLadenKnopf;
    private MenuItem ansichtPrompt;
    private MenuItem ansichtRelease;
    private MenuItem ansichtHandbuch;
    private TextArea protokoll;
    private Button start;
    private Button ergebnisZeigen;
    private Button oeffnen;

    private Zusammenstellung.Ergebnis letztes;
    /** Direktmodus ist bei jedem Start aus und wird nicht gespeichert (Anwender 03.11.2026). */
    private boolean direktGewaehlt;
    /** Forum-Beitrag und Adresse sind bei jedem Start leer und werden nicht gespeichert (Anwender 03.11.2026). */
    private boolean forumGewaehlt;
    private String forumAdresse = "";
    private String forumInhalt = "";
    private String letztesLayout = "";

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        ErsterStartDialog.beiBedarf();
        scene = new Scene(new StackPane(), 900, 760);
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> speichern());
        aufbauen();
        stage.show();
        pruefeAktualisierungen(false);
    }

    /** Baut den Fensterinhalt (neu), z. B. nach einem Wechsel der Sprache oder der Farben. */
    private void aufbauen() {
        stage.setTitle("iTrain-Analyse " + AppVersion.text());

        fortgeschritten = new RadioButton(I18n.t("modus.fortgeschritten"));
        anfaenger = new RadioButton(I18n.t("modus.anfaenger"));
        ToggleGroup tg = new ToggleGroup();
        fortgeschritten.setToggleGroup(tg);
        anfaenger.setToggleGroup(tg);
        (Einstellungen.get(Einstellungen.MODUS, "fortgeschritten").equals("anfaenger") ? anfaenger : fortgeschritten)
                .setSelected(true);
        HBox modusZeile = new HBox(12, new Label(I18n.t("modus.label")), fortgeschritten, anfaenger);
        modusZeile.setAlignment(Pos.CENTER_LEFT);
        modusZeile.setPadding(new Insets(12, 12, 0, 12));

        layout = new ComboBox<>();
        layout.setMaxWidth(Double.MAX_VALUE);
        layout.setCellFactory(lv -> new LayoutZelle());
        layout.setButtonCell(new LayoutZelle());
        andereKnopf = Oberflaeche.farbe(new Button(I18n.t("knopf.andere")), Oberflaeche.LILA);
        andereKnopf.setOnAction(e -> layoutWaehlen());
        neuLadenKnopf = Oberflaeche.farbe(new Button(I18n.t("knopf.neuEinlesen")), Oberflaeche.LILA);
        neuLadenKnopf.setOnAction(e -> layoutsLaden());
        gruppe = new ComboBox<>();
        alsXml = new CheckBox(I18n.t("feld.alsXml"));
        direkt = new CheckBox(I18n.t("feld.direkt"));
        sprache = new ComboBox<>();
        format = new ComboBox<>();
        zugtabelle = new CheckBox(I18n.t("feld.zugtabelle"));
        bahnsteige = new CheckBox(I18n.t("feld.bahnsteige"));
        forum = new CheckBox(I18n.t("feld.forum"));
        forumUrl = new TextField();
        forumUrl.setPromptText("https://berros.eu/itrain/forum/viewtopic.php?p=…");

        forumUrlLabel = new Label(I18n.t("feld.forumUrl"));
        // Das Forum verlangt eine Anmeldung, auch zum Lesen (Anwender 03.11.2026).
        forumHinweis = new Label(I18n.t("feld.forumHinweis"));
        forumHinweis.setWrapText(true);
        forumHinweis.setStyle("-fx-font-size: 11px; -fx-opacity: 0.85;");
        forumHinweis.setMinHeight(Region.USE_PREF_SIZE);
        forumTextLabel = new Label(I18n.t("feld.forumText"));
        forumText = new TextArea();
        forumText.setPrefRowCount(3);
        forumText.setWrapText(true);
        forumText.setPromptText(I18n.t("feld.forumTextLeer"));
        anfHinweis = new Label(I18n.t("feld.anfHinweis"));
        anfHinweis.setWrapText(true);
        anfHinweis.setStyle("-fx-font-size: 11px; -fx-opacity: 0.85;");
        anfHinweis.setMinHeight(Region.USE_PREF_SIZE);

        gitter = new GridPane();
        gitter.setHgap(8);
        gitter.setVgap(8);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        ColumnConstraints c0 = new ColumnConstraints();
        c0.setMinWidth(Region.USE_PREF_SIZE);
        gitter.getColumnConstraints().addAll(c0, c1, new ColumnConstraints());

        start = Oberflaeche.farbe(new Button(I18n.t("knopf.zusammenstellen")), Oberflaeche.GRUEN);
        start.setDefaultButton(true);
        ergebnisZeigen = Oberflaeche.farbe(new Button(I18n.t("knopf.ergebnis")), Oberflaeche.BLAU);
        oeffnen = Oberflaeche.farbe(new Button(I18n.t("knopf.ordnerOeffnen")), Oberflaeche.TUERKIS);
        anleitung = Oberflaeche.farbe(new Button(I18n.t("knopf.anleitung")), Oberflaeche.ORANGE);
        anleitung.setOnAction(e -> AnfaengerDialog.zeige(stage));
        anleitung.visibleProperty().bind(anfaenger.selectedProperty());
        anleitung.managedProperty().bind(anfaenger.selectedProperty());
        HBox knoepfe = new HBox(8, start, ergebnisZeigen, oeffnen, anleitung);
        protokoll = new TextArea();
        protokoll.setEditable(false);
        protokoll.setWrapText(true);
        protokoll.setStyle("-fx-font-family: 'monospaced';");
        VBox.setVgrow(protokoll, Priority.ALWAYS);
        Label fortschritt = new Label(I18n.t("feld.fortschritt"));
        // Solange im Fortschritt nichts steht, zeigt das Feld das Programmsymbol.
        ImageView symbol = new ImageView(Oberflaeche.symbolGross());
        symbol.setFitWidth(128);
        symbol.setFitHeight(128);
        symbol.setOpacity(0.85);
        symbol.setMouseTransparent(true);
        symbol.visibleProperty().bind(protokoll.textProperty().isEmpty());
        StackPane protokollFeld = new StackPane(protokoll, symbol);
        VBox.setVgrow(protokollFeld, Priority.ALWAYS);
        VBox mitte = new VBox(10, gitter, knoepfe, fortschritt, protokollFeld);
        mitte.setPadding(new Insets(12));

        BorderPane wurzel = new BorderPane();
        wurzel.setTop(new VBox(menue(), modusZeile));
        wurzel.setCenter(mitte);

        // Werte
        boolean anf = anfaenger.isSelected();
        gruppe.getItems().setAll(Sprachgruppe.von(anf));
        gruppe.setOnAction(e -> sprachenSetzen());
        format.getItems().setAll(Sprachgruppe.FORMATE);
        direkt.setOnAction(e -> schalten());
        forum.setOnAction(e -> schalten());
        Sprachgruppe gespeicherteGruppe;
        try {
            gespeicherteGruppe = Sprachgruppe.valueOf(Einstellungen.get("gruppe", Sprachgruppe.DE_NL.name()));
        } catch (IllegalArgumentException ex) {
            gespeicherteGruppe = Sprachgruppe.DE_NL;
        }
        gruppe.setValue(gespeicherteGruppe.fuer(anf));
        sprachenSetzen();
        String gespeichert = Einstellungen.get("sprache", "");
        if (sprache.getItems().contains(gespeichert)) {
            sprache.setValue(gespeichert);
        }
        format.setValue(Einstellungen.get("format", "Text"));
        alsXml.setSelected(Einstellungen.getBool("alsXml", false));
        direkt.setSelected(direktGewaehlt);
        zugtabelle.setSelected(Einstellungen.getBool("zugtabelle", Einstellungen.getBool("zusaetze", false)));
        bahnsteige.setSelected(Einstellungen.getBool("bahnsteige", Einstellungen.getBool("zusaetze", false)));
        forum.setSelected(forumGewaehlt);
        forumUrl.setText(forumAdresse);
        forumText.setText(forumInhalt);
        schalten();
        gitterFuellen();
        layoutsLaden();
        // Wechsel des Kenntnisstands: Felder anpassen; beim Wählen von "Anfänger" den Ablauf erklären.
        anfaenger.selectedProperty().addListener((o, alt, neu) -> {
            Sprachgruppe vorher = gruppe.getValue();
            gruppe.getItems().setAll(Sprachgruppe.von(neu));
            gruppe.setValue((vorher == null ? Sprachgruppe.DE_NL : vorher).fuer(neu));
            sprachenSetzen();
            gitterFuellen();
            if (neu) {
                Platform.runLater(() -> AnfaengerDialog.beiWahl(stage));
            }
        });

        start.setOnAction(e -> zusammenstellen());
        ergebnisZeigen.setOnAction(e -> {
            if (letztes != null) {
                ErgebnisFenster.zeige(stage, letztes, letztesLayout);
            }
        });
        oeffnen.setOnAction(e -> {
            if (letztes != null) {
                Oberflaeche.oeffne(letztes.ordner().toFile(), null, false);
            }
        });
        ergebnisZeigen.setDisable(letztes == null);
        oeffnen.setDisable(letztes == null);
        ansichtenSchalten();

        scene.setRoot(wurzel);
        Farben.anwenden(scene);
    }

    private MenuBar menue() {
        MenuItem beenden = new MenuItem(I18n.t("menu.beenden"));
        beenden.setOnAction(e -> {
            speichern();
            Platform.exit();
        });
        Menu datei = new Menu(I18n.t("menu.datei"), null, beenden);

        MenuItem vor = new MenuItem(I18n.t("menu.voreinstellungen"));
        vor.setOnAction(e -> {
            speichern();
            if (VoreinstellungenDialog.zeige(stage)) {
                aufbauen();
            }
        });
        Menu einst = new Menu(I18n.t("menu.einstellungen"), null, vor);

        ansichtPrompt = new MenuItem(I18n.t("menu.ansicht.prompt"));
        ansichtPrompt.setOnAction(e -> ansicht(Bearbeitet.Art.PROMPT));
        ansichtRelease = new MenuItem(I18n.t("menu.ansicht.release"));
        ansichtRelease.setOnAction(e -> ansicht(Bearbeitet.Art.RELEASE));
        ansichtHandbuch = new MenuItem(I18n.t("menu.ansicht.handbuch"));
        ansichtHandbuch.setOnAction(e -> ansicht(Bearbeitet.Art.HANDBUCH));
        Menu ansichten = new Menu(I18n.t("menu.ansichten"), null, ansichtPrompt, ansichtRelease, ansichtHandbuch);

        MenuItem hilfe = new MenuItem(I18n.t("menu.hilfe"));
        hilfe.setOnAction(e -> HilfeDialoge.hilfe(stage));
        MenuItem pruefen = new MenuItem(I18n.t("menu.pruefen"));
        pruefen.setOnAction(e -> pruefeAktualisierungen(true));
        MenuItem ueber = new MenuItem(I18n.t("menu.ueber"));
        ueber.setOnAction(e -> HilfeDialoge.ueber(stage));
        Menu hilfeMenue = new Menu(I18n.t("menu.hilfe"), null, hilfe, pruefen, new SeparatorMenuItem(), ueber);

        return new MenuBar(datei, einst, ansichten, hilfeMenue);
    }

    /** Zeigt Dateiname und Änderungsdatum. */
    private static final class LayoutZelle extends ListCell<Path> {
        private final SimpleDateFormat f = new SimpleDateFormat("dd.MM.yyyy HH:mm");

        @Override
        protected void updateItem(Path p, boolean leer) {
            super.updateItem(p, leer);
            if (leer || p == null) {
                setText(null);
                return;
            }
            String datum;
            try {
                datum = f.format(new Date(Files.getLastModifiedTime(p).toMillis()));
            } catch (IOException ex) {
                datum = "";
            }
            setText(p.getFileName() + "   (" + datum + ")");
        }
    }

    private void layoutsLaden() {
        Path gewaehlt = layout.getValue() != null ? layout.getValue()
                : Einstellungen.pfad(Einstellungen.LETZTES_LAYOUT, "");
        List<Path> liste = Layouts.liste(Einstellungen.layoutOrdner());
        layout.getItems().setAll(liste);
        if (gewaehlt != null && Files.isRegularFile(gewaehlt)) {
            if (!layout.getItems().contains(gewaehlt)) {
                layout.getItems().add(0, gewaehlt);
            }
            layout.setValue(gewaehlt);
        } else if (!liste.isEmpty()) {
            layout.setValue(liste.get(0));
        }
        if (liste.isEmpty()) {
            melde(Zusammenstellung.WARNUNG + " " + I18n.t("status.keineLayouts", Einstellungen.layoutOrdner()));
        }
    }

    private void layoutWaehlen() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("iTrain-Layout", "*.tcdz", "*.tcd"));
        Path ordner = layout.getValue() != null ? layout.getValue().getParent() : Einstellungen.layoutOrdner();
        if (ordner != null && Files.isDirectory(ordner)) {
            fc.setInitialDirectory(ordner.toFile());
        }
        File f = fc.showOpenDialog(stage);
        if (f != null) {
            Path p = f.toPath();
            if (!layout.getItems().contains(p)) {
                layout.getItems().add(0, p);
            }
            layout.setValue(p);
        }
    }

    private void speichern() {
        if (layout == null) {
            return;
        }
        Einstellungen.set(Einstellungen.MODUS, anfaenger.isSelected() ? "anfaenger" : "fortgeschritten");
        Einstellungen.set(Einstellungen.LETZTES_LAYOUT, layout.getValue() == null ? "" : layout.getValue().toString());
        Einstellungen.set("gruppe", gruppe.getValue().fuer(false).name());
        Einstellungen.set("sprache", sprache.getValue());
        Einstellungen.set("format", format.getValue());
        Einstellungen.setBool("alsXml", alsXml.isSelected());
        direktGewaehlt = direkt.isSelected();
        Einstellungen.setBool("zugtabelle", zugtabelle.isSelected());
        Einstellungen.setBool("bahnsteige", bahnsteige.isSelected());
        forumGewaehlt = forum.isSelected();
        forumAdresse = forumUrl.getText().trim();
        forumInhalt = forumText.getText();
    }

    /**
     * Füllt das Formular je nach Kenntnisstand. Anfänger: nur Layout-Datei, Prompt-Sprache, .tcd.xml und ein Hinweis;
     * Direktmodus, Zusätze und Forumsbeitrag entfallen (Anwender 04.11.2026).
     */
    private void gitterFuellen() {
        gitter.getChildren().clear();
        int z = 0;
        gitter.addRow(z++, new Label(I18n.t("feld.layout")), layout, new HBox(6, neuLadenKnopf, andereKnopf));
        gitter.addRow(z++, new Label(I18n.t("feld.promptSprache")), gruppe);
        gitter.add(alsXml, 1, z++, 2, 1);
        if (anfaenger.isSelected()) {
            gitter.add(anfHinweis, 1, z++, 2, 1);
            return;
        }
        gitter.add(Oberflaeche.linie3d(), 0, z++, 3, 1);
        gitter.add(direkt, 1, z++, 2, 1);
        gitter.addRow(z++, new Label(I18n.t("feld.sprache")), sprache);
        gitter.addRow(z++, new Label(I18n.t("feld.format")), format);
        gitter.add(zugtabelle, 1, z++, 2, 1);
        gitter.add(bahnsteige, 1, z++, 2, 1);
        gitter.add(Oberflaeche.linie3d(), 0, z++, 3, 1);
        gitter.add(forum, 1, z++, 2, 1);
        gitter.addRow(z++, forumUrlLabel, forumUrl);
        GridPane.setColumnSpan(forumUrl, 2);
        gitter.add(forumHinweis, 1, z++, 2, 1);
        gitter.add(forumTextLabel, 0, z);
        gitter.add(forumText, 1, z, 2, 1);
        GridPane.setValignment(forumTextLabel, javafx.geometry.VPos.TOP);
    }

    private void sprachenSetzen() {
        String alt = sprache.getValue();
        sprache.getItems().setAll(gruppe.getValue().sprachen());
        sprache.setValue(sprache.getItems().contains(alt) ? alt : sprache.getItems().get(0));
    }

    private void schalten() {
        boolean an = direkt.isSelected();
        sprache.setDisable(!an);
        format.setDisable(!an);
        zugtabelle.setDisable(!an);
        bahnsteige.setDisable(!an);
        forumUrl.setDisable(!forum.isSelected());
        forumUrlLabel.setDisable(!forum.isSelected());
        forumHinweis.setDisable(!forum.isSelected());
        forumTextLabel.setDisable(!forum.isSelected());
        forumText.setDisable(!forum.isSelected());
    }

    private boolean frage(String titel, String text, String ja, String nein) {
        return Oberflaeche.frage(stage, titel, text, ja, nein);
    }

    // ---------------------------------------------------------------- Prüfung beim Start

    /** Prüft Prompt (Webserver) und Handbuch auf neue Fassungen; jeder Download nur nach Rückfrage. */
    private void pruefeAktualisierungen(boolean vonHand) {
        Thread t = new Thread(() -> {
            meldeStart(vonHand, Zusammenstellung.LAEUFT + " " + I18n.t("start.pruefe"));
            if (Einstellungen.promptVomServer()) {
                try {
                    PromptQuelle.mitgelieferteInstallieren();
                    List<PromptQuelle.Neu> neu = PromptQuelle.pruefeServer();
                    if (neu.isEmpty()) {
                        meldeStart(vonHand, Zusammenstellung.OK + " " + I18n.t("start.promptAktuell"));
                    }
                    for (PromptQuelle.Neu n : neu) {
                        if (frage(I18n.t("dialog.prompt.titel"), I18n.t("dialog.prompt.text", n.datei()),
                                I18n.t("knopf.herunterladen"), I18n.t("knopf.spaeter"))) {
                            PromptQuelle.lade(n);
                            meldeFx(Zusammenstellung.OK + " " + I18n.t("start.promptGeladen", n.datei()));
                        } else {
                            meldeFx(Zusammenstellung.WARNUNG + " " + I18n.t("start.promptNichtGeladen", n.datei()));
                        }
                    }
                } catch (IOException ex) {
                    meldeFx(Zusammenstellung.WARNUNG + " " + I18n.t("start.serverOffline", ex.getMessage()));
                }
            } else {
                meldeStart(vonHand, Zusammenstellung.OK + " " + I18n.t("start.promptOrdner",
                        Einstellungen.get(Einstellungen.PROMPT_ORDNER, "")));
            }
            HandbuchSprache hs = HandbuchSprache.aus(Einstellungen.get(Einstellungen.HANDBUCH_SPRACHE, ""))
                    .aufloesen(gruppeWert(), direktWert(), spracheWert());
            String adresse = Einstellungen.handbuchAdresse();
            HandbuchDownload.Stand st = HandbuchDownload.pruefe(adresse, hs);
            String name = st.datei().getFileName().toString();
            String mb = st.groesse() > 0 ? String.valueOf(Math.round(st.groesse() / 1048576.0)) : "?";
            switch (st.zustand()) {
                case AKTUELL -> meldeStart(vonHand,
                        Zusammenstellung.OK + " " + I18n.t("start.handbuchAktuell", name));
                case OFFLINE -> meldeFx(Zusammenstellung.WARNUNG + " " + I18n.t("start.handbuchOffline", name));
                case NICHT_VERFUEGBAR -> meldeFx(Zusammenstellung.FEHLT + " " + I18n.t("start.handbuchNicht", name));
                case FEHLT, NEUER -> {
                    String text = st.zustand() == HandbuchDownload.Zustand.FEHLT
                            ? I18n.t("dialog.handbuch.fehlt", name, mb) : I18n.t("dialog.handbuch.neu", name, mb);
                    if (frage(I18n.t("dialog.handbuch.titel"), text, I18n.t("knopf.herunterladen"),
                            I18n.t("knopf.spaeter"))) {
                        meldeFx(Zusammenstellung.LAEUFT + " " + I18n.t("status.handbuchLaedt", name));
                        try {
                            HandbuchDownload.lade(adresse, hs);
                            meldeFx(Zusammenstellung.OK + " " + I18n.t("start.handbuchGeladen", name));
                        } catch (IOException ex) {
                            meldeFx(Zusammenstellung.FEHLT + " " + I18n.t("start.handbuchFehler", ex.getMessage()));
                        }
                    } else {
                        meldeFx(Zusammenstellung.WARNUNG + " " + I18n.t("start.handbuchNichtGeladen", name));
                    }
                }
                default -> {
                }
            }
            if (vonHand) {
                meldeStart(vonHand, Zusammenstellung.OK + " " + I18n.t("start.fertig"));
            }
        }, "pruefung");
        t.setDaemon(true);
        t.start();
    }

    // Werte aus dem JavaFX-Thread für Hintergrund-Threads lesen
    private Sprachgruppe gruppeWert() {
        return fx(() -> gruppe.getValue());
    }

    private boolean direktWert() {
        return fx(() -> direkt.isSelected() && !anfaenger.isSelected());
    }

    private String spracheWert() {
        return fx(() -> sprache.getValue());
    }

    private static <T> T fx(java.util.concurrent.Callable<T> c) {
        if (Platform.isFxApplicationThread()) {
            try {
                return c.call();
            } catch (Exception ex) {
                return null;
            }
        }
        java.util.concurrent.FutureTask<T> f = new java.util.concurrent.FutureTask<>(c);
        Platform.runLater(f);
        try {
            return f.get();
        } catch (Exception ex) {
            return null;
        }
    }

    // ---------------------------------------------------------------- Zusammenstellen

    private void zusammenstellen() {
        speichern();
        protokoll.clear();
        boolean anf = anfaenger.isSelected();
        String url = forumUrl.getText().trim();
        boolean mitText = !forumText.getText().isBlank();
        boolean mitForum = forum.isSelected() && !anf;
        if (mitForum && !(url.isEmpty() && mitText) && !url.matches("(?i)https?://\\S+")) {
            melde(Zusammenstellung.FEHLT + " " + I18n.t("status.forumUngueltig"));
            return;
        }
        letztesLayout = layout.getValue() == null ? "" : layout.getValue().getFileName().toString();
        Zusammenstellung.Auftrag auftrag = new Zusammenstellung.Auftrag(layout.getValue(), gruppe.getValue(),
                Einstellungen.docsOrdner(), Einstellungen.arbeitsordner(), alsXml.isSelected(),
                direkt.isSelected() && !anf, sprache.getValue(), zugtabelle.isSelected() && !anf,
                bahnsteige.isSelected() && !anf, format.getValue(),
                mitForum ? url : "", mitForum ? forumText.getText() : "",
                Einstellungen.handbuchAdresse(),
                HandbuchSprache.aus(Einstellungen.get(Einstellungen.HANDBUCH_SPRACHE, "")));
        Task<Zusammenstellung.Ergebnis> task = new Task<>() {
            @Override
            protected Zusammenstellung.Ergebnis call() throws IOException {
                return Zusammenstellung.ausfuehren(auftrag, AnalyseApplication.this::frage,
                        m -> Platform.runLater(() -> melde(m)));
            }
        };
        task.setOnSucceeded(e -> fertig(task.getValue()));
        task.setOnFailed(e -> {
            Throwable t = task.getException();
            if (!(t instanceof Zusammenstellung.Abbruch)) {
                melde(Zusammenstellung.FEHLT + " " + I18n.t("status.fehler",
                        t.getMessage() == null ? t.toString() : t.getMessage()));
            }
            start.setDisable(false);
        });
        start.setDisable(true);
        letztes = null;
        ansichtenSchalten();
        Thread th = new Thread(task, "zusammenstellung");
        th.setDaemon(true);
        th.start();
    }

    private void fertig(Zusammenstellung.Ergebnis e) {
        letztes = e;
        zwischenablage(e);
        start.setDisable(false);
        ergebnisZeigen.setDisable(false);
        oeffnen.setDisable(false);
        ansichtenSchalten();
        ErgebnisFenster.zeige(stage, e, letztesLayout);
    }

    /** Dateien und Nachrichtentext in die Zwischenablage; bearbeitete Texte werden im Protokoll genannt. */
    private void zwischenablage(Zusammenstellung.Ergebnis e) {
        ClipboardContent inhalt = new ClipboardContent();
        inhalt.putFiles(e.dateien().stream().map(Path::toFile).toList());
        inhalt.putString(e.nachricht());
        Clipboard.getSystemClipboard().setContent(inhalt);
        melde(Zusammenstellung.OK + " " + I18n.t("status.zwischenablage"));
        if (!e.bearbeitet().isEmpty()) {
            melde(Zusammenstellung.WARNUNG + " " + I18n.t("status.enthaeltBearbeitet",
                    String.join(", ", e.bearbeitet())));
        }
    }

    /** Menü "Ansichten": erst nutzbar, wenn alles zusammengestellt ist; Release-Text nur, wenn vorhanden. */
    private void ansichtenSchalten() {
        if (ansichtPrompt == null) {
            return;
        }
        ansichtPrompt.setDisable(letztes == null || letztes.text(Bearbeitet.Art.PROMPT).isEmpty());
        ansichtRelease.setDisable(letztes == null || letztes.text(Bearbeitet.Art.RELEASE).isEmpty());
        ansichtHandbuch.setDisable(letztes == null || letztes.text(Bearbeitet.Art.HANDBUCH).isEmpty());
    }

    private void ansicht(Bearbeitet.Art art) {
        if (letztes == null) {
            return;
        }
        letztes.text(art).ifPresent(t -> TextFenster.zeige(stage, t, () -> letztes, neu -> {
            letztes = neu;
            for (Zusammenstellung.Status s : neu.status().subList(Math.max(0, neu.status().size() - 1),
                    neu.status().size())) {
                melde(s.symbol() + " " + s.text());
            }
            zwischenablage(neu);
            ErgebnisFenster.aktualisieren(stage, neu, letztesLayout);
        }));
    }

    /** Beim automatischen Start bleiben reine Erfolgsmeldungen weg, damit das Symbol im Fortschritt stehen bleibt. */
    private void meldeStart(boolean vonHand, String text) {
        if (vonHand) {
            meldeFx(text);
        }
    }

    private void meldeFx(String text) {
        Platform.runLater(() -> melde(text));
    }

    private void melde(String text) {
        if (protokoll != null) {
            protokoll.appendText(text + "\n");
        }
    }
}
