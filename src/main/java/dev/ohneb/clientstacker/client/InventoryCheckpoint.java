package dev.ohneb.clientstacker.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import net.minecraft.client.Minecraft;
//? if >=1.21.5 {
import net.minecraft.network.HashedStack;
//?}
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** A full inventory response followed by a correlated server-thread barrier. */
final class InventoryCheckpoint {
    private static int nextId = Integer.MIN_VALUE;
    private final int id = nextResponseId();
    private final AbstractContainerMenu menu;
    private final List<ItemStack> expected;
    private final int resultSlot;
    private List<ItemStack> received;
    private boolean barrier;
    private boolean confirmed;

    static int nextResponseId() { return nextId++; }

    InventoryCheckpoint(AbstractContainerMenu menu, int resultSlot) {
        this(menu, resultSlot, true);
    }

    InventoryCheckpoint(AbstractContainerMenu menu, int resultSlot, boolean preserveLayout) {
        if (!ClientStacker.supportsAutomation()) throw new Operation.Stopped("unsupported_version");
        Operation.requireCurrent(menu);
        if (!menu.getCarried().isEmpty()) throw new Operation.Stopped("cursor");
        this.menu = menu;
        this.resultSlot = resultSlot;
        this.expected = preserveLayout ? MenuInventory.copySlots(menu) : null;
        var hotbar = MenuInventory.playerSlots(menu).stream().filter(slot -> slot.getContainerSlot() == 0)
            .findFirst().orElseThrow(() -> new Operation.Stopped("changed"));
        var connection = Minecraft.getInstance().getConnection();
        // A hotbar slot swapped with itself leaves both inventory and cursor unchanged.
        // Revision 32768 is outside vanilla's wrapping counter and requests a full correction.
        //? if >=1.21.5 {
        connection.send(new ServerboundContainerClickPacket(menu.containerId, 32768, (short) hotbar.index,
            (byte) 0, ClickType.SWAP, Int2ObjectMaps.emptyMap(), HashedStack.EMPTY));
        //?} else {
        /*connection.send(new ServerboundContainerClickPacket(menu.containerId, 32768, hotbar.index,
            0, ClickType.SWAP, ItemStack.EMPTY, Int2ObjectMaps.emptyMap()));
        *///?}
        // Through 26.2, completion requests run on the server thread and echo an ID.
        // No command is executed. A server that suppresses this response leaves the menu open.
        connection.send(new ServerboundCommandSuggestionPacket(id, "/"));
    }

    void contents(int container) {
        if (!barrier && container == menu.containerId) received = MenuInventory.copySlots(menu);
    }

    void barrier(int responseId) {
        if (barrier || responseId != id) return;
        barrier = true;
        confirmed = received != null && menu.getCarried().isEmpty() && MenuInventory.matches(received, menu, resultSlot)
            && (expected == null || MenuInventory.matches(expected, menu, resultSlot));
    }

    boolean ready() {
        Operation.requireCurrent(menu);
        if (!barrier) return false;
        if (!confirmed || !menu.getCarried().isEmpty() || !MenuInventory.matches(received, menu, resultSlot)) {
            throw new Operation.Stopped("uncertain");
        }
        return true;
    }
}
