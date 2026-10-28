package dev.ohneb.clientstacker.platform;

import dev.ohneb.clientstacker.client.ClientStacker;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "client_stacker", dist = Dist.CLIENT)
public final class NeoForgeClient {
    public NeoForgeClient(IEventBus bus) {
        var vtp = ModList.get().getModContainerById("villager_trading_plus")
            .map(mod -> mod.getModInfo().getVersion().toString()).orElse(null);
        ClientStacker.initialize(FMLPaths.CONFIGDIR.get(), vtp, "neoforge");
        bus.addListener(NeoForgeClient::registerKeys);
        NeoForge.EVENT_BUS.addListener(NeoForgeClient::tick);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(ClientStacker.UNPACK);
        event.register(ClientStacker.COMPACT);
        event.register(ClientStacker.PROTECT);
        event.register(ClientStacker.SETTINGS);
    }

    private static void tick(ClientTickEvent.Post event) { ClientStacker.tick(); }
}
