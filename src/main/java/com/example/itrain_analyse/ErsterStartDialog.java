package com.example.itrain_analyse;

import java.util.Locale;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Beim ersten Programmstart: Sprache der Oberfläche und Farben (hell/dunkel) abfragen (Anwender 03.11.2026).
 * Die Texte wechseln sofort mit der gewählten Sprache, die Farben sofort mit der Auswahl. Später änderbar unter
 * Einstellungen > Voreinstellungen.
 */
final class ErsterStartDialog {

    static final String EINGERICHTET = "eingerichtet";

    private ErsterStartDialog() {
    }

    /** Zeigt den Dialog, wenn das Programm noch nicht eingerichtet ist. */
    static void beiBedarf() {
        if (Einstellungen.getBool(EINGERICHTET, false)) {
            return;
        }
        String system = Locale.getDefault().getLanguage();
        String vorgabe = I18n.SPRACHEN.contains(system) ? system : "en";
        I18n.setSprache(vorgabe);

        Stage st = new Stage();
        st.initModality(Modality.APPLICATION_MODAL);
        ImageView symbol = new ImageView(Oberflaeche.symbolGross());
        symbol.setFitWidth(72);
        symbol.setFitHeight(72);
        Label titel = new Label();
        titel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Label text = new Label();
        text.setWrapText(true);
        text.setMaxWidth(380);
        text.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        Label spracheL = new Label();
        Label farbenL = new Label();

        ComboBox<String> sprache = new ComboBox<>();
        sprache.getItems().addAll(I18n.SPRACHEN);
        sprache.setCellFactory(lv -> new Zelle());
        sprache.setButtonCell(new Zelle());
        sprache.setValue(vorgabe);

        ToggleGroup tg = new ToggleGroup();
        RadioButton hell = new RadioButton();
        RadioButton dunkel = new RadioButton();
        hell.setToggleGroup(tg);
        dunkel.setToggleGroup(tg);
        hell.setSelected(true);

        Button ok = Oberflaeche.farbe(new Button(), Oberflaeche.GRUEN);
        ok.setDefaultButton(true);
        ok.setOnAction(e -> st.close());

        Runnable texte = () -> {
            st.setTitle("iTrain-Analyse");
            titel.setText(I18n.t("erst.titel"));
            text.setText(I18n.t("erst.text"));
            spracheL.setText(I18n.t("vor.oberflaeche"));
            farbenL.setText(I18n.t("vor.tab.farben"));
            hell.setText(I18n.t("vor.hell"));
            dunkel.setText(I18n.t("vor.dunkel"));
            ok.setText(I18n.t("erst.los"));
        };
        texte.run();

        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(10);
        g.addRow(0, spracheL, sprache);
        g.addRow(1, farbenL, new HBox(14, hell, dunkel));
        HBox knopf = new HBox(ok);
        knopf.setAlignment(Pos.CENTER_RIGHT);
        VBox rechts = new VBox(10, titel, text, g, knopf);
        HBox wurzel = new HBox(18, symbol, rechts);
        wurzel.setPadding(new Insets(20));
        Scene sc = new Scene(wurzel);
        Farben.anwenden(sc);
        st.setScene(sc);

        sprache.valueProperty().addListener((o, a, n) -> {
            I18n.setSprache(n);
            texte.run();
            st.sizeToScene();
        });
        tg.selectedToggleProperty().addListener((o, a, n) -> {
            Einstellungen.set(Einstellungen.FARBEN, dunkel.isSelected() ? "dunkel" : "hell");
            Farben.anwenden(sc);
        });
        st.showAndWait();

        Einstellungen.set(Einstellungen.UI_SPRACHE, sprache.getValue());
        Einstellungen.set(Einstellungen.FARBEN, dunkel.isSelected() ? "dunkel" : "hell");
        Einstellungen.setBool(EINGERICHTET, true);
        I18n.setSprache(sprache.getValue());
    }

    private static final class Zelle extends ListCell<String> {
        @Override
        protected void updateItem(String code, boolean leer) {
            super.updateItem(code, leer);
            setText(leer || code == null ? null : I18n.namen().get(code));
        }
    }
}
