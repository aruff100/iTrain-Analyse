package com.example.itrain_analyse;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Übersetzte Texte aus {@code texte/translations.properties} im Projektordner (beim Build ins Paket kopiert).
 * Format je Zeile wie in iTrain-Logistik und iTrain_import_export: {@code schluessel.sprachcode=Text}.
 * Platzhalter {0}, {1} … werden der Reihe nach ersetzt (bewusst ohne MessageFormat, damit Apostrophe in
 * französischen und italienischen Texten nicht verschluckt werden).
 *
 * <p>Fehlt ein Text in der gewählten Sprache, gilt Deutsch, sonst der Schlüssel selbst.
 */
public final class I18n {

    public static final List<String> SPRACHEN = List.of("de", "en", "nl", "fr", "es", "it");

    private static final Map<String, String> NAMEN = new LinkedHashMap<>();
    static {
        NAMEN.put("de", "Deutsch");
        NAMEN.put("en", "English");
        NAMEN.put("nl", "Nederlands");
        NAMEN.put("fr", "Français");
        NAMEN.put("es", "Español");
        NAMEN.put("it", "Italiano");
    }

    private static final Map<String, Map<String, String>> TABELLE = new HashMap<>();
    private static String sprache = "de";

    static {
        laden();
        setSprache(Einstellungen.get(Einstellungen.UI_SPRACHE, ""));
    }

    private I18n() {
    }

    private static void laden() {
        try (InputStream in = I18n.class.getResourceAsStream("translations.properties")) {
            if (in == null) {
                System.err.println("translations.properties fehlt - es werden die Schlüssel angezeigt.");
                return;
            }
            Properties p = new Properties();
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String k : p.stringPropertyNames()) {
                int punkt = k.lastIndexOf('.');
                if (punkt < 0) {
                    continue;
                }
                String basis = k.substring(0, punkt);
                String code = k.substring(punkt + 1);
                if (SPRACHEN.contains(code)) {
                    TABELLE.computeIfAbsent(basis, x -> new HashMap<>()).put(code, p.getProperty(k));
                }
            }
        } catch (IOException ex) {
            System.err.println("translations.properties nicht lesbar: " + ex.getMessage());
        }
    }

    /** Leer oder unbekannt = Sprache des Systems, falls vorhanden, sonst Deutsch. */
    public static void setSprache(String code) {
        if (code != null && SPRACHEN.contains(code)) {
            sprache = code;
            return;
        }
        String sys = Locale.getDefault().getLanguage();
        sprache = SPRACHEN.contains(sys) ? sys : "de";
    }

    public static String sprache() {
        return sprache;
    }

    public static Map<String, String> namen() {
        return NAMEN;
    }

    public static String t(String schluessel, Object... werte) {
        Map<String, String> m = TABELLE.get(schluessel);
        String text = m == null ? null : m.getOrDefault(sprache, m.get("de"));
        if (text == null) {
            text = schluessel;
        }
        for (int i = 0; i < werte.length; i++) {
            text = text.replace("{" + i + "}", String.valueOf(werte[i]));
        }
        return text;
    }

    /** Text einer Ressource in der gewählten Sprache ({@code name_de.txt} usw.), sonst die deutsche Fassung. */
    public static String datei(String name) {
        for (String code : List.of(sprache, "de")) {
            try (InputStream in = I18n.class.getResourceAsStream(name + "_" + code + ".txt")) {
                if (in != null) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (IOException ex) {
                // nächste Sprache versuchen
            }
        }
        return "(" + name + ")";
    }
}
