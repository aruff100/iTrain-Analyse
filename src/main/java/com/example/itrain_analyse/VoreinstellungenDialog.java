package com.example.itrain_analyse;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

/** Einstellungen > Voreinstellungen: Pfade, Arbeitsverzeichnis, Farben, Sprache. */
public final class VoreinstellungenDialog {

    private VoreinstellungenDialog() {
    }

    /** @return {@code true}, wenn mit OK geschlossen (dann sind die Werte gespeichert). */
    public static boolean zeige(Stage owner) {
        ToggleGroup quelle = new ToggleGroup();
        RadioButton server = new RadioButton(I18n.t("vor.promptServer"));
        RadioButton ordner = new RadioButton(I18n.t("vor.promptOrdner"));
        server.setToggleGroup(quelle);
        ordner.setToggleGroup(quelle);
        (Einstellungen.promptVomServer() ? server : ordner).setSelected(true);
        TextField promptAdresse = feld(Einstellungen.promptAdresse());
        TextField promptOrdner = feld(Einstellungen.get(Einstellungen.PROMPT_ORDNER, ""));
        TextField layouts = feld(Einstellungen.get(Einstellungen.LAYOUT_ORDNER, Einstellungen.standardLayoutOrdner()));
        Path docsGefunden = Einstellungen.docsOrdner();
        TextField docs = feld(docsGefunden == null ? "" : docsGefunden.toString());
        TextField adresse = feld(Einstellungen.handbuchAdresse());
        ComboBox<HandbuchSprache> hbSprache = new ComboBox<>();
        hbSprache.getItems().setAll(HandbuchSprache.values());
        hbSprache.setValue(HandbuchSprache.aus(Einstellungen.get(Einstellungen.HANDBUCH_SPRACHE, "")));
        TextField arbeit = feld(Einstellungen.arbeitsordner().toString());
        promptAdresse.disableProperty().bind(ordner.selectedProperty());
        promptOrdner.disableProperty().bind(server.selectedProperty());

        GridPane pfade = raster();
        int z = 0;
        pfade.add(new Label(I18n.t("vor.promptQuelle")), 0, z);
        pfade.add(new HBox(16, server, ordner), 1, z++, 2, 1);
        pfade.addRow(z++, new Label(I18n.t("vor.promptAdresse")), promptAdresse);
        pfade.addRow(z++, new Label(I18n.t("vor.promptOrdnerFeld")), promptOrdner, waehlen(owner, promptOrdner));
        pfade.add(Oberflaeche.linie3d(), 0, z++, 3, 1);
        pfade.addRow(z++, new Label(I18n.t("vor.layouts")), layouts, waehlen(owner, layouts));
        pfade.addRow(z++, new Label(I18n.t("vor.docs")), docs, waehlen(owner, docs));
        pfade.add(Oberflaeche.linie3d(), 0, z++, 3, 1);
        pfade.addRow(z++, new Label(I18n.t("vor.handbuchAdresse")), adresse);
        pfade.addRow(z++, new Label(I18n.t("vor.handbuchSprache")), hbSprache);
        pfade.add(hinweis(I18n.t("vor.handbuchHinweis")), 0, z, 3, 1);

        GridPane arbeitsTab = raster();
        arbeitsTab.addRow(0, new Label(I18n.t("vor.arbeitsordner")), arbeit, waehlen(owner, arbeit));
        arbeitsTab.add(hinweis(I18n.t("vor.arbeitsordnerHinweis")), 0, 1, 3, 1);

        ToggleGroup tg = new ToggleGroup();
        RadioButton hell = new RadioButton(I18n.t("vor.hell"));
        RadioButton dunkel = new RadioButton(I18n.t("vor.dunkel"));
        hell.setToggleGroup(tg);
        dunkel.setToggleGroup(tg);
        (Einstellungen.dunkel() ? dunkel : hell).setSelected(true);
        VBox farben = new VBox(8, hell, dunkel);
        farben.setPadding(new Insets(12));

        ComboBox<String> ui = new ComboBox<>();
        ui.getItems().add("");
        ui.getItems().addAll(I18n.SPRACHEN);
        ui.setCellFactory(lv -> new SprachZelle());
        ui.setButtonCell(new SprachZelle());
        ui.setValue(Einstellungen.get(Einstellungen.UI_SPRACHE, ""));
        GridPane sprachTab = raster();
        sprachTab.addRow(0, new Label(I18n.t("vor.oberflaeche")), ui);

        TabPane tabs = new TabPane(new Tab(I18n.t("vor.tab.pfade"), pfade),
                new Tab(I18n.t("vor.tab.arbeit"), arbeitsTab), new Tab(I18n.t("vor.tab.farben"), farben),
                new Tab(I18n.t("vor.tab.sprache"), sprachTab));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setPrefWidth(760);

        Dialog<ButtonType> d = new Dialog<>();
        d.initOwner(owner);
        d.setTitle(I18n.t("menu.voreinstellungen"));
        d.getDialogPane().setContent(tabs);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Farben.anwenden(d);
        if (d.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return false;
        }
        Einstellungen.set(Einstellungen.PROMPT_QUELLE,
                server.isSelected() ? Einstellungen.QUELLE_SERVER : Einstellungen.QUELLE_ORDNER);
        Einstellungen.set(Einstellungen.PROMPT_ADRESSE, promptAdresse.getText().trim());
        Einstellungen.set(Einstellungen.PROMPT_ORDNER, promptOrdner.getText().trim());
        Einstellungen.set(Einstellungen.LAYOUT_ORDNER, layouts.getText().trim());
        Einstellungen.set(Einstellungen.DOCS_ORDNER, docs.getText().trim());
        Einstellungen.set(Einstellungen.HANDBUCH_ADRESSE, adresse.getText().trim());
        Einstellungen.set(Einstellungen.HANDBUCH_SPRACHE, hbSprache.getValue().name());
        Einstellungen.set(Einstellungen.ARBEITSORDNER, arbeit.getText().trim());
        Einstellungen.set(Einstellungen.FARBEN, dunkel.isSelected() ? "dunkel" : "hell");
        Einstellungen.set(Einstellungen.UI_SPRACHE, ui.getValue() == null ? "" : ui.getValue());
        I18n.setSprache(ui.getValue());
        return true;
    }

    private static final class SprachZelle extends javafx.scene.control.ListCell<String> {
        @Override
        protected void updateItem(String code, boolean leer) {
            super.updateItem(code, leer);
            if (leer || code == null) {
                setText(null);
                return;
            }
            Map<String, String> namen = I18n.namen();
            setText(code.isEmpty() ? I18n.t("vor.systemSprache") : namen.get(code));
        }
    }

    private static Label hinweis(String t) {
        Label l = new Label(t);
        l.setWrapText(true);
        l.setMaxWidth(720);
        return l;
    }

    private static TextField feld(String text) {
        TextField t = new TextField(text);
        t.setPrefColumnCount(40);
        return t;
    }

    private static GridPane raster() {
        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(8);
        g.setPadding(new Insets(12));
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(new ColumnConstraints(), c1, new ColumnConstraints());
        return g;
    }

    private static Button waehlen(Stage owner, TextField feld) {
        Button b = new Button(I18n.t("knopf.waehlen"));
        b.disableProperty().bind(feld.disableProperty());
        b.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            File start = new File(feld.getText().trim());
            if (start.isDirectory()) {
                dc.setInitialDirectory(start);
            }
            File f = dc.showDialog(owner);
            if (f != null) {
                feld.setText(f.getAbsolutePath());
            }
        });
        return b;
    }
}
