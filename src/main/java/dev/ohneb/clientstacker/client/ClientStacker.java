package dev.ohneb.clientstacker.client;

import dev.ohneb.clientstacker.core.CraftPlanner;
import dev.ohneb.clientstacker.core.Conversion;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
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
    //? if >=1.21.9 {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.parse("client_stacker:controls"));
    //?} else {
    /*private static final String CATEGORY = "key.categories.client_stacker";
    *///?}
    public static final KeyMapping UNPACK = new KeyMapping("key.client_stacker.unpack", InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping COMPACT = new KeyMapping("key.client_stacker.compact", InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping PROTECT = new KeyMapping("key.client_stacker.protect", InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping SETTINGS = new KeyMapping("key.client_stacker.settings", InputConstants.UNKNOWN.getValue(), CATEGORY);
    private static StackerConfig config;
    private static Operation operation;
    private static Entity pendingMerchant;
    private static Integer merchantBarrier;
    private static boolean ambiguousMerchant;
    private static long interactionTime;
    private static Entity lastMerchant;
    private static MerchantMenu lastMerchantMenu;
    private static int handoffTicks;
    private static boolean internalClick;
    private static boolean internalInteraction;
    private static boolean keyRepeat;
    private static float health;
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
    public static void keyRepeat(boolean repeat) { keyRepeat = repeat; }
    public static boolean keyRepeat() { return keyRepeat; }

    public static boolean enabled() {
        return config != null && config.readable() && config.enabled && Minecraft.getInstance().player != null && Minecraft.getInstance().gameMode != null
            && !Minecraft.getInstance().player.isSpectator() && !Minecraft.getInstance().player.isCreative()
            && !config.disabledServers.contains(serverKey());
    }

    public static String serverKey() {
        return Minecraft.getInstance().getCurrentServer() == null ? "singleplayer" : Minecraft.getInstance().getCurrentServer().ip.toLowerCase(java.util.Locale.ROOT);
    }

    public static void tick() {
        if (config == null) return;
        if (handoffTicks > 0) handoffTicks--;
        if (Minecraft.getInstance().player == null || Minecraft.getInstance().level == null) {
            operation = null;
            pendingMerchant = null;
            merchantBarrier = null;
            ambiguousMerchant = false;
            lastMerchant = null;
            lastMerchantMenu = null;
            previousMenu = null;
            return;
        }
        if (operation != null) {
            if (!enabled() || !Minecraft.getInstance().player.isAlive()) { cancel(); return; }
            if (Minecraft.getInstance().player.getHealth() < health) { stop("damage"); return; }
            health = Minecraft.getInstance().player.getHealth();
            try {
                if (operation.tick()) {
                    if (operation.completed() > 0) message("completed", operation.completed(), Component.translatable("client_stacker." + operation.unit()));
                    operation = null;
                }
            } catch (Operation.Stopped exception) {
                stop(exception.reason);
            } catch (CraftPlanner.Unavailable exception) {
                stop(exception.reason().name().toLowerCase(java.util.Locale.ROOT));
            } catch (RuntimeException exception) {
                LoggerFactory.getLogger("client_stacker").error("Inventory operation stopped", exception);
                stop("changed");
            }
        }
        AbstractContainerMenu current = Minecraft.getInstance().player.containerMenu;
        if (current != previousMenu) {
            previousMenu = current;
            if (enabled() && !busy() && config.compactOnTableOpen && current instanceof CraftingMenu) {
                compactAll(current);
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
            Minecraft.getInstance().player.closeContainer();
            parent = null;
        }
        showScreen(new SettingsScreen(parent));
    }

    public static Screen screen() {
        //? if >=26.2 {
        /*return Minecraft.getInstance().gui.screen();
        *///?} else {
        return Minecraft.getInstance().screen;
        //?}
    }

    public static void showScreen(Screen screen) {
        //? if >=26.2 {
        /*Minecraft.getInstance().gui.setScreen(screen);
        *///?} else {
        Minecraft.getInstance().setScreen(screen);
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
        if (!canConvert(menu)) return;
        start(new CraftOperation(menu, conversion, compact, all ? 2304 : 1));
    }

    private static boolean canConvert(AbstractContainerMenu menu) {
        if (!enabled() || ownsInput()) return false;
        if (MenuInventory.grid(menu).isEmpty()) { message("grid_screen"); return false; }
        if (!menu.getCarried().isEmpty()) { message("cursor"); return false; }
        if (!MenuInventory.emptyGrid(menu)) { message("grid"); return false; }
        return true;
    }

    public static void convert(AbstractContainerMenu menu, Conversion material, boolean compact, int quantity) {
        if (!canConvert(menu) || !Materials.enabled(material)) return;
        int crafts = 1;
        if (quantity > 0) {
            long loose = MenuInventory.count(menu, new ItemStack(Materials.item(material.loose())), true);
            crafts = compact ? Math.toIntExact(Math.max(0, loose - config.looseReserve(material)) / material.ratio())
                : Math.toIntExact(material.blocksNeeded(quantity, loose));
        }
        if (crafts == 0) { message("ready"); return; }
        start(new CraftOperation(menu, material, compact, crafts));
    }

    public static void compactAll(AbstractContainerMenu menu) {
        if (!canConvert(menu)) return;
        if (!(menu instanceof CraftingMenu)) { message("need_table"); return; }
        start(new CompressionOperation(menu));
    }

    public static void conversions(AbstractContainerScreen<?> parent, ItemStack selected) {
        if (canConvert(parent.getMenu())) showScreen(new ConversionsScreen(parent, selected));
    }

    public static void trade(MerchantMenu menu, int index, boolean bulk) {
        if (!enabled() || ownsInput() || index < 0 || index >= menu.getOffers().size()) return;
        handoffTicks = 2;
        try {
            start(new TradeOperation(menu, index, bulk, menu == lastMerchantMenu ? lastMerchant : null));
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
        if (!(entity instanceof Merchant)) return;
        if (!internalInteraction && ownsInput()) cancel();
        if (pendingMerchant != null && pendingMerchant != entity) ambiguousMerchant = true;
        lastMerchantMenu = null;
        pendingMerchant = entity;
        interactionTime = System.nanoTime();
    }

    public static void interactionSent() {
        if (pendingMerchant == null) return;
        merchantBarrier = InventoryCheckpoint.nextResponseId();
        // Keep overlapping interactions ambiguous until their server replies have been processed.
        Minecraft.getInstance().getConnection().send(new ServerboundCommandSuggestionPacket(merchantBarrier, "/"));
    }

    static void reopen(Entity entity, InteractionHand hand) {
        internalInteraction = true;
        try {
            var client = Minecraft.getInstance();
            //? if >=26.1 {
            /*client.gameMode.interact(client.player, entity, new net.minecraft.world.phys.EntityHitResult(entity), hand);
            *///?} else {
            client.gameMode.interact(client.player, entity, hand);
            //?}
        } finally {
            internalInteraction = false;
        }
    }

    public static void slot(int container, int slot, ItemStack item) {
        if (operation != null) operation.slot(container, slot, item);
    }

    public static void contents(int container) {
        if (operation != null) operation.contents(container);
    }

    public static void barrier(int id) {
        if (operation != null) operation.barrier(id);
        if (merchantBarrier != null && merchantBarrier == id) {
            pendingMerchant = null;
            merchantBarrier = null;
            ambiguousMerchant = false;
        }
    }

    public static void offers(int container) {
        if (operation != null) operation.offers(container);
        if (!busy() && Minecraft.getInstance().player != null && Minecraft.getInstance().player.containerMenu instanceof MerchantMenu menu
            && menu.containerId == container && pendingMerchant != null && !ambiguousMerchant
            && System.nanoTime() - interactionTime < 10_000_000_000L) {
            lastMerchantMenu = menu;
            lastMerchant = pendingMerchant;
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
            Minecraft.getInstance().gameMode.handleInventoryMouseClick(menu.containerId, slot, button, ClickType.PICKUP, Minecraft.getInstance().player);
        } finally {
            internalClick = false;
        }
    }

    public static void cancel() {
        if (operation == null) return;
        try { operation.cancel(); } catch (RuntimeException ignored) { /* Leave uncertain contents for the player. */ }
        stop("cancelled");
    }

    private static void start(Operation request) {
        health = Minecraft.getInstance().player.getHealth();
        operation = request;
    }

    private static void stop(String reason) {
        // Corrections and timeouts must not trigger compensating clicks on an uncertain inventory.
        if (operation != null && operation.completed() > 0) {
            message("stopped_after", Component.translatable("client_stacker.message." + reason), operation.completed(),
                Component.translatable("client_stacker." + operation.unit()));
        } else message(reason);
        operation = null;
    }

    public static void message(String key, Object... arguments) {
        if (Minecraft.getInstance().player == null) return;
        //? if >=26.2 {
        /*Minecraft.getInstance().gui.hud.setOverlayMessage(Component.translatable("client_stacker.message." + key, arguments), false);
        *///?} else {
        Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("client_stacker.message." + key, arguments), false);
        //?}
    }

    public static boolean shiftDown() {
        //? if >=1.21.9 {
        return Minecraft.getInstance().hasShiftDown();
        //?} else {
        /*return Screen.hasShiftDown();
        *///?}
    }

    public static boolean controlDown() {
        //? if >=1.21.9 {
        return Minecraft.getInstance().hasControlDown();
        //?} else {
        /*return Screen.hasControlDown();
        *///?}
    }
}
