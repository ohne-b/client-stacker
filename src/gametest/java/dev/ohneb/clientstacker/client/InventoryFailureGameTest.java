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
            GameTestPackets.settle(context);
            assertUnspent(server, false, 1);
            if (recipePackets != 0) throw new AssertionError("Recipe-book placement bypassed inventory ownership");

            reset(context, server, false, false);
            context.runOnClient(client -> {
                start(context);
                ClientStacker.showScreen(null);
                ((dev.ohneb.clientstacker.testmixin.KeyboardInvoker) client.keyboardHandler).press(client.getWindow().handle(), com.mojang.blaze3d.platform.InputConstants.PRESS,
                    new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE, 0, 0));
                if (ClientStacker.busy()) throw new AssertionError("Escape with no container screen did not cancel");
            });
            GameTestPackets.settle(context);
            assertUnspent(server, false, 0);

            reset(context, server, true, false);
            context.runOnClient(client -> start(context));
            context.waitFor(client -> !ClientStacker.busy());
            assertUnspent(server, false, 0);

            reset(context, server, false, true);
            context.runOnClient(client -> {
                start(context);
                if (ClientStacker.busy()) throw new AssertionError("Custom item was accepted");
            });
            assertUnspent(server, true, 0);

            reset(context, server, false, false);
            context.runOnClient(client -> {
                start(context);
                ClientStacker.tick();
                client.player.closeContainer();
            });
            GameTestPackets.settle(context);
            context.runOnClient(client -> {
                if (ClientStacker.busy()) throw new AssertionError("Operation survived a closed screen");
            });
            assertUnspent(server, false, 0);

            reset(context, server, false, false);
            CraftTimingGameTest.afterOutput = false;
            CraftTimingGameTest.enabled = true;
            context.runOnClient(client -> start(context));
            // A server-side inventory correction while the recipe is pending must stop the craft.
            context.waitFor(client -> CraftTimingGameTest.holding && !client.player.inventoryMenu.getCarried().isEmpty());
            server.runOnServer(instance -> {
                var player = instance.getPlayerList().getPlayers().getFirst();
                player.getInventory().setItem(10, new ItemStack(Items.DIRT));
                CraftTimingGameTest.release(player.connection);
            });
            context.waitFor(client -> !ClientStacker.busy());
            context.runOnClient(client -> {
                if (!(client.screen instanceof InventoryScreen) || !client.player.getInventory().getItem(10).is(Items.DIRT)) {
                    throw new AssertionError("Correction was not received or closed the menu");
                }
            });
            assertUnspent(server, false, 1);
        }
    }

    private static void reset(ClientGameTestContext context, TestServerContext server, boolean full, boolean named) {
        context.runOnClient(client -> {
            ClientStacker.cancel();
            client.player.closeContainer();
            ClientStacker.config().protectedSlots.clear();
        });
        GameTestPackets.settle(context);
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
        GameTestPackets.settle(context);
        context.setScreen(() -> new InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
    }

    private static void start(ClientGameTestContext context) {
        var client = net.minecraft.client.Minecraft.getInstance();
        var screen = (AbstractContainerScreen<?>) client.screen;
        var slot = MenuInventory.playerSlots(screen.getMenu()).stream()
            .filter(candidate -> candidate.getContainerSlot() == 0).findFirst().orElseThrow();
        ClientStacker.convert(screen, slot, false);
    }

    private static void assertUnspent(TestServerContext server, boolean named, int input) {
        server.runOnServer(instance -> {
            var player = instance.getPlayerList().getPlayers().getFirst();
            if (player.getInventory().countItem(Items.EMERALD_BLOCK) != 2 - input || player.getInventory().countItem(Items.EMERALD) != 0
                || !player.inventoryMenu.getCarried().isEmpty() || player.inventoryMenu.getSlot(1).getItem().getCount() != input
                || !player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(8)).isEmpty()) {
                throw new AssertionError("Stopped conversion spent or dropped items");
            }
            if (named && !player.getInventory().getItem(0).has(DataComponents.CUSTOM_NAME)) {
                throw new AssertionError("Item name lost");
            }
        });
    }
}
