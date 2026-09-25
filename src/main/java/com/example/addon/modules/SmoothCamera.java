package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.Vec3d;

public class SmoothCamera extends Module {
    public static SmoothCamera INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Boolean> enableFirstPov = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-first-pov").description("Also smooth in first person.")
        .defaultValue(false).build());

    public final Setting<Boolean> resetOnPerspectiveChange = sgGeneral.add(new BoolSetting.Builder()
        .name("reset-on-perspective-change").description("Reset smoothing when switching perspective.")
        .defaultValue(true).build());

    public final Setting<Double> factorH = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal-factor").description("Higher = smoother.")
        .defaultValue(0.75).min(0.0).max(0.99).sliderRange(0.0, 0.99).build());

    public final Setting<Double> factorV = sgGeneral.add(new DoubleSetting.Builder()
        .name("vertical-factor").description("Higher = smoother.")
        .defaultValue(0.80).min(0.0).max(0.99).sliderRange(0.0, 0.99).build());

    private Vec3d smoothPos = Vec3d.ZERO;
    private Perspective lastPerspective = Perspective.FIRST_PERSON;
    private boolean initialized = false;

    public SmoothCamera() {
        super(AddonTemplate.OTHER, "smooth-camera", "Makes your camera move smoother.");
        INSTANCE = this;
    }

    @Override
    public void onDeactivate() {
        smoothPos = Vec3d.ZERO;
        initialized = false;
    }

    public Vec3d update(Vec3d pos, double dt) {
        Perspective perspective = mc.options.getPerspective();

        if (!initialized) {
            smoothPos = pos;
            lastPerspective = perspective;
            initialized = true;
            return pos;
        }

        if (resetOnPerspectiveChange.get() && lastPerspective != perspective) {
            smoothPos = pos;
            lastPerspective = perspective;
            return pos;
        }
        lastPerspective = perspective;

        if (!enableFirstPov.get() && perspective.isFirstPerson()) {
            smoothPos = pos;
            return pos;
        }

        double fh = Math.pow(factorH.get(), dt * 60.0);
        double fv = Math.pow(factorV.get(), dt * 60.0);

        smoothPos = new Vec3d(
            smoothPos.x * fh + pos.x * (1 - fh),
            smoothPos.y * fv + pos.y * (1 - fv),
            smoothPos.z * fh + pos.z * (1 - fh)
        );
        return smoothPos;
    }
}
