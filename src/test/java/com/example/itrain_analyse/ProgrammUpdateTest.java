package com.example.itrain_analyse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.junit.jupiter.api.Test;

class ProgrammUpdateTest {

    @Test
    void versionAusAdresse() {
        URI u = URI.create("https://github.com/aruff100/iTrain-Analyse/releases/tag/v1.1");
        assertEquals("1.1", ProgrammUpdate.ausAdresse(u).toString());
    }

    @Test
    void ohneTag() {
        // Ohne Release leitet GitHub nicht auf /releases/tag/… weiter.
        assertNull(ProgrammUpdate.ausAdresse(URI.create("https://github.com/aruff100/iTrain-Analyse/releases")));
        assertNull(ProgrammUpdate.ausAdresse(null));
    }

    @Test
    void vergleich() {
        assertTrue(Version.aus("v1.1").compareTo(Version.aus("1.0")) > 0);
        assertTrue(Version.aus("v1.10").compareTo(Version.aus("1.9")) > 0);
        assertEquals(0, Version.aus("v1.1").compareTo(Version.aus("1.1")));
    }
}
