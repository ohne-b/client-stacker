package dev.ohneb.clientstacker.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TradeLimitsTest {
    @Test
    void stopsBeforeOvershootingAnyLimit() {
        assertEquals(TradeLimits.Stop.SPENDING_CAP, new TradeLimits(20, 0, 0).check(19, 2, 100, 0, 1));
        assertEquals(TradeLimits.Stop.RESERVE, new TradeLimits(0, 64, 0).check(0, 2, 65, 0, 1));
        assertEquals(TradeLimits.Stop.OUTPUT_TARGET, new TradeLimits(0, 0, 32).check(0, 1, 100, 30, 3));
        assertEquals(TradeLimits.Stop.NONE, new TradeLimits(20, 64, 32).check(19, 1, 65, 31, 1));
    }

    @Test
    void disabledCapsDoNotOverflow() {
        assertEquals(TradeLimits.Stop.NONE, new TradeLimits(0, 0, 0)
            .check(Long.MAX_VALUE, 1, 1, Long.MAX_VALUE, 1));
        assertEquals(TradeLimits.Stop.SPENDING_CAP, new TradeLimits(Long.MAX_VALUE, 0, 0)
            .check(Long.MAX_VALUE, 1, 1, 0, 1));
    }

    @Test
    void sellingCanBuildUpAnEmeraldReserve() {
        assertEquals(TradeLimits.Stop.NONE, new TradeLimits(20, 64, 0).check(0, 0, 3, 3, 1));
    }

    @Test
    void calculatesOnlyWholeBlocksNeededForTheDeficit() {
        var emeralds = new Conversion("block", "emerald", 9);
        assertEquals(0, emeralds.blocksNeeded(20, 20));
        assertEquals(1, emeralds.blocksNeeded(1, 0));
        assertEquals(2, emeralds.blocksNeeded(20, 5));
        assertEquals(2, emeralds.blocksNeeded(20, 2));
        assertThrows(ArithmeticException.class, () -> emeralds.value(Long.MAX_VALUE, 1));
    }

    @Test
    void valuesCompactPaymentsInTheSameUnitsAsLoosePayments() {
        var iron = new Conversion("iron_block", "iron_ingot", 9);
        assertEquals(9, iron.itemValue("iron_block", 1));
        assertEquals(7, iron.itemValue("iron_ingot", 7));
        assertEquals(0, iron.itemValue("gold_ingot", 7));
        assertEquals(TradeLimits.Stop.RESERVE, new TradeLimits(0, 9, 0)
            .check(0, iron.itemValue("iron_block", 1), iron.value(1, 0), 0, 1));
        assertThrows(ArithmeticException.class, () -> iron.itemValue("iron_block", Long.MAX_VALUE));
    }
}
