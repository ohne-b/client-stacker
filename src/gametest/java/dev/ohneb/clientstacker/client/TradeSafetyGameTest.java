package dev.ohneb.clientstacker.client;

import dev.ohneb.clientstacker.testmixin.MerchantInvoker;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("UnstableApiUsage")
public final class TradeSafetyGameTest implements FabricClientGameTest {
    public static volatile boolean delayReopening;
    public static volatile int delayedMerchant = -1;
    public static volatile boolean rejectPurchase;
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            open(context, server, Items.EMERALD, 7, Items.EMERALD, 2,
                new ItemStack(Items.EMERALD_BLOCK, 2));
            trade(context, false);
            assertResult(server, 1, Items.EMERALD_BLOCK, 1, Items.EMERALD, 0);

            open(context, server, Items.IRON_INGOT, 7, Items.EMERALD, 2,
                new ItemStack(Items.IRON_BLOCK), new ItemStack(Items.EMERALD_BLOCK));
            context.runOnClient(client -> ClientStacker.config().sellingMaterials.add("minecraft:iron_ingot"));
            trade(context, false);
            assertResult(server, 1, Items.IRON_INGOT, 2, Items.EMERALD, 7);

            open(context, server, Items.IRON_INGOT, 7, null, 0, new ItemStack(Items.IRON_BLOCK));
            context.runOnClient(client -> {
                ClientStacker.config().sellingMaterials.add("minecraft:iron_ingot");
                ClientStacker.config().retainedValues.put("minecraft:iron_ingot", 3);
            });
            trade(context, false);
            assertResult(server, 0, Items.IRON_BLOCK, 1, Items.IRON_INGOT, 0);

            open(context, server, Items.IRON_BLOCK, 1, null, 0, new ItemStack(Items.IRON_BLOCK));
            context.runOnClient(client -> ClientStacker.config().retainedValues.put("minecraft:iron_ingot", 9));
            trade(context, false);
            assertResult(server, 0, Items.IRON_BLOCK, 1, Items.IRON_INGOT, 0);

            verifyMetalReserves(context, server);

            int namedMerchant = open(context, server, Items.EMERALD, 7, null, 0, new ItemStack(Items.EMERALD_BLOCK, 2));
            server.runOnServer(instance -> {
                var player = instance.getPlayerList().getPlayers().getFirst();
                var tag = new ItemStack(Items.NAME_TAG);
                tag.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Keep this tag"));
                player.getInventory().setItem(0, tag);
                player.getInventory().setItem(1, new ItemStack(Items.EMERALD_BLOCK, 2));
                player.containerMenu.broadcastFullState();
            });
            context.waitFor(client -> client.player.getMainHandItem().is(Items.NAME_TAG));
            context.runOnClient(client -> ClientStacker.config().protectedSlots.add(0));
            trade(context, false);
            assertResult(server, 1, Items.EMERALD_BLOCK, 1, Items.EMERALD, 2);
            server.runOnServer(instance -> {
                var player = instance.getPlayerList().getPlayers().getFirst();
                if (player.getInventory().getItem(0).getCount() != 1 || !player.getInventory().getItem(0).is(Items.NAME_TAG)
                    || player.level().getEntity(namedMerchant).hasCustomName()) throw new AssertionError("Reopening consumed the protected name tag");
            });

            open(context, server, Items.EMERALD, 7, null, 0, new ItemStack(Items.EMERALD, 7));
            context.runOnClient(client -> ClientStacker.config().protectedSlots.add(0));
            context.getInput().holdControl();
            context.runOnClient(client -> ((MerchantInvoker) client.screen).selectOffer());
            context.getInput().releaseControl();
            context.waitFor(client -> client.player.containerMenu.getSlot(0).getItem().is(Items.EMERALD));
            context.waitTicks(4);
            context.runOnClient(client -> {
                ClientStacker.trade((MerchantMenu) client.player.containerMenu, 0, false);
                if (ClientStacker.busy()) throw new AssertionError("Protected input with unknown provenance was accepted");
            });
            server.runOnServer(instance -> {
                var menu = (MerchantMenu) instance.getPlayerList().getPlayers().getFirst().containerMenu;
                if (menu.getOffers().getFirst().getUses() != 0 || menu.getSlot(0).getItem().getCount() != 7) {
                    throw new AssertionError("Protected payment changed: uses " + menu.getOffers().getFirst().getUses()
                        + ", input " + menu.getSlot(0).getItem());
                }
            });

            open(context, server, Items.EMERALD, 7, null, 0, new ItemStack(Items.EMERALD, 7));
            rejectPurchase = true;
            trade(context, true);
            context.waitTicks(3);
            server.runOnServer(instance -> {
                var player = instance.getPlayerList().getPlayers().getFirst();
                if (rejectPurchase || !(player.containerMenu instanceof MerchantMenu menu)
                    || menu.getOffers().getFirst().getUses() != 0 || player.getInventory().countItem(Items.BREAD) != 0
                    || menu.getSlot(0).getItem().getCount() != 7) throw new AssertionError("Rejected purchase was replayed or closed");
            });

            open(context, server, Items.EMERALD, 7, null, 0, new ItemStack(Items.EMERALD_BLOCK, 2));
            delayReopening = true;
            context.runOnClient(client -> ClientStacker.trade((MerchantMenu) client.player.containerMenu, 0, true));
            context.waitFor(client -> delayedMerchant >= 0 && client.screen == null && ClientStacker.busy());
            context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
            context.runOnClient(client -> {
                if (ClientStacker.busy()) throw new AssertionError("Escape did not cancel while reopening");
            });
            server.runOnServer(instance -> {
                var player = instance.getPlayerList().getPlayers().getFirst();
                var villager = (net.minecraft.world.entity.npc.villager.Villager) player.level().getEntity(delayedMerchant);
                villager.mobInteract(player, InteractionHand.MAIN_HAND);
            });
            context.waitForScreen(MerchantScreen.class);
            context.waitTicks(8);
            assertResult(server, 0, Items.EMERALD_BLOCK, 1, Items.EMERALD, 9);

            open(context, server, Items.EMERALD, 7, null, 0, new ItemStack(Items.EMERALD_BLOCK, 2));
            var secondMerchant = new AtomicInteger();
            server.runOnServer(instance -> {
                var player = instance.getPlayerList().getPlayers().getFirst();
                var villager = EntityType.VILLAGER.create(player.level(), EntitySpawnReason.COMMAND);
                villager.setPos(player.getX() + 1, player.getY(), player.getZ() + 2);
                villager.setNoAi(true);
                var offers = new MerchantOffers();
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 7), Optional.empty(), new ItemStack(Items.BREAD), 12, 1, 0));
                villager.setOffers(offers);
                player.level().addFreshEntity(villager);
                secondMerchant.set(villager.getId());
            });
            delayedMerchant = -1;
            delayReopening = true;
            context.runOnClient(client -> ClientStacker.trade((MerchantMenu) client.player.containerMenu, 0, true));
            context.waitFor(client -> delayedMerchant >= 0 && client.screen == null && client.level.getEntity(secondMerchant.get()) != null);
            context.runOnClient(client -> {
                client.gameMode.interact(client.player, client.level.getEntity(secondMerchant.get()), InteractionHand.MAIN_HAND);
                if (ClientStacker.busy()) throw new AssertionError("A second merchant interaction did not cancel the request");
            });
            context.waitForScreen(MerchantScreen.class);
            context.waitTicks(8);
            assertResult(server, 0, Items.EMERALD_BLOCK, 1, Items.EMERALD, 9);

            verifyOverlappingInteractions(context, server);
            if (VtpCompat.installed()) verifyVtp(context, server);
        } finally {
            delayReopening = false;
            delayedMerchant = -1;
            rejectPurchase = false;
        }
    }

    private static void verifyMetalReserves(ClientGameTestContext context, TestServerContext server) {
        for (Item[] metal : new Item[][]{{Items.IRON_BLOCK, Items.IRON_INGOT, Items.IRON_NUGGET},
            {Items.GOLD_BLOCK, Items.GOLD_INGOT, Items.GOLD_NUGGET}}) {
            Item block = metal[0], ingot = metal[1], nugget = metal[2];
            for (int ingots : new int[]{9, 10}) {
                open(context, server, nugget, 9, null, 0, new ItemStack(ingot, ingots));
                context.runOnClient(client -> {
                    ClientStacker.config().sellingMaterials.add(Materials.id(new ItemStack(nugget)));
                    ClientStacker.config().retainedValues.put(Materials.id(new ItemStack(ingot)), 9);
                });
                trade(context, false);
                assertResult(server, ingots - 9, ingot, 9, nugget, 0);
            }
            for (int price : new int[]{1, 2}) {
                open(context, server, nugget, price, null, 0, new ItemStack(ingot, 8), new ItemStack(nugget, 10));
                context.runOnClient(client -> ClientStacker.config().retainedValues.put(Materials.id(new ItemStack(ingot)), 9));
                trade(context, false);
                assertResult(server, price == 1 ? 1 : 0, ingot, 8, nugget, price == 1 ? 9 : 10);
            }
            open(context, server, block, 1, null, 0, new ItemStack(block));
            context.runOnClient(client -> ClientStacker.config().retainedValues.put(Materials.id(new ItemStack(nugget)), 9));
            trade(context, false);
            assertResult(server, 0, block, 1, nugget, 0);
        }
    }

    private static void verifyOverlappingInteractions(ClientGameTestContext context, TestServerContext server) {
        int first = open(context, server, Items.EMERALD, 7, null, 0, new ItemStack(Items.EMERALD_BLOCK, 2));
        context.runOnClient(client -> client.player.closeContainer());
        context.waitTicks(3);
        var second = new AtomicInteger();
        server.runOnServer(instance -> {
            var player = instance.getPlayerList().getPlayers().getFirst();
            var villager = EntityType.VILLAGER.create(player.level(), EntitySpawnReason.COMMAND);
            villager.setPos(player.getX() + 1, player.getY(), player.getZ() + 2);
            villager.setNoAi(true);
            var offers = new MerchantOffers();
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 7), Optional.empty(), new ItemStack(Items.BREAD), 12, 1, 0));
            villager.setOffers(offers);
            villager.setTradingPlayer(player);
            player.level().addFreshEntity(villager);
            second.set(villager.getId());
        });
        context.waitFor(client -> client.level.getEntity(second.get()) != null);
        context.runOnClient(client -> {
            client.gameMode.interact(client.player, client.level.getEntity(first), InteractionHand.MAIN_HAND);
            client.gameMode.interact(client.player, client.level.getEntity(second.get()), InteractionHand.MAIN_HAND);
        });
        context.waitForScreen(MerchantScreen.class);
        context.waitFor(client -> !((MerchantMenu) client.player.containerMenu).getOffers().isEmpty());
        context.waitTicks(3);
        server.runOnServer(instance -> {
            var player = instance.getPlayerList().getPlayers().getFirst();
            ((net.minecraft.world.entity.npc.villager.Villager) player.level().getEntity(second.get())).setTradingPlayer(null);
        });
        context.runOnClient(client -> {
            ClientStacker.trade((MerchantMenu) client.player.containerMenu, 0, true);
            if (ClientStacker.busy()) throw new AssertionError("An ambiguous merchant reply was accepted");
        });
        context.waitTicks(3);
        assertResult(server, 0, Items.EMERALD_BLOCK, 2, Items.EMERALD, 0);

        context.runOnClient(client -> client.player.closeContainer());
        context.waitTicks(3);
        context.runOnClient(client -> client.gameMode.interact(client.player, client.level.getEntity(first), InteractionHand.MAIN_HAND));
        context.waitForScreen(MerchantScreen.class);
        context.waitFor(client -> !((MerchantMenu) client.player.containerMenu).getOffers().isEmpty());
        trade(context, false);
        assertResult(server, 1, Items.EMERALD_BLOCK, 1, Items.EMERALD, 2);
        server.runOnServer(instance -> {
            var player = instance.getPlayerList().getPlayers().getFirst();
            var villager = (net.minecraft.world.entity.npc.villager.Villager) player.level().getEntity(second.get());
            if (villager.getOffers().getFirst().getUses() != 0) throw new AssertionError("The wrong merchant was used");
        });
    }

    private static void verifyVtp(ClientGameTestContext context, TestServerContext server) {
        open(context, server, Items.EMERALD, 7, Items.EMERALD, 2,
            new ItemStack(Items.EMERALD, 7), new ItemStack(Items.EMERALD_BLOCK));
        context.runOnClient(client -> {
            ((MerchantInvoker) client.screen).selectOffer();
            if (!ClientStacker.busy()) throw new AssertionError("Combined payment deficit was not handed off");
        });
        context.waitFor(client -> !ClientStacker.busy());
        assertResult(server, 1, Items.EMERALD, 7, Items.EMERALD_BLOCK, 0);

        open(context, server, Items.IRON_BLOCK, 1, null, 0, new ItemStack(Items.IRON_BLOCK));
        context.runOnClient(client -> {
            ClientStacker.config().blockReserves.put("minecraft:iron_block", 1);
            ((MerchantInvoker) client.screen).selectOffer();
            if (!ClientStacker.busy()) throw new AssertionError("Compact payment bypassed the block reserve");
        });
        context.waitFor(client -> !ClientStacker.busy());
        assertResult(server, 0, Items.IRON_BLOCK, 1, Items.IRON_INGOT, 0);

        open(context, server, Items.EMERALD, 7, null, 0,
            new ItemStack(Items.EMERALD, 7), new ItemStack(Items.EMERALD_BLOCK, 2));
        context.runOnClient(client -> {
            ClientStacker.config().spendingCap = 21;
            vtpFlag("swapshift", true);
            ((MerchantInvoker) client.screen).selectOffer();
            if (!ClientStacker.busy()) throw new AssertionError("Swapped-shift bulk handoff did not start");
            vtpFlag("swapshift", false);
        });
        context.waitFor(client -> !ClientStacker.busy(), 500);
        assertResult(server, 3, Items.EMERALD, 4, Items.EMERALD_BLOCK, 0);

        open(context, server, Items.EMERALD, 7, null, 0, new ItemStack(Items.EMERALD_BLOCK, 2));
        context.runOnClient(client -> {
            vtpFlag("enabled", false);
            ((MerchantInvoker) client.screen).selectOffer();
            if (ClientStacker.busy()) throw new AssertionError("Disabled VTP handed off a trade");
            vtpFlag("enabled", true);
            VtpCompat.initialize("unknown-test-version", "fabric");
            ((MerchantInvoker) client.screen).selectOffer();
            if (ClientStacker.busy()) throw new AssertionError("Unknown VTP handed off a trade");
            VtpCompat.initialize("1.3.0+1.21.11", "fabric");
        });
        context.waitTicks(4);
        assertResult(server, 0, Items.EMERALD_BLOCK, 2, Items.EMERALD, 0);
    }

    private static void vtpFlag(String name, boolean value) {
        try {
            Object config = Class.forName("cc.cassian.trading.VillagerTrading").getField("CONFIG").get(null);
            config.getClass().getField(name).setBoolean(config, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static int open(ClientGameTestContext context, TestServerContext server, Item a, int countA,
                             Item b, int countB, ItemStack... inventory) {
        context.runOnClient(client -> {
            ClientStacker.cancel();
            client.player.closeContainer();
            var config = ClientStacker.config();
            config.protectedSlots.clear();
            config.sellingMaterials.clear();
            config.retainedValues.clear();
            config.blockReserves.clear();
            config.spendingCap = config.retainedEmeralds = config.outputTarget = 0;
        });
        context.waitTicks(3);
        var merchant = new AtomicInteger();
        server.runOnServer(instance -> {
            var player = instance.getPlayerList().getPlayers().getFirst();
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            for (int i = 0; i < inventory.length; i++) player.getInventory().setItem(i, inventory[i].copy());
            player.inventoryMenu.broadcastFullState();
            var villager = EntityType.VILLAGER.create(player.level(), EntitySpawnReason.COMMAND);
            if (villager == null) throw new AssertionError("No villager");
            villager.setPos(player.getX(), player.getY(), player.getZ() + 2);
            villager.setNoAi(true);
            var offers = new MerchantOffers();
            offers.add(new MerchantOffer(new ItemCost(a, countA), b == null ? Optional.empty() : Optional.of(new ItemCost(b, countB)),
                new ItemStack(Items.BREAD), 12, 1, 0));
            villager.setOffers(offers);
            player.level().addFreshEntity(villager);
            merchant.set(villager.getId());
        });
        context.waitFor(client -> !client.player.isCreative() && client.level.getEntity(merchant.get()) != null);
        context.runOnClient(client -> client.gameMode.interact(client.player, client.level.getEntity(merchant.get()), InteractionHand.MAIN_HAND));
        context.waitForScreen(MerchantScreen.class);
        context.waitFor(client -> !((MerchantMenu) client.player.containerMenu).getOffers().isEmpty());
        return merchant.get();
    }

    private static void trade(ClientGameTestContext context, boolean bulk) {
        context.runOnClient(client -> {
            ClientStacker.trade((MerchantMenu) client.player.containerMenu, 0, bulk);
            if (!ClientStacker.busy()) throw new AssertionError("Trade did not start");
        });
        context.waitFor(client -> !ClientStacker.busy(), 500);
    }

    private static void assertResult(TestServerContext server, int uses, Item a, int countA, Item b, int countB) {
        server.runOnServer(instance -> {
            var player = instance.getPlayerList().getPlayers().getFirst();
            var menu = (MerchantMenu) player.containerMenu;
            if (menu.getOffers().getFirst().getUses() != uses || player.getInventory().countItem(Items.BREAD) != uses
                || player.getInventory().countItem(a) != countA || player.getInventory().countItem(b) != countB
                || !menu.getCarried().isEmpty() || !menu.getSlot(0).getItem().isEmpty() || !menu.getSlot(1).getItem().isEmpty()) {
                throw new AssertionError("Unexpected server inventory or trade count: " + player.getInventory().countItem(a)
                    + ", " + player.getInventory().countItem(b) + ", uses " + menu.getOffers().getFirst().getUses());
            }
            if (!player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(10)).isEmpty()) {
                throw new AssertionError("An inventory item was dropped");
            }
        });
    }
}
