package dev.ohneb.clientstacker.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ohneb.clientstacker.client.ClientStacker;
import net.minecraft.client.KeyboardHandler;
//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    //? if >=1.21.9 {
    private void clientStacker$escape(long window, int action, KeyEvent event, CallbackInfo ci) {
        int key = event.key();
    //?} else {
    /*private void clientStacker$escape(long window, int key, int scan, int action, int modifiers, CallbackInfo ci) {
    *///?}
        if (key == InputConstants.KEY_ESCAPE && action == InputConstants.PRESS && ClientStacker.ownsInput()) {
            ClientStacker.cancel();
            ci.cancel();
        }
    }
}
