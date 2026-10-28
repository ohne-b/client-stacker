package dev.ohneb.clientstacker.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Plans one craft with an empty grid and cursor. Slot IDs belong to the current menu. */
public final class CraftPlanner {
    public record Slot(int id, String item, int count, int limit, boolean usable) {
        public Slot {
            Objects.requireNonNull(item);
            if (id < 0 || count < 0 || limit < 1 || count > limit || (count > 0 && item.isEmpty())) {
                throw new IllegalArgumentException("Invalid inventory slot");
            }
        }
    }

    public record Click(int slot, int button) {
        public Click {
            if (slot < 0 || (button != 0 && button != 1)) {
                throw new IllegalArgumentException("Invalid pickup click");
            }
        }
    }

    public record Plan(List<Click> fill, List<Click> collect, List<Slot> after,
                       String input, int inputCount, String output, int outputCount) {
        public Plan {
            fill = List.copyOf(fill);
            collect = List.copyOf(collect);
            after = List.copyOf(after);
        }
    }

    public enum Failure { NEED_TABLE, NOT_ENOUGH_ITEMS, NO_SPACE }

    public static final class Unavailable extends RuntimeException {
        private final Failure reason;

        public Unavailable(Failure reason) {
            super(reason.name());
            this.reason = reason;
        }

        public Failure reason() { return reason; }
    }

    private CraftPlanner() {}

    public static Plan plan(List<Slot> inventory, List<Integer> grid, int resultSlot,
                            Conversion conversion, boolean compact, int reserve, int outputLimit) {
        if (reserve < 0 || outputLimit < 1) throw new IllegalArgumentException("Invalid craft limit");
        int inputCount = compact ? conversion.ratio() : 1;
        int outputCount = compact ? 1 : conversion.ratio();
        String input = compact ? conversion.loose() : conversion.compact();
        String output = compact ? conversion.compact() : conversion.loose();
        if (grid.size() < inputCount) throw new Unavailable(Failure.NEED_TABLE);

        var ids = new HashSet<Integer>();
        if (resultSlot < 0 || !ids.add(resultSlot)) throw new IllegalArgumentException("Invalid result slot");
        for (int slot : grid) {
            if (slot < 0 || !ids.add(slot)) throw new IllegalArgumentException("Overlapping crafting slots");
        }
        for (Slot slot : inventory) {
            if (!ids.add(slot.id())) throw new IllegalArgumentException("Overlapping inventory slots");
        }

        long available = inventory.stream().filter(s -> s.usable() && s.item().equals(input))
            .mapToLong(Slot::count).sum();
        if (available - reserve < inputCount) throw new Unavailable(Failure.NOT_ENOUGH_ITEMS);

        var after = new ArrayList<>(inventory);
        var fill = new ArrayList<Click>();
        int remaining = inputCount;
        int nextGrid = 0;
        for (int i = 0; i < after.size() && remaining > 0; i++) {
            Slot slot = after.get(i);
            if (!slot.usable() || !slot.item().equals(input) || slot.count() == 0) continue;
            int take = Math.min(remaining, slot.count());
            fill.add(new Click(slot.id(), 0));
            for (int n = 0; n < take; n++) fill.add(new Click(grid.get(nextGrid++), 1));
            int left = slot.count() - take;
            if (left > 0) fill.add(new Click(slot.id(), 0));
            after.set(i, new Slot(slot.id(), left == 0 ? "" : slot.item(), left, slot.limit(), true));
            remaining -= take;
        }

        var collect = new ArrayList<Click>();
        collect.add(new Click(resultSlot, 0));
        remaining = outputCount;
        // Fill partial stacks first, preserving empty slots for subsequent trade results.
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int i = 0; i < after.size() && remaining > 0; i++) {
                Slot slot = after.get(i);
                if (!slot.usable()) continue;
                if (pass == 0 && (slot.count() == 0 || !slot.item().equals(output))) continue;
                if (pass == 1 && slot.count() != 0) continue;
                int limit = slot.count() == 0 ? outputLimit : Math.min(slot.limit(), outputLimit);
                int put = Math.min(remaining, limit - slot.count());
                if (put <= 0) continue;
                collect.add(new Click(slot.id(), 0));
                after.set(i, new Slot(slot.id(), output, slot.count() + put, limit, true));
                remaining -= put;
            }
        }
        if (remaining != 0) throw new Unavailable(Failure.NO_SPACE);
        return new Plan(fill, collect, after, input, inputCount, output, outputCount);
    }
}
