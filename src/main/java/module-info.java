module com.example.itrain_analyse {
    requires javafx.controls;
    // Version aus der .tcd lesen (StAX).
    requires java.xml;
    // Einstellungen dauerhaft ueber die Java-Preferences-API.
    requires java.prefs;
    // Arbeitsordner im Explorer/Finder oeffnen, Mail/Webseite aus "Ueber" (Desktop).
    requires java.desktop;
    // Handbuch-PDF von der Download-Seite laden.
    requires java.net.http;
    // Handbuch-PDF seitenweise in Text umwandeln.
    requires org.apache.pdfbox;
    // PDFBox braucht commons-logging; als echtes Modul (mit module-info) wird es nur aufgeloest,
    // wenn es hier ausdruecklich verlangt wird - sonst NoClassDefFoundError LogFactory.
    requires org.apache.commons.logging;

    exports com.example.itrain_analyse;
}
