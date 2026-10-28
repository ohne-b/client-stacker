package dev.ohneb.clientstacker.client;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

@SuppressWarnings("UnstableApiUsage")
public final class InventoryFailureGameTest implements FabricClientGameTest {
    public static volatile int recipePackets;
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            reset(context, server, false, false);
            recipePackets = 0;
            context.runOnClient(client -> {
                start(context);
                client.gameMode.handlePlaceRecipe(0, new RecipeDisplayId(0), false);
                ClientStacker.tick();
                if (client.player.inventoryMenu.getSlot(1).getItem().isEmpty()) throw new AssertionError("No input to cancel");
                client.gameMode.handlePlaceRecipe(0, new RecipeDisplayId(0), true);
                ClientStacker.cancel();
            });
            context.waitTicks(4);
            assertUntouched(server, false);
            if (recipePackets != 0) throw new AssertionError("Recipe-book placement bypassed inventory ownership");

            reset(context, server, false, false);
            context.runOnClient(client -> {
                start(context);
                ClientStacker.showScreen(null);
                ((dev.ohneb.clientstacker.testmixin.KeyboardInvoker) client.keyboardHandler).press(client.getWindow().handle(), com.mojang.blaze3d.platform.InputConstants.PRESS,
                    new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE, 0, 0));
                if (ClientStacker.busy()) throw new AssertionError("Escape with no container screen did not cancel");
            });
            context.waitTicks(3);
            assertUntouched(server, false);

            reset(context, server, true, false);
            context.runOnClient(client -> start(context));
            context.waitFor(client -> !ClientStacker.busy());
            assertUntouched(server, false);

            reset(context, server, false, true);
            context.runOnClient(client -> {
                start(context);
                if (ClientStacker.busy()) throw new AssertionError("Custom item was accepted");
            });
            assertUntouched(server, true);

            reset(context, server, false, false);
            context.runOnClient(client -> {
                start(context);
                ClientStacker.tick();
                client.player.closeContainer();
            });
            context.waitTicks(4);
            context.runOnClient(client -> {
                if (ClientStacker.busy()) throw new AssertionError("Operation survived a closed screen");
            });
            assertUntouched(server, false);

            reset(context, server, false, false);
            var pending = new CraftOperation[1];
            context.runOnClient(client -> {
                pending[0] = new CraftOperation(client.player.inventoryMenu, Materials.EMERALDS, false, 1);
                pending[0].tick();
            });
            // A server-side inventory correction while the recipe is pending must stop the craft.
            context.waitTicks(2);
            server.runOnServer(instance -> {
                var player = instance.getPlayerList().getPlayers().getFirst();
                player.getInventory().setItem(10, new ItemStack(Items.DIRT));
                player.inventoryMenu.broadcastFullState();
            });
            context.waitFor(client -> client.player.getInventory().getItem(10).is(Items.DIRT));
            context.runOnClient(client -> {
                try {
                    pending[0].tick();
                    throw new AssertionError("Correction did not stop the craft");
                } catch (Operation.Stopped stopped) {
                    if (!stopped.reason.equals("changed")) throw stopped;
                }
            });
            server.runOnServer(instance -> {
                var player = instance.getPlayerList().getPlayers().getFirst();
                if (player.getInventory().countItem(Items.EMERALD) != 0
                    || player.inventoryMenu.getSlot(1).getItem().getCount() != 1) {
                    throw new AssertionError("Corrected craft was collected");
                }
            });
        }
    }

    private static void reset(ClientGameTestContext context, TestServerContext server, boolean full, boolean named) {
        context.runOnClient(client -> {
            ClientStacker.cancel();
            client.player.closeContainer();
            ClientStacker.config().protectedSlots.clear();
        });
        context.waitTicks(3);
        server.runOnServer(instance -> {
            var player = instance.getPlayerList().getPlayers().getFirst();
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            if (full) for (int i = 1; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            var blocks = new ItemStack(Items.EMERALD_BLOCK, 2);
            if (named) blocks.set(DataComponents.CUSTOM_NAME, Component.literal("Keep me"));
            player.getInventory().setItem(0, blocks);
            player.inventoryMenu.broadcastFullState();
        });
        context.waitTicks(3);
        context.setScreen(() -> new InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
    }

    private static void start(ClientGameTestContext context) {
        var client = net.minecraft.client.Minecraft.getInstance();
        var screen = (AbstractContainerScreen<?>) client.screen;
        var slot = MenuInventory.playerSlots(screen.getMenu()).stream()
            .filter(candidate -> candidate.getContainerSlot() == 0).findFirst().orElseThrow();
        ClientStacker.convert(screen, slot, false);
    }

    private static void assertUntouched(TestServerContext server, boolean named) {
        server.runOnServer(instance -> {
            var player = instance.getPlayerList().getPlayers().getFirst();
            if (player.getInventory().countItem(Items.EMERALD_BLOCK) != 2 || player.getInventory().countItem(Items.EMERALD) != 0
                || !player.inventoryMenu.getCarried().isEmpty() || !player.inventoryMenu.getSlot(1).getItem().isEmpty()) {
                throw new AssertionError("Stopped conversion changed the inventory");
            }
            if (named && !player.getInventory().getItem(0).has(DataComponents.CUSTOM_NAME)) {
                throw new AssertionError("Item name lost");
            }
        });
    }
}
