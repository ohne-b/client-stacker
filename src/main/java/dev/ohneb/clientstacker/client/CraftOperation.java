package dev.ohneb.clientstacker.client;

import dev.ohneb.clientstacker.core.Conversion;
import dev.ohneb.clientstacker.core.CraftPlanner;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** One recipe at a time, confirming each complete click batch with the server. */
final class CraftOperation implements Operation {
    private enum Stage { FILL, RESULT, COLLECTED }

    private final AbstractContainerMenu menu;
    private final Conversion conversion;
    private final boolean compact;
    private final int reserve;
    private int remaining;
    private Stage stage = Stage.FILL;
    private CraftPlanner.Plan plan;
    private List<ItemStack> expected;
    private long deadline;
    private int completed;
    private InventoryCheckpoint checkpoint;

    CraftOperation(AbstractContainerMenu menu, Conversion conversion, boolean compact, int crafts) {
        this.menu = menu;
        this.conversion = conversion;
        this.compact = compact;
        this.reserve = compact ? ClientStacker.config().looseReserve(conversion) : ClientStacker.config().blockReserve(conversion);
        this.remaining = crafts;
        if (crafts < 1) throw new IllegalArgumentException("No crafts requested");
        resetDeadline();
    }

    @Override
    public boolean tick() {
        Operation.requireCurrent(menu);
        if (System.nanoTime() > deadline) throw new Stopped("timeout");
        switch (stage) {
            case FILL -> {
                if (!menu.getCarried().isEmpty()) throw new Stopped("cursor");
                if (expected != null && !MenuInventory.matches(expected, menu, 0)) throw new Stopped("changed");
                if (!MenuInventory.emptyGrid(menu)) throw new Stopped("grid");
                try {
                    plan = CraftPlanner.plan(MenuInventory.snapshot(menu), MenuInventory.grid(menu), 0,
                        conversion, compact, reserve,
                        Materials.item(compact ? conversion.compact() : conversion.loose()).getDefaultMaxStackSize());
                } catch (CraftPlanner.Unavailable exception) {
                    if (completed > 0 && exception.reason() == CraftPlanner.Failure.NOT_ENOUGH_ITEMS) return true;
                    throw exception;
                }
                stage = Stage.RESULT;
                for (var click : plan.fill()) ClientStacker.click(menu, click.slot(), click.button());
                if (!menu.getCarried().isEmpty()) throw new Stopped("changed");
                expected = MenuInventory.copySlots(menu);
                checkpoint = new InventoryCheckpoint(menu, 0);
                resetDeadline();
            }
            case RESULT -> {
                if (!checkpoint.ready()) return false;
                ItemStack output = new ItemStack(Materials.item(plan.output()), plan.outputCount());
                if (!ItemStack.matches(menu.getSlot(0).getItem(), output)) throw new Stopped("recipe");
                stage = Stage.COLLECTED;
                for (var click : plan.collect()) ClientStacker.click(menu, click.slot(), click.button());
                if (!menu.getCarried().isEmpty() || !MenuInventory.emptyGrid(menu)) throw new Stopped("changed");
                for (var slot : plan.after()) {
                    ItemStack actual = menu.getSlot(slot.id()).getItem();
                    if (actual.getCount() != slot.count() || !Materials.id(actual).equals(slot.item())) throw new Stopped("changed");
                }
                expected = MenuInventory.copySlots(menu);
                checkpoint = new InventoryCheckpoint(menu, 0);
                resetDeadline();
            }
            case COLLECTED -> {
                if (!checkpoint.ready()) return false;
                if (!menu.getSlot(0).getItem().isEmpty()) throw new Stopped("recipe");
                completed++;
                if (--remaining == 0) return true;
                stage = Stage.FILL;
                checkpoint = null;
            }
        }
        return false;
    }

    @Override
    public void contents(int container) {
        if (checkpoint != null) checkpoint.contents(container);
    }

    @Override
    public void barrier(int id) { if (checkpoint != null) checkpoint.barrier(id); }

    @Override public int completed() { return completed; }

    @Override
    public void cancel() {
        Operation.requireCurrent(menu);
        if (checkpoint != null && checkpoint.ready()) {
            MenuInventory.clearInputs(menu, MenuInventory.grid(menu));
        }
    }

    private void resetDeadline() {
        deadline = System.nanoTime() + ClientStacker.config().timeoutSeconds * 1_000_000_000L;
    }
}
