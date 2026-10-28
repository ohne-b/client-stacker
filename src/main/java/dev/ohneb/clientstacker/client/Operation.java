package dev.ohneb.clientstacker.client;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

interface Operation {
    /** Returns true after the last operation has completed. */
    boolean tick();
    default void slot(int container, int slot, ItemStack item) {}
    default void contents(int container) {}
    default void offers(int container) {}
    default void barrier(int id) {}
    default void cancel() {}
    default int completed() { return 0; }
    default String unit() { return "crafts"; }

    final class Stopped extends RuntimeException {
        final String reason;
        Stopped(String reason) { super(reason); this.reason = reason; }
    }

    static void requireCurrent(AbstractContainerMenu menu) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.containerMenu != menu) throw new Stopped("closed");
        if (!(ClientStacker.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen)
            || screen.getMenu() != menu) throw new Stopped("closed");
        if (!menu.stillValid(minecraft.player)) throw new Stopped("closed");
    }
}
