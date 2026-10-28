package dev.ohneb.clientstacker.client;

import dev.ohneb.clientstacker.core.CraftPlanner;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class MenuInventory {
    private MenuInventory() {}

    public static List<Slot> playerSlots(AbstractContainerMenu menu) {
        var player = Minecraft.getInstance().player;
        if (player == null) return List.of();
        return menu.slots.stream().filter(slot -> slot.container == player.getInventory()
            && slot.getContainerSlot() >= 0 && slot.getContainerSlot() < 36).toList();
    }

    public static boolean protectedSlot(Slot slot) {
        return ClientStacker.config().protectedSlots.contains(slot.getContainerSlot());
    }

    public static List<CraftPlanner.Slot> snapshot(AbstractContainerMenu menu) {
        var result = new ArrayList<CraftPlanner.Slot>();
        for (Slot slot : playerSlots(menu)) {
            ItemStack stack = slot.getItem();
            result.add(new CraftPlanner.Slot(slot.index, Materials.id(stack), stack.getCount(),
                stack.isEmpty() ? 64 : stack.getMaxStackSize(), !protectedSlot(slot) && Materials.plain(stack)));
        }
        return result;
    }

    public static List<Integer> grid(AbstractContainerMenu menu) {
        if (menu instanceof InventoryMenu) return List.of(1, 2, 3, 4);
        if (menu instanceof CraftingMenu) return List.of(1, 2, 3, 4, 5, 6, 7, 8, 9);
        return List.of();
    }

    public static boolean emptyGrid(AbstractContainerMenu menu) {
        List<Integer> grid = grid(menu);
        return !grid.isEmpty() && grid.stream().allMatch(slot -> menu.getSlot(slot).getItem().isEmpty());
    }

    public static long count(AbstractContainerMenu menu, ItemStack item, boolean usableOnly) {
        return playerSlots(menu).stream().filter(slot -> !usableOnly || (!protectedSlot(slot) && Materials.plain(slot.getItem())))
            .map(Slot::getItem).filter(stack -> ItemStack.isSameItemSameComponents(stack, item))
            .mapToLong(ItemStack::getCount).sum();
    }

    public static List<ItemStack> copySlots(AbstractContainerMenu menu) {
        return menu.slots.stream().map(slot -> slot.getItem().copy()).toList();
    }

    public static boolean matches(List<ItemStack> expected, AbstractContainerMenu menu, int ignoredSlot) {
        if (expected.size() != menu.slots.size()) return false;
        for (int i = 0; i < expected.size(); i++) {
            if (i != ignoredSlot && !ItemStack.matches(expected.get(i), menu.getSlot(i).getItem())) return false;
        }
        return true;
    }

    public static boolean sameContents(List<ItemStack> before, AbstractContainerMenu menu, int ignoredSlot) {
        List<ItemStack> after = copySlots(menu);
        if (before.size() != after.size()) return false;
        for (List<ItemStack> contents : List.of(before, after)) {
            for (int i = 0; i < contents.size(); i++) {
                ItemStack stack = contents.get(i);
                if (i == ignoredSlot || stack.isEmpty()) continue;
                long oldCount = 0;
                long newCount = 0;
                for (int j = 0; j < before.size(); j++) {
                    if (j == ignoredSlot) continue;
                    if (ItemStack.isSameItemSameComponents(before.get(j), stack)) oldCount += before.get(j).getCount();
                    if (ItemStack.isSameItemSameComponents(after.get(j), stack)) newCount += after.get(j).getCount();
                }
                if (oldCount != newCount) return false;
            }
        }
        return true;
    }

    /** Checks room for all stacks before any click is sent. */
    public static List<List<Integer>> placement(AbstractContainerMenu menu, ItemStack... stacks) {
        var slots = playerSlots(menu);
        var contents = new ArrayList<>(slots.stream().map(slot -> slot.getItem().copy()).toList());
        var result = new ArrayList<List<Integer>>();
        for (ItemStack stack : stacks) {
            int remaining = stack.getCount();
            var destinations = new ArrayList<Integer>();
            for (int pass = 0; pass < 2 && remaining > 0; pass++) {
                for (int i = 0; i < slots.size() && remaining > 0; i++) {
                    Slot slot = slots.get(i);
                    ItemStack existing = contents.get(i);
                    if (protectedSlot(slot) || !slot.mayPlace(stack)) continue;
                    if (pass == 0 && (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, stack))) continue;
                    if (pass == 1 && !existing.isEmpty()) continue;
                    int put = Math.min(remaining, slot.getMaxStackSize(stack) - existing.getCount());
                    if (put <= 0) continue;
                    destinations.add(slot.index);
                    if (existing.isEmpty()) {
                        contents.set(i, stack.copyWithCount(put));
                    } else {
                        existing.grow(put);
                    }
                    remaining -= put;
                }
            }
            if (remaining != 0) throw new CraftPlanner.Unavailable(CraftPlanner.Failure.NO_SPACE);
            result.add(List.copyOf(destinations));
        }
        return result;
    }

    public static void putCarried(AbstractContainerMenu menu, List<Integer> destinations) {
        for (int slot : destinations) ClientStacker.click(menu, slot, 0);
        if (!menu.getCarried().isEmpty()) throw new Operation.Stopped("changed");
    }

    public static void clearInputs(AbstractContainerMenu menu, List<Integer> inputs) {
        if (!menu.getCarried().isEmpty()) throw new Operation.Stopped("cursor");
        ItemStack[] stacks = inputs.stream().map(slot -> menu.getSlot(slot).getItem().copy()).toArray(ItemStack[]::new);
        var destinations = placement(menu, stacks);
        for (int i = 0; i < inputs.size(); i++) {
            if (stacks[i].isEmpty()) continue;
            ClientStacker.click(menu, inputs.get(i), 0);
            putCarried(menu, destinations.get(i));
        }
    }
}
