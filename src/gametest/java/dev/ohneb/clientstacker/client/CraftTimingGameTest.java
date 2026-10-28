package dev.ohneb.clientstacker.client;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class CraftTimingGameTest implements FabricClientGameTest {
    public static volatile boolean enabled;
    public static volatile boolean afterOutput;
    public static volatile boolean holding;
    public static final List<Packet<ServerGamePacketListener>> held = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            for (boolean output : new boolean[]{false, true}) {
                server.runOnServer(instance -> {
                    var player = instance.getPlayerList().getPlayers().getFirst();
                    player.setGameMode(GameType.SURVIVAL);
                    player.getInventory().clearContent();
                    player.getInventory().setItem(0, new ItemStack(Items.EMERALD_BLOCK, 3));
                    player.inventoryMenu.broadcastFullState();
                });
                context.waitFor(client -> !client.player.isCreative()
                    && client.player.getInventory().countItem(Items.EMERALD_BLOCK) == 3);
                afterOutput = output;
                enabled = true;
                context.runOnClient(client -> {
                    var config = ClientStacker.config();
                    config.protectedSlots.clear();
                    config.blockReserves.clear();
                    config.blockReserves.put(Materials.EMERALDS.compact(), 1);
                    client.setScreen(new InventoryScreen(client.player));
                    ClientStacker.convert(client.player.inventoryMenu, Materials.EMERALDS, false, 18);
                });
                context.waitFor(client -> holding && !client.player.inventoryMenu.getCarried().isEmpty());
                context.waitTicks(3);
                context.runOnClient(client -> {
                    if (!ClientStacker.busy()) throw new AssertionError("An intermediate "
                        + (output ? "collection" : "preparation") + " snapshot stopped the craft");
                });
                server.runOnServer(instance -> release(instance.getPlayerList().getPlayers().getFirst().connection));
                context.waitFor(client -> !ClientStacker.busy());
                server.runOnServer(instance -> {
                    var player = instance.getPlayerList().getPlayers().getFirst();
                    if (player.getInventory().countItem(Items.EMERALD_BLOCK) != 1
                        || player.getInventory().countItem(Items.EMERALD) != 18
                        || !player.inventoryMenu.getCarried().isEmpty()
                        || !player.inventoryMenu.getSlot(1).getItem().isEmpty()
                        || !player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8)).isEmpty()) {
                        throw new AssertionError("The settled craft did not preserve its items and reserve");
                    }
                });
            }
            context.runOnClient(client -> ClientStacker.config().blockReserves.clear());
        } finally {
            enabled = holding = false;
            held.clear();
        }
    }

    public static void release(ServerGamePacketListenerImpl listener) {
        holding = false;
        for (var packet : List.copyOf(held)) packet.handle(listener);
        held.clear();
    }
}
