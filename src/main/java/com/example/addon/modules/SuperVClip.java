package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public class SuperVClip extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Integer> clipValue = sg.add(new IntSetting.Builder()
        .name("clip-value")
        .description("The height to clip.")
        .defaultValue(160)
        .min(0)
        .sliderMax(256)
        .build());

    private final Setting<Boolean> downClipWhenSneak = sg.add(new BoolSetting.Builder()
        .name("down-clip-when-sneaking")
        .description("Whether clip down when sneaking.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> feedBack = sg.add(new BoolSetting.Builder()
        .name("send-feedback")
        .description("Send feedback while clip")
        .defaultValue(false)
        .build());

    private final Setting<Double> moveDistance = sg.add(new DoubleSetting.Builder()
        .name("move-distance")
        .description("Max distance for a packet to move.")
        .defaultValue(10.0)
        .min(1.0)
        .max(50.0)
        .build());

    public SuperVClip() {
        super(AddonTemplate.ASSIST, "super-vclip", "slip?。");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        double clip = clipValue.get();
        double targetY = downClipWhenSneak.get() && mc.player.isSneaking() ? playerPos.y - clip : playerPos.y + clip;
        Vec3d targetPos = new Vec3d(playerPos.x, targetY, playerPos.z);

        doTp(playerPos, targetPos, moveDistance.get());
        mc.player.setPosition(targetPos.x, targetPos.y, targetPos.z);
        sendPos(targetPos);

        if (feedBack.get()) {
            double moved = targetPos.y - playerPos.y;
            mc.player.sendMessage(Text.literal("VClip " + (moved >= 0 ? "+" : "") + String.format("%.1f", moved) + " m"), false);
        }
        toggle();
    }

    private void doTp(Vec3d from, Vec3d to, double moveDistance) {
        double dist = from.distanceTo(to);
        if (dist <= moveDistance) {
            sendPos(to);
            return;
        }
        Vec3d dir = to.subtract(from).normalize();
        int segments = (int) Math.ceil(dist / moveDistance);
        Vec3d current = from;
        for (int i = 0; i < segments; i++) {
            current = current.add(dir.multiply(moveDistance));
            if (i == segments - 1) current = to;
            sendPos(current);
        }
    }

    private void sendPos(Vec3d pos) {
        if (mc.player != null && mc.player.networkHandler != null) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                pos.x, pos.y, pos.z, mc.player.isOnGround(), false));
        }
    }
}
