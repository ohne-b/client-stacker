package dev.ohneb.clientstacker.testmixin;

import dev.ohneb.clientstacker.client.TradeSafetyGameTest;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public abstract class DelayedMerchantMixin {
    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void delayReopening(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (!player.level().isClientSide() && TradeSafetyGameTest.delayReopening) {
            TradeSafetyGameTest.delayReopening = false;
            TradeSafetyGameTest.delayedMerchant = ((Villager) (Object) this).getId();
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
}
