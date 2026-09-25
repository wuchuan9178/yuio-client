package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;

public class Animations extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Swing animation style.")
        .defaultValue(Mode.Default)
        .build());

    public final Setting<Boolean> disableSwapMain = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-swap-main")
        .description("Disable main hand swap animation.")
        .defaultValue(true)
        .build());

    public final Setting<Boolean> disableSwapOff = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-swap-off")
        .description("Disable off hand swap animation.")
        .defaultValue(true)
        .build());

    public boolean flip = false;

    private static Animations INSTANCE;

    public Animations() {
        super(AddonTemplate.OTHER, "animations", "Custom first-person swing animations.");
        INSTANCE = this;
    }

    public static Animations get() { return INSTANCE; }

    public enum Mode {
        Normal, Default, One, Two, Three, Four, Five, Six, Seven, Eight, Nine, Ten, Eleven, Twelve, Thirteen, Fourteen
    }

    public boolean shouldAnimate() {
        return isActive() && mode.get() != Mode.Normal;
    }

    @EventHandler
    private void onSend(PacketEvent.Send event) {
        if (event.packet instanceof HandSwingC2SPacket) flip = !flip;
    }

    @Override
    public String getInfoString() {
        return mode.get().name();
    }
}
