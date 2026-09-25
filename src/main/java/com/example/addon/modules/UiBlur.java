package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public class UiBlur extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // 0=无 1=聊天 2=计分板 3=Tab
    public static final ThreadLocal<Integer> HUD_CTX = ThreadLocal.withInitial(() -> 0);

    private final Setting<Boolean> chat = sgGeneral.add(new BoolSetting.Builder()
        .name("chat").description("Custom background for chat.").defaultValue(true).build());

    private final Setting<Boolean> scoreboard = sgGeneral.add(new BoolSetting.Builder()
        .name("scoreboard").description("Custom background for scoreboard.").defaultValue(true).build());

    private final Setting<Boolean> tab = sgGeneral.add(new BoolSetting.Builder()
        .name("tab").description("Custom background for tab list.").defaultValue(true).build());

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color").description("Background color.")
        .defaultValue(new SettingColor(10, 10, 10, 160))
        .build());

    public UiBlur() {
        super(AddonTemplate.OTHER, "ui-blur", "Change the GUI's background color");
    }

    public static UiBlur get() {
        return Modules.get().get(UiBlur.class);
    }

    public boolean chat() { return isActive() && chat.get(); }
    public boolean scoreboard() { return isActive() && scoreboard.get(); }
    public boolean tab() { return isActive() && tab.get(); }

    public int colorArgb() {
        SettingColor c = color.get();
        return (c.a << 24) | (c.r << 16) | (c.g << 8) | c.b;
    }
}
