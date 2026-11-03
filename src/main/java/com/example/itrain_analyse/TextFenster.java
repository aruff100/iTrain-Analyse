package com.example.itrain_analyse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Menü "Ansichten": zeigt Prompt-, Release- oder Handbuch-Text formatiert (Überschriften und Seitenmarken
 * hervorgehoben) und lässt ihn bearbeiten. Gespeichert wird als {@code Name_bearbeitet.txt} im Ordner
 * {@code Bearbeitet} und im Ordner des Laufs; das Original bleibt unverändert.
 */
final class TextFenster {

    private static final Pattern SEITE = Pattern.compile("^===== Seite \\d+ von \\d+ =====$");
    private static final Pattern REGEL = Pattern.compile("^([PAEZX]\\d+\\.)\\s.*");
    private static final Pattern VERSION = Pattern.compile("^(Version\\s+)?\\d+\\.\\d+(\\.\\d+)?\\b.*");
    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

    private TextFenster() {
    }

    /**
     * @param aktuell liefert das jeweils aktuelle Ergebnis (es ändert sich nach jedem Speichern)
     * @param gespeichert erhält das neue Ergebnis nach Speichern oder Wiederherstellen
     */
    static void zeige(Stage owner, Zusammenstellung.Text start, Supplier<Zusammenstellung.Ergebnis> aktuell,
            Consumer<Zusammenstellung.Ergebnis> gespeichert) {
        String inhalt;
        try {
            inhalt = Files.readString(start.datei(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            Oberflaeche.hinweis(owner, I18n.t("ansicht.fehler", ex.getMessage()));
            return;
        }
        Zusammenstellung.Text[] text = {start};
        String[] gespeichertText = {inhalt};

        Stage st = new Stage();
        st.initOwner(owner);

        Label titel = new Label();
        titel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label info = new Label();
        info.setWrapText(true);
        Runnable kopf = () -> {
            st.setTitle(start.bezeichnung() + " – " + text[0].datei().getFileName());
            titel.setText(start.bezeichnung() + ": " + text[0].datei().getFileName());
            info.setText(text[0].bearbeitet() ? "⚠ " + I18n.t("ansicht.istBearbeitet", text[0].original().getFileName())
                    : I18n.t("ansicht.istOriginal"));
            info.setStyle(text[0].bearbeitet() ? "-fx-text-fill: #b26a00;" : "");
        };
        kopf.run();

        // Ansicht (formatiert, virtualisiert, auch für das Handbuch mit über 250 Seiten schnell)
        ObservableList<String> zeilen = FXCollections.observableArrayList(inhalt.split("\n", -1));
        ListView<String> ansicht = new ListView<>(zeilen);
        ansicht.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        ansicht.setCellFactory(lv -> new Zeile(lv));
        ansicht.setOnKeyPressed(ev -> {
            if (new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN).match(ev)) {
                kopiere(String.join("\n", ansicht.getSelectionModel().getSelectedItems()));
                ev.consume();
            }
        });

        // Bearbeiten
        TextArea editor = new TextArea(inhalt);
        editor.setWrapText(true);
        editor.setStyle("-fx-font-family: 'monospaced'; -fx-font-size: 13px;");
        editor.setVisible(false);
        StackPane mitte = new StackPane(ansicht, editor);

        ToggleButton bearbeiten = Oberflaeche.farbe(new ToggleButton("✎  " + I18n.t("ansicht.bearbeiten")),
                Oberflaeche.ORANGE);
        Button ausschneiden = Oberflaeche.farbe(new Button(I18n.t("ansicht.ausschneiden")), Oberflaeche.TUERKIS);
        Button kopieren = Oberflaeche.farbe(new Button(I18n.t("knopf.kopieren")), Oberflaeche.TUERKIS);
        Button einfuegen = Oberflaeche.farbe(new Button(I18n.t("ansicht.einfuegen")), Oberflaeche.TUERKIS);
        Button alles = Oberflaeche.farbe(new Button(I18n.t("ansicht.allesMarkieren")), Oberflaeche.TUERKIS);
        ausschneiden.setOnAction(e -> editor.cut());
        einfuegen.setOnAction(e -> editor.paste());
        kopieren.setOnAction(e -> {
            if (editor.isVisible()) {
                editor.copy();
            } else {
                kopiere(String.join("\n", ansicht.getSelectionModel().getSelectedItems()));
            }
        });
        alles.setOnAction(e -> {
            if (editor.isVisible()) {
                editor.selectAll();
                editor.requestFocus();
            } else {
                ansicht.getSelectionModel().selectAll();
                ansicht.requestFocus();
            }
        });

        TextField suche = new TextField();
        suche.setPromptText(I18n.t("ansicht.suchen"));
        suche.setPrefColumnCount(16);
        Label treffer = new Label();
        Runnable weiter = () -> suchen(suche.getText(), editor, ansicht, treffer);
        suche.setOnAction(e -> weiter.run());
        Button suchKnopf = Oberflaeche.farbe(new Button(I18n.t("ansicht.suchenKnopf")), Oberflaeche.LILA);
        suchKnopf.setOnAction(e -> weiter.run());

        Region l1 = new Region();
        HBox.setHgrow(l1, Priority.ALWAYS);
        HBox werkzeuge = new HBox(6, bearbeiten, ausschneiden, kopieren, einfuegen, alles, l1, suche, suchKnopf,
                treffer);
        werkzeuge.setAlignment(Pos.CENTER_LEFT);

        String mod = MAC ? "⌘" : I18n.t("ansicht.strg");
        Label tasten = new Label(I18n.t("ansicht.tasten", mod));
        tasten.setStyle("-fx-opacity: 0.8;");
        tasten.setWrapText(true);

        Button speichern = Oberflaeche.farbe(new Button("💾  " + I18n.t("ansicht.speichern")), Oberflaeche.GRUEN);
        Button original = Oberflaeche.farbe(new Button(I18n.t("ansicht.original")), Oberflaeche.ORANGE);
        Button schliessen = Oberflaeche.farbe(new Button(I18n.t("knopf.schliessen")), Oberflaeche.ROSA);
        speichern.setDisable(true);
        original.setDisable(!start.bearbeitet());
        editor.textProperty().addListener((o, a, n) -> speichern.setDisable(n.equals(gespeichertText[0])));

        Runnable schalten = () -> {
            boolean an = bearbeiten.isSelected();
            if (!an) {
                zeilen.setAll(editor.getText().split("\n", -1));
            }
            editor.setVisible(an);
            ansicht.setVisible(!an);
            ausschneiden.setDisable(!an);
            einfuegen.setDisable(!an);
            (an ? editor : ansicht).requestFocus();
        };
        bearbeiten.setOnAction(e -> schalten.run());
        schalten.run();

        speichern.setOnAction(e -> {
            String neu = editor.getText();
            if (start.art() == Bearbeitet.Art.HANDBUCH && marken(neu) != marken(gespeichertText[0])
                    && !Oberflaeche.frage(st, I18n.t("ansicht.speichern"), I18n.t("ansicht.seitenmarken",
                            marken(gespeichertText[0]), marken(neu)), I18n.t("ansicht.speichern"),
                            I18n.t("knopf.abbrechen"))) {
                return;
            }
            uebernehmen(st, aktuell, text, neu, gespeichert);
            gespeichertText[0] = neu;
            speichern.setDisable(true);
            original.setDisable(false);
            kopf.run();
            Oberflaeche.hinweis(st, I18n.t("ansicht.gespeichert", text[0].datei().getFileName(),
                    text[0].datei().getParent()));
        });
        original.setOnAction(e -> {
            if (!Oberflaeche.frage(st, I18n.t("ansicht.original"), I18n.t("ansicht.originalFrage",
                    text[0].original().getFileName()), I18n.t("ansicht.original"), I18n.t("knopf.abbrechen"))) {
                return;
            }
            uebernehmen(st, aktuell, text, null, gespeichert);
            try {
                gespeichertText[0] = Files.readString(text[0].datei(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                gespeichertText[0] = "";
            }
            editor.setText(gespeichertText[0]);
            zeilen.setAll(gespeichertText[0].split("\n", -1));
            original.setDisable(true);
            kopf.run();
        });
        java.util.function.BooleanSupplier darfSchliessen = () -> editor.getText().equals(gespeichertText[0])
                || Oberflaeche.frage(st, I18n.t("ansicht.ungespeichert.titel"), I18n.t("ansicht.ungespeichert"),
                        I18n.t("ansicht.verwerfen"), I18n.t("knopf.abbrechen"));
        schliessen.setOnAction(e -> {
            if (darfSchliessen.getAsBoolean()) {
                st.close();
            }
        });
        st.setOnCloseRequest(e -> {
            if (!darfSchliessen.getAsBoolean()) {
                e.consume();
            }
        });
        schliessen.setCancelButton(true);
        Region l2 = new Region();
        HBox.setHgrow(l2, Priority.ALWAYS);
        HBox knoepfe = new HBox(8, speichern, original, l2, schliessen);

        VBox oben = new VBox(6, titel, info, werkzeuge);
        oben.setPadding(new Insets(12, 12, 6, 12));
        VBox unten = new VBox(6, tasten, knoepfe);
        unten.setPadding(new Insets(6, 12, 12, 12));
        BorderPane wurzel = new BorderPane(mitte);
        BorderPane.setMargin(mitte, new Insets(0, 12, 0, 12));
        wurzel.setTop(oben);
        wurzel.setBottom(unten);
        Scene sc = new Scene(wurzel, 900, 720);
        Farben.anwenden(sc);
        st.setScene(sc);
        st.show();
    }

    /** Speichert bzw. stellt das Original wieder her und meldet das neue Ergebnis. */
    private static void uebernehmen(Stage st, Supplier<Zusammenstellung.Ergebnis> aktuell,
            Zusammenstellung.Text[] text, String neu, Consumer<Zusammenstellung.Ergebnis> gespeichert) {
        Zusammenstellung.Ergebnis e = aktuell.get();
        if (e == null) {
            Oberflaeche.hinweis(st, I18n.t("ansicht.keinErgebnis"));
            return;
        }
        Zusammenstellung.Text t = e.text(text[0].art()).orElse(text[0]);
        try {
            Zusammenstellung.Ergebnis n = Zusammenstellung.aendere(e, t, neu);
            text[0] = n.text(t.art()).orElse(t);
            gespeichert.accept(n);
        } catch (IOException ex) {
            Oberflaeche.hinweis(st, I18n.t("ansicht.fehler", ex.getMessage()));
        }
    }

    private static int marken(String s) {
        int n = 0;
        for (String z : s.split("\n")) {
            if (SEITE.matcher(z.strip()).matches()) {
                n++;
            }
        }
        return n;
    }

    private static void kopiere(String s) {
        ClipboardContent c = new ClipboardContent();
        c.putString(s);
        Clipboard.getSystemClipboard().setContent(c);
    }

    /** Sucht ab der aktuellen Stelle weiter (ohne Groß-/Kleinschreibung) und springt zum Treffer. */
    private static void suchen(String was, TextArea editor, ListView<String> ansicht, Label treffer) {
        if (was == null || was.isBlank()) {
            treffer.setText("");
            return;
        }
        String w = was.toLowerCase(Locale.ROOT);
        if (editor.isVisible()) {
            String t = editor.getText().toLowerCase(Locale.ROOT);
            int ab = editor.getCaretPosition();
            int i = t.indexOf(w, ab);
            if (i < 0) {
                i = t.indexOf(w);
            }
            if (i >= 0) {
                editor.selectRange(i, i + w.length());
                editor.requestFocus();
            }
            treffer.setText(i < 0 ? I18n.t("ansicht.keinTreffer") : "");
            return;
        }
        List<String> z = ansicht.getItems();
        int ab = ansicht.getSelectionModel().getSelectedIndex() + 1;
        int gefunden = -1;
        for (int k = 0; k < z.size(); k++) {
            int i = (ab + k) % z.size();
            if (z.get(i).toLowerCase(Locale.ROOT).contains(w)) {
                gefunden = i;
                break;
            }
        }
        if (gefunden >= 0) {
            ansicht.getSelectionModel().clearAndSelect(gefunden);
            ansicht.scrollTo(Math.max(0, gefunden - 3));
        }
        treffer.setText(gefunden < 0 ? I18n.t("ansicht.keinTreffer") : "");
    }

    /** Eine Zeile der formatierten Ansicht. */
    private static final class Zeile extends ListCell<String> {

        /**
         * Der Text steht direkt in der Zelle (kein Label als Grafik): Mit Breite 0 als Vorgabe bricht die Zelle in
         * der Breite der Liste um und meldet dazu die richtige Höhe. Sonst schätzt die VirtualFlow die Höhe zu
         * klein ("index exceeds maxCellCount").
         */
        Zeile(ListView<String> lv) {
            setWrapText(true);
            setPrefWidth(0);
            setMinHeight(Region.USE_PREF_SIZE);
            selectedProperty().addListener((o, a, n) -> stil());
        }

        @Override
        protected void updateItem(String s, boolean leer) {
            super.updateItem(s, leer);
            setGraphic(null);
            setText(leer || s == null ? null : s.isEmpty() ? " " : s);
            stil();
        }

        private void stil() {
            String s = getItem();
            if (isEmpty() || s == null) {
                setStyle("");
                return;
            }
            String z = s.strip();
            String stil = "-fx-font-size: 13px;";
            if (SEITE.matcher(z).matches()) {
                stil = "-fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 10 4 2 4;"
                        + (isSelected() ? "" : " -fx-text-fill: #3574c9;");
            } else if (ueberschrift(z)) {
                stil = "-fx-font-size: 15px; -fx-font-weight: bold; -fx-padding: 8 4 2 4;";
            } else if (REGEL.matcher(z).matches() || VERSION.matcher(z).matches() && z.length() < 60) {
                stil = "-fx-font-size: 13px; -fx-font-weight: bold;";
            } else if (z.startsWith("- ") || z.startsWith("• ")) {
                stil = "-fx-font-size: 13px; -fx-padding: 2 4 2 18;";
            }
            setStyle(stil);
        }

        /** Zeile in Großbuchstaben (Abschnittsüberschrift im Prompt), mindestens vier Buchstaben. */
        private static boolean ueberschrift(String z) {
            int buchstaben = 0;
            for (char c : z.toCharArray()) {
                if (Character.isLetter(c)) {
                    if (Character.isLowerCase(c) && c != 'i') {
                        return false;
                    }
                    buchstaben++;
                }
            }
            return buchstaben >= 4 && z.length() < 120;
        }
    }
}
