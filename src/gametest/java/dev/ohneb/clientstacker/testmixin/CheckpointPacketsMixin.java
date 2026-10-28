package dev.ohneb.clientstacker.testmixin;

import dev.ohneb.clientstacker.client.TradeSafetyGameTest;
import dev.ohneb.clientstacker.client.GameTestPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.world.inventory.MerchantMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class CheckpointPacketsMixin {
    @Inject(method = "handleCommandSuggestions", at = @At("RETURN"))
    private void fixtureReply(net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket packet, CallbackInfo ci) {
        if (packet.id() >= 0) GameTestPackets.received = packet.id();
    }

    @Inject(method = "handleContainerContent", at = @At("RETURN"))
    private void countSnapshot(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if (TradeSafetyGameTest.barrierMode != 0 && client.player != null
            && client.player.containerMenu instanceof MerchantMenu menu && menu.containerId == packet.containerId() && !menu.getOffers().isEmpty()
            && menu.getOffers().getFirst().getUses() >= TradeSafetyGameTest.barrierAfterUses) {
            TradeSafetyGameTest.checkpointSnapshots++;
        }
    }
}
