package com.wynnventory.unid;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.wynnventory.unid.BankDuplicateList.Duplicate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class BankDuplicateListTest {
    @Test
    void cleanNameDropsWynncraftsFontPadding() {
        // As stored in a bank: padding characters from Wynncraft's fonts around the real name.
        String padding = new String(Character.toChars(0xCF000)) + new String(Character.toChars(0xD0002));
        assertEquals("Taciturn", BankDuplicateList.cleanName(padding + "Taciturn" + padding + ""));
        assertEquals("Crown of Vicious Leaves", BankDuplicateList.cleanName("§5Crown of Vicious Leaves "));
    }

    @Test
    void rankKeepsOnlyDuplicatesMostCopiesFirstUpToTheLimit() {
        Map<String, Integer> counts = Map.of("Taciturn", 3, "Adherence", 2, "Cerberus", 3, "Jailbroken", 1);

        assertEquals(
                List.of(new Duplicate("Cerberus", 3), new Duplicate("Taciturn", 3), new Duplicate("Adherence", 2)),
                BankDuplicateList.rank(counts, 10));
        assertEquals(List.of(new Duplicate("Cerberus", 3)), BankDuplicateList.rank(counts, 1));
    }
}
