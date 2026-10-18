package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.utils.BlurRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class BlurSettings extends Module {
    private static final int DEFAULT_STRENGTH = 8;
    private static final int DEFAULT_TINT = 120;
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Boolean> enabled = sgGeneral.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Blur the glass panels, off means a flat dark background.")
        .defaultValue(true)
        .build());

    public final Setting<Integer> strength = sgGeneral.add(new IntSetting.Builder()
        .name("blur-strength")
        .description("Blur radius of every glass panel, 0 turns the blur off.")
        .defaultValue(DEFAULT_STRENGTH)
        .min(0)
        .max(BlurRenderer.MAX_STRENGTH)
        .sliderRange(0, BlurRenderer.MAX_STRENGTH)
        .build());

    public final Setting<Integer> tint = sgGeneral.add(new IntSetting.Builder()
        .name("tint-strength")
        .description("How strongly the dark tint replaces the blurred picture.")
        .defaultValue(DEFAULT_TINT)
        .min(0)
        .max(255)
        .sliderRange(0, 255)
        .build());

    public final Setting<Boolean> gui = sgGeneral.add(new BoolSetting.Builder()
        .name("meteor-gui")
        .description("Rounded blur glass for the Meteor GUI windows, headers and tooltips.")
        .defaultValue(true)
        .build());

    public BlurSettings() {
        super(AddonTemplate.SETTINGS, "blur-settings", "Shared blur options for the chat, scoreboard and HUD panels.");
        instance = this;
    }

    private static volatile BlurSettings instance;

    private static BlurSettings instance() {
        BlurSettings module = instance;
        if (module == null) {
            module = Modules.get().get(BlurSettings.class);
            instance = module;
        }

        return module;
    }

    public static boolean blurEnabled() {
        BlurSettings module = instance();
        return module == null || module.enabled.get();
    }

    public static int strength() {
        BlurSettings module = instance();
        if (module == null) return DEFAULT_STRENGTH;
        return module.enabled.get() ? module.strength.get() : 0;
    }

    public static int tint() {
        BlurSettings module = instance();
        return module == null ? DEFAULT_TINT : module.tint.get();
    }

    public static boolean guiGlass() {
        BlurSettings module = instance();
        return module == null || module.gui.get();
    }

    public static void setGuiGlass(boolean value) {
        BlurSettings module = instance();
        if (module != null) module.gui.set(value);
    }
}
