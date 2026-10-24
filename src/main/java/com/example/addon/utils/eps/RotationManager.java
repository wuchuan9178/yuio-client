package com.example.addon.utils.eps;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

public class RotationManager {

    public static final RotationManager INSTANCE = new RotationManager();

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // Server = 只转服务端（静默）, Client = 客户端真实视角也转, View = 只在 F5 第三人称转镜头
    public enum HeadTurn {
        Server,
        Client,
        View
    }

    public Rot2f rotations = new Rot2f();
    public Rot2f lastRotations = new Rot2f();
    public Rot2f targetRotations;

    private double speed;
    private boolean active;
    private HeadTurn headTurn = HeadTurn.Server;

    private boolean lookPacketSent;
    private boolean moveFixApplied;
    private float savedYaw;
    private float savedPitch;
    private final Rot2f lastSent = new Rot2f();

    private RotationManager() {
    }

    public void setRotations(Rot2f target, double speed) {
        if (target == null) return;
        this.targetRotations = new Rot2f(target.getYaw(), target.getPitch());
        this.speed = speed;
        this.active = true;

        // 立即朝目标推进一步，避免本 tick 内 raytrace/放置用上一 tick 的旧旋转
        if (mc.player != null) {
            Rot2f next = RotationUtils.smooth(new Rot2f(rotations.getYaw(), rotations.getPitch()), targetRotations, speed);
            rotations.set(next.getYaw(), next.getPitch());
        }
    }

    public void setActive(boolean active) {
        this.active = active;
        if (!active) targetRotations = null;
    }

    public boolean isActive() {
        return active;
    }

    public HeadTurn getHeadTurn() {
        return headTurn;
    }

    public void setHeadTurn(HeadTurn headTurn) {
        this.headTurn = headTurn == null ? HeadTurn.Server : headTurn;
    }

    public void tick() {
        lookPacketSent = false;
        lastRotations.set(rotations.getYaw(), rotations.getPitch());
        if (!active || targetRotations == null || mc.player == null) return;

        Rot2f next = RotationUtils.smooth(new Rot2f(lastRotations.getYaw(), lastRotations.getPitch()), targetRotations, speed);
        rotations.set(next.getYaw(), next.getPitch());

        // Client 模式：把静默旋转真正写到玩家身上，第一人称和 F5 都会跟着转
        if (headTurn == HeadTurn.Client) {
            float yaw = rotations.getYaw();
            float pitch = MathHelper.clamp(rotations.getPitch(), -90.0f, 90.0f);
            mc.player.setYaw(yaw);
            mc.player.setPitch(pitch);
            mc.player.setHeadYaw(yaw);
            mc.player.setBodyYaw(yaw);
        }
    }

    public Rot2f getRotation() {
        if (active) return rotations;
        if (mc.player == null) return new Rot2f(0.0f, 0.0f);
        return new Rot2f(mc.player.getYaw(), mc.player.getPitch());
    }

    public Rot2f getLastRotation() {
        return lastRotations;
    }

    public void markLookPacketSent() {
        lookPacketSent = true;
    }
    public boolean hasSentLookPacket() {
        return lookPacketSent;
    }

    // 同一个角度连发两次，Grim 判 aimduplicatelook
    public boolean isDuplicate(float yaw, float pitch) {
        return Math.abs(yaw - lastSent.getYaw()) < 0.001f && Math.abs(pitch - lastSent.getPitch()) < 0.001f;
    }

    public void markSent(Rot2f rot) {
        if (rot == null) return;
        lastSent.set(rot.getYaw(), rot.getPitch());
        lookPacketSent = true;
    }

    public float getRealYaw() {
        if (moveFixApplied) return savedYaw;
        return mc.player == null ? 0.0f : mc.player.getYaw();
    }

    public float getRealPitch() {
        if (moveFixApplied) return savedPitch;
        return mc.player == null ? 0.0f : mc.player.getPitch();
    }

    // movefix：静默旋转时用旋转角度算移动方向，再把玩家真实视角还原，避免服务端模拟偏差
    public void beginMoveFix() {
        if (moveFixApplied || mc.player == null || !active || headTurn == HeadTurn.Client) return;
        if (targetRotations == null) return;

        savedYaw = mc.player.getYaw();
        savedPitch = mc.player.getPitch();
        mc.player.setYaw(rotations.getYaw());
        mc.player.setPitch(MathHelper.clamp(rotations.getPitch(), -90.0f, 90.0f));
        moveFixApplied = true;
    }

    public void endMoveFix() {
        if (!moveFixApplied) return;
        moveFixApplied = false;
        if (mc.player != null) {
            mc.player.setYaw(savedYaw);
            mc.player.setPitch(savedPitch);
        }
    }
}
