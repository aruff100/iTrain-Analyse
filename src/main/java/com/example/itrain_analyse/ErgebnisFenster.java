package com.example.itrain_analyse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Ergebnis der Zusammenstellung: Dateien zum Hineinziehen in den Chat, der Text für das Nachrichtenfeld in einem
 * Kasten mit Kopier-Knopf, die Statusliste. Kann gedruckt werden.
 */
final class ErgebnisFenster {

    /** Es ist immer nur ein Ergebnisfenster offen; nach dem Bearbeiten eines Textes wird es neu aufgebaut. */
    private static Stage offen;

    private ErgebnisFenster() {
    }

    /** Baut ein offenes Ergebnisfenster mit dem neuen Ergebnis neu auf; ist keines offen, passiert nichts. */
    static void aktualisieren(Stage owner, Zusammenstellung.Ergebnis e, String layoutName) {
        if (offen != null && offen.isShowing()) {
            zeige(owner, e, layoutName);
        }
    }

    static void zeige(Stage owner, Zusammenstellung.Ergebnis e, String layoutName) {
        double px = Double.NaN;
        double py = Double.NaN;
        if (offen != null) {
            px = offen.getX();
            py = offen.getY();
            offen.close();
        }
        Stage st = new Stage();
        offen = st;
        if (!Double.isNaN(px)) {
            st.setX(px);
            st.setY(py);
        }
        st.initOwner(owner);
        st.setTitle(I18n.t("ergebnis.titel", layoutName));

        Label titel = new Label(I18n.t("ergebnis.ueberschrift", layoutName));
        titel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Label ordner = new Label(I18n.t("ergebnis.ordner", e.ordner()));
        ordner.setWrapText(true);
        Label bearbeitet = new Label("⚠  " + I18n.t("ergebnis.bearbeitet", String.join(", ", e.bearbeitet())));
        bearbeitet.setWrapText(true);
        bearbeitet.setMaxWidth(Double.MAX_VALUE);
        bearbeitet.setPadding(new Insets(10));
        bearbeitet.setStyle("-fx-border-color: #b26a00; -fx-border-width: 2; -fx-border-radius: 6;"
                + "-fx-background-radius: 6; -fx-background-color: rgba(178,106,0,0.12); -fx-font-weight: bold;");
        bearbeitet.setVisible(!e.bearbeitet().isEmpty());
        bearbeitet.setManaged(!e.bearbeitet().isEmpty());

        // 1. Dateien
        Label h1 = ueberschrift(I18n.t("ergebnis.schritt1"));
        Label hinweis1 = text(I18n.t("ergebnis.schritt1.text"));
        Label alle = new Label("📎  " + I18n.t("ergebnis.alleDateien", e.dateien().size(),
                Zusammenstellung.groesse(e.dateien()) / 1024));
        alle.setMaxWidth(Double.MAX_VALUE);
        alle.setPadding(new Insets(14));
        alle.setStyle("-fx-border-color: #3574c9; -fx-border-style: dashed; -fx-border-width: 2;"
                + "-fx-border-radius: 6; -fx-background-radius: 6; -fx-background-color: rgba(53,116,201,0.10);"
                + "-fx-font-weight: bold; -fx-cursor: hand;");
        ziehbar(alle, e.dateien());
        VBox liste = new VBox(4);
        for (Path p : e.dateien()) {
            boolean geaendert = p.getFileName().toString().endsWith(Bearbeitet.ENDUNG);
            Label l = new Label((geaendert ? "✎  " : "📄  ") + p.getFileName() + "   (" + kb(p) + " KB)"
                    + (geaendert ? "   – " + I18n.t("ergebnis.dateiBearbeitet") : ""));
            if (geaendert) {
                l.setStyle("-fx-cursor: hand; -fx-text-fill: #b26a00; -fx-font-weight: bold;");
            }
            if (!geaendert) {
                l.setStyle("-fx-cursor: hand;");
            }
            ziehbar(l, List.of(p));
            liste.getChildren().add(l);
        }

        // 2. Text
        Label h2 = ueberschrift(I18n.t("ergebnis.schritt2"));
        Label hinweis2 = text(I18n.t("ergebnis.schritt2.text"));
        Node kasten = codeKasten(st, e.nachricht(), e.bearbeitet());

        // 3. Absenden
        Label h3 = ueberschrift(I18n.t("ergebnis.schritt3"));
        Label hinweis3 = text(I18n.t("ergebnis.schritt3.text"));

        // Status
        Label h4 = ueberschrift(I18n.t("ergebnis.status"));
        VBox status = new VBox(3);
        for (Zusammenstellung.Status s : e.status()) {
            Label l = text(s.symbol() + "  " + s.text());
            l.setStyle(farbe(s.symbol()));
            status.getChildren().add(l);
        }

        VBox inhalt = new VBox(10, titel, ordner, bearbeitet, h1, hinweis1, alle, liste, h2, hinweis2, kasten, h3, hinweis3,
                h4, status);
        inhalt.setPadding(new Insets(16));
        ScrollPane sp = new ScrollPane(inhalt);
        sp.setFitToWidth(true);

        Button drucken = Oberflaeche.farbe(new Button("🖨  " + I18n.t("knopf.drucken")), Oberflaeche.LILA);
        drucken.setOnAction(x -> drucken(st, e, layoutName));
        Button ordnerOeffnen = Oberflaeche.farbe(new Button(I18n.t("knopf.ordnerOeffnen")), Oberflaeche.TUERKIS);
        ordnerOeffnen.setOnAction(x -> Oberflaeche.oeffne(e.ordner().toFile(), null, false));
        Button schliessen = Oberflaeche.farbe(new Button(I18n.t("knopf.schliessen")), Oberflaeche.ROSA);
        schliessen.setCancelButton(true);
        schliessen.setOnAction(x -> st.close());
        Region luecke = new Region();
        HBox.setHgrow(luecke, Priority.ALWAYS);
        HBox knoepfe = new HBox(8, drucken, ordnerOeffnen, luecke, schliessen);
        if (e.pruefbericht() != null) {
            // Variante B: Prüfbericht des Programms ohne KI (Anwender 18.11.2026)
            Button bericht = Oberflaeche.farbe(new Button("📄  " + I18n.t("knopf.pruefbericht")), Oberflaeche.TUERKIS);
            bericht.setOnAction(x -> Oberflaeche.oeffne(e.pruefbericht().toFile(), null, false));
            knoepfe.getChildren().add(2, bericht);
        }
        knoepfe.setPadding(new Insets(10, 16, 12, 16));

        BorderPane wurzel = new BorderPane(sp);
        wurzel.setBottom(knoepfe);
        javafx.geometry.Rectangle2D bild = javafx.stage.Screen.getPrimary().getVisualBounds();
        Scene sc = new Scene(wurzel, Math.min(980, bild.getWidth() * 0.9), Math.min(980, bild.getHeight() * 0.9));
        Farben.anwenden(sc);
        st.setScene(sc);
        st.show();
    }

    /** Kasten wie ein Codeblock: Kopfzeile mit Kopier-Knopf, darunter der Text in fester Schriftbreite. */
    private static Node codeKasten(Stage st, String text, List<String> bearbeitet) {
        TextArea ta = new TextArea(text);
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setStyle("-fx-font-family: 'monospaced'; -fx-font-size: 13px;");
        ta.setPrefRowCount(Math.max(2, (int) text.lines().count() + 1));
        Button kopieren = Oberflaeche.farbe(new Button("📋  " + I18n.t("knopf.kopieren")), Oberflaeche.ORANGE);
        kopieren.setOnAction(x -> {
            ClipboardContent c = new ClipboardContent();
            c.putString(text);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(c);
            if (!bearbeitet.isEmpty()) {
                Oberflaeche.hinweis(st, I18n.t("ergebnis.kopiertBearbeitet", String.join(", ", bearbeitet)));
            }
            kopieren.setText("✔  " + I18n.t("knopf.kopiert"));
            PauseTransition p = new PauseTransition(Duration.seconds(2));
            p.setOnFinished(y -> kopieren.setText("📋  " + I18n.t("knopf.kopieren")));
            p.play();
        });
        Label art = new Label(I18n.t("ergebnis.textKasten"));
        Region luecke = new Region();
        HBox.setHgrow(luecke, Priority.ALWAYS);
        HBox kopf = new HBox(8, art, luecke, kopieren);
        kopf.setAlignment(Pos.CENTER_LEFT);
        kopf.setPadding(new Insets(4, 6, 4, 10));
        kopf.setStyle("-fx-background-color: rgba(128,128,128,0.18);");
        VBox box = new VBox(kopf, ta);
        box.setStyle("-fx-border-color: rgba(128,128,128,0.6); -fx-border-radius: 6; -fx-border-width: 1;");
        return box;
    }

    /** Ziehen einer oder mehrerer Dateien aus dem Fenster (z. B. in das Nachrichtenfeld des Chats im Browser). */
    private static void ziehbar(Node n, List<Path> dateien) {
        n.setOnDragDetected(ev -> {
            Dragboard db = n.startDragAndDrop(TransferMode.COPY);
            ClipboardContent c = new ClipboardContent();
            List<File> fs = dateien.stream().map(Path::toFile).toList();
            c.putFiles(fs);
            db.setContent(c);
            ev.consume();
        });
    }

    private static Label ueberschrift(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-padding: 8 0 0 0;");
        return l;
    }

    private static Label text(String t) {
        Label l = new Label(t);
        l.setWrapText(true);
        return l;
    }

    private static String farbe(String symbol) {
        return switch (symbol) {
            case Zusammenstellung.FEHLT -> "-fx-text-fill: #c62828;";
            case Zusammenstellung.WARNUNG -> "-fx-text-fill: #b26a00;";
            default -> "";
        };
    }

    private static long kb(Path p) {
        try {
            return Files.size(p) / 1024;
        } catch (IOException ex) {
            return 0;
        }
    }

    // ---------------------------------------------------------------- Drucken

    /** Druckt eine schwarz-weiße Fassung (unabhängig vom Farbschema des Fensters). */
    private static void drucken(Stage owner, Zusammenstellung.Ergebnis e, String layoutName) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null || !job.showPrintDialog(owner)) {
            return;
        }
        PageLayout pl = job.getJobSettings().getPageLayout();
        double breite = pl.getPrintableWidth();
        VBox seite = new VBox(6);
        seite.setStyle("-fx-background-color: white;");
        seite.setPrefWidth(breite);
        seite.getChildren().add(druckText(I18n.t("ergebnis.ueberschrift", layoutName), 15, true, false, breite));
        seite.getChildren().add(druckText(I18n.t("ergebnis.ordner", e.ordner()), 9, false, false, breite));
        if (!e.bearbeitet().isEmpty()) {
            seite.getChildren().add(druckText("⚠  " + I18n.t("ergebnis.bearbeitet", String.join(", ",
                    e.bearbeitet())), 9, true, false, breite));
        }
        seite.getChildren().add(druckText(I18n.t("ergebnis.schritt1"), 11, true, false, breite));
        for (Path p : e.dateien()) {
            seite.getChildren().add(druckText("•  " + p.getFileName() + "  (" + kb(p) + " KB)", 9, false, false,
                    breite));
        }
        seite.getChildren().add(druckText(I18n.t("ergebnis.schritt2"), 11, true, false, breite));
        seite.getChildren().add(druckText(e.nachricht(), 9, false, true, breite));
        seite.getChildren().add(druckText(I18n.t("ergebnis.status"), 11, true, false, breite));
        for (Zusammenstellung.Status s : e.status()) {
            seite.getChildren().add(druckText(s.symbol() + "  " + s.text(), 9, false, false, breite));
        }
        seite.applyCss();
        seite.layout();
        double hoehe = seite.prefHeight(breite);
        if (hoehe > pl.getPrintableHeight()) {
            double f = pl.getPrintableHeight() / hoehe;
            seite.getTransforms().add(new javafx.scene.transform.Scale(f, f));
        }
        if (job.printPage(pl, seite)) {
            job.endJob();
        }
    }

    private static Text druckText(String t, double groesse, boolean fett, boolean fest, double breite) {
        Text x = new Text(t);
        x.setFill(Color.BLACK);
        x.setWrappingWidth(breite);
        x.setFont(fest ? Font.font("Monospaced", groesse)
                : Font.font("System", fett ? FontWeight.BOLD : FontWeight.NORMAL, groesse));
        return x;
    }
}
