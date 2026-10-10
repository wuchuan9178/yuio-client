package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AimAssist extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSmooth = settings.createGroup("Smoothing");

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to aim at.")
        .onlyAttackable()
        .defaultValue(EntityType.PLAYER)
        .build());

    private final Setting<AcquireMode> acquireMode = sgGeneral.add(new EnumSetting.Builder<AcquireMode>()
        .name("acquire-mode")
        .description("Auto picks by priority, Crosshair only picks an entity your crosshair is on.")
        .defaultValue(AcquireMode.Auto)
        .build());

    private final Setting<Boolean> lockTarget = sgGeneral.add(new BoolSetting.Builder()
        .name("lock-target")
        .description("Keep the first acquired target until it dies, leaves range or the module is turned off.")
        .defaultValue(false)
        .build());

    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("priority")
        .description("How to pick a target.")
        .defaultValue(SortPriority.LowestDistance)
        .build());

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum target distance.")
        .defaultValue(24.0)
        .min(1.0)
        .sliderRange(1.0, 120.0)
        .build());

    private final Setting<Double> fov = sgGeneral.add(new DoubleSetting.Builder()
        .name("fov")
        .description("Only aim at targets inside this field of view. 180 aims at everything.")
        .defaultValue(180.0)
        .min(10.0)
        .max(180.0)
        .sliderRange(10.0, 180.0)
        .build());

    private final Setting<Double> aimHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("aim-height")
        .description("Vertical offset from the target centre, in blocks.")
        .defaultValue(0.0)
        .min(-1.0)
        .max(1.0)
        .sliderRange(-1.0, 1.0)
        .build());

    private final Setting<Boolean> ignoreWalls = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-walls")
        .description("Keep aiming at targets that are behind blocks.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Do not aim at friends.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> ignoreCreative = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-creative")
        .description("Do not aim at creative or spectator players.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swing the hand while aiming.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug")
        .description("Send aim data to chat.")
        .defaultValue(false)
        .build());

    private final Setting<RotationMode> rotationMode = sgGeneral.add(new EnumSetting.Builder<RotationMode>()
        .name("rotation-mode")
        .description("Server only hides the aim from your screen, Client moves your own camera too.")
        .defaultValue(RotationMode.Server)
        .build());

    private final Setting<SmoothMode> smoothMode = sgSmooth.add(new EnumSetting.Builder<SmoothMode>()
        .name("smooth-mode")
        .description("Smoothing function used to reach the target angle.")
        .defaultValue(SmoothMode.Exponential)
        .build());

    private final Setting<Double> smoothness = sgSmooth.add(new DoubleSetting.Builder()
        .name("responsiveness")
        .description("Fraction of the way to the target angle covered per tick. Higher is faster.")
        .defaultValue(0.35)
        .min(0.01)
        .max(1.0)
        .sliderRange(0.01, 1.0)
        .visible(() -> smoothMode.get() == SmoothMode.Exponential)
        .build());

    private final Setting<Double> speed = sgSmooth.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Degrees per tick.")
        .defaultValue(12.0)
        .min(0.1)
        .sliderRange(0.1, 90.0)
        .visible(() -> smoothMode.get() == SmoothMode.Linear || smoothMode.get() == SmoothMode.Sine)
        .build());

    private final Setting<Double> a = sgSmooth.add(new DoubleSetting.Builder()
        .name("sample-factor")
        .description("Fraction of the full step taken each tick.")
        .defaultValue(0.35)
        .min(0.01)
        .max(1.0)
        .sliderRange(0.01, 1.0)
        .visible(() -> smoothMode.get() == SmoothMode.ExponentialSmooth)
        .build());

    private final Setting<Integer> release = sgSmooth.add(new IntSetting.Builder()
        .name("release-ticks")
        .description("Ticks to keep aiming after the target is lost.")
        .defaultValue(4)
        .min(0)
        .sliderRange(0, 40)
        .build());

    public enum SmoothMode {
        Exponential,
        ExponentialSmooth,
        Linear,
        Sine,
        Instant
    }

    public enum RotationMode {
        Server,
        Client
    }

    public enum AcquireMode {
        Auto,
        Crosshair
    }

    private final List<Entity> targets = new ArrayList<>();

    private boolean active;
    private float curYaw;
    private float curPitch;
    private float targetYaw;
    private float targetPitch;
    private float stepYaw;
    private float stepPitch;
    private boolean hasTarget;
    private int releaseTicks;
    private Vec3d aimPoint = Vec3d.ZERO;
    private Entity locked;
    private int lockedId = -1;

    public AimAssist() {
        super(AddonTemplate.ATTACK, "aim-assist", "Smoothly aims at nearby entities.");
    }

    @Override
    public void onActivate() {
        active = false;
        hasTarget = false;
        releaseTicks = 0;
        targets.clear();
        locked = null;
        lockedId = -1;
    }

    @Override
    public void onDeactivate() {
        active = false;
        hasTarget = false;
        releaseTicks = 0;
        targets.clear();
        locked = null;
        lockedId = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        targets.clear();
        Entity target = pickTarget();
        hasTarget = target != null;

        if (!hasTarget) {
            if (releaseTicks > 0) releaseTicks--;
            else active = false;
            if (!active) return;
        } else {
            releaseTicks = release.get();
            aimPoint = aimPoint(target);
            targetYaw = (float) Rotations.getYaw(aimPoint);
            targetPitch = (float) Rotations.getPitch(aimPoint);

            if (!active) {
                curYaw = mc.player.getYaw();
                curPitch = mc.player.getPitch();
                targetYaw = curYaw + MathHelper.wrapDegrees(targetYaw - curYaw);
                active = true;
            }
        }

        stepTarget();
        applyRotation();
        if (swing.get()) mc.player.swingHand(mc.player.getActiveHand());

        if (debug.get()) {
            info("yaw=%.1f/%.1f pitch=%.1f/%.1f", curYaw, targetYaw, curPitch, targetPitch);
        }
    }

    private Entity pickTarget() {
        if (locked != null) {
            if (locked.isRemoved() || !isValid(locked)) {
                locked = null;
                lockedId = -1;
            } else {
                return locked;
            }
        }

        Entity picked;
        if (acquireMode.get() == AcquireMode.Crosshair) {
            picked = crosshairEntity();
        } else {
            TargetUtils.getList(targets, this::isValid, priority.get(), 1);
            picked = targets.isEmpty() ? null : targets.get(0);
        }

        if (picked != null && lockTarget.get()) {
            locked = picked;
            lockedId = picked.getId();
        }

        return picked;
    }

    private Entity crosshairEntity() {
        Vec3d eyes = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVector();
        Vec3d end = eyes.add(look.multiply(range.get()));
        Box search = mc.player.getBoundingBox().stretch(look.multiply(range.get())).expand(1.0);

        Entity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Entity entity : mc.world.getOtherEntities(mc.player, search)) {
            if (!isValid(entity)) continue;

            Box box = entity.getBoundingBox().expand(entity.getTargetingMargin());
            if (box.raycast(eyes, end).isEmpty()) continue;

            double distance = entity.distanceTo(mc.player);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }

        return best;
    }

    private boolean isValid(Entity entity) {
        if (entity == mc.player) return false;
        if (!entities.get().contains(entity.getType())) return false;
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) return false;
        if (entity.distanceTo(mc.player) > range.get()) return false;

        if (entity instanceof PlayerEntity player) {
            if (ignoreCreative.get() && (player.isCreative() || player.isSpectator())) return false;
            if (ignoreFriends.get() && !Friends.get().shouldAttack(player)) return false;
        }

        if (!ignoreWalls.get() && !canSee(aimPoint(entity))) return false;
        if (fov.get() < 180.0 && !insideFov(aimPoint(entity))) return false;

        return true;
    }

    private Vec3d aimPoint(Entity entity) {
        Box box = entity.getBoundingBox();
        double x = (box.minX + box.maxX) * 0.5;
        double z = (box.minZ + box.maxZ) * 0.5;
        double y = box.minY + (box.maxY - box.minY) * 0.5 + aimHeight.get();
        return new Vec3d(x, y, z);
    }

    private boolean insideFov(Vec3d point) {
        Vec3d eyes = mc.player.getEyePos();
        Vec3d to = point.subtract(eyes);
        double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
        if (horizontal < 0.001) return true;
        return Math.abs(MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(-to.x, to.z)) - mc.player.getYaw())) <= fov.get() * 0.5;
    }

    private boolean canSee(Vec3d point) {
        Vec3d eyes = mc.player.getEyePos();
        BlockHitResult hit = mc.world.raycast(new RaycastContext(
            eyes,
            point,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            mc.player
        ));
        return hit.getType() == HitResult.Type.MISS;
    }

    private void stepTarget() {
        float deltaYaw = MathHelper.wrapDegrees(targetYaw - curYaw);
        float deltaPitch = targetPitch - curPitch;

        float nextYaw;
        float nextPitch;

        switch (smoothMode.get()) {
            case Instant -> {
                nextYaw = curYaw + deltaYaw;
                nextPitch = curPitch + deltaPitch;
            }
            case Linear -> {
                stepYaw = (float) Math.min(Math.abs(deltaYaw), speed.get());
                stepPitch = (float) Math.min(Math.abs(deltaPitch), speed.get());
                nextYaw = curYaw + Math.copySign(stepYaw, deltaYaw);
                nextPitch = curPitch + Math.copySign(stepPitch, deltaPitch);
            }
            case Sine -> {
                float ey = (float) Math.sin(Math.min(1.0, speed.get() / Math.max(0.01, Math.abs(deltaYaw))) * Math.PI * 0.5);
                float ep = (float) Math.sin(Math.min(1.0, speed.get() / Math.max(0.01, Math.abs(deltaPitch))) * Math.PI * 0.5);
                nextYaw = curYaw + deltaYaw * ey;
                nextPitch = curPitch + deltaPitch * ep;
            }
            case ExponentialSmooth -> {
                nextYaw = curYaw + deltaYaw * a.get().floatValue();
                nextPitch = curPitch + deltaPitch * a.get().floatValue();
            }
            default -> {
                float f = smoothness.get().floatValue();
                nextYaw = curYaw + deltaYaw * f;
                nextPitch = curPitch + deltaPitch * f;
            }
        }

        curYaw = MathHelper.wrapDegrees(nextYaw);
        curPitch = MathHelper.clamp(nextPitch, -90.0f, 90.0f);
    }

    private void applyRotation() {
        if (rotationMode.get() == RotationMode.Client) {
            mc.player.setYaw(curYaw);
            mc.player.setPitch(curPitch);
            return;
        }

        Rotations.rotate(curYaw, curPitch);
    }

    @Override
    public String getInfoString() {
        return "%.1f/%.1f".formatted(curYaw, curPitch);
    }
}
