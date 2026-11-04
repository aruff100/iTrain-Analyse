package com.example.itrain_analyse;

import java.net.URI;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Erklärt den Anfänger-Modus (Anwender 04.11.2026): wie der Prompt in Runden vorgeht, was das Programm bereitstellt,
 * was der Anwender braucht und Schritt für Schritt tun muss, dass der Prompt mit Claude geprüft wurde und was es
 * kostet (mit Link auf die kleinste bezahlte Lösung). Erscheint beim Wählen von "Anfänger", bis der Anwender
 * "nicht mehr anzeigen" anhakt; über den Knopf "Anleitung Anfänger" jederzeit.
 */
final class AnfaengerDialog {

    static final String AUS = "anfaengerDialogAus";
    /** Kleinste bezahlte Lösung: Claude Pro (Seite zum Abschließen eines Abos). */
    static final String PRO = "https://claude.ai/upgrade";
    /** Hilfeseite zu zusätzlichem Guthaben (nur für bezahlte Pläne). */
    static final String GUTHABEN = "https://support.claude.com/en/articles/12429409-manage-usage-credits-for-paid-claude-plans";

    private static final int BREITE = 600;

    private AnfaengerDialog() {
    }

    /** Beim Wählen von "Anfänger": nur, wenn der Anwender es nicht abgeschaltet hat. */
    static void beiWahl(Window owner) {
        if (!Einstellungen.getBool(AUS, false)) {
            zeige(owner);
        }
    }

    static void zeige(Window owner) {
        VBox inhalt = new VBox(6);
        inhalt.setPadding(new Insets(4, 12, 4, 4));
        abschnitt(inhalt, "anf.weg.titel", I18n.t("anf.weg.text"));
        abschnitt(inhalt, "anf.bereit.titel", I18n.t("anf.bereit.text"));
        abschnitt(inhalt, "anf.brauchen.titel", I18n.t("anf.brauchen.text"));

        inhalt.getChildren().add(ueberschrift("anf.schritte.titel"));
        GridPane schritte = new GridPane();
        schritte.setHgap(6);
        schritte.setVgap(4);
        for (int i = 1; i <= 7; i++) {
            String wert = switch (i) {
                case 1 -> I18n.t("anf.schritt1", I18n.t("knopf.zusammenstellen"));
                case 2 -> I18n.t("anf.schritt2", I18n.t("knopf.kopieren"));
                default -> I18n.t("anf.schritt" + i);
            };
            Label nr = new Label(i + ".");
            nr.setMinWidth(Region.USE_PREF_SIZE);
            schritte.addRow(i - 1, nr, text(wert, BREITE - 30));
            GridPane.setValignment(nr, javafx.geometry.VPos.TOP);
        }
        inhalt.getChildren().add(schritte);

        abschnitt(inhalt, "anf.claude.titel", I18n.t("anf.claude.text"));
        abschnitt(inhalt, "anf.kosten.titel", I18n.t("anf.kosten.text"));
        Hyperlink pro = link(I18n.t("anf.link.pro"), PRO);
        Hyperlink guthaben = link(I18n.t("anf.link.hilfe"), GUTHABEN);
        inhalt.getChildren().addAll(pro, guthaben);

        CheckBox aus = new CheckBox(I18n.t("anf.nichtMehr"));
        aus.setSelected(Einstellungen.getBool(AUS, false));
        VBox.setMargin(aus, new Insets(10, 0, 0, 0));
        inhalt.getChildren().add(aus);

        ScrollPane sp = new ScrollPane(inhalt);
        sp.setFitToWidth(true);
        sp.setPrefViewportWidth(BREITE + 20);
        sp.setPrefViewportHeight(560);
        sp.setStyle("-fx-background-color: transparent;");

        Dialog<ButtonType> d = new Dialog<>();
        if (owner != null) {
            d.initOwner(owner);
        }
        d.setTitle(I18n.t("anf.titel"));
        d.setHeaderText(I18n.t("anf.titel"));
        d.setResizable(true);
        d.getDialogPane().setContent(sp);
        d.getDialogPane().getButtonTypes().add(ButtonType.OK);
        Farben.anwenden(d);
        d.showAndWait();
        Einstellungen.setBool(AUS, aus.isSelected());
    }

    private static void abschnitt(VBox ziel, String titelSchluessel, String text) {
        ziel.getChildren().addAll(ueberschrift(titelSchluessel), text(text, BREITE));
    }

    private static Label ueberschrift(String schluessel) {
        Label l = new Label(I18n.t(schluessel));
        l.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        VBox.setMargin(l, new Insets(8, 0, 0, 0));
        return l;
    }

    private static Label text(String text, double breite) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMaxWidth(breite);
        l.setPrefWidth(breite);
        l.setMinHeight(Region.USE_PREF_SIZE);
        return l;
    }

    private static Hyperlink link(String text, String adresse) {
        Hyperlink h = new Hyperlink(text);
        h.setOnAction(e -> Oberflaeche.oeffne(null, URI.create(adresse), false));
        h.setPadding(new Insets(0, 0, 0, 0));
        return h;
    }
}
