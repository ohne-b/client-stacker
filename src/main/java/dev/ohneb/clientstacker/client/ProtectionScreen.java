package dev.ohneb.clientstacker.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

final class ProtectionScreen extends Screen {
    private final Screen parent;
    private final AbstractContainerScreen<?> inventory;

    ProtectionScreen(Screen parent, AbstractContainerScreen<?> inventory) {
        super(Component.translatable("client_stacker.protected_slots"));
        this.parent = parent;
        this.inventory = inventory;
    }

    @Override
    protected void init() {
        int x = width / 2 - 108;
        int y = height / 2 - 55;
        for (Slot slot : MenuInventory.playerSlots(inventory.getMenu())) {
            int index = slot.getContainerSlot();
            int row = index < 9 ? 3 : (index - 9) / 9;
            int column = index % 9;
            addRenderableWidget(new SlotButton(x + column * 24, y + row * 24 + (row == 3 ? 6 : 0), slot, inventory));
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
            .bounds(width / 2 - 50, y + 126, 100, 20).build());
    }

    @Override public void onClose() { ClientStacker.showScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("client_stacker.protected_slots.help"), width / 2, height / 2 + 54, 0xFFE0E0E0);
    }

    private static final class SlotButton extends Button {
        private final Slot slot;

        SlotButton(int x, int y, Slot slot, AbstractContainerScreen<?> inventory) {
            super(x, y, 22, 22, Component.empty(), button -> ClientStacker.protect(inventory, slot), DEFAULT_NARRATION);
            this.slot = slot;
            updateLabel();
        }

        private void updateLabel() {
            Component item = slot.getItem().isEmpty() ? Component.translatable("client_stacker.empty") : slot.getItem().getHoverName();
            Component label = Component.translatable("client_stacker.protection_slot", slot.getContainerSlot() + 1, item,
                Component.translatable(MenuInventory.protectedSlot(slot) ? "client_stacker.protected" : "client_stacker.available"));
            if (!label.equals(getMessage())) {
                setMessage(label);
                setTooltip(Tooltip.create(label));
            }
        }

        @Override
        //? if >=1.21.11 {
        protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        //?} else {
        /*protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        *///?}
            updateLabel();
            int border = MenuInventory.protectedSlot(slot) ? 0xFFFFC857 : isHoveredOrFocused() ? 0xFFFFFFFF : 0xFF777777;
            graphics.fill(getX(), getY(), getX() + 22, getY() + 22, border);
            graphics.fill(getX() + 1, getY() + 1, getX() + 21, getY() + 21, 0xFF303030);
            graphics.renderItem(slot.getItem(), getX() + 3, getY() + 3);
            graphics.renderItemDecorations(Minecraft.getInstance().font, slot.getItem(), getX() + 3, getY() + 3);
        }
    }
}
