package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AntiLag extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgNotify = settings.createGroup("Notify");

    private final Setting<Boolean> confirm = sgGeneral.add(new BoolSetting.Builder()
        .name("confirm").description("Send a fake teleport confirm to the server.").defaultValue(true).build());

    private final Setting<Boolean> blockVelocity = sgGeneral.add(new BoolSetting.Builder()
        .name("block-velocity").description("Also cancel velocity update packets that cause rubberband.").defaultValue(true).build());

    private final Setting<Boolean> onlyLarge = sgGeneral.add(new BoolSetting.Builder()
        .name("only-large").description("Only cancel large rubberbands (distance > threshold).").defaultValue(false).build());

    private final Setting<Double> threshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("threshold").description("Minimum distance to consider a rubberband as large.").defaultValue(5.0).min(0.5).sliderMax(50.0).visible(onlyLarge::get).build());

    private final Setting<Boolean> antiFake = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-fake").description("Detect and block repeated fake teleports from anti-cheat.").defaultValue(true).build());

    private final Setting<Integer> fakeWindow = sgGeneral.add(new IntSetting.Builder()
        .name("fake-window").description("Milliseconds to track duplicate teleport IDs.").defaultValue(2000).min(500).sliderMax(10000).visible(antiFake::get).build());

    private final Setting<Boolean> notify = sgNotify.add(new BoolSetting.Builder()
        .name("notify").description("Show chat feedback.").defaultValue(true).build());

    private final Setting<String> rubberbandMessage = sgNotify.add(new StringSetting.Builder()
        .name("rubberband-message")
        .description("%id% = teleport id, %x% %y% %z% = target pos")
        .defaultValue("Blocked rubberband (id=%id% -> %x%, %y%, %z%)")
        .visible(notify::get)
        .build());

    private final Setting<String> fakeMessage = sgNotify.add(new StringSetting.Builder()
        .name("fake-message")
        .description("%id% = teleport id")
        .defaultValue("Blocked duplicate fake TP (id=%id%)")
        .visible(notify::get)
        .build());

    private final Setting<String> velocityMessage = sgNotify.add(new StringSetting.Builder()
        .name("velocity-message")
        .description("%x% %y% %z% = velocity")
        .defaultValue("Blocked velocity (%x%, %y%, %z%)")
        .visible(notify::get)
        .build());

    private final Setting<Boolean> notifyVelocity = sgNotify.add(new BoolSetting.Builder()
        .name("notify-velocity").description("Also notify when velocity packet is blocked.").defaultValue(false).visible(notify::get).build());

    private final Setting<Boolean> showPrefix = sgNotify.add(new BoolSetting.Builder()
        .name("show-prefix").description("Show [AntiLag] prefix.").defaultValue(true).visible(notify::get).build());

    private final Set<Integer> recentIds = ConcurrentHashMap.newKeySet();
    private long lastCleanup = 0L;

    public AntiLag() {
        super(AddonTemplate.ASSIST, "anti-lag", "Prevent being pulled back by the server");
    }

    @Override
    public void onDeactivate() {
        recentIds.clear();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null || mc.world == null) return;

        if (event.packet instanceof PlayerPositionLookS2CPacket packet) {
            handlePositionLook(event, packet);
        } else if (blockVelocity.get() && event.packet instanceof EntityVelocityUpdateS2CPacket packet) {
            handleVelocity(event, packet);
        }
    }

    private void handlePositionLook(PacketEvent.Receive event, PlayerPositionLookS2CPacket packet) {
        int id = packet.teleportId();
        Vec3d target = packet.change().position();
        double px = target.x;
        double py = target.y;
        double pz = target.z;

        if (antiFake.get()) {
            cleanup();
            if (!recentIds.add(id)) {
                event.cancel();
                if (notify.get()) {
                    String msg = fakeMessage.get().replace("%id%", String.valueOf(id));
                    notifyChat(msg, Formatting.GOLD);
                }
                return;
            }
        }

        if (onlyLarge.get()) {
            double dx = px - mc.player.getX();
            double dy = py - mc.player.getY();
            double dz = pz - mc.player.getZ();
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) < threshold.get()) return;
        }

        event.cancel();

        if (confirm.get() && mc.player.networkHandler != null) {
            mc.player.networkHandler.sendPacket(new TeleportConfirmC2SPacket(id));
        }

        if (notify.get()) {
            String msg = rubberbandMessage.get()
                .replace("%id%", String.valueOf(id))
                .replace("%x%", String.format("%.1f", px))
                .replace("%y%", String.format("%.1f", py))
                .replace("%z%", String.format("%.1f", pz));
            notifyChat(msg, Formatting.GREEN);
        }
    }

    private void handleVelocity(PacketEvent.Receive event, EntityVelocityUpdateS2CPacket packet) {
        if (packet.getEntityId() != mc.player.getId()) return;

        Vec3d velocity = packet.getVelocity();
        double vx = velocity.x;
        double vy = velocity.y;
        double vz = velocity.z;

        if (Math.abs(vx) + Math.abs(vy) + Math.abs(vz) < 0.5) return;

        event.cancel();
        if (notify.get() && notifyVelocity.get()) {
            String msg = velocityMessage.get()
                .replace("%x%", String.format("%.2f", vx))
                .replace("%y%", String.format("%.2f", vy))
                .replace("%z%", String.format("%.2f", vz));
            notifyChat(msg, Formatting.YELLOW);
        }
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup < fakeWindow.get()) return;
        lastCleanup = now;
        recentIds.clear();
    }

    private void notifyChat(String msg, Formatting color) {
        Text text = showPrefix.get()
            ? Text.literal("[AntiLag] ").formatted(Formatting.AQUA).append(Text.literal(msg).formatted(color))
            : Text.literal(msg).formatted(color);
        ChatUtils.sendMsg(text);
    }
}
