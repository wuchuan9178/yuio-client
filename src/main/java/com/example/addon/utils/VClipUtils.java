package com.example.addon.utils;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

public final class VClipUtils {
    private VClipUtils() {
    }

    public static void teleport(Vec3d target, double step) {
        if (net.minecraft.client.MinecraftClient.getInstance().player == null) return;

        var player = net.minecraft.client.MinecraftClient.getInstance().player;
        Vec3d from = new Vec3d(player.getX(), player.getY(), player.getZ());

        double distance = from.distanceTo(target);
        double maxStep = Math.max(1.0, step);

        if (distance > maxStep) {
            Vec3d dir = target.subtract(from).normalize();
            int segments = (int) Math.ceil(distance / maxStep);
            for (int i = 1; i < segments; i++) {
                send(from.add(dir.multiply(maxStep * i)));
            }
        }

        send(target);
        player.setPosition(target.x, target.y, target.z);
    }

    private static void send(Vec3d pos) {
        var player = net.minecraft.client.MinecraftClient.getInstance().player;
        if (player == null || player.networkHandler == null) return;

        player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            pos.x, pos.y, pos.z, player.isOnGround(), false));
    }
}
