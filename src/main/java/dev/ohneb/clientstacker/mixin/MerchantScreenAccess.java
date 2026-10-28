package dev.ohneb.clientstacker.mixin;

import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MerchantScreen.class)
public interface MerchantScreenAccess {
    @Accessor("shopItem") int clientStacker$selection();
    @Accessor("shopItem") void clientStacker$selection(int selection);
    @Accessor("scrollOff") int clientStacker$scroll();
    @Accessor("scrollOff") void clientStacker$scroll(int scroll);
}
