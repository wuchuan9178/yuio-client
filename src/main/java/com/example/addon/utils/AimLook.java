package com.example.addon.utils;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class AimLook {
    private static float lastYaw = Float.NaN;
    private static float lastPitch = Float.NaN;

    private AimLook() {
    }

    public static void lookAt(Vec3d point, float smooth) {
        ClientPlayerEntity player = MeteorClient.mc.player;
        if (player == null || point == null) return;

        Vec3d eye = player.getEyePos();
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, horizontal));

        apply(player, yaw, pitch, smooth);
    }

    public static void lookAt(Entity entity, float smooth) {
        if (entity == null) return;

        lookAt(entity.getBoundingBox().getCenter(), smooth);
    }

    public static void stopLooking() {
        lastYaw = Float.NaN;
        lastPitch = Float.NaN;
    }

    private static void apply(ClientPlayerEntity player, float targetYaw, float targetPitch, float smooth) {
        float step = MathHelper.clamp(smooth, 0.0f, 1.0f);
        float baseYaw = Float.isNaN(lastYaw) ? player.getYaw() : lastYaw;
        float basePitch = Float.isNaN(lastPitch) ? player.getPitch() : lastPitch;

        float yaw = MathHelper.wrapDegrees(baseYaw + MathHelper.wrapDegrees(targetYaw - baseYaw) * step);
        float pitch = MathHelper.clamp(basePitch + (targetPitch - basePitch) * step, -90.0f, 90.0f);

        player.setYaw(yaw);
        player.setPitch(pitch);
        player.setHeadYaw(yaw);
        player.setBodyYaw(yaw);

        lastYaw = yaw;
        lastPitch = pitch;
    }
}
