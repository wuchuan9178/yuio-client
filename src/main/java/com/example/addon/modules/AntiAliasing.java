package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.utils.RoundRectRenderer;
import dev.sherii.yuio.AModule;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.MathHelper;

import java.util.List;

public class AntiAliasing extends AModule {
    public static AntiAliasing INSTANCE;

    private enum EdgeMode { Linear, Smooth, Soft }
    private enum TargetMode { All, Selected }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAdaptive = settings.createGroup("Adaptive");

    private final Setting<Boolean> enabled = sgGeneral.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Enable anti-aliased rounded corners.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> strength = sgGeneral.add(new DoubleSetting.Builder()
        .name("strength")
        .description("Edge transition width multiplier.")
        .defaultValue(2.5)
        .min(0.25)
        .max(8.0)
        .sliderRange(0.25, 8.0)
        .build()
    );

    private final Setting<Boolean> capsuleCorners = sgGeneral.add(new BoolSetting.Builder()
        .name("capsule-corners")
        .description("Force pill-shaped corners on every glass HUD.")
        .defaultValue(false)
        .build()
    );

    private final Setting<EdgeMode> edgeMode = sgGeneral.add(new EnumSetting.Builder<EdgeMode>()
        .name("edge-mode")
        .description("Edge curve used by the rounded rect shader.")
        .defaultValue(EdgeMode.Smooth)
        .build()
    );

    private final Setting<TargetMode> targetMode = sgGeneral.add(new EnumSetting.Builder<TargetMode>()
        .name("target-mode")
        .description("Apply to every rounded rect or only the selected HUDs.")
        .defaultValue(TargetMode.All)
        .build()
    );

    private final Setting<List<String>> targets = sgGeneral.add(new StringListSetting.Builder()
        .name("targets")
        .description("HUD class names, e.g. YuioIsland, ModuleListHud, NotifyHud.")
        .defaultValue(List.of())
        .visible(() -> targetMode.get() == TargetMode.Selected)
        .build()
    );

    private final Setting<Boolean> adaptive = sgAdaptive.add(new BoolSetting.Builder()
        .name("fps-adaptive")
        .description("Lower the strength when FPS drops, then ease it back up.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> fpsThreshold = sgAdaptive.add(new IntSetting.Builder()
        .name("fps-threshold")
        .description("Below this FPS the strength is reduced.")
        .defaultValue(50)
        .min(10)
        .sliderRange(10, 240)
        .visible(adaptive::get)
        .build()
    );

    private final Setting<Double> minStrength = sgAdaptive.add(new DoubleSetting.Builder()
        .name("min-strength")
        .description("Strength used while FPS is below the threshold.")
        .defaultValue(0.5)
        .min(0.0)
        .max(4.0)
        .sliderRange(0.0, 4.0)
        .visible(adaptive::get)
        .build()
    );

    private final Setting<Double> dropSpeed = sgAdaptive.add(new DoubleSetting.Builder()
        .name("drop-speed")
        .description("How fast the strength falls when FPS is low.")
        .defaultValue(0.35)
        .min(0.01)
        .max(1.0)
        .sliderRange(0.01, 1.0)
        .visible(adaptive::get)
        .build()
    );

    private final Setting<Double> recoverSpeed = sgAdaptive.add(new DoubleSetting.Builder()
        .name("recover-speed")
        .description("How fast the strength returns after FPS recovers.")
        .defaultValue(0.04)
        .min(0.01)
        .max(1.0)
        .sliderRange(0.01, 1.0)
        .visible(adaptive::get)
        .build()
    );

    private final Setting<Boolean> debug = sgAdaptive.add(new BoolSetting.Builder()
        .name("debug")
        .description("Log the effective strength once per second.")
        .defaultValue(false)
        .build()
    );

    private double effective;
    private int ticks;

    public AntiAliasing() {
        super(AddonTemplate.SETTINGS, "anti-aliasing", "Anti-aliased rounded corners with FPS-aware quality scaling.");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        effective = strength.get();
        apply();
    }

    @Override
    public void onDeactivate() {
        RoundRectRenderer.aaEnabled = true;
        RoundRectRenderer.aaStrength = 1.0f;
        RoundRectRenderer.aaEdgeMode = 1;
        RoundRectRenderer.aaFilter = null;
        RoundRectRenderer.capsuleAll = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        double desired = strength.get();
        if (adaptive.get() && mc.getCurrentFps() < fpsThreshold.get()) desired = minStrength.get();

        double speed = MathHelper.clamp(desired < effective ? dropSpeed.get() : recoverSpeed.get(), 0.01, 1.0);
        effective += (desired - effective) * speed;

        apply();

        if (debug.get() && ++ticks % 20 == 0) {
            AddonTemplate.LOG.info("[yuio-aa] strength={} fps={}", String.format("%.2f", effective), mc.getCurrentFps());
        }
    }

    private void apply() {
        RoundRectRenderer.aaEnabled = enabled.get();
        RoundRectRenderer.aaStrength = (float) MathHelper.clamp(effective, 0.0, 8.0);
        RoundRectRenderer.aaEdgeMode = edgeMode.get().ordinal();
        RoundRectRenderer.aaFilter = targetMode.get() == TargetMode.All ? null : context -> targets.get().contains(context);
        RoundRectRenderer.capsuleAll = capsuleCorners.get();
    }
}
