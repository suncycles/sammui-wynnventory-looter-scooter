package com.wynnventory.unid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

public class UnidSettingsTest {
    @Test
    void blacklistIgnoresCaseAndKeepsOneSortedEntryPerItem() {
        UnidSettings settings = new UnidSettings();

        assertTrue(settings.addToBlacklist("Skyfall"));
        assertTrue(settings.addToBlacklist("Apology"));
        assertFalse(settings.addToBlacklist("skyfall"));
        assertEquals(List.of("Apology", "Skyfall"), settings.getBlacklist());
        assertTrue(settings.isBlacklisted("SKYFALL"));

        assertTrue(settings.removeFromBlacklist("apology"));
        assertFalse(settings.removeFromBlacklist("apology"));
        assertFalse(settings.isBlacklisted("Apology"));
    }
}
