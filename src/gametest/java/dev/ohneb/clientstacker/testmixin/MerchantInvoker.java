package dev.ohneb.clientstacker.testmixin;

import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MerchantScreen.class)
public interface MerchantInvoker {
    @Invoker("postButtonClick") void selectOffer();
}
