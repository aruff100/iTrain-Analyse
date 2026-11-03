package com.example.itrain_analyse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReleaseNotesTest {

    private static final List<String> BEISPIEL = List.of(
            "iTrain Release Notes", "====================", "",
            "6.0.4", "-----", "- Punkt 604", "",
            "6.0.1", "-----", "- Punkt 601", "",
            "6.0.0", "-----", "6.0.0", "-----", "- General", "  - Punkt 600", "",
            "5.1.21", "------", "- Punkt 5121");

    @Test
    void zerlegtMitDoppelterKopfzeile() {
        List<ReleaseNotes.Abschnitt> a = ReleaseNotes.zerlege(BEISPIEL);
        assertEquals(4, a.size());
        assertEquals("6.0.0", a.get(2).version().toString());
        assertTrue(a.get(2).text().contains("Punkt 600"));
    }

    @Test
    void auszugAbHauptversion(@TempDir Path tmp) throws Exception {
        Path docs = Files.createDirectories(tmp.resolve("docs"));
        Files.write(docs.resolve(ReleaseNotes.DATEINAME), BEISPIEL);
        ReleaseNotes.Ergebnis e = ReleaseNotes.auszug(docs, Version.aus("6"), tmp);
        assertEquals(3, e.abschnitte().size());
        assertEquals("6.0.4", e.installiert().toString());
        assertEquals("Release_Notes_6.0.0-6.0.4.txt", e.datei().getFileName().toString());
        String text = Files.readString(e.datei());
        assertTrue(text.contains("Punkt 604") && text.contains("Punkt 600") && !text.contains("Punkt 5121"));
    }

    @Test
    void versionVergleich() {
        assertEquals(0, Version.aus("6.0").compareTo(Version.aus("6.0.0")));
        assertTrue(Version.aus("5.1.19-b404").compareTo(Version.aus("6.0.1")) < 0);
        assertEquals("6.0.4", Version.aus("6.0.4-b300").toString());
    }
}
