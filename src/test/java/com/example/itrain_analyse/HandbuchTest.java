package com.example.itrain_analyse;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Prüft die seitenweise PDF-Umwandlung. Hinweis: Gradle testet nicht im reinen Modulsystem, ein fehlendes
 * "requires org.apache.commons.logging" fällt hier nicht auf, nur beim Start mit gradlew run. */
class HandbuchTest {

    @Test
    void wandeltSeitenUm(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("Handbuch.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
            doc.save(pdf.toFile());
        }
        String text = Handbuch.umwandeln(pdf);
        assertTrue(text.contains("===== Seite 2 von 2 ====="));
    }
}
