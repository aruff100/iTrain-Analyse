package com.example.itrain_analyse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProgrammUpdateTest {

    @Test
    void tagAusAntwort() {
        String json = "{\"url\":\"x\",\"tag_name\": \"v1.1\",\"name\":\"iTrain-Analyse 1.1\"}";
        assertEquals("1.1", ProgrammUpdate.neuesteAus(json).toString());
    }

    @Test
    void ohneTag() {
        assertNull(ProgrammUpdate.neuesteAus("{\"message\":\"Not Found\"}"));
        assertNull(ProgrammUpdate.neuesteAus(null));
    }

    @Test
    void vergleich() {
        assertTrue(Version.aus("v1.1").compareTo(Version.aus("1.0")) > 0);
        assertTrue(Version.aus("v1.10").compareTo(Version.aus("1.9")) > 0);
        assertEquals(0, Version.aus("v1.1").compareTo(Version.aus("1.1")));
    }
}
