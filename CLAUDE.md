# CLAUDE.md

## Was das ist

Kleines JavaFX-Programm (Liberica JDK 25, Gradle, Windows/macOS/Linux), das die Dateien für den
Analyse-Prompt (siehe itrainplus.de, Seite ".tcdz-Prüfung") zusammenstellt und in die Zwischenablage legt.
Den Chat öffnet der Anwender selbst und fügt mit Strg+V ein oder zieht die Dateien per Drag & Drop hinein.
Das Programm ruft keine KI auf und sieht deren Ergebnis nicht.

Auftrag (Anwender 02.11.2026): Weg 2 = Dateien statt Text in die Zwischenablage, weil Handbuch,
Release Notes und .tcd zusammen für eingefügten Text zu groß sind. Weg 3 (Vorverarbeitung: Werte für
Eingangscheck und Zusätze selbst rechnen, nur zitierte Handbuchseiten) ist für später vorgesehen.

## Bedienung (Anwender 02.11. und 03.11.2026)

Menü: Datei (Beenden), Einstellungen > Voreinstellungen (Pfade, Arbeitsverzeichnis, Farben hell/dunkel,
Sprache der Oberfläche), Ansichten (Prompt-, Release-, Handbuch-Text; ausgegraut bis zusammengestellt), Hilfe (Hilfe, "Auf neue Prompt- und Handbuchfassung prüfen", Über).
Lizenzen stehen nur im Dialog "Über" (Knopf "Lizenzen …"), kein eigener Menüpunkt.
Hauptfenster von oben: Kenntnisstand, Layout-Datei, Prompt-Sprache, .tcd.xml, 3D-Linie, Direktmodus,
Sprache, Format, Zugtabelle, Bahnsteige, 3D-Linie, "Forum-Beitrag einbeziehen" + URL (Feld und Beschriftung
ausgegraut ohne Haken), Knöpfe, Fortschritt.
Nach dem Zusammenstellen öffnet `ErgebnisFenster`: Dateien zum Ziehen, Nachrichtentext mit Kopierknopf,
Status mit Symbolen, Drucken.
Kenntnisstand Anfänger zeigt "In Entwicklung" (eigener, iterativer Prompt folgt).
Dunkles Schema `dark-theme.css` aus iTrain-Logistik übernommen und gekürzt.

## Aussehen (Anwender 03.11.2026)

Programmsymbol: Blatt mit Balkendiagramm und Lupe mit Haken, Quelle `packaging/icons/icon.svg`; daraus
app-icon.ico/.icns/-256.png (jpackage) und `app-icon-16/32/64/256.png` (Ressourcen, alle Fenster über
`Farben.anwenden`). Das Symbol steht im Feld "Fortschritt", solange es leer ist; die Startprüfung schreibt
deshalb nur Meldungen, wenn etwas zu berichten ist (von Hand über das Hilfe-Menü: alle Meldungen).
Alle Knöpfe pastell wie in iTrain-Logistik (`Oberflaeche.pastell`, Schrift #1f2328, auch in Dialogen; Farbe
mit `Oberflaeche.farbe`, sonst OK/Ja grün, Abbrechen/Nein rosa, Rest blau).
Erster Start: `ErsterStartDialog` fragt Sprache und Farben (Schlüssel "eingerichtet").
Direktmodus, "Forum-Beitrag einbeziehen" und die Forum-Adresse sind bei jedem Start aus bzw. leer und werden
nicht gespeichert.
Hilfe und Über: "Programm, Prompt und Hilfe wurden in Zusammenarbeit mit Claude erstellt", Claude als Link.

## Forum (Anwender 03.11.2026)

Das iTrain-Forum zeigt Beiträge nur angemeldeten Nutzern. Eine KI liest einen Beitrag nur über einen Browser
mit Anmeldung des Anwenders (Claude: Erweiterung "Claude in Chrome" oder Browser der Claude-App); Zugangsdaten
speichert das Programm nicht. Hinweis unter dem URL-Feld; optionales Feld "Beitragstext" wird als
`Forum_Beitrag_<p>.txt` beigelegt und im Text für den Chat genannt; nur URL ohne Text gibt eine Warnung.

## Ansichten und bearbeitete Texte (Anwender 03.11.2026)

`TextFenster`: formatierte Ansicht (virtualisierte ListView, auch für das Handbuch schnell) und Bearbeiten
(TextArea), Strg/⌘+C/V/X, Suche. Speichern legt `<Original>_bearbeitet.txt` in `<Arbeitsordner>/Bearbeitet/`
ab (`Bearbeitet`, Index `bearbeitet.properties` mit Basisdatei und SHA-256) und ersetzt die Datei im Ordner
des Laufs (`Zusammenstellung.aendere`). Der Text für den Chat nennt der KI die bearbeiteten Dateien
(`Sprachgruppe.nachricht`), Ergebnisfenster und Kopieren weisen darauf hin. Bei jeder Zusammenstellung
Rückfrage "Bearbeitete Fassung / Original", mit Hinweis, wenn sich das Original seitdem geändert hat.

## Texte und Übersetzung

Alle Oberflächentexte in `texte/translations.properties` (Zeilen `schluessel.sprache=Text`, Sprachen
de, en, nl, fr, es, it; Platzhalter {0}), Hilfe in `texte/hilfe_<sprache>.txt`. `I18n` lädt sie, Ersatz
ist Deutsch. `processResources` kopiert `texte/` ins Paket. Neue Texte immer in allen sechs Sprachen.

## Prompt auf dem Webserver

`server/analyse-daten/` wird 1:1 nach `https://itrainplus.de/analyse-daten/` hochgeladen (FileZilla):
Prompt-Dateien, `manifest.properties`, leere `index.html` (kein Verzeichnislisting; nicht passwortgeschützt).
Nach neuer Prompt-Datei: `./gradlew promptManifest` (neueste je Gruppe, SHA-256), dann hochladen.
Das Programm liefert die Prompts mit (Ressource `prompt-manifest.properties`), prüft beim Start das
Manifest und fragt vor jedem Download. Cache `<Arbeitsordner>/.cache/prompt`. Alternativ in den
Voreinstellungen: lokaler Prompt-Ordner.

## Ablauf (Zusammenstellung.ausfuehren)

1. Layout-Datei: Liste aus `<user.home>/iTrain/layouts`, neueste zuerst; .tcdz entpacken, Version aus
   `<train-control version=…>`.
2. Prompt: `PromptQuelle.aktuell` (Server-Cache oder lokaler Ordner).
3. Handbuch: `https://berros.eu/download/6-0/` (`HandbuchDownload`, HEAD mit Last-Modified/Größe, beim
   Start Prüfung auf neue Fassung mit Rückfrage). Cache `<Arbeitsordner>/.cache/handbuch`, Text-Cache
   `.cache/handbuch-text` (wird wiederverwendet). Sprachen: DE, NL, EN, FR, ES (kein IT -> EN).
   Seitenweise Text mit PDFBox, Marke `===== Seite x von N =====`.
4. Release Notes: `Release Notes.txt` aus dem iTrain-docs-Ordner des Rechners, nur Versionen ab der
   Hauptversion des Handbuchs (6 -> 6.0.0 …). Fehlt die Datei: Dialog "Release Notes nicht verfügbar.
   Trotzdem weiter?" mit Abbruch/Weiter.
5. Nachricht: "Führe den Prompt aus.", ggf. Forum-URL-Zeile, Direktmodus-Zeile zuletzt.
6. Zwischenablage: Dateiliste + Nachrichtentext; `Nachricht_fuer_den_Chat.txt` im Arbeitsordner.

Arbeitsordner je Layout: `<Arbeitsordner>/<Name der .tcdz>/`.

Prompt 2.3 (03.11.2026): Zusätze getrennt ("Zusätze Zugtabelle und Bahnsteige" / "Zugtabelle" /
"Bahnsteige" / "Nein", EN "Extras Train table and Platforms" …), Pflicht-Routenprüfung (P10, Prüfkatalog
"ROUTENPRÜFUNG", Handbuch S. 155–161), ohne Befund "Routen überprüft (<n> Routen), keine Befunde."
`PromptQuelle.pruefeServer` bietet nie eine ältere Fassung an (Versionsnummer im Dateinamen).

Modulsystem: `requires org.apache.commons.logging` ist nötig (sonst NoClassDefFoundError LogFactory beim
Start); Gradle-Tests laufen nicht im reinen Modulsystem und merken das nicht.

## Release

`.github/workflows/release.yml` wie in iTrain_import_export: Tag `v*` baut .msi/.dmg/.deb und legt ein
GitHub-Release an. Tags lassen sich aus der Claude-Sitzung nicht pushen; das Release (mit neuem Tag) wird auf
GitHub unter "Releases" > "Draft a new release" angelegt, das startet den Build (1.0 am 03.11.2026).
`itrainplus.de/analyse.html`, Abschnitt Download, verlinkt die Installer direkt
(`releases/download/v<version>/…`): bei jeder neuen Version Links und Versionsnummer dort und auf
`index.html` anpassen. Die Prompt-Dateien verlinkt die Seite aus `/analyse-daten/`; das Handbuch nur als
Link auf Berros (Rechte bei Berros). `version` in build.gradle.kts = appVersion (macOS verlangt ≥ 1).

## Build & Run

```
./gradlew run
./gradlew test
./gradlew promptManifest
./gradlew jpackage
```

Tests: `ReleaseNotesTest` (Zerlegen, Auszug, Versionsvergleich), `HandbuchTest`, `PromptQuelleTest`.
