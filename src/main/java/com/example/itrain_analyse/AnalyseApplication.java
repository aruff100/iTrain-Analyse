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
 * Hauptfenster: Kenntnisstand, Layout-Datei, Prompt-Sprache, Direktmodus mit Sprache/Format/Zusätzen (Zusatz 3
 * "Signale prüfen" legt die Datei Zusatz_Signale bei), Zusatzauftrag an die KI, Forumsbeitrag, Fortschritt. Im Kenntnisstand "Anfänger" (Prompt für Anfänger, Runden) entfallen Direktmodus,
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
    private CheckBox signale;
    // Kurzprüfung ohne KI und Schritte zur KI-Analyse in eigenen Fenstern (Anwender 18.11.2026)
    private Label ablauf;
    private Button kurzStart;
    private Zusammenstellung.Kurz letzteKurz;
    /** Das gerade offene Schrittfenster und sein Fortschrittsfeld (Meldungen gehen auch dorthin). */
    private Stage schritt;
    private TextArea schrittProtokoll;
    private Label auftragLabel;
    private TextArea auftragFeld;
    private Label auftragHinweis;
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
    private MenuItem ansichtAktualisieren;
    private MenuItem ansichtPrompt;
    private MenuItem ansichtSignale;
    private MenuItem ansichtRelease;
    private MenuItem ansichtHandbuch;
    private TextArea protokoll;
    private Button start;
    private Button ergebnisZeigen;
    private Button oeffnen;

    private Zusammenstellung.Ergebnis letztes;
    /** Quelle des Menüs "Ansichten": die letzte Zusammenstellung oder die Dokumente nach "Aktualisieren". */
    private Zusammenstellung.Ergebnis ansichtQuelle;
    /** Direktmodus ist bei jedem Start aus und wird nicht gespeichert (Anwender 03.11.2026). */
    private boolean direktGewaehlt;
    /** Forum-Beitrag und Adresse sind bei jedem Start leer und werden nicht gespeichert (Anwender 03.11.2026). */
    private boolean forumGewaehlt;
    private String forumAdresse = "";
    private String forumInhalt = "";
    /** Der Zusatzauftrag gilt für eine Analyse und wird nicht gespeichert (Anwender 16.11.2026). */
    private String auftragInhalt = "";
    private String letztesLayout = "";

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        ErsterStartDialog.beiBedarf();
        // Größer als bisher, aber nie größer als der Bildschirm (Anwender 18.11.2026).
        javafx.geometry.Rectangle2D bild = javafx.stage.Screen.getPrimary().getVisualBounds();
        scene = new Scene(new StackPane(), Math.min(960, bild.getWidth() * 0.95), Math.min(760, bild.getHeight() * 0.95));
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
        signale = new CheckBox(I18n.t("feld.signale"));
        // Hauptfenster: nur Kenntnisstand, Layout-Datei und der Start; alles Weitere in Schrittfenstern.
        ablauf = new Label(I18n.t("haupt.ablauf"));
        ablauf.setWrapText(true);
        ablauf.setMinHeight(Region.USE_PREF_SIZE);
        kurzStart = Oberflaeche.farbe(new Button("▶  " + I18n.t("knopf.kurzpruefung")), Oberflaeche.GRUEN);
        kurzStart.setStyle(kurzStart.getStyle() + " -fx-font-size: 15px; -fx-font-weight: bold; -fx-padding: 8 18;");
        kurzStart.setDefaultButton(true);
        kurzStart.setOnAction(e -> kurzpruefung());
        // Eigener Auftrag an die KI, z. B. ein Relais oder den Ablauf einer Aktion untersuchen (Anwender 16.11.2026).
        auftragLabel = new Label(I18n.t("feld.auftrag"));
        auftragLabel.setWrapText(true);
        auftragLabel.setMaxWidth(150);
        auftragFeld = new TextArea();
        auftragFeld.setPrefRowCount(3);
        auftragFeld.setWrapText(true);
        auftragFeld.setPromptText(I18n.t("feld.auftragLeer"));
        auftragHinweis = new Label(I18n.t("feld.auftragHinweis"));
        auftragHinweis.setWrapText(true);
        auftragHinweis.setStyle("-fx-font-size: 11px; -fx-opacity: 0.85;");
        auftragHinweis.setMinHeight(Region.USE_PREF_SIZE);
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
        ergebnisZeigen = Oberflaeche.farbe(new Button(I18n.t("knopf.ergebnis")), Oberflaeche.BLAU);
        oeffnen = Oberflaeche.farbe(new Button(I18n.t("knopf.ordnerOeffnen")), Oberflaeche.TUERKIS);
        anleitung = Oberflaeche.farbe(new Button(I18n.t("knopf.anleitung")), Oberflaeche.ORANGE);
        anleitung.setOnAction(e -> AnfaengerDialog.zeige(stage));
        anleitung.visibleProperty().bind(anfaenger.selectedProperty());
        anleitung.managedProperty().bind(anfaenger.selectedProperty());
        HBox knoepfe = new HBox(8, ergebnisZeigen, oeffnen, anleitung);
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
        // Das Formular rollt, wenn der Platz nicht reicht; der Fortschritt bleibt immer sichtbar (Anwender 18.11.2026).
        VBox formular = new VBox(10, gitter, knoepfe);
        formular.setPadding(new Insets(12, 12, 0, 12));
        javafx.scene.control.ScrollPane rollen = new javafx.scene.control.ScrollPane(formular);
        rollen.setFitToWidth(true);
        rollen.setStyle("-fx-background-color: transparent; -fx-background-insets: 0;");
        protokollFeld.setMinHeight(170);
        VBox unten = new VBox(6, fortschritt, protokollFeld);
        unten.setPadding(new Insets(0, 12, 12, 12));
        VBox.setVgrow(protokollFeld, Priority.ALWAYS);
        javafx.scene.control.SplitPane mitte = new javafx.scene.control.SplitPane(rollen, unten);
        mitte.setOrientation(javafx.geometry.Orientation.VERTICAL);
        mitte.setDividerPositions(0.42);

        BorderPane wurzel = new BorderPane();
        wurzel.setTop(new VBox(menue(), modusZeile));
        wurzel.setCenter(mitte);

        // Werte
        boolean anf = anfaenger.isSelected();
        gruppe.getItems().setAll(Sprachgruppe.von(anf));
        gruppe.setOnAction(e -> sprachenSetzen());
        format.getItems().setAll(Sprachgruppe.FORMATE);
        direkt.setOnAction(e -> {
            // Beim Einschalten sind alle Zusätze aus; der Anwender wählt sie selbst (Anwender 16.11.2026).
            if (direkt.isSelected()) {
                zugtabelle.setSelected(false);
                bahnsteige.setSelected(false);
                signale.setSelected(false);
            }
            schalten();
        });
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
        signale.setSelected(Einstellungen.getBool("signale", false));
        auftragFeld.setText(auftragInhalt);
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

        // "Aktualisieren" prüft die Fassungen und stellt die Dokumente bereit (Anwender 16.11.2026).
        ansichtAktualisieren = new MenuItem(I18n.t("menu.ansicht.aktualisieren"));
        ansichtAktualisieren.setOnAction(e -> aktualisieren());
        ansichtPrompt = new MenuItem(I18n.t("menu.ansicht.prompt"));
        ansichtPrompt.setOnAction(e -> ansicht(Bearbeitet.Art.PROMPT));
        ansichtSignale = new MenuItem(I18n.t("menu.ansicht.signale"));
        ansichtSignale.setOnAction(e -> ansicht(Bearbeitet.Art.SIGNALE));
        ansichtRelease = new MenuItem(I18n.t("menu.ansicht.release"));
        ansichtRelease.setOnAction(e -> ansicht(Bearbeitet.Art.RELEASE));
        ansichtHandbuch = new MenuItem(I18n.t("menu.ansicht.handbuch"));
        ansichtHandbuch.setOnAction(e -> ansicht(Bearbeitet.Art.HANDBUCH));
        Menu ansichten = new Menu(I18n.t("menu.ansichten"), null, ansichtAktualisieren, new SeparatorMenuItem(),
                ansichtPrompt, ansichtSignale, ansichtRelease, ansichtHandbuch);

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
        Einstellungen.setBool("signale", signale.isSelected());
        auftragInhalt = auftragFeld.getText();
        forumGewaehlt = forum.isSelected();
        forumAdresse = forumUrl.getText().trim();
        forumInhalt = forumText.getText();
    }

    /**
     * Hauptfenster (Anwender 18.11.2026): Layout-Datei und der Knopf "Kurzprüfung starten". Prompt-Sprache,
     * Direktmodus, Zusätze, eigener Auftrag und Forumsbeitrag fragen die Schrittfenster nacheinander ab.
     */
    private void gitterFuellen() {
        gitter.getChildren().clear();
        int z = 0;
        gitter.addRow(z++, new Label(I18n.t("feld.layout")), layout, andereKnopf);
        // "Neu einlesen" linksbündig unter der Liste (Anwender 16.11.2026)
        gitter.add(neuLadenKnopf, 1, z++);
        gitter.add(Oberflaeche.linie3d(), 0, z++, 3, 1);
        gitter.add(ablauf, 0, z++, 3, 1);
        gitter.add(kurzStart, 1, z++, 2, 1);
        if (anfaenger.isSelected()) {
            gitter.add(anfHinweis, 1, z++, 2, 1);
        }
    }

    // ---------------------------------------------------------------- Schrittfenster

    private static Label absatz(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMinHeight(Region.USE_PREF_SIZE);
        return l;
    }

    private static Label ueberschrift(String text, int groesse) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMinHeight(Region.USE_PREF_SIZE);
        l.setStyle("-fx-font-weight: bold; -fx-font-size: " + groesse + "px;");
        return l;
    }

    private static Region luecke() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    /** Öffnet ein Schrittfenster (ersetzt das vorige) mit Inhalt und Knopfleiste. */
    private Stage zeigeSchritt(String titel, javafx.scene.Node inhalt, HBox knoepfe, double breite, double hoehe) {
        if (schritt != null) {
            schritt.close();
        }
        Stage st = new Stage();
        st.initOwner(stage);
        st.initModality(javafx.stage.Modality.WINDOW_MODAL);
        st.setTitle(titel);
        javafx.scene.control.ScrollPane sp = new javafx.scene.control.ScrollPane(inhalt);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        knoepfe.setPadding(new Insets(10, 16, 12, 16));
        knoepfe.setAlignment(Pos.CENTER_LEFT);
        BorderPane w = new BorderPane(sp);
        w.setBottom(knoepfe);
        javafx.geometry.Rectangle2D bild = javafx.stage.Screen.getPrimary().getVisualBounds();
        Scene sc = new Scene(w, Math.min(breite, bild.getWidth() * 0.95), Math.min(hoehe, bild.getHeight() * 0.95));
        Farben.anwenden(sc);
        st.setScene(sc);
        st.setOnHidden(e -> {
            if (schritt == st) {
                schritt = null;
                schrittProtokoll = null;
            }
        });
        schritt = st;
        st.show();
        return st;
    }

    private static VBox inhalt(javafx.scene.Node... teile) {
        VBox v = new VBox(10, teile);
        v.setPadding(new Insets(16));
        return v;
    }

    /** Fenster 1: Ergebnis der Kurzprüfung mit Erklärung, Drucken, "Fertig" oder "Weiter zur KI-Analyse". */
    private void kurzFenster(Zusammenstellung.Kurz k) {
        Vorpruefung.Ergebnis e = k.ergebnis();
        Label titel = ueberschrift(I18n.t("kurz.titel") + ": " + letztesLayout, 18);
        Label ergebnis = ueberschrift(I18n.t("kurz.ergebnis", e.anzahl(Vorpruefung.Art.FEHLER),
                e.anzahl(Vorpruefung.Art.HINWEIS), e.anzahl(Vorpruefung.Art.ANMERKUNG), e.routenGeprueft()), 14);
        TextArea befunde = new TextArea(Vorpruefung.uebersicht(e));
        befunde.setEditable(false);
        befunde.setWrapText(true);
        befunde.setPrefRowCount(16);
        befunde.setStyle("-fx-font-family: 'monospaced';");
        VBox.setVgrow(befunde, Priority.ALWAYS);
        Label ersparnis = ueberschrift(k.ersparnis() > 0
                ? I18n.t("kurz.ersparnis", k.ersparnis(), k.mitKb(), k.ohneKb())
                : I18n.t("kurz.ersparnisUnbekannt"), 12);
        VBox v = inhalt(titel,
                ueberschrift(I18n.t("kurz.h.was"), 14), absatz(I18n.t("kurz.wasGeprueft")),
                ueberschrift(I18n.t("kurz.h.ergebnis"), 14), ergebnis, befunde,
                ueberschrift(I18n.t("kurz.h.ki"), 14), absatz(I18n.t("kurz.wasKi")),
                ueberschrift(I18n.t("kurz.h.token"), 14), absatz(I18n.t("kurz.token")), ersparnis,
                Oberflaeche.linie3d(), absatz(I18n.t("kurz.wieWeiter")));
        Button drucken = Oberflaeche.farbe(new Button("🖨  " + I18n.t("knopf.drucken")), Oberflaeche.LILA);
        drucken.setOnAction(x -> Pruefbericht.drucken(schritt, k.pruefbericht()));
        Button pdf = Oberflaeche.farbe(new Button("📄  " + I18n.t("knopf.berichtAnsehen")), Oberflaeche.TUERKIS);
        pdf.setOnAction(x -> Oberflaeche.oeffne(k.pruefbericht().toFile(), null, false));
        Button fertig = Oberflaeche.farbe(new Button(I18n.t("knopf.fertig")), Oberflaeche.ROSA);
        Button weiter = Oberflaeche.farbe(new Button(I18n.t("knopf.weiterKi") + "  ▶"), Oberflaeche.GRUEN);
        weiter.setDefaultButton(true);
        weiter.setOnAction(x -> kiFenster());
        fertig.setOnAction(x -> {
            // Die Kurzprüfung ist oberflächlich und kann Fehler machen (Anwender 18.11.2026).
            if (frage(I18n.t("dialog.kurzFertig.titel"), I18n.t("dialog.kurzFertig.text"), I18n.t("knopf.dochKi"),
                    I18n.t("knopf.beenden"))) {
                kiFenster();
            } else {
                schritt.close();
            }
        });
        zeigeSchritt(I18n.t("kurz.titel") + " – " + letztesLayout, v,
                new HBox(8, drucken, pdf, luecke(), fertig, weiter), 980, 1000);
    }

    private int schritte() {
        return anfaenger.isSelected() ? 2 : 3;
    }

    /** Fenster 2: Prompt-Sprache, Direktmodus, Sprache, Format, Zusätze. */
    private void kiFenster() {
        GridPane g = raster();
        int z = 0;
        g.addRow(z++, new Label(I18n.t("feld.promptSprache")), gruppe);
        g.add(alsXml, 1, z++, 2, 1);
        if (anfaenger.isSelected()) {
            g.add(anfHinweis, 1, z++, 2, 1);
            Button a = Oberflaeche.farbe(new Button(I18n.t("knopf.anleitung")), Oberflaeche.ORANGE);
            a.setOnAction(x -> AnfaengerDialog.zeige(schritt));
            g.add(a, 1, z, 2, 1);
        } else {
            g.add(Oberflaeche.linie3d(), 0, z++, 3, 1);
            g.add(direkt, 1, z++, 2, 1);
            g.addRow(z++, new Label(I18n.t("feld.sprache")), sprache);
            g.addRow(z++, new Label(I18n.t("feld.format")), format);
            g.add(zugtabelle, 1, z++, 2, 1);
            g.add(bahnsteige, 1, z++, 2, 1);
            g.add(signale, 1, z, 2, 1);
        }
        VBox v = inhalt(ueberschrift(I18n.t("ki.titel"), 18),
                absatz(I18n.t("ki.schritt", 1, schritte(), I18n.t("ki.s1"))), absatz(I18n.t("ki.einleitung1")),
                Oberflaeche.linie3d(), g);
        Button abbruch = Oberflaeche.farbe(new Button(I18n.t("knopf.abbrechen")), Oberflaeche.ROSA);
        abbruch.setOnAction(x -> schritt.close());
        Button weiter = Oberflaeche.farbe(new Button(I18n.t("knopf.weiter") + "  ▶"), Oberflaeche.GRUEN);
        weiter.setDefaultButton(true);
        weiter.setOnAction(x -> {
            speichern();
            if (anfaenger.isSelected()) {
                zusammenFenster();
            } else {
                auftragFenster();
            }
        });
        zeigeSchritt(I18n.t("ki.titel") + " – " + letztesLayout, v, new HBox(8, abbruch, luecke(), weiter), 860, 720);
    }

    /** Fenster 3: eigener Auftrag an die KI und Forumsbeitrag. */
    private void auftragFenster() {
        GridPane g = raster();
        int z = 0;
        g.add(auftragLabel, 0, z);
        g.add(auftragFeld, 1, z++, 2, 1);
        GridPane.setValignment(auftragLabel, javafx.geometry.VPos.TOP);
        g.add(auftragHinweis, 1, z++, 2, 1);
        g.add(Oberflaeche.linie3d(), 0, z++, 3, 1);
        g.add(forum, 1, z++, 2, 1);
        g.addRow(z++, forumUrlLabel, forumUrl);
        GridPane.setColumnSpan(forumUrl, 2);
        g.add(forumHinweis, 1, z++, 2, 1);
        g.add(forumTextLabel, 0, z);
        g.add(forumText, 1, z, 2, 1);
        GridPane.setValignment(forumTextLabel, javafx.geometry.VPos.TOP);
        VBox v = inhalt(ueberschrift(I18n.t("ki.titel"), 18),
                absatz(I18n.t("ki.schritt", 2, schritte(), I18n.t("ki.s2"))), absatz(I18n.t("ki.einleitung2")),
                Oberflaeche.linie3d(), g);
        Button abbruch = Oberflaeche.farbe(new Button(I18n.t("knopf.abbrechen")), Oberflaeche.ROSA);
        abbruch.setOnAction(x -> schritt.close());
        Button weiter = Oberflaeche.farbe(new Button(I18n.t("knopf.weiter") + "  ▶"), Oberflaeche.GRUEN);
        weiter.setDefaultButton(true);
        weiter.setOnAction(x -> {
            speichern();
            zusammenFenster();
        });
        zeigeSchritt(I18n.t("ki.titel") + " – " + letztesLayout, v, new HBox(8, abbruch, luecke(), weiter), 900, 760);
    }

    /**
     * Letztes Fenster: "Zusammenstellen" füllt nur den Ordner und den Fortschritt; "Ausführen" legt das Paket in
     * die Zwischenablage und öffnet das Fenster für den Chat.
     */
    private void zusammenFenster() {
        TextArea p = new TextArea();
        p.setEditable(false);
        p.setWrapText(true);
        p.setStyle("-fx-font-family: 'monospaced';");
        VBox.setVgrow(p, Priority.ALWAYS);
        Button ordner = Oberflaeche.farbe(new Button(I18n.t("knopf.ordnerOeffnen")), Oberflaeche.TUERKIS);
        ordner.setOnAction(x -> {
            Path o = letztes != null ? letztes.ordner() : Einstellungen.arbeitsordner();
            Oberflaeche.oeffne(o.toFile(), null, false);
        });
        HBox oben = new HBox(8, start, ordner);
        VBox v = inhalt(ueberschrift(I18n.t("ki.titel"), 18),
                absatz(I18n.t("ki.schritt", schritte(), schritte(), I18n.t("ki.s3"))),
                absatz(I18n.t("ki.einleitung3")), oben, new Label(I18n.t("feld.fortschritt")), p);
        Button abbruch = Oberflaeche.farbe(new Button(I18n.t("knopf.abbrechen")), Oberflaeche.ROSA);
        abbruch.setOnAction(x -> schritt.close());
        Button ausfuehren = Oberflaeche.farbe(new Button(I18n.t("knopf.ausfuehren") + "  ▶"), Oberflaeche.GRUEN);
        ausfuehren.setDisable(true);
        ausfuehren.setOnAction(x -> {
            if (letztes != null) {
                zwischenablage(letztes);
                schritt.close();
                ErgebnisFenster.zeige(stage, letztes, letztesLayout);
            }
        });
        start.setDisable(false);
        start.setOnAction(x -> zusammenstellen(() -> {
            ausfuehren.setDisable(false);
            ausfuehren.setDefaultButton(true);
        }));
        zeigeSchritt(I18n.t("ki.titel") + " – " + letztesLayout, v, new HBox(8, abbruch, luecke(), ausfuehren),
                900, 780);
        schrittProtokoll = p;
    }

    private static GridPane raster() {
        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(8);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        ColumnConstraints c0 = new ColumnConstraints();
        c0.setMinWidth(Region.USE_PREF_SIZE);
        g.getColumnConstraints().addAll(c0, c1, new ColumnConstraints());
        return g;
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
        signale.setDisable(!an);
        forumUrl.setDisable(!forum.isSelected());
        forumUrlLabel.setDisable(!forum.isSelected());
        forumHinweis.setDisable(!forum.isSelected());
        forumTextLabel.setDisable(!forum.isSelected());
        forumText.setDisable(!forum.isSelected());
    }

    private boolean frage(String titel, String text, String ja, String nein) {
        // Rückfragen gehören zum offenen Schrittfenster, damit es nicht hinter das Hauptfenster rutscht.
        Stage owner = schritt != null ? schritt : stage;
        return Oberflaeche.frage(owner, titel, text, ja, nein);
    }

    // ---------------------------------------------------------------- Prüfung beim Start

    /** Prüft Programm, Prompt (Webserver) und Handbuch auf neue Fassungen; jeder Download nur nach Rückfrage. */
    private void pruefeAktualisierungen(boolean vonHand) {
        Thread t = new Thread(() -> pruefung(vonHand, true), "pruefung");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Prüft Programm (optional), Prompt und Zusatzdateien auf dem Server, die Signalregeln bei Berros und das
     * Handbuch; neue Fassungen nach Rückfrage laden. Läuft im aufrufenden Hintergrund-Thread.
     */
    private void pruefung(boolean vonHand, boolean programm) {
        meldeStart(vonHand, Zusammenstellung.LAEUFT + " " + I18n.t("start.pruefe"));
        if (programm) {
            try {
                ProgrammUpdate.Stand ps = ProgrammUpdate.pruefe();
                if (ps.neu() == null) {
                    meldeStart(vonHand, Zusammenstellung.OK + " " + I18n.t("start.programmAktuell", AppVersion.text()));
                } else {
                    String inst = ps.installiert() == null ? "?" : ps.installiert().toString();
                    meldeFx(Zusammenstellung.WARNUNG + " " + I18n.t("start.programmNeu", ps.neu(), inst));
                    if (frage(I18n.t("dialog.programm.titel"), I18n.t("dialog.programm.text", ps.neu(), inst),
                            I18n.t("knopf.webseite"), I18n.t("knopf.spaeter"))) {
                        Oberflaeche.oeffne(null, ProgrammUpdate.WEBSEITE, false);
                    }
                }
            } catch (IOException | RuntimeException ex) {
                meldeStart(vonHand, Zusammenstellung.WARNUNG + " " + I18n.t("start.programmOffline", ex.getMessage()));
            }
        }
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
        // Signalregeln bei Berros: Hat sich die Quelle der Zusatzdatei geändert? (nur Hinweis, kein Download)
        try {
            Sprachgruppe g = gruppeWert();
            Path zusatz = PromptQuelle.aktuell(Zusatzdatei.signale(g == null ? Sprachgruppe.DE_NL : g));
            String m = SignalQuelle.meldung(SignalQuelle.pruefe(zusatz));
            if (m == null) {
                meldeStart(vonHand, Zusammenstellung.OK + " " + I18n.t("start.signaleAktuell"));
            } else {
                meldeFx(Zusammenstellung.WARNUNG + " " + m);
            }
        } catch (IOException ex) {
            meldeStart(vonHand, Zusammenstellung.WARNUNG + " " + I18n.t("start.signaleOffline", ex.getMessage()));
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
    }

    /**
     * Menü "Ansichten" > "Aktualisieren": prüft, ob Prompt, Signale und Handbuch die aktuelle Fassung haben (neue
     * nach Rückfrage laden), und legt die Dokumente in {@code <Arbeitsordner>/Dokumente} bereit. Danach lassen sie
     * sich wie nach dem Zusammenstellen ansehen und bearbeiten.
     */
    private void aktualisieren() {
        speichern();
        protokoll.clear();
        boolean anf = anfaenger.isSelected();
        Zusammenstellung.Auftrag a = new Zusammenstellung.Auftrag(layout.getValue(), gruppe.getValue(),
                Einstellungen.docsOrdner(), Einstellungen.arbeitsordner(), false, direkt.isSelected() && !anf,
                sprache.getValue(), false, false, !anf, format.getValue(), "", "", "",
                Einstellungen.handbuchAdresse(),
                HandbuchSprache.aus(Einstellungen.get(Einstellungen.HANDBUCH_SPRACHE, "")), false, false);
        ansichtAktualisieren.setDisable(true);
        Thread t = new Thread(() -> {
            pruefung(true, false);
            try {
                Zusammenstellung.Ergebnis e = Zusammenstellung.dokumente(a, this::frage, this::meldeFx);
                Platform.runLater(() -> {
                    ansichtQuelle = e;
                    ansichtAktualisieren.setDisable(false);
                    ansichtenSchalten();
                });
            } catch (IOException | RuntimeException ex) {
                meldeFx(Zusammenstellung.FEHLT + " " + I18n.t("status.fehler",
                        ex.getMessage() == null ? ex.toString() : ex.getMessage()));
                Platform.runLater(() -> ansichtAktualisieren.setDisable(false));
            }
        }, "aktualisieren");
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

    private void zusammenstellen(Runnable fertig) {
        speichern();
        if (schrittProtokoll != null) {
            schrittProtokoll.clear();
        }
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
                bahnsteige.isSelected() && !anf, direkt.isSelected() && signale.isSelected() && !anf,
                format.getValue(), mitForum ? url : "", mitForum ? forumText.getText() : "",
                anf ? "" : auftragFeld.getText(),
                Einstellungen.handbuchAdresse(),
                HandbuchSprache.aus(Einstellungen.get(Einstellungen.HANDBUCH_SPRACHE, "")),
                !anf, Einstellungen.getBool(Einstellungen.HANDBUCH_AUSZUG, true));
        Task<Zusammenstellung.Ergebnis> task = new Task<>() {
            @Override
            protected Zusammenstellung.Ergebnis call() throws IOException {
                return Zusammenstellung.ausfuehren(auftrag, AnalyseApplication.this::frage,
                        m -> Platform.runLater(() -> melde(m)));
            }
        };
        task.setOnSucceeded(e -> {
            fertig(task.getValue());
            fertig.run();
        });
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
        ansichtQuelle = null;
        ansichtenSchalten();
        Thread th = new Thread(task, "zusammenstellung");
        th.setDaemon(true);
        th.start();
    }

    /** Kurzprüfung ohne KI: Prüfbericht für die gewählte Layout-Datei (Anwender 18.11.2026). */
    private void kurzpruefung() {
        speichern();
        protokoll.clear();
        boolean anf = anfaenger.isSelected();
        letztesLayout = layout.getValue() == null ? "" : layout.getValue().getFileName().toString();
        // Sprache des Prüfberichts nach der Oberfläche: Deutsch für de und nl, sonst Englisch.
        boolean deutsch = List.of("de", "nl").contains(I18n.sprache());
        Zusammenstellung.Auftrag a = new Zusammenstellung.Auftrag(layout.getValue(),
                deutsch ? Sprachgruppe.DE_NL : Sprachgruppe.EN_FR_ES_IT,
                Einstellungen.docsOrdner(), Einstellungen.arbeitsordner(), false, direkt.isSelected() && !anf,
                sprache.getValue(), true, true, false, format.getValue(), "", "", "",
                Einstellungen.handbuchAdresse(),
                HandbuchSprache.aus(Einstellungen.get(Einstellungen.HANDBUCH_SPRACHE, "")), true,
                Einstellungen.getBool(Einstellungen.HANDBUCH_AUSZUG, true));
        kurzStart.setDisable(true);
        Task<Zusammenstellung.Kurz> task = new Task<>() {
            @Override
            protected Zusammenstellung.Kurz call() throws IOException {
                return Zusammenstellung.kurzpruefung(a, m -> Platform.runLater(() -> melde(m)));
            }
        };
        task.setOnSucceeded(e -> {
            letzteKurz = task.getValue();
            kurzStart.setDisable(false);
            kurzFenster(letzteKurz);
        });
        task.setOnFailed(e -> {
            kurzStart.setDisable(false);
            Throwable ex = task.getException();
            melde(Zusammenstellung.FEHLT + " " + I18n.t("status.fehler",
                    ex == null || ex.getMessage() == null ? String.valueOf(ex) : ex.getMessage()));
        });
        Thread th = new Thread(task, "kurzpruefung");
        th.setDaemon(true);
        th.start();
    }

    /** Zusammengestellt: Ordner und Fortschritt sind gefüllt; Zwischenablage und Chat-Fenster erst bei "Ausführen". */
    private void fertig(Zusammenstellung.Ergebnis e) {
        letztes = e;
        ansichtQuelle = e;
        start.setDisable(false);
        ergebnisZeigen.setDisable(false);
        oeffnen.setDisable(false);
        ansichtenSchalten();
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

    /**
     * Menü "Ansichten": die Dokumente sind ausgegraut, bis zusammengestellt oder über "Aktualisieren" bereitgestellt
     * wurde; ein Eintrag nur, wenn das Dokument vorhanden ist (Signale z. B. nur mit Zusatz 3 oder nach
     * "Aktualisieren").
     */
    private void ansichtenSchalten() {
        if (ansichtPrompt == null) {
            return;
        }
        ansichtPrompt.setDisable(ansichtQuelle == null || ansichtQuelle.text(Bearbeitet.Art.PROMPT).isEmpty());
        ansichtSignale.setDisable(ansichtQuelle == null || ansichtQuelle.text(Bearbeitet.Art.SIGNALE).isEmpty());
        ansichtRelease.setDisable(ansichtQuelle == null || ansichtQuelle.text(Bearbeitet.Art.RELEASE).isEmpty());
        ansichtHandbuch.setDisable(ansichtQuelle == null || ansichtQuelle.text(Bearbeitet.Art.HANDBUCH).isEmpty());
    }

    private void ansicht(Bearbeitet.Art art) {
        if (ansichtQuelle == null) {
            return;
        }
        ansichtQuelle.text(art).ifPresent(t -> TextFenster.zeige(stage, t, () -> ansichtQuelle, neu -> {
            ansichtQuelle = neu;
            for (Zusammenstellung.Status s : neu.status().subList(Math.max(0, neu.status().size() - 1),
                    neu.status().size())) {
                melde(s.symbol() + " " + s.text());
            }
            if (!Zusammenstellung.nurDokumente(neu)) {
                letztes = neu;
                zwischenablage(neu);
                ErgebnisFenster.aktualisieren(stage, neu, letztesLayout);
            }
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
        if (schrittProtokoll != null) {
            schrittProtokoll.appendText(text + "\n");
        }
    }
}
