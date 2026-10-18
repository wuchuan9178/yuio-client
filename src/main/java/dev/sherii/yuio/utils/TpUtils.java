package dev.sherii.yuio.utils;

import dev.sherii.yuio.modules.settings.TpSetting;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.mixininterface.IPlayerMoveC2SPacket;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.OnGroundOnly;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class TpUtils {
    public static final TpUtils INSTANCE = new TpUtils();

    private TpUtils() {
    }


    public void tp(Vec3d from, Vec3d to, float yaw, float pitch, boolean onGround) {
        boolean ground = resolveGround(onGround);

        if (TpSetting.INSTANCE.isSmooth()) {
            double step = TpSetting.INSTANCE.getMoveDistance();
            if (step > 0.0) {
                double distance = from.distanceTo(to);
                int steps = (int) Math.ceil(distance / step);

                Vec3d last = from;
                for (int i = 1; i < steps; i++) {
                    Vec3d point = from.lerp(to, (double) i / steps);
                    if (point.squaredDistanceTo(last) < 1.0E-6) continue;
                    sendTeleport(point, yaw, pitch, ground);
                    last = point;
                }
            }
        }

        int repeat = Math.max(1, TpSetting.INSTANCE.getRepeat());
        for (int i = 0; i < repeat; i++) {
            sendTeleport(to, yaw, pitch, ground);
        }

        if (TpSetting.INSTANCE.isApplyClientPos()) {
            setPos(to);
        }
    }

    public void tp(Vec3d from, Vec3d to, boolean onGround) {
        ClientPlayerEntity player = MeteorClient.mc.player;
        float yaw = player != null ? player.getYaw() : 0.0F;
        float pitch = player != null ? player.getPitch() : 0.0F;
        tp(from, to, yaw, pitch, onGround);
    }

    public void tp3rd(Vec3d from, Vec3d to, boolean onGround) {
        if (isPathMiss(from, to)) {
            tp(from, to, onGround);
            return;
        }

        Double vclip = FindUtils.INSTANCE.findVClip(from, to);
        if (vclip == null) {
            tp(from, to, onGround);
            return;
        }

        Vec3d fromVClipPos = new Vec3d(from.x, vclip, from.z);
        Vec3d toVClipPos = new Vec3d(to.x, vclip, to.z);
        tp(from, fromVClipPos, onGround);
        tp(fromVClipPos, toVClipPos, onGround);
        tp(toVClipPos, to, onGround);
    }

    private void sendTeleport(Vec3d pos, float yaw, float pitch, boolean onGround) {
        switch (TpSetting.INSTANCE.getPacketMode()) {
            case Position -> sendMovePacket(pos, onGround, null);
            case PositionRotation -> sendMovePacket(pos, yaw, pitch, onGround, null);
            case Rotation -> sendMovePacket(yaw, pitch, onGround, null);
            case Ground -> sendMovePacket(onGround, null);
        }
    }

    private boolean resolveGround(boolean onGround) {
        return switch (TpSetting.INSTANCE.getGroundMode()) {
            case Keep -> onGround;
            case ForceTrue -> true;
            case ForceFalse -> false;
        };
    }

    // helpers

    public boolean isPathMiss(Vec3d from, Vec3d to) {
        if (MeteorClient.mc.player == null) {
            return false;
        }

        ClientPlayerEntity player = MeteorClient.mc.player;
        Vec3d playerPos = player.getEntityPos();
        Vec3d diff = to.subtract(from);
        boolean doUpdate = !java.util.Objects.equals(from, playerPos);
        if (doUpdate) {
            setPos(from);
        }

        boolean isMiss = java.util.Objects.equals(player.adjustMovementForCollisions(diff), diff);
        if (doUpdate) {
            setPos(playerPos);
        }

        return isMiss;
    }

    public boolean isPlayerSafe(BlockPos pos) {
        if (MeteorClient.mc.player == null) {
            return false;
        }

        Integer height = FindUtils.INSTANCE.getPlayerHeight();
        if (height == null) {
            return false;
        }

        for (int i = 0; i < height; i++) {
            BlockPos newPos = pos.add(0, i, 0);
            BlockState state = ChunkBuffer.INSTANCE.getBlockState(newPos);
            if (state == null) {
                return false;
            }
            if (!FindUtils.INSTANCE.isAir(newPos, state)) {
                return false;
            }
        }

        return true;
    }

    public void setPos(Vec3d pos) {
        if (MeteorClient.mc.player == null) return;

        ClientPlayerEntity player = MeteorClient.mc.player;
        player.pos = pos;
        player.setBoundingBox(player.calculateBoundingBox());
    }

    // packet senders

    public void sendMovePacket(double x, double y, double z, float yaw, float pitch, boolean onGround, Integer tag) {
        ClientPlayerEntity player = MeteorClient.mc.player;
        if (player == null) return;

        player.setSneaking(false);
        Full packet = new Full(x, y, z, yaw, pitch, onGround, false);
        applyTag(packet, tag);
        send(player, packet);
    }

    public void sendMovePacket(Vec3d pos, float yaw, float pitch, boolean onGround, Integer tag) {
        sendMovePacket(pos.x, pos.y, pos.z, yaw, pitch, onGround, tag);
    }

    public void sendMovePacket(double x, double y, double z, boolean onGround, Integer tag) {
        ClientPlayerEntity player = MeteorClient.mc.player;
        if (player == null) return;

        player.setSneaking(false);
        PositionAndOnGround packet = new PositionAndOnGround(x, y, z, onGround, false);
        applyTag(packet, tag);
        send(player, packet);
    }

    public void sendMovePacket(Vec3d pos, boolean onGround, Integer tag) {
        sendMovePacket(pos.x, pos.y, pos.z, onGround, tag);
    }

    public void sendMovePacket(float yaw, float pitch, boolean onGround, Integer tag) {
        ClientPlayerEntity player = MeteorClient.mc.player;
        if (player == null) return;

        LookAndOnGround packet = new LookAndOnGround(yaw, pitch, onGround, false);
        applyTag(packet, tag);
        send(player, packet);
    }

    public void sendMovePacket(boolean onGround, Integer tag) {
        ClientPlayerEntity player = MeteorClient.mc.player;
        if (player == null) return;

        OnGroundOnly packet = new OnGroundOnly(onGround, false);
        applyTag(packet, tag);
        send(player, packet);
    }

    private void applyTag(Packet<?> packet, Integer tag) {
        if (tag == null) return;
        ((IPlayerMoveC2SPacket) packet).meteor$setTag(tag);
    }

    private void send(ClientPlayerEntity player, Packet<?> packet) {
        if (player.networkHandler == null) return;
        player.networkHandler.sendPacket(packet);
    }
}
