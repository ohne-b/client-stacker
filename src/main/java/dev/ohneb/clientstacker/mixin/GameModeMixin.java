package dev.ohneb.clientstacker.mixin;

import dev.ohneb.clientstacker.client.ClientStacker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class GameModeMixin {
    //? if >=26.1 {
    /*@Inject(method = "interact", at = @At("HEAD"))
    private void clientStacker$interact(Player player, Entity entity, EntityHitResult hit, InteractionHand hand,
                                       CallbackInfoReturnable<InteractionResult> cir) {
        ClientStacker.interacted(entity);
    }
    *///?} else {
    @Inject(method = "interact", at = @At("HEAD"))
    private void clientStacker$interact(Player player, Entity entity, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        ClientStacker.interacted(entity);
    }

    @Inject(method = "interactAt", at = @At("HEAD"))
    private void clientStacker$interactAt(Player player, Entity entity, EntityHitResult hit, InteractionHand hand,
                                         CallbackInfoReturnable<InteractionResult> cir) {
        ClientStacker.interacted(entity);
    }

    //?}

    @Inject(method = "handleInventoryMouseClick", at = @At("HEAD"), cancellable = true)
    private void clientStacker$click(int container, int slot, int button, ClickType type, Player player, CallbackInfo ci) {
        if (ClientStacker.blocksClick()) ci.cancel();
    }

    @Inject(method = "handlePlaceRecipe", at = @At("HEAD"), cancellable = true)
    private void clientStacker$recipe(CallbackInfo ci) {
        if (ClientStacker.ownsInput()) ci.cancel();
    }
}
