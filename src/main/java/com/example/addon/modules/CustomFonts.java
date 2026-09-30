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

    private static final long DEBOUNCE_MS = 1500;

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
        .description("Turn the font back on once when you join a world.")
        .defaultValue(true)
        .build());

    public volatile boolean applied;

    private volatile boolean dirty;
    private long lastChange;

    private static CustomFontsHook hook;

    private volatile boolean pendingReload;

    private void requestReload() {
        pendingReload = true;
    }

    public CustomFonts() {
        super(AddonTemplate.OTHER, "custom-fonts",
            "Replace all the fonts in the game with HarmonyOS fonts.");
        INSTANCE = this;

        if (hook == null) {
            hook = new CustomFontsHook();
            MeteorClient.EVENT_BUS.subscribe(hook);
        }
    }

    public static class CustomFontsHook {
        private Object lastWorld;

        @EventHandler
        private void onTick(TickEvent.Post event) {
            CustomFonts fonts = INSTANCE;
            if (fonts == null || !fonts.persistent.get()) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (fonts.pendingReload && mc.world != null && mc.currentScreen == null && mc.player != null) { fonts.pendingReload = false; fonts.reloadFonts(); return; }

            if (mc.world == null) {
                lastWorld = null;
                return;
            }

            if (mc.world == lastWorld) return;
            lastWorld = mc.world;

            if (!fonts.isActive()) fonts.toggle();
        }
    }

    private void markDirty() {
        dirty = true;
        lastChange = System.currentTimeMillis();
    }

    @Override
    public void onActivate() {
        dirty = false;
        requestReload();
    }

    @Override
    public void onDeactivate() {
        dirty = false;

        if (applied) {
            applied = false;
            requestReload();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!dirty || !isActive()) return;
        if (System.currentTimeMillis() - lastChange < DEBOUNCE_MS) return;

        dirty = false;
        requestReload();
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

