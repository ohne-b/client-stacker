package dev.ohneb.clientstacker.client;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.nio.file.Files;
import java.nio.file.Path;

@SuppressWarnings("UnstableApiUsage")
public final class SettingsGameTest implements FabricClientGameTest {
    private static final net.minecraft.client.input.KeyEvent ENTER = new net.minecraft.client.input.KeyEvent(
        com.mojang.blaze3d.platform.InputConstants.KEY_RETURN, 0, 0);
    @Override
    public void runTest(ClientGameTestContext context) {
        testConfig();
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL);
                player.getInventory().clearContent();
                player.getInventory().setItem(0, new ItemStack(Items.EMERALD_BLOCK, 3));
                player.inventoryMenu.broadcastFullState();
            });
            context.waitFor(client -> !client.player.isCreative() && client.player.getInventory().countItem(Items.EMERALD_BLOCK) == 3);
            context.runOnClient(client -> {
                ClientStacker.config().protectedSlots.clear();
                ClientStacker.config().blockReserves.clear();
                client.setScreen(new InventoryScreen(client.player));
            });
            click(context, "client_stacker.conversions");
            context.waitForScreen(ConversionsScreen.class);
            context.waitTicks(100);
            screenshot(context, "conversions");
            click(context, "client_stacker.protected_slots");
            context.waitForScreen(ProtectionScreen.class);
            context.runOnClient(client -> {
                Button slot = (Button) client.screen.children().getFirst();
                slot.onPress(ENTER);
                if (ClientStacker.config().protectedSlots.size() != 1) throw new AssertionError("Slot protection was not toggled");
            });
            screenshot(context, "protected-slots");
            context.runOnClient(client -> ((Button) client.screen.children().getFirst()).onPress(ENTER));
            click(context, "gui.done");
            click(context, "client_stacker.material_settings");
            context.waitForScreen(MaterialSettingsScreen.class);
            context.runOnClient(client -> {
                var fields = client.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
                fields.get(0).setValue("5");
                fields.get(1).setValue("1");
                fields.get(2).setValue("9");
            });
            screenshot(context, "material-settings");
            click(context, "gui.done");
            context.runOnClient(client -> {
                var config = ClientStacker.config();
                if (config.looseReserve(Materials.EMERALDS) != 5 || config.blockReserve(Materials.EMERALDS) != 1
                    || config.retainedValue(Materials.EMERALDS) != 9) throw new AssertionError("Material settings were not saved");
                var quantity = client.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                quantity.setValue("18");
            });
            click(context, "client_stacker.prepare");
            context.waitFor(client -> !ClientStacker.busy());
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                if (player.getInventory().countItem(Items.EMERALD) != 18 || player.getInventory().countItem(Items.EMERALD_BLOCK) != 1) {
                    throw new AssertionError("Prepare quantity or block reserve failed");
                }
            });
            context.runOnClient(client -> {
                ClientStacker.config().looseReserves.clear();
                ClientStacker.config().blockReserves.clear();
                ClientStacker.config().retainedValues.clear();
                ClientStacker.config().save();
            });
        }
    }

    private static void click(ClientGameTestContext context, String translation) {
        context.runOnClient(client -> client.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
            .filter(button -> button.getMessage().getString().equals(Component.translatable(translation).getString()))
            .findFirst().orElseThrow(() -> new AssertionError("Missing control: " + translation)).onPress(ENTER));
    }

    private static void screenshot(ClientGameTestContext context, String name) {
        context.waitTicks(2);
        context.takeScreenshot(TestScreenshotOptions.of(name).withDestinationDir(Path.of(System.getProperty("clientstacker.test.artifacts"))));
    }

    private static void testConfig() {
        try {
            Path directory = Path.of(System.getProperty("clientstacker.test.artifacts"), "config-tests");
            Files.createDirectories(directory);
            Path path = Files.createTempDirectory(directory, "settings-").resolve("client-stacker.json");
            var config = StackerConfig.load(path);
            config.blockReserves.put(Materials.EMERALDS.compact(), 2);
            config.retainedValues.put(Materials.EMERALDS.loose(), 17);
            if (!config.save()) throw new AssertionError("Valid settings were not saved");
            var loaded = StackerConfig.load(path);
            if (loaded.blockReserve(Materials.EMERALDS) != 2 || loaded.tradeLimits().retainedValue() != 17) {
                throw new AssertionError("Settings did not round-trip");
            }
            for (String invalid : new String[]{"{", "{\"formatVersion\":2}", "{\"protectedSlots\":[36]}",
                "{\"blockReserves\":{\"minecraft:emerald_block\":-1}}", "{\"retainedValues\":null}"}) {
                Files.writeString(path, invalid);
                var rejected = StackerConfig.load(path);
                if (rejected.readable() || rejected.enabled || rejected.save() || !Files.readString(path).equals(invalid)) {
                    throw new AssertionError("Invalid settings were accepted or overwritten");
                }
            }
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }
}
