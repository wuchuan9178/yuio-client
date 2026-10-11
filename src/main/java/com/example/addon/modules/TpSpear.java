package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import dev.sherii.yuio.predict.PredictEngine;
import dev.sherii.yuio.predict.Prediction;
import dev.sherii.yuio.utils.InteractUtils;
import dev.sherii.yuio.utils.TpUtils;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TpSpear extends Module {
    private final SettingGroup sgTarget = settings.createGroup("Target");
    private final SettingGroup sgSpear = settings.createGroup("Spear");
    private final SettingGroup sgMove = settings.createGroup("Move");
    private final SettingGroup sgPredict = settings.createGroup("Predict");
    private final SettingGroup sgArmor = settings.createGroup("Armor");
    private final SettingGroup sgDebug = settings.createGroup("Debug");

    private final Setting<Set<EntityType<?>>> targetTypes = sgTarget.add(new EntityTypeListSetting.Builder()
        .name("target-type").description("Entities to pierce.")
        .onlyAttackable().defaultValue(EntityType.PLAYER).build());
    private final Setting<SortPriority> priority = sgTarget.add(new EnumSetting.Builder<SortPriority>()
        .name("priority").description("Target sorting priority.").defaultValue(SortPriority.LowestDistance).build());
    private final Setting<Double> range = sgTarget.add(new DoubleSetting.Builder()
        .name("range").description("Max target distance.").defaultValue(60).min(1).sliderMax(200).build());
    private final Setting<Boolean> targetPlayers = sgTarget.add(new BoolSetting.Builder()
        .name("target-players").description("Target players.").defaultValue(true).build());
    private final Setting<Boolean> targetMobs = sgTarget.add(new BoolSetting.Builder()
        .name("target-mobs").description("Target mobs.").defaultValue(false).build());
    private final Setting<Boolean> ignoreFriends = sgTarget.add(new BoolSetting.Builder()
        .name("ignore-friends").description("Ignore friends.").defaultValue(true).build());
    private final Setting<Boolean> keepTarget = sgTarget.add(new BoolSetting.Builder()
        .name("keep-target").description("Keep the current target until it dies or leaves range.").defaultValue(true).build());

    private final Setting<SwitchMode> switchMode = sgSpear.add(new EnumSetting.Builder<SwitchMode>()
        .name("switch-mode").description("Weapon switch method.").defaultValue(SwitchMode.Silent).build());
    private final Setting<Boolean> requireSpear = sgSpear.add(new BoolSetting.Builder()
        .name("require-spear").description("Only work while holding a spear.").defaultValue(true).build());
    private final Setting<Boolean> charge = sgSpear.add(new BoolSetting.Builder()
        .name("charge").description("Hold use to keep the spear charged.").defaultValue(true).build());
    private final Setting<Boolean> chargeInteract = sgSpear.add(new BoolSetting.Builder()
        .name("charge-interact").description("Send one item use every tick, this is what keeps the charge alive on most servers.")
        .defaultValue(true).visible(charge::get).build());
    private final Setting<Boolean> attackPacket = sgSpear.add(new BoolSetting.Builder()
        .name("attack-packet").description("Also send attack packets on every pass.").defaultValue(false).build());
    private final Setting<Integer> attackTimes = sgSpear.add(new IntSetting.Builder()
        .name("attack-times").description("Attack packets per pass, all in the same tick.")
        .defaultValue(3).min(1).sliderMax(10).visible(attackPacket::get).build());
    private final Setting<Boolean> rotation = sgSpear.add(new BoolSetting.Builder()
        .name("rotation").description("Rotate before sending the attack packet.").defaultValue(false).build());
    private final Setting<Boolean> swing = sgSpear.add(new BoolSetting.Builder()
        .name("swing").description("Swing the hand on every pass.").defaultValue(true).build());

    private final Setting<MoveMode> moveMode = sgMove.add(new EnumSetting.Builder<MoveMode>()
        .name("move-mode").description("How to reach the target. Teleport is the raw vclip.")
        .defaultValue(MoveMode.Teleport).build());
    private final Setting<Double> moveSpeed = sgMove.add(new DoubleSetting.Builder()
        .name("move-speed").description("Velocity multiplier used by the velocity modes.")
        .defaultValue(1.0).min(0.1).sliderMax(3).build());
    private final Setting<Double> originGap = sgMove.add(new DoubleSetting.Builder()
        .name("origin-gap").description("Extra distance before the hitbox where the pierce line starts.")
        .defaultValue(0.6).min(0).sliderMax(4).build());
    private final Setting<Double> pierceDistance = sgMove.add(new DoubleSetting.Builder()
        .name("pierce-distance").description("Distance past the hitbox where the pierce line ends.")
        .defaultValue(1.2).min(0).sliderMax(4).build());
    private final Setting<Double> heightOffset = sgMove.add(new DoubleSetting.Builder()
        .name("height-offset").description("Vertical offset from the target feet.")
        .defaultValue(0.0).min(-1).max(1).sliderMin(-1).sliderMax(1).build());
    private final Setting<Integer> settle = sgMove.add(new IntSetting.Builder()
        .name("settle").description("Ticks to wait between two passes, stay-on-target keeps us glued in the meantime.")
        .defaultValue(0).min(0).sliderMax(10).build());
    private final Setting<Integer> maxPasses = sgMove.add(new IntSetting.Builder()
        .name("max-passes").description("Stop after this many passes, 0 for unlimited.")
        .defaultValue(0).min(0).sliderMax(50).build());
    private final Setting<Boolean> stayOnTarget = sgMove.add(new BoolSetting.Builder()
        .name("stay-on-target").description("Keep sending the position on top of the target instead of going back and forth.")
        .defaultValue(true).build());
    private final Setting<Boolean> pullBackProtection = sgMove.add(new BoolSetting.Builder()
        .name("pull-back-protection").description("Stop teleporting for a moment whenever the server teleports us back.")
        .defaultValue(true).build());
    private final Setting<Integer> pullBackCooldown = sgMove.add(new IntSetting.Builder()
        .name("pull-back-cooldown").description("Ticks of stillness after a server position correction.")
        .defaultValue(5).min(0).sliderMax(40).visible(pullBackProtection::get).build());

    private final Setting<Boolean> usePredict = sgPredict.add(new BoolSetting.Builder()
        .name("use-predict").description("Aim at the predicted position instead of the current one.").defaultValue(true).build());
    private final Setting<PredictMode> predictMode = sgPredict.add(new EnumSetting.Builder<PredictMode>()
        .name("predict-mode").description("How the target is reached. OnTop is a single vclip straight onto the target.")
        .defaultValue(PredictMode.OnTop).build());
    private final Setting<Double> startupTicks = sgPredict.add(new DoubleSetting.Builder()
        .name("startup-ticks").description("Own pipeline latency in ticks.").defaultValue(1.0).min(0).sliderMax(6).build());
    private final Setting<Double> actionTicks = sgPredict.add(new DoubleSetting.Builder()
        .name("action-ticks").description("Ticks the pierce itself needs.").defaultValue(1.0).min(0).sliderMax(6).build());
    private final Setting<Integer> rings = sgPredict.add(new IntSetting.Builder()
        .name("rings").description("Number of sigma rings sampled around the prediction.")
        .defaultValue(1).min(0).sliderMax(3).visible(() -> predictMode.get() == PredictMode.Multi).build());
    private final Setting<Integer> maxPoints = sgPredict.add(new IntSetting.Builder()
        .name("max-points").description("Maximum stations in a single multi-point pass.")
        .defaultValue(8).min(1).sliderMax(20).visible(() -> predictMode.get() == PredictMode.Multi).build());
    private final Setting<Double> minLead = sgPredict.add(new DoubleSetting.Builder()
        .name("min-lead").description("Lower bound of the lead in ticks.").defaultValue(1.0).min(0).sliderMax(10).build());
    private final Setting<Double> maxLead = sgPredict.add(new DoubleSetting.Builder()
        .name("max-lead").description("Upper bound of the lead in ticks.").defaultValue(12.0).min(1).sliderMax(40).build());
    private final Setting<Boolean> resetOnTeleport = sgPredict.add(new BoolSetting.Builder()
        .name("reset-on-teleport").description("Clear the motion history when the target vclips.").defaultValue(true).build());

    private final Setting<Double> armorThreshold = sgArmor.add(new DoubleSetting.Builder()
        .name("armor-threshold").description("Stop attacking below this armor durability.").defaultValue(20).min(0).sliderMax(200).build());
    private final Setting<Boolean> stopWhenBroken = sgArmor.add(new BoolSetting.Builder()
        .name("stop-when-broken").description("Release the target once its armor is gone.").defaultValue(true).build());
    private final Setting<Boolean> trackDurability = sgArmor.add(new BoolSetting.Builder()
        .name("track-durability").description("Use armor durability changes as hit feedback.").defaultValue(true).build());
    private final Setting<Integer> missLimit = sgArmor.add(new IntSetting.Builder()
        .name("miss-limit").description("Consecutive misses before the pierce line is widened.").defaultValue(3).min(1).sliderMax(10).build());
    private final Setting<Double> maxSpread = sgArmor.add(new DoubleSetting.Builder()
        .name("max-spread").description("Maximum widening multiplier of the pierce line.").defaultValue(3.0).min(1).sliderMax(6).build());

    private final Setting<Boolean> debug = sgDebug.add(new BoolSetting.Builder()
        .name("debug").description("Print the armor durability, the vclip resets and every server pull-back into the chat.")
        .defaultValue(false).build());

    private Entity target;
    private int spearSlot = -1;
    private int previousSlot = -1;
    private boolean weaponSwitched;
    private boolean charging;
    private int settleTicks;
    private int passes;
    private int armorBaseline = -1;
    private int missStreak;
    private int correctionTicks;
    private int pullBacks;
    private int teleportResets;
    private double spread = 1.0;
    private Vec3d approach;

    public TpSpear() {
        super(AddonTemplate.ATTACK, "tp-spear", "VClip onto the target and pierce straight through its armor.");
    }

    @Override
    public void onActivate() {
        target = null;
        spearSlot = -1;
        previousSlot = -1;
        weaponSwitched = false;
        charging = false;
        settleTicks = 0;
        passes = 0;
        armorBaseline = -1;
        missStreak = 0;
        correctionTicks = 0;
        pullBacks = 0;
        teleportResets = 0;
        spread = 1.0;
        approach = null;
    }

    @Override
    public void onDeactivate() {
        release();
        if (weaponSwitched) {
            if (switchMode.get() == SwitchMode.Silent) InvUtils.swapBack();
            else if (switchMode.get() == SwitchMode.Normal && previousSlot >= 0) setSelectedSlot(previousSlot);
        }
        weaponSwitched = false;
        target = null;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof PlayerPositionLookS2CPacket)) return;

        pullBacks++;
        if (!pullBackProtection.get()) return;

        correctionTicks = pullBackCooldown.get();
        if (debug.get()) info("server pulled us back #%d, holding still for %d ticks", pullBacks, correctionTicks);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        updateTarget();
        if (target == null || !target.isAlive() || target.isRemoved()) {
            release();
            return;
        }

        Entity current = target;

        if (current instanceof PlayerEntity player && stopWhenBroken.get() && armorBaseline >= 0
            && getArmorDurability(player) <= armorThreshold.get()) {
            if (debug.get()) info("armor gone after %d passes (durability %d)", passes, getArmorDurability(player));
            release();
            target = null;
            return;
        }

        if (!ensureWeapon()) return;

        if (charge.get()) maintainCharge();

        if (correctionTicks > 0) {
            correctionTicks--;
            return;
        }

        if (maxPasses.get() > 0 && passes >= maxPasses.get()) {
            release();
            return;
        }

        double lead = clamp(PredictEngine.INSTANCE.leadTicks(current, startupTicks.get(), actionTicks.get()),
            minLead.get(), maxLead.get());

        if (usePredict.get()) {
            Prediction probe = PredictEngine.INSTANCE.predict(current, lead);
            if (probe != null && probe.teleport() && resetOnTeleport.get()) {
                PredictEngine.INSTANCE.tracker(current).reset();
                teleportResets++;
                if (debug.get()) info("target vclipped, motion history cleared #%d", teleportResets);
            }
        }

        if (settleTicks > 0) {
            settleTicks--;
            if (stayOnTarget.get()) stick(current, lead);
            return;
        }

        Vec3d direction = swingDirection(current);
        List<Vec3d> stations = buildStations(current, lead, direction);
        if (stations.isEmpty()) return;
        execute(current, stations, direction);
    }

    private List<Vec3d> buildStations(Entity entity, double lead, Vec3d direction) {
        List<Vec3d> stations = new ArrayList<>();
        Box reference = entity.getBoundingBox();
        Vec3d center = reference.getCenter().add(0.0, heightOffset.get(), 0.0);
        Prediction prediction = usePredict.get() ? PredictEngine.INSTANCE.predict(entity, lead) : null;

        if (prediction != null) {
            center = prediction.aim(heightOffset.get());
            if (prediction.box() != null) reference = prediction.box();

            if (predictMode.get() == PredictMode.Swept) {
                Box swept = PredictEngine.INSTANCE.sweptBox(entity, entity.getEntityPos(), prediction.pos());
                if (swept != null) reference = swept;
            }
        }

        switch (predictMode.get()) {
            case OnTop -> stations.add(onTopPoint(entity, lead));
            case Direct, Swept -> stations.add(center);
            case Pierce -> {
                stations.add(nearPoint(center, direction, reference));
                stations.add(center);
                stations.add(farPoint(center, direction, reference));
            }
            case Multi -> {
                stations.add(nearPoint(center, direction, reference));
                if (prediction != null) {
                    List<Vec3d> candidates = PredictEngine.INSTANCE.candidates(entity, lead, rings.get());
                    int limit = Math.min(candidates.size(), maxPoints.get());
                    for (int i = 0; i < limit; i++) {
                        Vec3d point = candidates.get(i);
                        stations.add(new Vec3d(point.x, center.y, point.z));
                    }
                } else {
                    stations.add(center);
                }
                stations.add(farPoint(center, direction, reference));
            }
        }

        return stations;
    }

    private Vec3d onTopPoint(Entity entity, double lead) {
        if (usePredict.get()) {
            Prediction prediction = PredictEngine.INSTANCE.predict(entity, lead);
            if (prediction != null) {
                return new Vec3d(prediction.pos().x, entity.getY() + heightOffset.get(), prediction.pos().z);
            }
        }

        return new Vec3d(entity.getX(), entity.getY() + heightOffset.get(), entity.getZ());
    }

    private void stick(Entity entity, double lead) {
        Vec3d point = onTopPoint(entity, lead);
        TpUtils.INSTANCE.sendMovePacket(point, mc.player.isOnGround(), null);
        TpUtils.INSTANCE.setPos(point);
        mc.player.fallDistance = 0.0;
    }

    private void execute(Entity entity, List<Vec3d> stations, Vec3d direction) {
        if (moveMode.get() != MoveMode.Velocity) {
            for (Vec3d station : stations) {
                TpUtils.INSTANCE.sendMovePacket(station, mc.player.isOnGround(), null);
                TpUtils.INSTANCE.setPos(station);
                mc.player.fallDistance = 0.0;
            }
        }

        if (moveMode.get() != MoveMode.Teleport) {
            mc.player.setVelocity(direction.x * moveSpeed.get(), 0.0, direction.z * moveSpeed.get());
            mc.player.velocityDirty = true;
        }

        if (attackPacket.get()) {
            int times = Math.max(1, attackTimes.get());
            for (int i = 0; i < times; i++) {
                InteractUtils.INSTANCE.attack(entity, rotation.get(), swing.get());
            }
        } else if (swing.get()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }

        passes++;
        settleTicks = Math.max(0, settle.get());
        feedback(entity);
    }

    private void feedback(Entity entity) {
        if (!(entity instanceof PlayerEntity player)) return;

        int current = getArmorDurability(player);
        if (armorBaseline < 0) {
            armorBaseline = current;
            if (debug.get()) info("armor baseline %d", current);
            return;
        }

        if (current < armorBaseline) {
            PredictEngine.INSTANCE.registerHit(entity);
            if (debug.get()) info("armor hit on pass %d: %d -> %d", passes, armorBaseline, current);
            armorBaseline = current;
            missStreak = 0;
            spread = 1.0;
            return;
        }

        if (!trackDurability.get()) return;

        missStreak++;
        if (missStreak >= missLimit.get()) {
            PredictEngine.INSTANCE.registerMiss(entity);
            missStreak = 0;
            spread = Math.min(maxSpread.get(), spread + 0.5);
            if (debug.get()) info("no armor damage for %d passes, pierce line widened to %.1fx", missLimit.get(), spread);
        }
    }

    private Vec3d nearPoint(Vec3d center, Vec3d direction, Box box) {
        double radius = Math.max(box.getLengthX(), box.getLengthZ()) * 0.5;
        return center.subtract(direction.multiply(radius + originGap.get() * spread));
    }

    private Vec3d farPoint(Vec3d center, Vec3d direction, Box box) {
        double radius = Math.max(box.getLengthX(), box.getLengthZ()) * 0.5;
        return center.add(direction.multiply(radius + pierceDistance.get() * spread));
    }

    private Vec3d swingDirection(Entity entity) {
        Vec3d velocity = entity.getVelocity();
        double speed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (speed > 0.02) return new Vec3d(velocity.x / speed, 0.0, velocity.z / speed);

        Vec3d playerPos = mc.player.getEntityPos();
        Vec3d targetPos = entity.getEntityPos();
        double distance = Math.sqrt(sq(targetPos.x - playerPos.x) + sq(targetPos.z - playerPos.z));
        if (distance > 1.0 || approach == null) approach = horizontal(playerPos, targetPos);
        return approach;
    }

    private void maintainCharge() {
        if (!mc.options.useKey.isPressed()) mc.options.useKey.setPressed(true);
        charging = true;

        if (chargeInteract.get() && mc.interactionManager != null) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        }
    }

    private void release() {
        if (charging) {
            mc.options.useKey.setPressed(false);
            charging = false;
        }
    }

    private boolean ensureWeapon() {
        if (weaponSwitched) return true;
        spearSlot = findSpearSlot();
        if (spearSlot == -1) {
            if (requireSpear.get()) return false;
            weaponSwitched = true;
            return true;
        }
        previousSlot = getSelectedSlot();
        if (switchMode.get() == SwitchMode.Silent) InvUtils.swap(spearSlot, false);
        else if (switchMode.get() == SwitchMode.Normal) setSelectedSlot(spearSlot);
        weaponSwitched = true;
        return true;
    }

    private void updateTarget() {
        if (keepTarget.get() && target != null && target.isAlive() && !target.isRemoved() && targetCheck(target)) return;
        List<Entity> candidates = new ArrayList<>();
        TargetUtils.getList(candidates, this::targetCheck, priority.get(), 1);
        Entity next = candidates.isEmpty() ? null : candidates.get(0);
        if (next != target) {
            armorBaseline = -1;
            missStreak = 0;
            spread = 1.0;
            passes = 0;
            settleTicks = 0;
            correctionTicks = 0;
            approach = null;
            target = next;
        }
    }

    private boolean targetCheck(Entity entity) {
        if (!targetTypes.get().contains(entity.getType())) return false;
        if (entity == mc.player) return false;
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) return false;
        if (entity instanceof PlayerEntity player) {
            if (!targetPlayers.get()) return false;
            if (player.isCreative() || player.isSpectator()) return false;
            if (ignoreFriends.get() && !Friends.get().shouldAttack(player)) return false;
        } else if (!targetMobs.get()) return false;
        return entity.squaredDistanceTo(mc.player) <= range.get() * range.get();
    }

    private int findSpearSlot() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem().toString().toLowerCase().contains("spear")) return i;
        }
        return -1;
    }

    private int getSelectedSlot() {
        try {
            Field field = mc.player.getInventory().getClass().getDeclaredField("selectedSlot");
            field.setAccessible(true);
            return field.getInt(mc.player.getInventory());
        } catch (Exception ignored) {
            return -1;
        }
    }

    private void setSelectedSlot(int slot) {
        try {
            Field field = mc.player.getInventory().getClass().getDeclaredField("selectedSlot");
            field.setAccessible(true);
            field.setInt(mc.player.getInventory(), slot);
        } catch (Exception ignored) {
        }
    }

    private int getArmorDurability(PlayerEntity player) {
        int total = 0;
        for (int slot : new int[]{39, 38, 37, 36}) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!stack.isEmpty()) total += stack.getMaxDamage() - stack.getDamage();
        }
        return total;
    }

    private Vec3d horizontal(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.001) return new Vec3d(0.0, 0.0, 1.0);
        return new Vec3d(dx / length, 0.0, dz / length);
    }

    private double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }

    private static double sq(double value) {
        return value * value;
    }

    public enum SwitchMode { Normal, None, Silent }

    public enum MoveMode { Teleport, Velocity, Both }

    public enum PredictMode { OnTop, Direct, Pierce, Swept, Multi }
}
