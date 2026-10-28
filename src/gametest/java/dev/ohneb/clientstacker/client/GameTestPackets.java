package dev.ohneb.clientstacker.client;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;

@SuppressWarnings("UnstableApiUsage")
public final class GameTestPackets {
    public static volatile int received;
    private static int nextId = 1;

    private GameTestPackets() {}

    static void settle(ClientGameTestContext context) {
        int id = nextId++;
        context.runOnClient(client -> client.getConnection().send(new ServerboundCommandSuggestionPacket(id, "/")));
        context.waitFor(client -> received == id);
    }
}
