package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.Map;

public class KeepBouncy extends Module {
    public static KeepBouncy INSTANCE;

    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Double> range = sg.add(new DoubleSetting.Builder()
        .name("range").defaultValue(8).min(0).sliderMax(16).build());
    private final Setting<Double> animationDuration = sg.add(new DoubleSetting.Builder()
        .name("animation-duration")
        .description("Animation cycle duration in milliseconds")
        .defaultValue(1000).min(200).sliderMax(5000).build());
    private final Setting<Double> intensityX = sg.add(new DoubleSetting.Builder()
        .name("intensity-x")
        .description("X axis stretch/squash intensity")
        .defaultValue(-0.3).min(-0.9).build());
    private final Setting<Double> intensityY = sg.add(new DoubleSetting.Builder()
        .name("intensity-y")
        .description("Y axis stretch/squash intensity")
        .defaultValue(0.3).min(-0.9).build());
    private final Setting<Double> intensityZ = sg.add(new DoubleSetting.Builder()
        .name("intensity-z")
        .description("Z axis stretch/squash intensity")
        .defaultValue(0.0).min(-0.9).build());
    private final Setting<Boolean> players = sg.add(new BoolSetting.Builder()
        .name("players").defaultValue(true).build());
    private final Setting<Boolean> mobs = sg.add(new BoolSetting.Builder()
        .name("mobs").defaultValue(true).build());

    private final Map<Entity, Boolean> targets = new HashMap<>();
    private long animationStartTime;

    public KeepBouncy() {
        super(AddonTemplate.MAIN, "keep-bouncy", "Make nearby creatures bouncy");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        animationStartTime = System.currentTimeMillis();
        targets.clear();
    }

    @Override
    public void onDeactivate() {
        targets.clear();
    }

    public boolean isBouncy(Entity entity) {
        return isActive() && targets.containsKey(entity);
    }

    public double getScaleX(Entity entity) {
        return getScale(entity, intensityX.get(), WaveType.X);
    }

    public double getScaleY(Entity entity) {
        return getScale(entity, intensityY.get(), WaveType.Y);
    }

    public double getScaleZ(Entity entity) {
        return getScale(entity, intensityZ.get(), WaveType.Z);
    }

    private double getScale(Entity entity, double intensity, WaveType type) {
        if (!targets.containsKey(entity)) return 1.0;

        long elapsed = System.currentTimeMillis() - animationStartTime;
        double duration = animationDuration.get();
        double cycle = (elapsed % duration) / duration;
        double angle = cycle * Math.PI * 2;

        double wave;
        switch (type) {
            case X -> wave = Math.abs(Math.sin(angle));
            case Y -> wave = -Math.sin(angle);
            case Z -> wave = Math.sin(angle);
            default -> wave = 0;
        }

        return 1.0 + intensity * wave;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        targets.entrySet().removeIf(entry ->
            entry.getKey().isRemoved() ||
                mc.player.distanceTo(entry.getKey()) > range.get() ||
                !(entry.getKey() instanceof LivingEntity)
        );

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !(entity instanceof LivingEntity)) continue;
            if (entity instanceof PlayerEntity && !players.get()) continue;
            if (!(entity instanceof PlayerEntity) && !mobs.get()) continue;
            if (mc.player.distanceTo(entity) > range.get()) continue;
            targets.putIfAbsent(entity, true);
        }
    }

    private enum WaveType { X, Y, Z }
}
