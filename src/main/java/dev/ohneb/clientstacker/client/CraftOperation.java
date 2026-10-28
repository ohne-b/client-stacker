package dev.ohneb.clientstacker.client;

import dev.ohneb.clientstacker.core.Conversion;
import dev.ohneb.clientstacker.core.CraftPlanner;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** One recipe at a time, fenced by the server's crafting-result updates. */
final class CraftOperation implements Operation {
    private enum Stage { FILL, RESULT, COLLECTED, CHECKPOINT }

    private final AbstractContainerMenu menu;
    private final Conversion conversion;
    private final boolean compact;
    private final int reserve;
    private int remaining;
    private Stage stage = Stage.FILL;
    private CraftPlanner.Plan plan;
    private List<ItemStack> expected;
    private boolean serverResult;
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
        if (!menu.getCarried().isEmpty()) throw new Stopped("cursor");
        if (System.nanoTime() > deadline) throw new Stopped("timeout");
        if (expected != null && !MenuInventory.matches(expected, menu, 0)) throw new Stopped("changed");
        switch (stage) {
            case FILL -> {
                if (!MenuInventory.emptyGrid(menu)) throw new Stopped("grid");
                try {
                    plan = CraftPlanner.plan(MenuInventory.snapshot(menu), MenuInventory.grid(menu), 0,
                        conversion, compact, reserve,
                        Materials.item(compact ? conversion.compact() : conversion.loose()).getDefaultMaxStackSize());
                } catch (CraftPlanner.Unavailable exception) {
                    if (completed > 0 && exception.reason() == CraftPlanner.Failure.NOT_ENOUGH_ITEMS) return true;
                    throw exception;
                }
                serverResult = false;
                stage = Stage.RESULT;
                for (var click : plan.fill()) ClientStacker.click(menu, click.slot(), click.button());
                if (!menu.getCarried().isEmpty()) throw new Stopped("changed");
                expected = MenuInventory.copySlots(menu);
                resetDeadline();
            }
            case RESULT -> {
                if (!serverResult) return false;
                ItemStack output = new ItemStack(Materials.item(plan.output()), plan.outputCount());
                if (!ItemStack.matches(menu.getSlot(0).getItem(), output)) throw new Stopped("recipe");
                serverResult = false;
                stage = Stage.COLLECTED;
                for (var click : plan.collect()) ClientStacker.click(menu, click.slot(), click.button());
                if (!menu.getCarried().isEmpty() || !MenuInventory.emptyGrid(menu)) throw new Stopped("changed");
                for (var slot : plan.after()) {
                    ItemStack actual = menu.getSlot(slot.id()).getItem();
                    if (actual.getCount() != slot.count() || !Materials.id(actual).equals(slot.item())) throw new Stopped("changed");
                }
                expected = MenuInventory.copySlots(menu);
                resetDeadline();
            }
            case COLLECTED -> {
                if (!serverResult) return false;
                if (!menu.getSlot(0).getItem().isEmpty()) throw new Stopped("recipe");
                checkpoint = new InventoryCheckpoint(menu, 0);
                stage = Stage.CHECKPOINT;
                resetDeadline();
            }
            case CHECKPOINT -> {
                if (!checkpoint.ready()) return false;
                completed++;
                if (--remaining == 0) return true;
                stage = Stage.FILL;
                checkpoint = null;
            }
        }
        return false;
    }

    @Override
    public void slot(int container, int slot, ItemStack item) {
        if (container != menu.containerId || slot != 0) return;
        if (stage == Stage.RESULT) {
            serverResult = ItemStack.matches(item, new ItemStack(Materials.item(plan.output()), plan.outputCount()));
        } else if (stage == Stage.COLLECTED) {
            serverResult = item.isEmpty();
        }
    }

    @Override
    public void contents(int container) {
        if (container == menu.containerId) slot(container, 0, menu.getSlot(0).getItem());
        if (checkpoint != null) checkpoint.contents(container);
    }

    @Override
    public void barrier(int id) { if (checkpoint != null) checkpoint.barrier(id); }

    @Override public int completed() { return completed; }

    @Override
    public void cancel() {
        Operation.requireCurrent(menu);
        if (expected != null && MenuInventory.matches(expected, menu, 0) && menu.getCarried().isEmpty()) {
            MenuInventory.clearInputs(menu, MenuInventory.grid(menu));
        }
    }

    private void resetDeadline() {
        deadline = System.nanoTime() + ClientStacker.config().timeoutSeconds * 1_000_000_000L;
    }
}
