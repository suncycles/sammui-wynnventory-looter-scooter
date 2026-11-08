package com.wynnventory.unid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void anItemIsEitherAFavoriteOrBlacklistedNeverBoth() {
        UnidSettings settings = new UnidSettings();

        settings.addToBlacklist("Skyfall");
        assertTrue(settings.addToFavorites("skyfall"));
        assertTrue(settings.isFavorite("Skyfall"));
        assertFalse(settings.isBlacklisted("Skyfall"));

        settings.addToBlacklist("Skyfall");
        assertTrue(settings.isBlacklisted("Skyfall"));
        assertFalse(settings.isFavorite("Skyfall"));
    }

    @Test
    void hexColorsReadShortAndLongFormsWithOrWithoutHash() {
        assertEquals(0xFF0000, UnidSettings.parseHexColor("f00"));
        assertEquals(0x00FF00, UnidSettings.parseHexColor("#0F0"));
        assertEquals(0x00FFCC, UnidSettings.parseHexColor("#00ffcc"));
        assertEquals(0x0000FF, UnidSettings.parseHexColor("0000FF"));
        assertNull(UnidSettings.parseHexColor("#12345"));
        assertNull(UnidSettings.parseHexColor("red"));
        assertNull(UnidSettings.parseHexColor(""));
    }
}
