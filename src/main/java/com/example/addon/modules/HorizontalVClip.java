package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

public class HorizontalVClip extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> distance = sgGeneral.add(new DoubleSetting.Builder()
        .name("distance")
        .description("Horizontal distance to clip.")
        .defaultValue(10.0)
        .min(1)
        .sliderMax(100)
        .build());

    private final Setting<Boolean> useMoveDistance = sgGeneral.add(new BoolSetting.Builder()
        .name("split-packets")
        .description("Split the teleport into multiple packets to avoid anti-cheat kicks.")
        .defaultValue(true)
        .build());

    private final Setting<Double> maxStep = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-step")
        .description("Max distance per packet when splitting.")
        .defaultValue(10.0)
        .min(1)
        .sliderMax(50)
        .visible(useMoveDistance::get)
        .build());

    public HorizontalVClip() {
        super(AddonTemplate.ASSIST, "horizontal-vclip", "Perform a horizontal vclip");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        double yaw = Math.toRadians(mc.player.getYaw());
        double dx = -Math.sin(yaw) * distance.get();
        double dz = Math.cos(yaw) * distance.get();

        double startX = mc.player.getX();
        double startY = mc.player.getY();
        double startZ = mc.player.getZ();
        double endX = startX + dx;
        double endZ = startZ + dz;

        if (useMoveDistance.get()) {
            //分段传送
            double totalDist = Math.sqrt(dx * dx + dz * dz);
            double step = maxStep.get();
            int segments = (int) Math.ceil(totalDist / step);
            for (int i = 1; i <= segments; i++) {
                double t = (double) i / segments;
                double px = startX + dx * t;
                double pz = startZ + dz * t;
                mc.player.setPosition(px, startY, pz);
                sendPacket(px, startY, pz);
            }
        } else {
            //一步
            mc.player.setPosition(endX, startY, endZ);
            sendPacket(endX, startY, endZ);
        }

        toggle();
    }

    private void sendPacket(double x, double y, double z) {
        if (mc.player == null || mc.player.networkHandler == null) return;
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            x, y, z, mc.player.isOnGround(), false));
    }

    @Override
    public String getInfoString() {
        return distance.get() + "m";
    }
}
