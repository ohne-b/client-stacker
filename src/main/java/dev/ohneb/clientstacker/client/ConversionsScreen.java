package dev.ohneb.clientstacker.client;

import dev.ohneb.clientstacker.core.Conversion;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class ConversionsScreen extends Screen {
    private final AbstractContainerScreen<?> parent;
    private final List<Conversion> materials = Materials.ALL.stream().filter(Materials::enabled).toList();
    private int selected;
    private EditBox quantity;
    private String quantityValue = "64";

    ConversionsScreen(AbstractContainerScreen<?> parent, ItemStack item) {
        super(Component.translatable("client_stacker.conversions"));
        this.parent = parent;
        var conversion = Materials.forStack(item, false);
        if (conversion == null) conversion = Materials.forStack(item, true);
        selected = Math.max(0, materials.indexOf(conversion));
    }

    @Override
    protected void init() {
        int x = width / 2 - 150;
        int y = Math.max(30, height / 2 - 84);
        var material = material();
        addRenderableWidget(Button.builder(Component.translatable(Materials.item(material.loose()).getDescriptionId()), button -> {
            selected = (selected + 1) % materials.size();
            rebuildWidgets();
        }).bounds(x, y, 300, 20).build());
        quantity = new EditBox(font, x, y + 44, 146, 18, Component.translatable("client_stacker.quantity"));
        quantity.setMaxLength(9);
        quantity.setValue(quantityValue);
        quantity.setResponder(value -> quantityValue = value);
        addRenderableWidget(quantity);
        addRenderableWidget(Button.builder(Component.translatable("client_stacker.unpack_one"), button -> execute(false, 0))
            .bounds(x, y + 78, 146, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("client_stacker.prepare"), button -> {
            if (!quantity.getValue().matches("[0-9]+") || Integer.parseInt(quantity.getValue()) == 0) {
                quantity.setTextColor(0xFFFF7777);
                return;
            }
            execute(false, Integer.parseInt(quantity.getValue()));
        }).bounds(x + 154, y + 78, 146, 20).build());
        var compact = addRenderableWidget(Button.builder(Component.translatable("client_stacker.compact_selected"), button -> execute(true, 1))
            .bounds(x, y + 102, 146, 20).build());
        var all = addRenderableWidget(Button.builder(Component.translatable("client_stacker.compact_all"), button -> {
            if (restore()) ClientStacker.compactAll(parent.getMenu());
        }).bounds(x + 154, y + 102, 146, 20).build());
        compact.active = all.active = parent.getMenu() instanceof CraftingMenu;
        addRenderableWidget(Button.builder(Component.translatable("client_stacker.protected_slots"), button ->
            ClientStacker.showScreen(new ProtectionScreen(this, parent))).bounds(x, y + 130, 146, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("client_stacker.material_settings"), button ->
            ClientStacker.showScreen(new MaterialSettingsScreen(this, material()))).bounds(x + 154, y + 130, 146, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
            .bounds(width / 2 - 50, Math.min(height - 24, y + 160), 100, 20).build());
    }

    private Conversion material() { return materials.get(selected); }

    private boolean restore() {
        if (minecraft.player == null || minecraft.player.containerMenu != parent.getMenu()) {
            ClientStacker.showScreen(null);
            return false;
        }
        ClientStacker.showScreen(parent);
        return true;
    }

    private void execute(boolean compact, int quantity) {
        if (restore()) ClientStacker.convert(parent.getMenu(), material(), compact, quantity);
    }

    @Override public void onClose() { restore(); }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
        graphics.drawString(font, Component.translatable("client_stacker.quantity"), quantity.getX(), quantity.getY() - 11, 0xFFE0E0E0);
        var pair = material();
        ItemStack loose = new ItemStack(Materials.item(pair.loose()));
        ItemStack block = new ItemStack(Materials.item(pair.compact()));
        long available = MenuInventory.count(parent.getMenu(), loose, true);
        long compact = MenuInventory.count(parent.getMenu(), block, true);
        long protectedValue = pair.value(MenuInventory.count(parent.getMenu(), block, false) - compact,
            MenuInventory.count(parent.getMenu(), loose, false) - available);
        graphics.drawString(font, Component.translatable("client_stacker.loose", available), width / 2 + 4, quantity.getY() - 10, 0xFFE0E0E0);
        graphics.drawString(font, Component.translatable("client_stacker.blocks", compact), width / 2 + 4, quantity.getY(), 0xFFE0E0E0);
        graphics.drawString(font, Component.translatable("client_stacker.protected_value", protectedValue), width / 2 + 4, quantity.getY() + 10, 0xFFE0E0E0);
    }
}
