package dev.ohneb.clientstacker.client;

import dev.ohneb.clientstacker.core.CraftPlanner;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.Merchant;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class ClientStacker {
    private static final Minecraft MC = Minecraft.getInstance();
    //? if >=1.21.9 {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.parse("client_stacker:controls"));
    //?} else {
    /*private static final String CATEGORY = "key.categories.client_stacker";
    *///?}
    public static final KeyMapping UNPACK = new KeyMapping("key.client_stacker.unpack", InputConstants.KEY_U, CATEGORY);
    public static final KeyMapping COMPACT = new KeyMapping("key.client_stacker.compact", InputConstants.KEY_J, CATEGORY);
    public static final KeyMapping PROTECT = new KeyMapping("key.client_stacker.protect", InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping SETTINGS = new KeyMapping("key.client_stacker.settings", InputConstants.UNKNOWN.getValue(), CATEGORY);
    private static StackerConfig config;
    private static Operation operation;
    private static Entity pendingMerchant;
    private static long interactionTime;
    private static Entity lastMerchant;
    private static MerchantMenu lastMerchantMenu;
    private static int handoffTicks;
    private static boolean internalClick;
    private static AbstractContainerMenu previousMenu;

    private ClientStacker() {}

    public static void initialize(Path configDirectory, String vtpVersion, String loader) {
        config = StackerConfig.load(configDirectory.resolve("client-stacker.json"));
        VtpCompat.initialize(vtpVersion, loader);
    }

    public static StackerConfig config() { return config; }
    public static boolean busy() { return operation != null; }
    public static boolean ownsInput() { return busy() || handoffTicks > 0; }
    public static boolean blocksClick() { return ownsInput() && !internalClick; }

    public static boolean enabled() {
        return config != null && config.readable() && config.enabled && MC.player != null && MC.gameMode != null
            && !MC.player.isSpectator() && !MC.player.isCreative()
            && !config.disabledServers.contains(serverKey());
    }

    public static String serverKey() {
        return MC.getCurrentServer() == null ? "singleplayer" : MC.getCurrentServer().ip.toLowerCase(java.util.Locale.ROOT);
    }

    public static void tick() {
        if (config == null) return;
        if (handoffTicks > 0) handoffTicks--;
        if (MC.player == null || MC.level == null) {
            operation = null;
            pendingMerchant = null;
            lastMerchant = null;
            lastMerchantMenu = null;
            previousMenu = null;
            return;
        }
        if (operation != null) {
            if (!enabled() || !MC.player.isAlive()) { cancel(); return; }
            try {
                if (operation.tick()) operation = null;
            } catch (Operation.Stopped exception) {
                stop(exception.reason);
            } catch (CraftPlanner.Unavailable exception) {
                stop(exception.reason().name().toLowerCase(java.util.Locale.ROOT));
            } catch (RuntimeException exception) {
                LoggerFactory.getLogger("client_stacker").error("Inventory operation stopped", exception);
                stop("changed");
            }
        }
        AbstractContainerMenu current = MC.player.containerMenu;
        if (current != previousMenu) {
            previousMenu = current;
            if (enabled() && !busy() && config.compactOnTableOpen && current instanceof CraftingMenu) {
                startCraft(current, new ItemStack(Items.EMERALD), true, true);
            }
        }
        while (SETTINGS.consumeClick()) {
            if (!ownsInput()) openSettings();
        }
    }

    public static void openSettings() {
        if (ownsInput()) return;
        Screen parent = screen();
        if (parent instanceof AbstractContainerScreen<?> container) {
            var menu = container.getMenu();
            if (!menu.getCarried().isEmpty()) { message("cursor"); return; }
            if ((!MenuInventory.grid(menu).isEmpty() && !MenuInventory.emptyGrid(menu))
                || (menu instanceof MerchantMenu && (!menu.getSlot(0).getItem().isEmpty() || !menu.getSlot(1).getItem().isEmpty()))) {
                message("grid");
                return;
            }
            MC.player.closeContainer();
            parent = null;
        }
        showScreen(new SettingsScreen(parent));
    }

    public static Screen screen() {
        //? if >=26.2 {
        /*return MC.gui.screen();
        *///?} else {
        return MC.screen;
        //?}
    }

    public static void showScreen(Screen screen) {
        //? if >=26.2 {
        /*MC.gui.setScreen(screen);
        *///?} else {
        MC.setScreen(screen);
        //?}
    }

    public static void convert(AbstractContainerScreen<?> screen, Slot hovered, boolean compact) {
        if (!enabled() || ownsInput() || screen instanceof CreativeModeInventoryScreen) return;
        if (hovered == null || MenuInventory.protectedSlot(hovered)
            || !MenuInventory.playerSlots(screen.getMenu()).contains(hovered)) return;
        startCraft(screen.getMenu(), hovered.getItem(), compact, shiftDown());
    }

    private static void startCraft(AbstractContainerMenu menu, ItemStack stack, boolean compact, boolean all) {
        var conversion = Materials.forStack(stack, compact);
        if (conversion == null) { message("material"); return; }
        if (MenuInventory.grid(menu).isEmpty()) { message("grid_screen"); return; }
        if (!menu.getCarried().isEmpty()) { message("cursor"); return; }
        if (!MenuInventory.emptyGrid(menu)) { message("grid"); return; }
        operation = new CraftOperation(menu, conversion, compact, all ? 2304 : 1);
    }

    public static void trade(MerchantMenu menu, int index, boolean bulk) {
        if (!enabled() || ownsInput() || index < 0 || index >= menu.getOffers().size()) return;
        handoffTicks = 2;
        try {
            operation = new TradeOperation(menu, index, bulk, menu == lastMerchantMenu ? lastMerchant : null);
        } catch (Operation.Stopped exception) {
            message(exception.reason);
        }
    }

    public static void protect(AbstractContainerScreen<?> screen, Slot slot) {
        if (busy() || slot == null || !MenuInventory.playerSlots(screen.getMenu()).contains(slot)) return;
        int index = slot.getContainerSlot();
        if (!config.protectedSlots.remove(index)) config.protectedSlots.add(index);
        if (!config.save()) message("save");
    }

    public static long balance(AbstractContainerMenu menu, boolean usableOnly) {
        long blocks = MenuInventory.count(menu, new ItemStack(Items.EMERALD_BLOCK), usableOnly);
        long loose = MenuInventory.count(menu, new ItemStack(Items.EMERALD), usableOnly);
        if (menu instanceof MerchantMenu) {
            for (int slot : new int[]{0, 1}) {
                ItemStack stack = menu.getSlot(slot).getItem();
                if (!Materials.plain(stack)) continue;
                if (stack.is(Items.EMERALD)) loose += stack.getCount();
                if (stack.is(Items.EMERALD_BLOCK)) blocks += stack.getCount();
            }
        }
        return Materials.EMERALDS.value(blocks, loose);
    }

    public static void interacted(Entity entity) {
        if (busy() || !(entity instanceof Merchant)) return;
        pendingMerchant = entity;
        interactionTime = System.nanoTime();
    }

    public static void slot(int container, int slot, ItemStack item) {
        if (operation != null) operation.slot(container, slot, item);
    }

    public static void contents(int container) {
        if (operation != null) operation.contents(container);
    }

    public static void barrier(int id) {
        if (operation != null) operation.barrier(id);
    }

    public static void offers(int container) {
        if (operation != null) operation.offers(container);
        if (!busy() && MC.player != null && MC.player.containerMenu instanceof MerchantMenu menu
            && menu.containerId == container && pendingMerchant != null
            && System.nanoTime() - interactionTime < 10_000_000_000L) {
            lastMerchantMenu = menu;
            lastMerchant = pendingMerchant;
            pendingMerchant = null;
        }
    }

    static void associate(MerchantMenu menu, Entity merchant) {
        lastMerchantMenu = menu;
        lastMerchant = merchant;
    }

    public static void click(AbstractContainerMenu menu, int slot, int button) {
        Operation.requireCurrent(menu);
        if (slot < 0 || slot >= menu.slots.size()) throw new Operation.Stopped("changed");
        internalClick = true;
        try {
            MC.gameMode.handleInventoryMouseClick(menu.containerId, slot, button, ClickType.PICKUP, MC.player);
        } finally {
            internalClick = false;
        }
    }

    public static void cancel() {
        if (operation == null) return;
        try { operation.cancel(); } catch (RuntimeException ignored) { /* Leave uncertain contents for the player. */ }
        operation = null;
        message("cancelled");
    }

    private static void stop(String reason) {
        // Corrections and timeouts must not trigger compensating clicks on an uncertain inventory.
        operation = null;
        message(reason);
    }

    public static void message(String key) {
        if (MC.player == null) return;
        //? if >=26.2 {
        /*MC.gui.hud.setOverlayMessage(Component.translatable("client_stacker.message." + key), false);
        *///?} else {
        MC.gui.setOverlayMessage(Component.translatable("client_stacker.message." + key), false);
        //?}
    }

    public static boolean shiftDown() {
        //? if >=1.21.9 {
        return MC.hasShiftDown();
        //?} else {
        /*return Screen.hasShiftDown();
        *///?}
    }

    public static boolean controlDown() {
        //? if >=1.21.9 {
        return MC.hasControlDown();
        //?} else {
        /*return Screen.hasControlDown();
        *///?}
    }
}
