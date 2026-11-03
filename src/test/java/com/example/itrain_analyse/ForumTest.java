package com.example.itrain_analyse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ForumTest {

    @Test
    void dateinameAusDerBeitragsnummer() {
        assertEquals("Forum_Beitrag_282639.txt",
                Zusammenstellung.forumDateiname("https://berros.eu/itrain/forum/viewtopic.php?p=282639#p282639"));
        assertEquals("Forum_Beitrag.txt", Zusammenstellung.forumDateiname(""));
    }

    @Test
    void nachrichtNenntDieForumsdatei() {
        String n = Sprachgruppe.DE_NL.nachricht(false, "Deutsch", false, false, "Text",
                "https://berros.eu/itrain/forum/viewtopic.php?p=1", "Forum_Beitrag_1.txt", List.of());
        assertTrue(n.contains("viewtopic.php?p=1"));
        assertTrue(n.contains("Der Text dieses Beitrags liegt als Datei Forum_Beitrag_1.txt bei."));
    }
}
