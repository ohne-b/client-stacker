package dev.ohneb.clientstacker.mixin;

import dev.ohneb.clientstacker.client.ClientStacker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketsMixin {
    @Inject(method = "handleContainerSetSlot", at = @At("RETURN"))
    private void clientStacker$slot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        ClientStacker.slot(packet.getContainerId(), packet.getSlot(), packet.getItem());
    }

    @Inject(method = "handleContainerContent", at = @At("RETURN"))
    private void clientStacker$contents(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        //? if >=1.21.5 {
        ClientStacker.contents(packet.containerId());
        //?} else {
        /*ClientStacker.contents(packet.getContainerId());
        *///?}
    }

    @Inject(method = "handleMerchantOffers", at = @At("RETURN"))
    private void clientStacker$offers(ClientboundMerchantOffersPacket packet, CallbackInfo ci) {
        ClientStacker.offers(packet.getContainerId());
    }
}
