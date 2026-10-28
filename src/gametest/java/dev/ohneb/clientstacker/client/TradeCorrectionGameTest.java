package dev.ohneb.clientstacker.client;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("UnstableApiUsage")
public final class TradeCorrectionGameTest implements FabricClientGameTest {
    public static volatile boolean interfere;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var merchant = new AtomicInteger();
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL);
                player.getInventory().clearContent();
                for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
                player.getInventory().setItem(9, new ItemStack(Items.EMERALD, 7));
                player.inventoryMenu.broadcastFullState();
                var villager = EntityType.VILLAGER.create(player.level(), EntitySpawnReason.COMMAND);
                if (villager == null) throw new AssertionError("No villager");
                villager.setPos(player.getX(), player.getY(), player.getZ() + 2);
                villager.setNoAi(true);
                var offers = new MerchantOffers();
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 7), Optional.empty(), new ItemStack(Items.BREAD), 12, 1, 0));
                villager.setOffers(offers);
                player.level().addFreshEntity(villager);
                merchant.set(villager.getId());
            });
            context.waitFor(client -> !client.player.isCreative() && client.level.getEntity(merchant.get()) != null);
            context.runOnClient(client -> {
                ClientStacker.config().protectedSlots.clear();
                ClientStacker.config().spendingCap = 14;
                client.gameMode.interact(client.player, client.level.getEntity(merchant.get()), InteractionHand.MAIN_HAND);
            });
            context.waitForScreen(MerchantScreen.class);
            context.waitFor(client -> !((MerchantScreen) client.screen).getMenu().getOffers().isEmpty());
            context.runOnClient(client -> {
                interfere = true;
                ClientStacker.trade(((MerchantScreen) client.screen).getMenu(), 0, true);
                if (!ClientStacker.busy()) throw new AssertionError("Trade did not start");
            });
            context.waitFor(client -> !ClientStacker.busy());
            context.waitTicks(3);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                if (interfere || !(player.containerMenu instanceof MerchantMenu menu)) throw new AssertionError("Menu closed before correction");
                if (!menu.getCarried().is(Items.DIRT) || menu.getCarried().getCount() != 64
                    || player.getInventory().countItem(Items.BREAD) != 1 || menu.getOffers().getFirst().getUses() != 1) {
                    throw new AssertionError("Corrected purchase was replayed or its cursor was lost");
                }
                if (!player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(10)).isEmpty()) {
                    throw new AssertionError("An inventory item was dropped");
                }
                // Settle this synthetic fixture before the test framework closes its world.
                player.getInventory().setItem(10, menu.getCarried());
                menu.setCarried(ItemStack.EMPTY);
                menu.broadcastFullState();
            });
        } finally {
            interfere = false;
        }
    }
}
