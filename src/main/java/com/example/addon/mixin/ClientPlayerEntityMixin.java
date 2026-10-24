package com.example.addon.mixin;

import com.example.addon.events.PlayerTickEvent;
import com.example.addon.utils.eps.RotationManager;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;tick()V"), cancellable = true)
    private void yuio$playerTickPre(CallbackInfo ci) {
        RotationManager.INSTANCE.tick();

        PlayerTickEvent.Pre event = MeteorClient.EVENT_BUS.post(new PlayerTickEvent.Pre());

        // 让 tickMovement 用旋转角度算移动方向
        RotationManager.INSTANCE.beginMoveFix();

        if (event.isCancelled()) {
            RotationManager.INSTANCE.endMoveFix();
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;tick()V", shift = At.Shift.AFTER))
    private void yuio$playerTickPost(CallbackInfo ci) {
        RotationManager.INSTANCE.endMoveFix();
        MeteorClient.EVENT_BUS.post(new PlayerTickEvent.Post());
    }
}
