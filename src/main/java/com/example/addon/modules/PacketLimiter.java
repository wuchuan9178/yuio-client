package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PacketLimiter extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgNotify = settings.createGroup("Notify");

    private final Setting<Integer> maxPps = sgGeneral.add(new IntSetting.Builder()
        .name("max-pps")
        .description("Maximum outgoing packets per second. Extra packets are cancelled. 0 = disabled.")
        .defaultValue(200)
        .min(0)
        .sliderMax(1000)
        .build());

    private final Setting<List<String>> whitelist = sgGeneral.add(new StringListSetting.Builder()
        .name("whitelist")
        .description("Simple class names of packets that are never limited.")
        .defaultValue(List.of(
            "KeepAliveC2SPacket",
            "PongC2SPacket",
            "TeleportConfirmC2SPacket",
            "ClientStatusC2SPacket",
            "ChatMessageC2SPacket",
            "CommandExecutionC2SPacket",
            "QueryBlockNbtC2SPacket",
            "RequestCommandCompletionsC2SPacket"
        ))
        .build());

    private final Setting<Boolean> notify = sgNotify.add(new BoolSetting.Builder()
        .name("notify")
        .description("Chat feedback when packets are dropped.")
        .defaultValue(true)
        .build());

    private final Setting<String> notifyMessage = sgNotify.add(new StringSetting.Builder()
        .name("message")
        .description("%packet% = packet name, %count% = dropped pps")
        .defaultValue("Dropped %packet% (%count% pps)")
        .visible(notify::get)
        .build());

    private final Setting<Boolean> showPrefix = sgNotify.add(new BoolSetting.Builder()
        .name("show-prefix")
        .description("Show [PacketLimiter] prefix.")
        .defaultValue(true)
        .visible(notify::get)
        .build());

    private final Setting<Integer> notifyInterval = sgNotify.add(new IntSetting.Builder()
        .name("notify-interval")
        .description("Minimum ms between notifications.")
        .defaultValue(1000)
        .min(100)
        .sliderMax(10000)
        .visible(notify::get)
        .build());

    private final Set<String> whitelistSet = ConcurrentHashMap.newKeySet();

    private long secondStart = 0L;
    private int countThisSecond = 0;
    private int droppedThisSecond = 0;
    private long lastNotify = 0L;

    public PacketLimiter() {
        super(AddonTemplate.ASSIST, "packet-limiter", "Limit outgoing packets per second to avoid server kicks.");
    }

    @Override
    public void onActivate() {
        rebuildWhitelist();
        secondStart = System.currentTimeMillis();
        countThisSecond = 0;
        droppedThisSecond = 0;
    }

    @Override
    public void onDeactivate() {
        whitelistSet.clear();
    }

    @EventHandler
    private void onSend(PacketEvent.Send event) {
        if (mc.player == null) return;

        String simple = event.packet.getClass().getSimpleName();
        if (whitelistSet.contains(simple)) return;

        long now = System.currentTimeMillis();
        if (now - secondStart >= 1000L) {
            secondStart = now;
            countThisSecond = 0;
            droppedThisSecond = 0;
        }

        if (maxPps.get() > 0 && countThisSecond >= maxPps.get()) {
            event.cancel();
            droppedThisSecond++;
            maybeNotify(simple);
            return;
        }

        countThisSecond++;
    }

    private void maybeNotify(String packetName) {
        if (!notify.get()) return;
        long now = System.currentTimeMillis();
        if (now - lastNotify < notifyInterval.get()) return;
        lastNotify = now;

        String body = notifyMessage.get()
            .replace("%packet%", packetName)
            .replace("%count%", String.valueOf(droppedThisSecond));

        Text text = showPrefix.get()
            ? Text.literal("[PacketLimiter] ").formatted(Formatting.AQUA)
            .append(Text.literal(body).formatted(Formatting.YELLOW))
            : Text.literal(body).formatted(Formatting.YELLOW);
        ChatUtils.sendMsg(text);
    }

    private void rebuildWhitelist() {
        whitelistSet.clear();
        for (String s : whitelist.get()) {
            if (s != null && !s.isBlank()) whitelistSet.add(s.trim());
        }
    }

    @Override
    public String getInfoString() {
        return countThisSecond + " pps";
    }
}
