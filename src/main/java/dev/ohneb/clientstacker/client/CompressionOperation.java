package dev.ohneb.clientstacker.client;

import dev.ohneb.clientstacker.core.Conversion;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.Iterator;

final class CompressionOperation implements Operation {
    private final AbstractContainerMenu menu;
    private final Iterator<Conversion> materials;
    private CraftOperation craft;
    private int completed;

    CompressionOperation(AbstractContainerMenu menu) {
        this.menu = menu;
        this.materials = Materials.ALL.stream().filter(Materials::enabled).toList().iterator();
    }

    @Override
    public boolean tick() {
        Operation.requireCurrent(menu);
        if (craft != null) {
            if (!craft.tick()) return false;
            completed += craft.completed();
            craft = null;
        }
        while (materials.hasNext()) {
            Conversion material = materials.next();
            long available = MenuInventory.count(menu, new ItemStack(Materials.item(material.loose())), true)
                - ClientStacker.config().looseReserve(material);
            if (available < material.ratio()) continue;
            craft = new CraftOperation(menu, material, true, Math.toIntExact(available / material.ratio()));
            return false;
        }
        return true;
    }

    @Override public void slot(int container, int slot, ItemStack item) { if (craft != null) craft.slot(container, slot, item); }
    @Override public void contents(int container) { if (craft != null) craft.contents(container); }
    @Override public void barrier(int id) { if (craft != null) craft.barrier(id); }
    @Override public void cancel() { if (craft != null) craft.cancel(); }
    @Override public int completed() { return completed + (craft == null ? 0 : craft.completed()); }
}
