package dev.ohneb.clientstacker.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CraftPlannerTest {
    private static final Conversion EMERALDS = new Conversion("block", "emerald", 9);
    private static final List<Integer> SMALL_GRID = List.of(1, 2, 3, 4);
    private static final List<Integer> TABLE_GRID = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9);

    private CraftPlanner.Slot slot(int id, String item, int count) {
        return new CraftPlanner.Slot(id, item, count, 64, true);
    }

    @Test
    void unpacksOneBlockAndReturnsTheStackBeforeTakingOutput() {
        var plan = CraftPlanner.plan(List.of(slot(10, "block", 64), slot(11, "emerald", 5)),
            SMALL_GRID, 0, EMERALDS, false, 0, 64);
        assertEquals(List.of(new CraftPlanner.Click(10, 0), new CraftPlanner.Click(1, 1),
            new CraftPlanner.Click(10, 0)), plan.fill());
        assertEquals(63, plan.after().get(0).count());
        assertEquals(14, plan.after().get(1).count());
    }

    @Test
    void theLastBlockFreesItsSlotForTheEmeralds() {
        var plan = CraftPlanner.plan(List.of(slot(10, "block", 1)), SMALL_GRID, 0, EMERALDS, false, 0, 64);
        assertEquals(slot(10, "emerald", 9), plan.after().getFirst());
    }

    @Test
    void refusesAnUnpackWhoseIntermediateResultDoesNotFit() {
        var failure = assertThrows(CraftPlanner.Unavailable.class, () -> CraftPlanner.plan(
            List.of(slot(10, "block", 2), slot(11, "emerald", 60)), SMALL_GRID, 0, EMERALDS, false, 0, 64));
        assertEquals(CraftPlanner.Failure.NO_SPACE, failure.reason());
    }

    @Test
    void usesMultiplePartialStacksWithoutRequiringAnEmptySlot() {
        var plan = CraftPlanner.plan(List.of(slot(10, "block", 2), slot(11, "emerald", 60),
            slot(12, "emerald", 59)), SMALL_GRID, 0, EMERALDS, false, 0, 64);
        assertEquals(64, plan.after().get(1).count());
        assertEquals(64, plan.after().get(2).count());
    }

    @Test
    void skipsProtectedSourcesAndDestinations() {
        var protectedSlot = new CraftPlanner.Slot(11, "emerald", 1, 64, false);
        var plan = CraftPlanner.plan(List.of(slot(10, "block", 1), protectedSlot),
            SMALL_GRID, 0, EMERALDS, false, 0, 64);
        assertEquals(protectedSlot, plan.after().get(1));
        assertEquals(9, plan.after().getFirst().count());
        assertThrows(CraftPlanner.Unavailable.class, () -> CraftPlanner.plan(
            List.of(new CraftPlanner.Slot(10, "block", 1, 64, false)), SMALL_GRID, 0, EMERALDS, false, 0, 64));
    }

    @Test
    void compressionUsesNineSlotsAndKeepsTheReserve() {
        var inventory = List.of(slot(10, "emerald", 25), slot(11, "", 0));
        var plan = CraftPlanner.plan(inventory, TABLE_GRID, 0, EMERALDS, true, 16, 64);
        assertEquals(16, plan.after().getFirst().count());
        assertEquals(slot(11, "block", 1), plan.after().get(1));
        assertThrows(CraftPlanner.Unavailable.class, () -> CraftPlanner.plan(
            plan.after(), TABLE_GRID, 0, EMERALDS, true, 16, 64));
        var failure = assertThrows(CraftPlanner.Unavailable.class, () -> CraftPlanner.plan(
            inventory, SMALL_GRID, 0, EMERALDS, true, 0, 64));
        assertEquals(CraftPlanner.Failure.NEED_TABLE, failure.reason());
    }

    @Test
    void compressionCanUseInputsAcrossSeveralStacks() {
        var plan = CraftPlanner.plan(List.of(slot(10, "emerald", 4), slot(11, "emerald", 5)),
            TABLE_GRID, 0, EMERALDS, true, 0, 64);
        assertEquals(9, plan.fill().stream().filter(c -> c.button() == 1).count());
        assertEquals(1, plan.after().stream().filter(s -> s.item().equals("block")).mapToInt(CraftPlanner.Slot::count).sum());
    }

    @Test
    void conversionsConserveValueAcrossStackBoundaries() {
        for (int blocks = 1; blocks <= 64; blocks++) {
            for (int loose = 0; loose <= 64; loose++) {
                var plan = CraftPlanner.plan(List.of(slot(10, "block", blocks), slot(11, "emerald", loose),
                    slot(12, "", 0)), SMALL_GRID, 0, EMERALDS, false, 0, 64);
                long afterBlocks = plan.after().stream().filter(s -> s.item().equals("block")).mapToLong(CraftPlanner.Slot::count).sum();
                long afterLoose = plan.after().stream().filter(s -> s.item().equals("emerald")).mapToLong(CraftPlanner.Slot::count).sum();
                assertEquals(EMERALDS.value(blocks, loose), EMERALDS.value(afterBlocks, afterLoose));
            }
        }
    }

    @Test
    void rejectsOverlappingMenuSlots() {
        assertThrows(IllegalArgumentException.class, () -> CraftPlanner.plan(List.of(slot(1, "block", 1)),
            SMALL_GRID, 0, EMERALDS, false, 0, 64));
    }
}
