package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.utils.YuioSounds;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

public class FriendOfAlcohol extends Module {
    public static FriendOfAlcohol INSTANCE;

    private final SettingGroup sg = settings.getDefaultGroup();
    private final SettingGroup sgRing = settings.createGroup("Ring");

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
        .defaultValue(0.6).min(-0.9).build());
    private final Setting<Double> intensityZ = sg.add(new DoubleSetting.Builder()
        .name("intensity-z")
        .description("Z axis stretch/squash intensity")
        .defaultValue(0.0).min(-0.9).build());
    private final Setting<Boolean> players = sg.add(new BoolSetting.Builder()
        .name("players").defaultValue(true).build());
    private final Setting<Boolean> mobs = sg.add(new BoolSetting.Builder()
        .name("mobs").defaultValue(true).build());
    private final Setting<Boolean> includeSelf = sg.add(new BoolSetting.Builder()
        .name("include-self")
        .description("Also squash your own model.")
        .defaultValue(true).build());

    private final Setting<Boolean> moveEntities = sgRing.add(new BoolSetting.Builder()
        .name("move-entities")
        .description("Slowly drag nearby creatures onto a ring around you, then orbit them.")
        .defaultValue(true).build());
    private final Setting<Boolean> movePlayers = sgRing.add(new BoolSetting.Builder()
        .name("move-players")
        .description("Also move other players locally.")
        .defaultValue(false).build());
    private final Setting<Double> ringRadius = sgRing.add(new DoubleSetting.Builder()
        .name("ring-radius")
        .description("Radius of the ring around you.")
        .defaultValue(6.0).min(1.0).sliderRange(1.0, 24.0).build());
    private final Setting<Double> approachSpeed = sgRing.add(new DoubleSetting.Builder()
        .name("approach-speed")
        .description("Blocks per tick toward the ring.")
        .defaultValue(0.35).min(0.05).sliderRange(0.05, 2.0).build());
    private final Setting<Double> orbitSpeed = sgRing.add(new DoubleSetting.Builder()
        .name("orbit-speed")
        .description("Degrees per tick while orbiting.")
        .defaultValue(8.0).min(0.5).sliderRange(0.5, 30.0).build());

    private final Setting<Boolean> playMusic = sgRing.add(new BoolSetting.Builder()
        .name("play-music")
        .description("Play the song while the module is active.")
        .defaultValue(true).build());

    private final Map<Entity, Boolean> targets = new HashMap<>();
    private final Map<Entity, Double> angles = new HashMap<>();
    private final Map<Entity, Vec3d> renderPos = new HashMap<>();
    private long animationStartTime;
    private SoundInstance song;

    public FriendOfAlcohol() {
        super(AddonTemplate.OTHER, "friend-of-alcohol", "朋友的酒 - make nearby creatures bouncy and orbit them.");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        animationStartTime = System.currentTimeMillis();
        targets.clear();
        angles.clear();
        renderPos.clear();
        playSong();
    }

    @Override
    public void onDeactivate() {
        targets.clear();
        angles.clear();
        renderPos.clear();
        stopSong();
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

        if (includeSelf.get()) targets.putIfAbsent(mc.player, true);

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !(entity instanceof LivingEntity)) continue;
            if (entity instanceof PlayerEntity && !players.get()) continue;
            if (!(entity instanceof PlayerEntity) && !mobs.get()) continue;
            if (mc.player.distanceTo(entity) > range.get()) continue;
            targets.putIfAbsent(entity, true);
        }

        if (moveEntities.get()) tickRing();
        else renderPos.clear();
    }

    private void tickRing() {
        Vec3d center = mc.player.getEntityPos();
        double radius = ringRadius.get();
        double reach = range.get() + radius + 6.0;

        angles.keySet().removeIf(entity ->
            entity.isRemoved() || mc.player.distanceTo(entity) > reach || !(entity instanceof LivingEntity));
        renderPos.keySet().removeIf(entity -> entity.isRemoved() || !angles.containsKey(entity));

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !(entity instanceof LivingEntity) || entity.isRemoved()) continue;
            if (entity instanceof PlayerEntity && !movePlayers.get()) continue;
            if (mc.player.distanceTo(entity) > reach) continue;

            Vec3d current = renderPos.getOrDefault(entity, entity.getEntityPos());
            double dx = current.x - center.x;
            double dz = current.z - center.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            double angle = angles.computeIfAbsent(entity, k -> Math.atan2(dz, dx));

            Vec3d next;
            if (Math.abs(distance - radius) > 0.4) {
                double ux = distance < 1.0E-4 ? 1.0 : dx / distance;
                double uz = distance < 1.0E-4 ? 0.0 : dz / distance;
                double step = approachSpeed.get();
                double delta = radius - distance;
                double newDistance = distance + Math.copySign(Math.min(step, Math.abs(delta)), delta);
                next = new Vec3d(center.x + ux * newDistance, current.y, center.z + uz * newDistance);
            } else {
                angle += Math.toRadians(orbitSpeed.get());
                angles.put(entity, angle);
                next = new Vec3d(center.x + Math.cos(angle) * radius, current.y, center.z + Math.sin(angle) * radius);
            }

            entity.setVelocity(Vec3d.ZERO);
            renderPos.put(entity, next);
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (renderPos.isEmpty()) return;

        for (Map.Entry<Entity, Vec3d> entry : renderPos.entrySet()) {
            Entity entity = entry.getKey();
            if (entity.isRemoved()) continue;

            Vec3d pos = entry.getValue();
            entity.setPosition(pos.x, pos.y, pos.z);
            entity.lastRenderX = pos.x;
            entity.lastRenderY = pos.y;
            entity.lastRenderZ = pos.z;
        }
    }

    private void playSong() {
        if (!playMusic.get()) return;
        if (mc.getSoundManager() == null) return;

        song = PositionedSoundInstance.master(YuioSounds.ALCOHOL, 1.0f);
        mc.getSoundManager().play(song);
    }

    private void stopSong() {
        if (song == null) return;
        if (mc.getSoundManager() != null) mc.getSoundManager().stop(song);
        song = null;
    }

    private enum WaveType { X, Y, Z }
}
