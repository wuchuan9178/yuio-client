package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.utils.BlurRenderer;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class MotionBlur extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> strength = sgGeneral.add(new IntSetting.Builder()
        .name("strength")
        .description("How wide the blur is.")
        .defaultValue(5).min(1).sliderRange(1, 10).build());

    private final Setting<Integer> maxOpacity = sgGeneral.add(new IntSetting.Builder()
        .name("max-opacity")
        .description("How much of the blurred frame is mixed over the screen while moving at full speed.")
        .defaultValue(150).min(10).sliderRange(10, 255).build());

    private final Setting<Double> sensitivity = sgGeneral.add(new DoubleSetting.Builder()
        .name("sensitivity")
        .description("How quickly the effect reaches max-opacity when you move or turn.")
        .defaultValue(1.0).min(0.1).sliderRange(0.1, 5).build());

    private final Setting<Boolean> onlyWhenMoving = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-moving")
        .description("Fade the blur out completely when you stand still and do not turn.")
        .defaultValue(true).build());

    private final Setting<SettingColor> tint = sgGeneral.add(new ColorSetting.Builder()
        .name("tint")
        .description("Colour mixed into the blurred frame. Keep alpha at 0 for no tint.")
        .defaultValue(new SettingColor(255, 255, 255, 0)).build());

    private Vec3d lastPos;
    private float lastYaw;
    private float lastPitch;
    private double smooth;

    public MotionBlur() {
        super(AddonTemplate.OTHER, "motion-blur",
            "Global screen motion blur. Strength follows how fast you move or turn.");
    }

    @Override
    public void onActivate() {
        lastPos = null;
        smooth = 0;
    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Vec3d pos = mc.player.getEntityPos();
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();

        double raw = 0;

        if (lastPos != null) {
            raw = pos.distanceTo(lastPos) * 6.0
                + Math.abs(yaw - lastYaw) / 10.0
                + Math.abs(pitch - lastPitch) / 10.0;
        }

        lastPos = pos;
        lastYaw = yaw;
        lastPitch = pitch;

        double amount = Math.min(1.0, raw * sensitivity.get() * 4.0);
        smooth = smooth + (amount - smooth) * 0.25;

        if (onlyWhenMoving.get() && smooth < 0.02) return;

        int opacity = (int) Math.min(maxOpacity.get(), smooth * maxOpacity.get());
        if (opacity < 4) return;

        BlurRenderer.init();
        BlurRenderer.request(strength.get());
        BlurRenderer.draw(0, 0,
            mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(),
            0, new Color(tint.get().r, tint.get().g, tint.get().b, tint.get().a), opacity);
    }
}
