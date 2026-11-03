package com.example.itrain_analyse;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;

/** Menü Hilfe: Hilfetext und Über (mit den Lizenzhinweisen; Aufbau wie AboutDialog in iTrain_import_export). */
public final class HilfeDialoge {

    private static final String NAME = "iTrain-Analyse";
    private static final String AUTOR = "Andre Ruff";
    private static final String EMAIL = "aruff@allesruff.de";
    private static final String WEBSEITE = "https://itrainplus.de/analyse.html";

    private HilfeDialoge() {
    }

    private static final String CLAUDE = "https://claude.ai";

    public static void hilfe(Stage owner) {
        textFenster(owner, I18n.t("menu.hilfe"), I18n.datei("hilfe"), true, 780, 620, claudeZeile());
    }

    /** "Programm, Prompt und Hilfe wurden in Zusammenarbeit mit Claude erstellt." – Claude als Link. */
    private static TextFlow claudeZeile() {
        Hyperlink claude = new Hyperlink("Claude");
        claude.setOnAction(e -> Oberflaeche.oeffne(null, URI.create(CLAUDE), false));
        claude.setPadding(Insets.EMPTY);
        Text vor = new Text(I18n.t("hilfe.claude.vor"));
        Text nach = new Text(I18n.t("hilfe.claude.nach"));
        vor.getStyleClass().add("text");
        nach.getStyleClass().add("text");
        String farbe = Einstellungen.dunkel() ? "-fx-fill: #e0e0e0;" : "";
        vor.setStyle(farbe);
        nach.setStyle(farbe);
        TextFlow f = new TextFlow(vor, claude, nach);
        f.setPadding(new Insets(8, 0, 0, 0));
        return f;
    }

    public static void ueber(Stage owner) {
        Label name = new Label(NAME);
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        Label version = new Label(I18n.t("ueber.version", AppVersion.text()));
        Label autor = new Label(AUTOR);
        Hyperlink mail = new Hyperlink(EMAIL);
        mail.setOnAction(e -> Oberflaeche.oeffne(null, URI.create("mailto:" + EMAIL + "?subject="
                + URLEncoder.encode(NAME + " " + AppVersion.text(), StandardCharsets.UTF_8).replace("+", "%20")),
                true));
        Hyperlink web = new Hyperlink(WEBSEITE);
        web.setOnAction(e -> Oberflaeche.oeffne(null, URI.create(WEBSEITE), false));
        Label hinweis = new Label(I18n.t("ueber.berros"));
        hinweis.setWrapText(true);
        hinweis.setMaxWidth(360);
        hinweis.setStyle("-fx-font-size: 11px; -fx-opacity: 0.8;");
        Label lizenzHinweis = new Label(I18n.t("ueber.lizenzen"));
        lizenzHinweis.setWrapText(true);
        lizenzHinweis.setMaxWidth(360);
        lizenzHinweis.setStyle("-fx-font-size: 11px; -fx-opacity: 0.8;");
        Button lizenzen = Oberflaeche.farbe(new Button(I18n.t("knopf.lizenzen")), Oberflaeche.LILA);
        lizenzen.setOnAction(e -> textFenster(owner, I18n.t("knopf.lizenzen"), ressource("lizenzen.txt"), false,
                780, 600, null));

        ImageView symbol = new ImageView(Oberflaeche.symbolGross());
        symbol.setFitWidth(64);
        symbol.setFitHeight(64);
        symbol.setSmooth(true);
        TextFlow claude = claudeZeile();
        claude.setMaxWidth(360);
        VBox inhalt = new VBox(6, symbol, name, version, autor, mail, web, claude, hinweis, lizenzHinweis, lizenzen);
        inhalt.setPadding(new Insets(15));
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.initOwner(owner);
        a.setTitle(I18n.t("menu.ueber"));
        a.setHeaderText(null);
        a.getDialogPane().setContent(inhalt);
        a.getButtonTypes().setAll(ButtonType.CLOSE);
        Farben.anwenden(a);
        a.showAndWait();
    }

    private static void textFenster(Stage owner, String titel, String text, boolean umbrechen, int b, int h,
            javafx.scene.Node fuss) {
        TextArea area = new TextArea(text);
        area.setEditable(false);
        area.setWrapText(umbrechen);
        if (!umbrechen) {
            area.setStyle("-fx-font-family: 'monospaced'; -fx-font-size: 11px;");
        }
        area.setPrefSize(b, h);
        Dialog<Void> d = new Dialog<>();
        d.initOwner(owner);
        d.setTitle(titel);
        d.setResizable(true);
        d.getDialogPane().setContent(fuss == null ? area : new VBox(area, fuss));
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Farben.anwenden(d);
        d.showAndWait();
    }

    private static String ressource(String name) {
        try (InputStream in = HilfeDialoge.class.getResourceAsStream(name)) {
            return in == null ? "(" + name + ")" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return String.valueOf(ex.getMessage());
        }
    }
}
