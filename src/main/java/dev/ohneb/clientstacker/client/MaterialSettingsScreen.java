package dev.ohneb.clientstacker.client;

import dev.ohneb.clientstacker.core.Conversion;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class MaterialSettingsScreen extends Screen {
    private final Screen parent;
    private final Conversion material;
    private EditBox loose;
    private EditBox blocks;
    private EditBox retained;
    private Component error = Component.empty();

    MaterialSettingsScreen(Screen parent, Conversion material) {
        super(Component.translatable(Materials.item(material.loose()).getDescriptionId()));
        this.parent = parent;
        this.material = material;
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = Math.max(36, height / 2 - 75);
        var config = ClientStacker.config();
        loose = field(x, y, "reserve", config.looseReserve(material));
        blocks = field(x, y + 36, "block_reserve", config.blockReserve(material));
        retained = field(x, y + 72, "material_retain", config.retainedValue(material));
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
            .bounds(width / 2 - 50, y + 110, 100, 20).build());
    }

    private EditBox field(int x, int y, String key, int value) {
        var field = new EditBox(font, x, y, 200, 18, Component.translatable("client_stacker.setting." + key));
        field.setMaxLength(9);
        field.setValue(Integer.toString(value));
        return addRenderableWidget(field);
    }

    @Override
    public void onClose() {
        for (EditBox field : new EditBox[]{loose, blocks, retained}) {
            if (!field.getValue().matches("[0-9]+")) {
                field.setTextColor(0xFFFF7777);
                return;
            }
        }
        var config = ClientStacker.config();
        config.looseReserves.put(material.loose(), Integer.parseInt(loose.getValue()));
        config.blockReserves.put(material.compact(), Integer.parseInt(blocks.getValue()));
        config.retainedValues.put(material.loose(), Integer.parseInt(retained.getValue()));
        if (!config.save()) { error = Component.translatable("client_stacker.message.save"); return; }
        ClientStacker.showScreen(parent);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
        graphics.drawString(font, loose.getMessage(), loose.getX(), loose.getY() - 11, 0xFFE0E0E0);
        graphics.drawString(font, blocks.getMessage(), blocks.getX(), blocks.getY() - 11, 0xFFE0E0E0);
        graphics.drawString(font, retained.getMessage(), retained.getX(), retained.getY() - 11, 0xFFE0E0E0);
        graphics.drawCenteredString(font, error, width / 2, height - 28, 0xFFFF7777);
    }
}
