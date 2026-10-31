package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.utils.VClipUtils;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

public class RandomTp extends Module {
    public enum Mode {
        Vertical,
        ThreeD
    }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Mode> mode = sg.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Vertical = only random height. ThreeD = random position inside a box.")
        .defaultValue(Mode.Vertical)
        .build());

    private final Setting<Double> range = sg.add(new DoubleSetting.Builder()
        .name("range")
        .description("Horizontal radius for ThreeD mode.")
        .defaultValue(6.0)
        .min(1.0)
        .sliderRange(1.0, 24.0)
        .visible(() -> mode.get() == Mode.ThreeD)
        .build());

    private final Setting<Double> heightRange = sg.add(new DoubleSetting.Builder()
        .name("height-range")
        .description("Random height offset, positive and negative.")
        .defaultValue(6.0)
        .min(1.0)
        .sliderRange(1.0, 32.0)
        .build());

    private final Setting<Integer> delay = sg.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between two random teleports.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 60)
        .build());

    private final Setting<Double> clipStep = sg.add(new DoubleSetting.Builder()
        .name("clip-step")
        .description("Max distance per move packet.")
        .defaultValue(10.0)
        .min(1.0)
        .sliderRange(1.0, 32.0)
        .build());

    private final Setting<Boolean> fixedCenter = sg.add(new BoolSetting.Builder()
        .name("fixed-center")
        .description("Lock the center point when the module is enabled.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> renderCenter = sg.add(new BoolSetting.Builder()
        .name("render-center")
        .description("Render the locked center point.")
        .defaultValue(true)
        .visible(fixedCenter::get)
        .build());

    private final Color sideColor = new Color(120, 200, 255, 50);
    private final Color lineColor = new Color(120, 200, 255, 255);

    private final Random random = new Random();
    private int timer;
    private Vec3d center;

    public RandomTp() {
        super(AddonTemplate.ASSIST, "random-tp", "Randomly vclips around you to dodge predictions.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        center = mc.player != null ? mc.player.getEntityPos() : null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        if (++timer < delay.get()) return;
        timer = 0;

        Vec3d pos = fixedCenter.get() && center != null
            ? center
            : new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        double dy = (random.nextDouble() * 2.0 - 1.0) * heightRange.get();

        Vec3d target;
        if (mode.get() == Mode.Vertical) {
            target = new Vec3d(pos.x, pos.y + dy, pos.z);
        } else {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = Math.sqrt(random.nextDouble()) * range.get();
            target = new Vec3d(pos.x + Math.cos(angle) * radius, pos.y + dy, pos.z + Math.sin(angle) * radius);
        }

        VClipUtils.teleport(target, clipStep.get());
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!renderCenter.get() || center == null) return;
        Box box = new Box(center.x - 0.5, center.y - 0.5, center.z - 0.5,
            center.x + 0.5, center.y + 0.5, center.z + 0.5);
        event.renderer.box(box, sideColor, lineColor, ShapeMode.Both, 0);
    }
}
