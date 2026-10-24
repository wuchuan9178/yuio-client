package com.example.addon.mixin;

import com.example.addon.utils.DummyLike;
import com.example.addon.utils.ForcedFriends;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    @Inject(
        method = "attackEntity(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void yuio$attackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (ForcedFriends.protects(target)) {
            ci.cancel();
            return;
        }

        // 假人只存在于本地，伤害自己结算
        if (target instanceof DummyLike dummy) {
            dummy.hurtBy(player);
            player.swingHand(Hand.MAIN_HAND);
            ci.cancel();
        }
    }
}
