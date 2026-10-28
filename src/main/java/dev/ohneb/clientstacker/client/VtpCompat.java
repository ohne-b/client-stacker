package dev.ohneb.clientstacker.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.util.Set;

/** Only the inspected upstream releases participate in request handoff. */
public final class VtpCompat {
    private static final Set<String> KNOWN = Set.of("1.1+1.21", "1.2+1.21.9", "1.3.0+1.21.11",
        "1.3.0+26.1-snapshot-5", "1.3.0+26.2-rc-2");
    private static String version;
    private static String prefix;
    private static boolean failed;

    private VtpCompat() {}

    public static void initialize(String installedVersion, String loader) {
        version = installedVersion;
        failed = false;
        prefix = "1.1+1.21".equals(version) ? loader + ".cc.cassian.trading." : "cc.cassian.trading.";
    }

    public static boolean installed() { return version != null; }
    public static boolean supported() { return version != null && KNOWN.contains(version) && !failed; }
    public static net.minecraft.network.chat.Component status() {
        return net.minecraft.network.chat.Component.translatable(!installed() ? "client_stacker.vtp.absent"
            : supported() ? "client_stacker.vtp.supported" : "client_stacker.vtp.unknown", version == null ? "" : version);
    }

    public static boolean capture(MerchantScreen screen, int index) {
        if (!supported() || ClientStacker.controlDown() || !ClientStacker.enabled()) return false;
        if (!screen.getClass().getName().equals(prefix + "BetterGuiMerchant")) return false;
        MerchantMenu menu = screen.getMenu();
        if (index < 0 || index >= menu.getOffers().size()) return false;
        try {
            Object config;
            boolean enabled = true;
            if (version.startsWith("1.3.0")) {
                config = Class.forName(prefix + "VillagerTrading").getField("CONFIG").get(null);
                enabled = config.getClass().getField("enabled").getBoolean(config);
            } else {
                config = Class.forName(prefix + "config.ModConfig").getMethod("get").invoke(null);
            }
            if (!enabled) return false;
            Field swapped = config.getClass().getField("swapshift");
            boolean bulk = ClientStacker.shiftDown() != swapped.getBoolean(config);
            if (!needsManagement(menu, index, bulk)) return false;
            ClientStacker.trade(menu, index, bulk);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            failed = true;
            ClientStacker.message("vtp_unknown");
            return false;
        }
    }

    private static boolean needsManagement(MerchantMenu menu, int index, boolean bulk) {
        var config = ClientStacker.config();
        if (config.spendingCap > 0 || config.retainedValue(Materials.EMERALDS) > 0 || config.outputTarget > 0
            || config.retainedValues.values().stream().anyMatch(value -> value > 0)
            || config.blockReserves.values().stream().anyMatch(value -> value > 0)
            || !config.protectedSlots.isEmpty()) return true;
        var offer = menu.getOffers().get(index);
        for (ItemStack cost : Materials.costs(offer)) {
            var pair = Materials.forTrade(cost);
            if (pair == null) continue;
            long compact = MenuInventory.count(menu, new ItemStack(Materials.item(pair.compact())), true);
            if (compact == 0) continue;
            // Capture bulk intent even if the first purchase can use loose items.
            if (bulk || MenuInventory.count(menu, cost, true) < cost.getCount()) return true;
        }
        return false;
    }
}
