package dev.ohneb.clientstacker.smoke;

import dev.ohneb.clientstacker.client.ClientStacker;
import dev.ohneb.clientstacker.client.SettingsScreen;
import dev.ohneb.clientstacker.client.VtpCompat;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;

final class SmokeProbe {
    private static int ticks;
    private static boolean opened;

    private SmokeProbe() {}

    static void tick(String loader) {
        var client = Minecraft.getInstance();
        if (ClientStacker.screen() == null) return;
        if (++ticks < 40) return;
        try {
            if (!opened) {
                boolean automation = Boolean.parseBoolean(System.getProperty("clientstacker.smoke.automation"));
                if (ClientStacker.supportsAutomation() != automation) throw new AssertionError("Incorrect automation availability");
                if (!automation) {
                    ClientStacker.convert((net.minecraft.world.inventory.AbstractContainerMenu) null,
                        dev.ohneb.clientstacker.client.Materials.EMERALDS, false, 9);
                    ClientStacker.trade(null, 0, true);
                    ClientStacker.interacted(null);
                    ClientStacker.interactionSent();
                    if (ClientStacker.busy()) throw new AssertionError("Unsupported automation started");
                }
                // Force transformation of targets that are otherwise first loaded on joining a world.
                for (String name : new String[]{"net.minecraft.client.gui.screens.inventory.MerchantScreen",
                    "net.minecraft.client.multiplayer.MultiPlayerGameMode", "net.minecraft.client.multiplayer.ClientPacketListener"}) {
                    Class.forName(name).getDeclaredMethods();
                }
                if (VtpCompat.installed()) {
                    if (!VtpCompat.supported()) throw new AssertionError("VTP handoff did not initialize");
                    boolean found = false;
                    for (String prefix : new String[]{"", loader + "."}) {
                        String name = prefix + "cc.cassian.trading.BetterGuiMerchant";
                        if (SmokeProbe.class.getClassLoader().getResource(name.replace('.', '/') + ".class") != null) {
                            Class.forName(name).getDeclaredMethods();
                            found = true;
                        }
                    }
                    if (!found) throw new AssertionError("VTP screen is missing");
                }
                ClientStacker.showScreen(new SettingsScreen(null));
                opened = true;
                ticks = 0;
            } else {
                Path result = Path.of(System.getProperty("clientstacker.smoke.result"));
                Files.createDirectories(result.getParent());
                Files.writeString(result, "Startup, mixin targets, and settings render passed; VTP " + VtpCompat.status().getString() + "\n");
                client.stop();
            }
        } catch (Exception exception) {
            throw new AssertionError("Startup smoke test failed", exception);
        }
    }
}
