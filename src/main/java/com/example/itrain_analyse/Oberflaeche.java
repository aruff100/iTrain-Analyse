package com.example.itrain_analyse;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.FutureTask;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.Region;
import javafx.stage.Window;

/** Kleine gemeinsame Bausteine der Oberfläche. */
final class Oberflaeche {

    // Pastellfarben wie in iTrain-Logistik (MainController.pastell); Schrift immer dunkel (#1f2328), damit sie auf
    // den hellen Flächen in beiden Farbschemata lesbar bleibt.
    static final String BLAU = "#cfe2f7";
    static final String GRUEN = "#d6efc7";
    static final String ORANGE = "#fbe3bd";
    static final String TUERKIS = "#c6ece6";
    static final String LILA = "#e2d9f3";
    static final String ROSA = "#f5cccc";
    private static final String SCHRIFT = "#1f2328";
    private static final String FARBE = "pastellFarbe";

    private static final List<Image> SYMBOLE = new ArrayList<>();

    private Oberflaeche() {
    }

    /** Programmsymbol in mehreren Größen (für Fenster und Taskleiste). */
    static List<Image> symbole() {
        if (SYMBOLE.isEmpty()) {
            for (int s : new int[] {16, 32, 64, 256}) {
                try (InputStream in = Oberflaeche.class.getResourceAsStream("app-icon-" + s + ".png")) {
                    if (in != null) {
                        SYMBOLE.add(new Image(in));
                    }
                } catch (IOException ex) {
                    // ohne Symbol weiter
                }
            }
        }
        return SYMBOLE;
    }

    /** Das große Programmsymbol (256 px). */
    static Image symbolGross() {
        List<Image> s = symbole();
        return s.isEmpty() ? null : s.get(s.size() - 1);
    }

    /** Legt die Pastellfarbe eines Knopfs fest; angewendet wird sie von {@link #pastellAlle}. */
    static <T extends ButtonBase> T farbe(T b, String farbe) {
        b.getProperties().put(FARBE, farbe);
        return b;
    }

    /**
     * Färbt alle Knöpfe unterhalb von {@code wurzel} pastell, auch die Knöpfe von Dialogen. Ohne festgelegte Farbe:
     * OK/Ja grün, Abbrechen/Nein rosa, sonst blau.
     */
    static void pastellAlle(Parent wurzel) {
        if (wurzel == null) {
            return;
        }
        for (Node n : wurzel.lookupAll(".button")) {
            if (n instanceof ButtonBase b) {
                pastell(b, vorgabe(b));
            }
        }
        for (Node n : wurzel.lookupAll(".toggle-button")) {
            if (n instanceof ButtonBase b) {
                pastell(b, vorgabe(b));
            }
        }
    }

    static String vorgabe(ButtonBase b) {
        Object f = b.getProperties().get(FARBE);
        if (f instanceof String s) {
            return s;
        }
        ButtonBar.ButtonData d = ButtonBar.getButtonData(b);
        if (d != null) {
            if (d.isDefaultButton() || d == ButtonBar.ButtonData.YES || d == ButtonBar.ButtonData.OK_DONE) {
                return GRUEN;
            }
            if (d.isCancelButton() || d == ButtonBar.ButtonData.NO) {
                return ROSA;
            }
        }
        return BLAU;
    }

    /** Pastellfarbe mit dunkler Schrift; beim Überfahren und eingeschaltet (ToggleButton) etwas dunkler. */
    static void pastell(ButtonBase b, String farbe) {
        if (b.getStyleClass().contains("pastell")) {
            return;
        }
        Color c = Color.web(farbe);
        String normal = stil(c);
        String dunkler = stil(c.deriveColor(0, 1.15, 0.92, 1));
        String gedrueckt = stil(c.deriveColor(0, 1.3, 0.82, 1));
        Runnable setzen = () -> {
            boolean an = b instanceof ToggleButton tb && tb.isSelected();
            b.setStyle(an ? gedrueckt : b.isHover() ? dunkler : normal);
        };
        setzen.run();
        b.hoverProperty().addListener((o, a, n) -> setzen.run());
        if (b instanceof ToggleButton tb) {
            tb.selectedProperty().addListener((o, a, n) -> setzen.run());
        }
        // Der breitere Rahmen darf die Beschriftung nicht abschneiden ("Herunterla…").
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.getStyleClass().add("pastell");
        b.getStylesheets().add("data:text/css;base64," + java.util.Base64.getEncoder().encodeToString(
                (".pastell, .pastell > .label, .pastell:disabled { -fx-text-fill: " + SCHRIFT + "; }")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        // Unter Windows färbt das dunkle Schema die innere Beschriftung sonst hell (Erfahrung iTrain-Logistik).
        Runnable innen = () -> {
            for (Node n : b.lookupAll(".label")) {
                if (n != b) {
                    n.setStyle("-fx-text-fill: " + SCHRIFT + ";");
                }
            }
        };
        b.skinProperty().addListener((o, a, n) -> Platform.runLater(innen));
        b.textProperty().addListener((o, a, n) -> Platform.runLater(innen));
        Platform.runLater(innen);
    }

    private static String stil(Color c) {
        String hex = String.format("#%02x%02x%02x", (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
        return "-fx-background-color: " + hex + "; -fx-text-fill: " + SCHRIFT + ";"
                + " -fx-border-color: derive(" + hex + ", 25%) derive(" + hex + ", -40%) derive(" + hex
                + ", -40%) derive(" + hex + ", 25%); -fx-border-width: 2; -fx-border-radius: 3;"
                + " -fx-background-radius: 3;";
    }

    /** Waagerechte, vertiefte Linie ("3D-Linie"): dunkle Kante oben, helle Kante unten. */
    static Region linie3d() {
        Region r = new Region();
        String oben = Einstellungen.dunkel() ? "#1a1a1a" : "#9a9a9a";
        String unten = Einstellungen.dunkel() ? "#5a5a5a" : "#ffffff";
        r.setStyle("-fx-border-color: " + oben + " transparent " + unten + " transparent;"
                + "-fx-border-width: 1 0 1 0; -fx-min-height: 2; -fx-pref-height: 2; -fx-max-height: 2;");
        r.setMaxWidth(Double.MAX_VALUE);
        return r;
    }

    /** Ja/Nein-Dialog; aus jedem Thread aufrufbar (wartet auf die Antwort im JavaFX-Thread). */
    static boolean frage(Window owner, String titel, String text, String ja, String nein) {
        if (Platform.isFxApplicationThread()) {
            return zeigeFrage(owner, titel, text, ja, nein);
        }
        FutureTask<Boolean> f = new FutureTask<>(() -> zeigeFrage(owner, titel, text, ja, nein));
        Platform.runLater(f);
        try {
            return f.get();
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean zeigeFrage(Window owner, String titel, String text, String ja, String nein) {
        ButtonType jaB = new ButtonType(ja, ButtonBar.ButtonData.YES);
        ButtonType neinB = new ButtonType(nein, ButtonBar.ButtonData.NO);
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, text, jaB, neinB);
        if (owner != null) {
            a.initOwner(owner);
        }
        a.setTitle(titel);
        a.setHeaderText(null);
        a.getDialogPane().setMinWidth(480);
        a.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        Farben.anwenden(a);
        return a.showAndWait().orElse(neinB) == jaB;
    }

    /** Hinweis mit OK-Knopf (nur im JavaFX-Thread). */
    static void hinweis(Window owner, String text) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, text, ButtonType.OK);
        if (owner != null) {
            a.initOwner(owner);
        }
        a.setTitle("iTrain-Analyse");
        a.setHeaderText(null);
        a.getDialogPane().setMinWidth(480);
        a.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        Farben.anwenden(a);
        a.showAndWait();
    }

    /** Ordner, Datei, Webseite oder Mail mit dem Standardprogramm öffnen (nicht im JavaFX-Thread). */
    static void oeffne(File datei, URI uri, boolean mail) {
        Thread t = new Thread(() -> {
            try {
                if (Desktop.isDesktopSupported()) {
                    if (datei != null) {
                        Desktop.getDesktop().open(datei);
                    } else if (mail) {
                        Desktop.getDesktop().mail(uri);
                    } else {
                        Desktop.getDesktop().browse(uri);
                    }
                } else if (datei != null
                        && System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) {
                    new ProcessBuilder("xdg-open", datei.getAbsolutePath()).start();
                }
            } catch (IOException | RuntimeException ex) {
                System.err.println("Öffnen nicht möglich: " + ex.getMessage());
            }
        }, "oeffnen");
        t.setDaemon(true);
        t.start();
    }
}
