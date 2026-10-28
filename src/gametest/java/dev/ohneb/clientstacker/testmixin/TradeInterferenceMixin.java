package dev.ohneb.clientstacker.testmixin;

import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.ohneb.clientstacker.client.TradeCorrectionGameTest;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class TradeInterferenceMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleContainerClick", at = @At("RETURN"))
    private void fillDestination(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (TradeCorrectionGameTest.interfere && player.containerMenu instanceof MerchantMenu
            && packet.slotNum() == 2 && packet.clickType() == ClickType.PICKUP) {
            TradeCorrectionGameTest.interfere = false;
            player.getInventory().setItem(9, new ItemStack(Items.DIRT, 64));
        }
    }
}
