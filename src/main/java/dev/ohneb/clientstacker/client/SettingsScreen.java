package dev.ohneb.clientstacker.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class SettingsScreen extends Screen {
    private record NumberSetting(String key, EditBox field, IntConsumer apply) {}
    private final Screen parent;
    private final List<NumberSetting> numbers = new ArrayList<>();
    private int page;
    private Component error = Component.empty();

    public SettingsScreen(Screen parent) {
        super(Component.translatable("client_stacker.settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        numbers.clear();
        var config = ClientStacker.config();
        int x = width / 2 - 150;
        int y = Math.max(32, height / 2 - 95);
        if (page == 0) {
            toggle(x, y, "enabled", () -> config.enabled, value -> config.enabled = value);
            toggle(x + 154, y, "server", () -> !config.disabledServers.contains(ClientStacker.serverKey()), value -> {
                if (value) config.disabledServers.remove(ClientStacker.serverKey());
                else config.disabledServers.add(ClientStacker.serverKey());
            });
            toggle(x, y + 24, "emeralds", () -> config.emeraldTrading, value -> config.emeraldTrading = value);
            toggle(x + 154, y + 24, "balance", () -> config.showBalance, value -> config.showBalance = value);
            toggle(x, y + 48, "auto_compact", () -> config.compactOnTableOpen, value -> config.compactOnTableOpen = value);
            toggle(x + 154, y + 48, "netherite", () -> config.allowNetherite, value -> config.allowNetherite = value);
            number(x, y + 84, "reserve", config.looseReserve, value -> config.looseReserve = value);
            number(x + 154, y + 84, "retain", config.retainedValue(Materials.EMERALDS), value -> {
                config.retainedEmeralds = value;
                config.retainedValues.remove(Materials.EMERALDS.loose());
            });
            number(x, y + 120, "spending", config.spendingCap, value -> config.spendingCap = value);
            number(x + 154, y + 120, "target", config.outputTarget, value -> config.outputTarget = value);
            number(x, y + 156, "timeout", config.timeoutSeconds, value -> config.timeoutSeconds = value);
            addRenderableWidget(Button.builder(Component.translatable("client_stacker.materials"), button -> {
                if (applyNumbers()) { page = 1; rebuildWidgets(); }
            }).bounds(x + 154, y + 156, 146, 20).build());
        } else {
            int i = 0;
            for (var material : Materials.ALL) {
                if (material.equals(Materials.EMERALDS)) continue;
                String name = Materials.item(material.loose()).getDescriptionId();
                int row = i / 2;
                int column = i++ % 2;
                BooleanSupplier selected = () -> config.sellingMaterials.contains(material.loose());
                var button = Button.builder(label(Component.translatable(name), selected.getAsBoolean()), b -> {
                    if (!config.sellingMaterials.remove(material.loose())) config.sellingMaterials.add(material.loose());
                    b.setMessage(label(Component.translatable(name), selected.getAsBoolean()));
                }).bounds(x + column * 154, y + row * 22, 146, 20).build();
                button.setTooltip(Tooltip.create(Component.translatable("client_stacker.materials.help")));
                addRenderableWidget(button);
            }
        }
        addRenderableWidget(Button.builder(page == 0 ? CommonComponents.GUI_DONE : CommonComponents.GUI_BACK, button -> {
            if (page == 1) { page = 0; rebuildWidgets(); }
            else onClose();
        }).bounds(width / 2 - 50, Math.min(height - 24, y + 184), 100, 20).build());
    }

    private void toggle(int x, int y, String key, BooleanSupplier get, Consumer<Boolean> set) {
        Component title = Component.translatable("client_stacker.setting." + key);
        var button = Button.builder(label(title, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(label(title, get.getAsBoolean()));
        }).bounds(x, y, 146, 20).build();
        button.setTooltip(Tooltip.create(Component.translatable("client_stacker.setting." + key + ".help")));
        button.active = ClientStacker.config().readable();
        addRenderableWidget(button);
    }

    private Component label(Component title, boolean value) {
        return Component.translatable("options.generic_value", title, value ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }

    private void number(int x, int y, String key, int value, IntConsumer apply) {
        var field = new EditBox(font, x, y, 146, 18, Component.translatable("client_stacker.setting." + key));
        field.setMaxLength(9);
        field.setValue(Integer.toString(value));
        field.setTooltip(Tooltip.create(Component.translatable("client_stacker.setting." + key + ".help")));
        field.setEditable(ClientStacker.config().readable());
        addRenderableWidget(field);
        numbers.add(new NumberSetting(key, field, apply));
    }

    private boolean applyNumbers() {
        for (var number : numbers) {
            if (!number.field.getValue().matches("[0-9]+")) { error = Component.translatable("client_stacker.settings.invalid"); return false; }
            int value = Integer.parseInt(number.field.getValue());
            if (number.key.equals("timeout") && (value < 5 || value > 15)) {
                error = Component.translatable("client_stacker.settings.invalid");
                return false;
            }
        }
        for (var number : numbers) number.apply.accept(Integer.parseInt(number.field.getValue()));
        return true;
    }

    @Override
    public void onClose() {
        if (!applyNumbers()) return;
        if (!ClientStacker.config().save()) {
            error = Component.translatable("client_stacker.message.save");
            if (ClientStacker.config().readable()) return;
        }
        ClientStacker.showScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(font, page == 0 ? title : Component.translatable("client_stacker.materials"), width / 2, 6, 0xFFFFFFFF);
        graphics.drawCenteredString(font, VtpCompat.status(), width / 2, 18, 0xFFBBBBBB);
        for (var number : numbers) {
            graphics.drawString(font, Component.translatable("client_stacker.setting." + number.key),
                number.field.getX(), number.field.getY() - 10, 0xFFE0E0E0);
        }
        if (!error.getString().isEmpty()) graphics.drawCenteredString(font, error, width / 2, height - 36, 0xFFFF7777);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
