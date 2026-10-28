package dev.ohneb.clientstacker.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConversionTest {
    @Test
    void appliesReservesAcrossMetalDenominations() {
        for (String metal : List.of("iron", "gold")) {
            var blocks = new Conversion(metal + "_block", metal + "_ingot", 9);
            var ingots = new Conversion(metal + "_ingot", metal + "_nugget", 9);
            var catalog = List.of(blocks, ingots);
            for (var reserve : catalog) {
                assertEquals(81, reserve.unitsPerItem(metal + "_block", catalog));
                assertEquals(9, reserve.unitsPerItem(metal + "_ingot", catalog));
                assertEquals(1, reserve.unitsPerItem(metal + "_nugget", catalog));
                assertEquals(0, reserve.unitsPerItem("emerald", catalog));
            }
            long retained = 9L * blocks.unitsPerItem(metal + "_ingot", catalog);
            long expense = 9L * blocks.unitsPerItem(metal + "_nugget", catalog);
            assertEquals(TradeLimits.Stop.RESERVE, new TradeLimits(0, retained, 0).check(0, expense, 81, 0, 1));
            assertEquals(TradeLimits.Stop.NONE, new TradeLimits(0, retained, 0).check(0, expense, 90, 0, 1));
        }
    }

    @Test
    void keepsTwoTierCurrenciesInLooseUnits() {
        var emeralds = new Conversion("emerald_block", "emerald", 9);
        assertEquals(9, emeralds.unitsPerItem("emerald_block", List.of(emeralds)));
        assertEquals(1, emeralds.unitsPerItem("emerald", List.of(emeralds)));
    }
}
