package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class PacketLimitTester extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> ratePerTick = sgGeneral.add(new IntSetting.Builder()
        .name("rate-per-tick")
        .description("Junk packets sent per tick.")
        .defaultValue(5).min(1).sliderMax(100).build());

    private final Setting<Boolean> notifyBurst = sgGeneral.add(new BoolSetting.Builder()
        .name("notify-burst")
        .description("Chat message each second with current pps. Spammy.")
        .defaultValue(false).build());

    private final Setting<Boolean> toggleOnKick = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-on-kick")
        .description("Disable module when kicked.")
        .defaultValue(true).build());

    private static long lastLimit = 0L;
    private static boolean hasResult = false;

    private long secondStart = 0L;
    private long sessionPackets = 0L;
    private long packetsAtLastSecond = 0L;
    private long peakPerSecond = 0L;
    private boolean wasInWorld = false;
    private boolean awaitingRejoin = false;
    private boolean toggledOff = false;

    public PacketLimitTester() {
        super(AddonTemplate.ASSIST, "packet-limit-tester", "Floods junk packets to measure server packet limit.");
    }

    @Override
    public void onActivate() {
        if (mc.world != null && hasResult) {
            showResult(lastLimit);
            hasResult = false;
        }

        secondStart = System.currentTimeMillis();
        sessionPackets = 0L;
        packetsAtLastSecond = 0L;
        peakPerSecond = 0L;
        wasInWorld = mc.world != null;
        awaitingRejoin = false;
        toggledOff = false;
    }

    @Override
    public void onDeactivate() {
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        boolean inWorld = mc.world != null && mc.player != null;

        if (wasInWorld && !inWorld) {
            updatePeak();
            lastLimit = peakPerSecond;
            hasResult = true;

            ChatUtils.sendMsg(Text.literal("[PacketLimit] ").formatted(Formatting.AQUA)
                .append(Text.literal("Kicked. Measured limit: ").formatted(Formatting.WHITE))
                .append(Text.literal(lastLimit + " pps").formatted(Formatting.YELLOW)));

            if (toggleOnKick.get()) {
                awaitingRejoin = true;
                toggledOff = true;
            }
        }

        wasInWorld = inWorld;

        if (!inWorld) return;

        if (awaitingRejoin) {
            showResult(lastLimit);
            hasResult = false;
            awaitingRejoin = false;
            toggle();
            return;
        }

        if (toggledOff) return;

        for (int i = 0; i < ratePerTick.get(); i++) {
            sendJunk();
        }
        sessionPackets++;

        long now = System.currentTimeMillis();
        if (now - secondStart >= 1000L) {
            long perSecond = sessionPackets - packetsAtLastSecond;
            if (perSecond > peakPerSecond) peakPerSecond = perSecond;
            packetsAtLastSecond = sessionPackets;
            secondStart = now;

            if (notifyBurst.get()) {
                ChatUtils.sendMsg(Text.literal("[PacketLimit] ").formatted(Formatting.AQUA)
                    .append(Text.literal(perSecond + " pps, peak " + peakPerSecond + " pps").formatted(Formatting.GRAY)));
            }
        }
    }

    private void updatePeak() {
        long now = System.currentTimeMillis();
        long elapsed = Math.max(1L, now - secondStart);
        long perSecond = (sessionPackets - packetsAtLastSecond) * 1000L / elapsed;
        if (perSecond > peakPerSecond) peakPerSecond = perSecond;
    }

    private void showResult(long limit) {
        ChatUtils.sendMsg(Text.literal("[PacketLimit] ").formatted(Formatting.AQUA)
            .append(Text.literal("Last server packet limit: ").formatted(Formatting.WHITE))
            .append(Text.literal(limit + " pps").formatted(Formatting.GREEN)));
    }

    private void sendJunk() {
        if (mc.player == null || mc.player.networkHandler == null) return;
        float yaw = mc.player.getYaw() + (float) (Math.random() * 0.002 - 0.001);
        float pitch = mc.player.getPitch() + (float) (Math.random() * 0.002 - 0.001);
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
            yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
    }

    @Override
    public String getInfoString() {
        return peakPerSecond + " pps";
    }
}
