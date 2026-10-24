package com.example.addon.mixin;

import com.example.addon.utils.DummyLike;
import com.example.addon.utils.eps.Rot2f;
import com.example.addon.utils.eps.RotationManager;
import dev.sherii.yuio.utils.InteractUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonNetworkHandler.class)
public class ClientCommonNetworkHandlerMixin {

    // 走封包的攻击（重锤/长矛等）也要能打到假人
    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void yuio$sendPacket(Packet<?> packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (!(packet instanceof PlayerInteractEntityC2SPacket interact)) return;
        if (!InteractUtils.INSTANCE.isAttack(interact)) return;

        Entity target = InteractUtils.INSTANCE.getEntity(interact);
        if (target instanceof DummyLike dummy) {
            dummy.hurtBy(mc.player);
            ci.cancel();
        }
    }

    // 静默旋转：任何移动包都带上 RotationManager 的角度，否则服务端视角不会更新、放方块会被拒
    @ModifyVariable(method = "sendPacket", at = @At("HEAD"), argsOnly = true)
    private Packet<?> yuio$rotateMovePacket(Packet<?> packet) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return packet;
        if (!RotationManager.INSTANCE.isActive()) return packet;
        if (!(packet instanceof PlayerMoveC2SPacket move)) return packet;
        // 本 tick 已经主动发过视角包，就不再改写，避免 Grim 的 AimDuplicateLook
        if (RotationManager.INSTANCE.hasSentLookPacket()) return packet;

        Rot2f rot = RotationManager.INSTANCE.getRotation();
        float yaw = rot.getYaw();
        float pitch = MathHelper.clamp(rot.getPitch(), -90.0f, 90.0f);
        if (Float.isNaN(yaw) || Float.isNaN(pitch)) return packet;

        boolean onGround = move.isOnGround();
        boolean horizontalCollision = move.horizontalCollision();

        // 视角没变就只发位置，重复的 look 会被 Grim 抓
        if (RotationManager.INSTANCE.isDuplicate(yaw, pitch)) {
            return new PlayerMoveC2SPacket.PositionAndOnGround(
                    move.getX(mc.player.getX()),
                    move.getY(mc.player.getY()),
                    move.getZ(mc.player.getZ()),
                    onGround,
                    horizontalCollision
            );
        }

        RotationManager.INSTANCE.markSent(new Rot2f(yaw, pitch));

        if (move.changesPosition()) {
            return new PlayerMoveC2SPacket.Full(
                    move.getX(mc.player.getX()),
                    move.getY(mc.player.getY()),
                    move.getZ(mc.player.getZ()),
                    yaw,
                    pitch,
                    onGround,
                    horizontalCollision
            );
        }

        return new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, onGround, horizontalCollision);
    }
}
