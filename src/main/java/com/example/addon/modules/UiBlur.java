package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class UiBlur extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgChat = settings.createGroup("Chat");
    private final SettingGroup sgScoreboard = settings.createGroup("Scoreboard");

    public final Setting<Double> radius = sgGeneral.add(new DoubleSetting.Builder()
        .name("radius")
        .description("Corner radius of the panels.")
        .defaultValue(6.0)
        .min(0.0)
        .max(24.0)
        .sliderRange(0.0, 24.0)
        .build());

    public final Setting<Boolean> chat = sgChat.add(new BoolSetting.Builder()
        .name("chat")
        .description("Replace the chat background.")
        .defaultValue(true)
        .build());

    public final Setting<Integer> chatAlpha = sgChat.add(new IntSetting.Builder()
        .name("chat-alpha")
        .description("Panel opacity, the vanilla fade still applies on top of it.")
        .defaultValue(175)
        .min(0)
        .max(255)
        .sliderRange(0, 255)
        .build());

    public final Setting<Double> chatPadding = sgChat.add(new DoubleSetting.Builder()
        .name("chat-padding")
        .description("Extra space around the chat text.")
        .defaultValue(3.0)
        .min(0.0)
        .max(10.0)
        .sliderRange(0.0, 10.0)
        .build());

    public final Setting<Boolean> scoreboard = sgScoreboard.add(new BoolSetting.Builder()
        .name("scoreboard")
        .description("Replace the scoreboard background.")
        .defaultValue(true)
        .build());

    public final Setting<Integer> scoreboardAlpha = sgScoreboard.add(new IntSetting.Builder()
        .name("scoreboard-alpha")
        .description("Panel opacity.")
        .defaultValue(175)
        .min(0)
        .max(255)
        .sliderRange(0, 255)
        .build());

    public final Setting<Double> scoreboardPadding = sgScoreboard.add(new DoubleSetting.Builder()
        .name("scoreboard-padding")
        .description("Extra space around the scoreboard.")
        .defaultValue(3.0)
        .min(0.0)
        .max(10.0)
        .sliderRange(0.0, 10.0)
        .build());

    public UiBlur() {
        super(AddonTemplate.OTHER, "ui-blur", "Blurred rounded backgrounds for the vanilla chat and scoreboard.");
    }

    public static UiBlur active() {
        UiBlur module = Modules.get().get(UiBlur.class);
        return module != null && module.isActive() ? module : null;
    }
}
