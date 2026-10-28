package dev.ohneb.clientstacker.mixin;

import dev.ohneb.clientstacker.client.ClientStacker;
import dev.ohneb.clientstacker.client.VtpCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
//? if <=1.21.8 && fabric {
/*@Mixin(targets = "fabric.cc.cassian.trading.BetterGuiMerchant", remap = false)
*///?} else if <=1.21.8 && neoforge {
/*@Mixin(targets = "neoforge.cc.cassian.trading.BetterGuiMerchant", remap = false)
*///?} else {
@Mixin(targets = "cc.cassian.trading.BetterGuiMerchant", remap = false)
//?}
public abstract class VtpTradeMixin {
    @Inject(method = "trade", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void clientStacker$owned(int index, CallbackInfo ci) {
        if (VtpCompat.supported() && ClientStacker.ownsInput()) ci.cancel();
    }
}
