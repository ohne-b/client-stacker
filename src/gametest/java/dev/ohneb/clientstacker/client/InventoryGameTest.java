package dev.ohneb.clientstacker.client;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import dev.ohneb.clientstacker.testmixin.MerchantInvoker;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("UnstableApiUsage")
public final class InventoryGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            TestServerContext server = world.getServer();
            server.runOnServer(instance -> {
                ServerPlayer player = instance.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL);
                player.getInventory().clearContent();
                player.getInventory().setItem(0, new ItemStack(Items.EMERALD_BLOCK, 2));
                player.inventoryMenu.broadcastChanges();
            });
            context.waitFor(client -> !client.player.isCreative() && count(client.player, Items.EMERALD_BLOCK) == 2);
            context.setScreen(() -> new InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
            convert(context, Items.EMERALD_BLOCK, false);
            context.waitFor(client -> !ClientStacker.busy());
            assertInventory(server, Items.EMERALD_BLOCK, 1, Items.EMERALD, 9);

            context.runOnClient(client -> {
                ClientStacker.config().protectedSlots.add(0);
                var screen = (AbstractContainerScreen<?>) client.screen;
                var slot = MenuInventory.playerSlots(screen.getMenu()).stream()
                    .filter(candidate -> candidate.getContainerSlot() == 0).findFirst().orElseThrow();
                ClientStacker.convert(screen, slot, false);
                if (ClientStacker.busy()) throw new AssertionError("Protected conversion started");
                ClientStacker.config().protectedSlots.clear();
                client.player.closeContainer();
            });
            context.waitTicks(3);
            server.runOnServer(instance -> {
                ServerPlayer player = instance.getPlayerList().getPlayers().getFirst();
                BlockPos pos = player.blockPosition().offset(1, 0, 0);
                player.level().setBlockAndUpdate(pos, Blocks.CRAFTING_TABLE.defaultBlockState());
                player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new CraftingMenu(id, inventory,
                    ContainerLevelAccess.create(player.level(), pos)), Component.literal("Crafting")));
            });
            context.waitFor(client -> client.player.containerMenu instanceof CraftingMenu);
            context.runOnClient(client -> ClientStacker.config().looseReserve = 0);
            convert(context, Items.EMERALD, true);
            context.waitFor(client -> !ClientStacker.busy());
            assertInventory(server, Items.EMERALD_BLOCK, 2, Items.EMERALD, 0);

            context.runOnClient(client -> client.player.closeContainer());
            context.waitTicks(3);
            var merchantId = new AtomicInteger();
            server.runOnServer(instance -> {
                ServerPlayer player = instance.getPlayerList().getPlayers().getFirst();
                Villager villager = EntityType.VILLAGER.create(player.level(), EntitySpawnReason.COMMAND);
                if (villager == null) throw new AssertionError("No villager");
                villager.setPos(player.getX(), player.getY(), player.getZ() + 2);
                villager.setNoAi(true);
                var offers = new MerchantOffers();
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 7), Optional.empty(),
                    new ItemStack(Items.BREAD, 1), 12, 1, 0));
                villager.setOffers(offers);
                player.level().addFreshEntity(villager);
                merchantId.set(villager.getId());
            });
            context.waitFor(client -> client.level.getEntity(merchantId.get()) != null);
            context.runOnClient(client -> client.gameMode.interact(client.player,
                client.level.getEntity(merchantId.get()), InteractionHand.MAIN_HAND));
            context.waitForScreen(MerchantScreen.class);
            context.waitFor(client -> !((MerchantScreen) client.screen).getMenu().getOffers().isEmpty());
            context.getInput().holdShift();
            context.runOnClient(client -> {
                ClientStacker.config().spendingCap = 14;
                if (VtpCompat.installed()) {
                    if (!VtpCompat.supported() || !client.screen.getClass().getName().endsWith("BetterGuiMerchant")) {
                        throw new AssertionError("VTP integration not loaded");
                    }
                    ((MerchantInvoker) client.screen).selectOffer();
                } else {
                    ClientStacker.trade(((MerchantScreen) client.screen).getMenu(), 0, true);
                }
                if (!ClientStacker.busy()) throw new AssertionError("Trade did not start");
            });
            context.getInput().releaseShift();
            context.waitFor(client -> !ClientStacker.busy(), 400);
            assertInventory(server, Items.BREAD, 2, Items.EMERALD, 4);
            assertInventory(server, Items.EMERALD_BLOCK, 0, Items.BREAD, 2);
            context.waitTicks(100);
            context.takeScreenshot(TestScreenshotOptions.of("trading").withDestinationDir(Path.of(System.getProperty("clientstacker.test.artifacts"))));
            context.runOnClient(client -> ClientStacker.openSettings());
            context.waitForScreen(SettingsScreen.class);
            context.takeScreenshot(TestScreenshotOptions.of("settings").withDestinationDir(Path.of(System.getProperty("clientstacker.test.artifacts"))));
        }
    }

    private static void convert(ClientGameTestContext context, Item input, boolean compact) {
        context.runOnClient(client -> {
            var screen = (AbstractContainerScreen<?>) client.screen;
            var slot = MenuInventory.playerSlots(screen.getMenu()).stream()
                .filter(candidate -> candidate.getItem().is(input)).findFirst().orElseThrow();
            ClientStacker.convert(screen, slot, compact);
            if (!ClientStacker.busy()) throw new AssertionError("Conversion did not start");
        });
    }

    private static long count(net.minecraft.world.entity.player.Player player, Item item) {
        return player.getInventory().countItem(item);
    }

    private static void assertInventory(TestServerContext server, Item first, int firstCount, Item second, int secondCount) {
        server.runOnServer(instance -> {
            ServerPlayer player = instance.getPlayerList().getPlayers().getFirst();
            long actualFirst = count(player, first);
            long actualSecond = count(player, second);
            if (actualFirst != firstCount || actualSecond != secondCount) {
                throw new AssertionError("Server inventory: " + first + "=" + actualFirst + " (expected " + firstCount
                    + "), " + second + "=" + actualSecond + " (expected " + secondCount + ")");
            }
        });
    }
}
