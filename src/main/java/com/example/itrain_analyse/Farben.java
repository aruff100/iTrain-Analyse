package com.example.itrain_analyse;

import java.net.URL;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;

/** Hell/Dunkel: das dunkle Schema ist ein zusätzliches Stylesheet über dem JavaFX-Standard. */
public final class Farben {

    private static final URL DUNKEL = Farben.class.getResource("dark-theme.css");

    private Farben() {
    }

    public static void anwenden(Scene scene) {
        if (scene == null || DUNKEL == null) {
            return;
        }
        String css = DUNKEL.toExternalForm();
        scene.getStylesheets().remove(css);
        if (Einstellungen.dunkel()) {
            scene.getStylesheets().add(css);
        }
        // Jedes Fenster bekommt das Programmsymbol, alle Knöpfe die Pastellfarben.
        if (scene.getProperties().putIfAbsent("itrainAnalyse", Boolean.TRUE) == null) {
            scene.windowProperty().addListener((o, a, w) -> symbol(w));
            scene.rootProperty().addListener((o, a, r) -> javafx.application.Platform.runLater(
                    () -> Oberflaeche.pastellAlle(r)));
        }
        symbol(scene.getWindow());
        javafx.application.Platform.runLater(() -> Oberflaeche.pastellAlle(scene.getRoot()));
    }

    private static void symbol(javafx.stage.Window w) {
        if (w instanceof javafx.stage.Stage st && st.getIcons().isEmpty()) {
            st.getIcons().setAll(Oberflaeche.symbole());
        }
    }

    /** Die Scene eines Dialogs gibt es erst beim Anzeigen. */
    public static void anwenden(Dialog<?> dialog) {
        dialog.getDialogPane().sceneProperty().addListener((obs, alt, neu) -> anwenden(neu));
        anwenden(dialog.getDialogPane().getScene());
        // Knöpfe vor dem ersten Layout färben, damit die Knopfleiste ihre Breite mit dem breiteren Rahmen berechnet.
        for (javafx.scene.control.ButtonType bt : dialog.getDialogPane().getButtonTypes()) {
            javafx.scene.Node n = dialog.getDialogPane().lookupButton(bt);
            if (n instanceof javafx.scene.control.ButtonBase b) {
                Oberflaeche.pastell(b, Oberflaeche.vorgabe(b));
            }
        }
        // Die Knöpfe der Knopfleiste entstehen erst beim Anzeigen.
        dialog.setOnShown(e -> {
            Oberflaeche.pastellAlle(dialog.getDialogPane());
            // Der Rahmen der Pastellknöpfe ist breiter: Größe neu berechnen, sonst wird die Beschriftung gekürzt.
            javafx.application.Platform.runLater(() -> {
                dialog.getDialogPane().applyCss();
                dialog.getDialogPane().requestLayout();
                if (dialog.getDialogPane().getScene() != null
                        && dialog.getDialogPane().getScene().getWindow() != null) {
                    dialog.getDialogPane().getScene().getWindow().sizeToScene();
                }
            });
        });
    }
}
