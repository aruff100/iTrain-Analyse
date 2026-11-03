package com.example.itrain_analyse;

import javafx.application.Application;

/**
 * Einstiegspunkt, der selbst nicht von {@code Application} erbt - wie in den Schwesterprojekten
 * iTrain-Logistik und iTrain_import_export, weil manche JavaFX/jlink-Kombinationen das verlangen.
 */
public class Launcher {
    public static void main(String[] args) {
        Application.launch(AnalyseApplication.class, args);
    }
}
