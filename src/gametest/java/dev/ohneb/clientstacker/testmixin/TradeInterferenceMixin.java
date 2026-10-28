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
import dev.ohneb.clientstacker.client.InventoryFailureGameTest;
import dev.ohneb.clientstacker.client.TradeSafetyGameTest;
import dev.ohneb.clientstacker.client.CraftTimingGameTest;
import dev.ohneb.clientstacker.client.CheckpointCorrectionGameTest;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class TradeInterferenceMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleCustomCommandSuggestions", at = @At("HEAD"), cancellable = true)
    private void blockCheckpoint(net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket packet, CallbackInfo ci) {
        if (CheckpointCorrectionGameTest.replaceAfterBarrier(player, packet.getId())) {
            ci.cancel();
            return;
        }
        if (CraftTimingGameTest.holding && player.level().getServer().isSameThread()) {
            CraftTimingGameTest.held.add(packet);
            ci.cancel();
            return;
        }
        if (TradeSafetyGameTest.barrierMode == 0 || !player.level().getServer().isSameThread()
            || !(player.containerMenu instanceof MerchantMenu menu)
            || menu.getOffers().getFirst().getUses() < TradeSafetyGameTest.barrierAfterUses) return;
        TradeSafetyGameTest.blockedBarrier = packet.getId();
        if (TradeSafetyGameTest.barrierMode == 2) {
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket(packet.getId() - 1,
                com.mojang.brigadier.suggestion.Suggestions.empty().join()));
        }
        ci.cancel();
    }

    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void rejectPurchase(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (CraftTimingGameTest.holding && player.level().getServer().isSameThread()) {
            CraftTimingGameTest.held.add(packet);
            ci.cancel();
            return;
        }
        if (TradeSafetyGameTest.rejectPurchase && player.level().getServer().isSameThread()
            && player.containerMenu instanceof MerchantMenu && packet.slotNum() == 2 && packet.clickType() == ClickType.PICKUP) {
            TradeSafetyGameTest.rejectPurchase = false;
            player.containerMenu.broadcastFullState();
            ci.cancel();
        }
    }

    @Inject(method = "handlePlaceRecipe", at = @At("RETURN"))
    private void countRecipePlacement(CallbackInfo ci) {
        InventoryFailureGameTest.recipePackets++;
    }

    @Inject(method = "handleContainerClick", at = @At("RETURN"))
    private void fillDestination(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (CraftTimingGameTest.enabled && player.containerMenu == player.inventoryMenu
            && packet.clickType() == ClickType.PICKUP && (packet.slotNum() == 0) == CraftTimingGameTest.afterOutput) {
            CraftTimingGameTest.enabled = false;
            CraftTimingGameTest.holding = true;
            player.inventoryMenu.broadcastFullState();
        }
        if (TradeCorrectionGameTest.interfere && player.containerMenu instanceof MerchantMenu
            && packet.slotNum() == 2 && packet.clickType() == ClickType.PICKUP) {
            TradeCorrectionGameTest.interfere = false;
            player.getInventory().setItem(9, new ItemStack(Items.DIRT, 64));
        }
    }
}
