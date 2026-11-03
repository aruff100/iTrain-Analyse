# iTrain-Analyse

Stellt alle Dateien für den KI-Analyse-Prompt einer iTrain-Projektdatei zusammen: Prompt (von itrainplus.de),
Layout-Datei (.tcdz entpackt), iTrain-Handbuch als Text mit Seitenangaben und die Release Notes der lokalen
iTrain-Installation. Dateien und Text für den Chat liegen danach in der Zwischenablage oder werden per
Drag & Drop in den Chat gezogen. Das Programm ruft selbst keine KI auf.

Beschreibung und Download: https://itrainplus.de/analyse.html

Oberfläche in Deutsch, English, Nederlands, Français, Español und Italiano. Texte zum Bearbeiten im Ordner `texte`.

## Bauen

```
./gradlew run        # starten
./gradlew test       # Tests
./gradlew jpackage   # Installer für das aktuelle Betriebssystem
```

Ein Tag `v*` baut über GitHub Actions die Installer für Windows (.msi), macOS (.dmg) und Linux (.deb) und legt
ein Release an.

iTrain ist ein Produkt von Berros. Dieses Programm ist eine private, unabhängige Entwicklung.
Programm, Prompt und Hilfe wurden in Zusammenarbeit mit [Claude](https://claude.ai) erstellt.
