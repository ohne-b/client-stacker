package dev.ohneb.clientstacker.client;

import com.mojang.brigadier.suggestion.Suggestions;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class CheckpointCorrectionGameTest implements FabricClientGameTest {
    private static volatile boolean replaceNext;
    private static volatile boolean replaced;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runOnServer(instance -> {
                var player = instance.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL);
                player.getInventory().clearContent();
                player.getInventory().setItem(0, new ItemStack(Items.EMERALD_BLOCK, 2));
                player.inventoryMenu.broadcastFullState();
            });
            context.waitFor(client -> !client.player.isCreative()
                && client.player.getInventory().countItem(Items.EMERALD_BLOCK) == 2);
            replaced = false;
            replaceNext = true;
            context.runOnClient(client -> {
                ClientStacker.config().protectedSlots.clear();
                ClientStacker.config().blockReserves.clear();
                client.setScreen(new InventoryScreen(client.player));
                ClientStacker.convert(client.player.inventoryMenu, Materials.EMERALDS, false, 9);
            });
            context.waitFor(client -> !ClientStacker.busy());
            if (!replaced) throw new AssertionError("The post-confirmation correction was not delivered");
            context.runOnClient(client -> {
                if (!(client.screen instanceof InventoryScreen)) throw new AssertionError("Correction closed the inventory");
            });
            server.runOnServer(instance -> {
                var player = instance.getPlayerList().getPlayers().getFirst();
                ItemStack input = player.inventoryMenu.getSlot(1).getItem();
                if (!input.is(Items.EMERALD_BLOCK) || input.getCount() != 1
                    || !Component.literal("Keep this block").equals(input.get(DataComponents.CUSTOM_NAME))
                    || player.getInventory().countItem(Items.EMERALD_BLOCK) != 1
                    || player.getInventory().countItem(Items.EMERALD) != 0
                    || !player.inventoryMenu.getCarried().isEmpty()
                    || !player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8)).isEmpty()) {
                    throw new AssertionError("A post-confirmation correction spent or dropped the named ingredient");
                }
            });
        } finally {
            replaceNext = false;
        }
    }

    public static boolean replaceAfterBarrier(ServerPlayer player, int id) {
        if (!replaceNext || id >= 0 || !player.level().getServer().isSameThread()
            || player.containerMenu != player.inventoryMenu || !player.inventoryMenu.getSlot(1).getItem().is(Items.EMERALD_BLOCK)) return false;
        replaceNext = false;
        var menu = player.inventoryMenu;
        var named = menu.getSlot(1).getItem().copy();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Keep this block"));
        menu.getSlot(1).set(named);
        var reply = new ClientboundCommandSuggestionsPacket(id, Suggestions.empty().join());
        var correction = new ClientboundContainerSetContentPacket(menu.containerId, menu.incrementStateId(),
            menu.slots.stream().map(slot -> slot.getItem().copy()).toList(), menu.getCarried().copy());
        // The bundle applies the reply and correction in order, without a client tick between them.
        player.connection.send(new ClientboundBundlePacket(List.of(reply, correction)));
        replaced = true;
        return true;
    }
}
