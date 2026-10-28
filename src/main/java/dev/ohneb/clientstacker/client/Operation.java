package dev.ohneb.clientstacker.client;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

interface Operation {
    /** Returns true after the last operation has completed. */
    boolean tick();
    default void slot(int container, int slot, ItemStack item) {}
    default void contents(int container) {}
    default void offers(int container) {}
    default void cancel() {}

    final class Stopped extends RuntimeException {
        final String reason;
        Stopped(String reason) { super(reason); this.reason = reason; }
    }

    static void requireCurrent(AbstractContainerMenu menu) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.containerMenu != menu) throw new Stopped("closed");
        if (!menu.stillValid(minecraft.player)) throw new Stopped("closed");
    }
}
