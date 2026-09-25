package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;

import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Repeater extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode").description("Repeat all players or whitelist only.")
        .defaultValue(Mode.All).build());

    private final Setting<List<String>> whitelist = sgGeneral.add(new StringListSetting.Builder()
        .name("whitelist").description("Player names to repeat in whitelist mode.")
        .defaultValue(List.of("Steve", "Alex"))
        .visible(() -> mode.get() == Mode.Whitelist)
        .build());

    private final Setting<String> format = sgGeneral.add(new StringSetting.Builder()
        .name("format").description("%s = player name, %n = message content.")
        .defaultValue("%n")
        .build());

    private final Setting<Boolean> ignoreSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-self").description("Skip your own messages.")
        .defaultValue(true).build());

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay").description("Ticks to wait before repeating.")
        .defaultValue(2).min(0).sliderMax(40).build());

    private static final Pattern CHAT_PATTERN = Pattern.compile("<([^>]+)>\\s*(.+)");

    private int pendingTicks = -1;
    private String pendingMessage = null;

    public Repeater() {
        super(AddonTemplate.OTHER, "repeater", "Repeats player chat messages.");
    }

    @Override
    public void onDeactivate() {
        pendingTicks = -1;
        pendingMessage = null;
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (mc.player == null || mc.world == null) return;

        Text content = getMessage(event.packet);
        if (content == null) return;

        String raw = content.getString();
        Matcher m = CHAT_PATTERN.matcher(raw);
        if (!m.find()) return;

        String sender = m.group(1).trim();
        String message = m.group(2).trim();

        if (ignoreSelf.get() && sender.equals(mc.player.getName().getString())) return;

        if (mode.get() == Mode.Whitelist && !whitelist.get().contains(sender)) return;

        pendingMessage = format.get()
            .replace("%s", sender)
            .replace("%n", message);
        pendingTicks = delay.get();
    }

    @EventHandler
    private void onTick(meteordevelopment.meteorclient.events.world.TickEvent.Post event) {
        if (pendingTicks < 0) return;

        if (pendingTicks > 0) { pendingTicks--; return; }

        if (pendingMessage != null && !pendingMessage.isEmpty()) {
            ChatUtils.sendPlayerMsg(pendingMessage);
        }
        pendingMessage = null;
        pendingTicks = -1;
    }

    private Text getMessage(Object packet) {
        for (String name : new String[]{"content", "getContent", "message", "getMessage"}) {
            try {
                Method m = packet.getClass().getMethod(name);
                Object o = m.invoke(packet);
                if (o instanceof Text t) return t;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public enum Mode { All, Whitelist }
}
