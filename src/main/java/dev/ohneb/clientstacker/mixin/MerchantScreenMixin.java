package dev.ohneb.clientstacker.mixin;

import dev.ohneb.clientstacker.client.ClientStacker;
import dev.ohneb.clientstacker.client.VtpCompat;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MerchantScreen.class, priority = 1100)
public abstract class MerchantScreenMixin {
    @Shadow private int shopItem;

    @Inject(method = "postButtonClick", at = @At("HEAD"), cancellable = true)
    private void clientStacker$request(CallbackInfo ci) {
        if (ClientStacker.ownsInput() || VtpCompat.capture((MerchantScreen) (Object) this, shopItem)) ci.cancel();
    }
}
