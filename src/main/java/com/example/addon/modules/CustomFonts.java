package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;

public class CustomFonts extends Module {
    public static CustomFonts INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<FontWeight> weight = sgGeneral.add(new EnumSetting.Builder<FontWeight>()
        .name("weight")
        .description("Which font weight to use.")
        .defaultValue(FontWeight.Regular)
        .onChanged(w -> markDirty())
        .build());

    public final Setting<Double> size = sgGeneral.add(new DoubleSetting.Builder()
        .name("size").description("Font size in points.")
        .defaultValue(8).min(1).sliderRange(4, 32)
        .onChanged(v -> markDirty())
        .build());

    public final Setting<Double> oversample = sgGeneral.add(new DoubleSetting.Builder()
        .name("oversample").description("Oversampling factor, higher = sharper.")
        .defaultValue(2.0).min(1).max(8).sliderRange(1, 4)
        .onChanged(v -> markDirty())
        .build());

    public final Setting<Double> shiftX = sgGeneral.add(new DoubleSetting.Builder()
        .name("shift-x").description("Horizontal offset.")
        .defaultValue(0.0).sliderRange(-2, 2)
        .onChanged(v -> markDirty())
        .build());

    public final Setting<Double> shiftY = sgGeneral.add(new DoubleSetting.Builder()
        .name("shift-y").description("Vertical offset.")
        .defaultValue(0.0).sliderRange(-2, 2)
        .onChanged(v -> markDirty())
        .build());

    public final Setting<Boolean> persistent = sgGeneral.add(new BoolSetting.Builder()
        .name("persistent")
        .description("Auto re-enable after world reload.")
        .defaultValue(true)
        .build());

    private volatile boolean dirty = false;
    private long lastChange = 0;

    //全局hook
    private static CustomFontsHook hook;

    public CustomFonts() {
        super(AddonTemplate.OTHER, "custom-fonts",
            "Replace all the fonts in the game with HarmonyOS fonts.");
        INSTANCE = this;
        if (hook == null) {
            hook = new CustomFontsHook();
            MeteorClient.EVENT_BUS.subscribe(hook);
        }
    }

    //监听
    public static class CustomFontsHook {
        @EventHandler
        private void onTick(TickEvent.Post event) {
            CustomFonts cf = INSTANCE;
            if (cf == null) return;
            if (!cf.persistent.get()) return;
            if (cf.isActive()) return;
            if (MinecraftClient.getInstance().world == null) return;
            cf.toggle();
        }
    }

    private void markDirty() {
        dirty = true;
        lastChange = System.currentTimeMillis();
    }

    @Override
    public void onActivate() {
        reloadFonts();
    }

    @Override
    public void onDeactivate() {
        dirty = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!dirty) return;
        if (!isActive()) return;
        if (System.currentTimeMillis() - lastChange < 600) return;
        dirty = false;
        reloadFonts();
    }

    private void reloadFonts() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) mc.reloadResources();
    }

    public enum FontWeight {
        Thin("harmonyos_sans_sc_thin.ttf"),
        Light("harmonyos_sans_sc_light.ttf"),
        Regular("harmonyos_sans_sc_regular.ttf"),
        Medium("harmonyos_sans_sc_medium.ttf"),
        Bold("harmonyos_sans_sc_bold.ttf"),
        Black("harmonyos_sans_sc_black.ttf");

        public final String file;

        FontWeight(String file) {
            this.file = file;
        }
    }
}
