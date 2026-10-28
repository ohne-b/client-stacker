package dev.ohneb.clientstacker.mixin;

import dev.ohneb.clientstacker.client.ClientStacker;
import dev.ohneb.clientstacker.client.MenuInventory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin extends Screen {
    @Shadow protected AbstractContainerMenu menu;
    @Shadow protected Slot hoveredSlot;
    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected int imageWidth;
    @Shadow protected int imageHeight;
    @Unique private Button clientStacker$trade;
    @Unique private Button clientStacker$cancel;
    @Unique private Button clientStacker$settings;

    protected ContainerScreenMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("RETURN"))
    private void clientStacker$init(CallbackInfo ci) {
        if ((Object) this instanceof CreativeModeInventoryScreen || ClientStacker.config() == null) return;
        if (!(menu instanceof MerchantMenu || menu instanceof InventoryMenu || menu instanceof CraftingMenu)) return;
        int y = Math.min(height - 22, topPos + imageHeight + 4);
        int buttonWidth = menu instanceof MerchantMenu ? 86 : 100;
        clientStacker$settings = addRenderableWidget(Button.builder(Component.translatable("client_stacker.settings"),
            button -> ClientStacker.openSettings()).bounds(leftPos + imageWidth - buttonWidth, y, buttonWidth, 20).build());
        clientStacker$settings.setTooltip(Tooltip.create(Component.translatable("client_stacker.controls.help",
            ClientStacker.UNPACK.getTranslatedKeyMessage(), ClientStacker.COMPACT.getTranslatedKeyMessage())));
        if ((Object) this instanceof MerchantScreen) {
            clientStacker$trade = addRenderableWidget(Button.builder(Component.translatable("client_stacker.trade"), button -> {
                int index = ((MerchantScreenAccess) this).clientStacker$selection();
                ClientStacker.trade((MerchantMenu) menu, index, ClientStacker.shiftDown());
            }).bounds(leftPos, y, 86, 20).build());
            clientStacker$trade.setTooltip(Tooltip.create(Component.translatable("client_stacker.trade.help")));
        }
        clientStacker$cancel = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),
            button -> ClientStacker.cancel()).bounds(leftPos + (menu instanceof MerchantMenu ? 90 : 0), y,
                menu instanceof MerchantMenu ? 86 : 72, 20).build());
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void clientStacker$render(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (clientStacker$settings == null) return;
        clientStacker$settings.active = !ClientStacker.ownsInput();
        clientStacker$cancel.visible = ClientStacker.busy();
        if (clientStacker$trade != null) clientStacker$trade.active = ClientStacker.enabled() && !ClientStacker.ownsInput();
        for (Slot slot : MenuInventory.playerSlots(menu)) {
            if (MenuInventory.protectedSlot(slot)) {
                int x = leftPos + slot.x;
                int y = topPos + slot.y;
                graphics.fill(x, y + 14, x + 16, y + 16, 0xFFFFC857);
            }
        }
        if (menu instanceof MerchantMenu && ClientStacker.config().showBalance) {
            Component balance = Component.translatable("client_stacker.balance", ClientStacker.balance(menu, false));
            graphics.drawString(font, balance, Math.max(4, leftPos + imageWidth - font.width(balance)), Math.max(2, topPos - 12), 0xFFFFFFFF);
        }
    }

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void clientStacker$guard(Slot slot, int slotId, int button, ClickType type, CallbackInfo ci) {
        if (ClientStacker.blocksClick()) ci.cancel();
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    //? if >=1.21.9 {
    private void clientStacker$key(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        int key = event.key();
        boolean unpack = ClientStacker.UNPACK.matches(event);
        boolean compact = ClientStacker.COMPACT.matches(event);
        boolean protect = ClientStacker.PROTECT.matches(event);
        boolean settings = ClientStacker.SETTINGS.matches(event);
    //?} else {
    /*private void clientStacker$key(int key, int scan, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        boolean unpack = ClientStacker.UNPACK.matches(key, scan);
        boolean compact = ClientStacker.COMPACT.matches(key, scan);
        boolean protect = ClientStacker.PROTECT.matches(key, scan);
        boolean settings = ClientStacker.SETTINGS.matches(key, scan);
    *///?}
        if (ClientStacker.ownsInput()) {
            if (key == 256) ClientStacker.cancel();
            cir.setReturnValue(true);
        } else if (unpack || compact) {
            ClientStacker.convert((AbstractContainerScreen<?>) (Object) this, hoveredSlot, compact);
            cir.setReturnValue(true);
        } else if (protect) {
            ClientStacker.protect((AbstractContainerScreen<?>) (Object) this, hoveredSlot);
            cir.setReturnValue(true);
        } else if (settings) {
            ClientStacker.openSettings();
            cir.setReturnValue(true);
        }
    }
}
