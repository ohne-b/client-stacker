package dev.ohneb.clientstacker.platform;

import dev.ohneb.clientstacker.client.ClientStacker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;

public final class FabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        var loader = FabricLoader.getInstance();
        String vtp = loader.getModContainer("villager_trading_plus")
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse(null);
        ClientStacker.initialize(loader.getConfigDir(), vtp, "fabric");
        for (var key : new net.minecraft.client.KeyMapping[]{ClientStacker.UNPACK, ClientStacker.COMPACT,
            ClientStacker.PROTECT, ClientStacker.SETTINGS}) KeyBindingHelper.registerKeyBinding(key);
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientStacker.tick());
    }
}
