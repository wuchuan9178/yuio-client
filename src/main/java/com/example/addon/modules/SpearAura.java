package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import dev.sherii.yuio.AModule;
import dev.sherii.yuio.utils.InteractUtils;
import dev.sherii.yuio.utils.TpUtils;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SpearAura extends AModule {
    public enum Phase {
        Idle,
        Acquire,
        Rise,
        Position,
        Dive,
        Recover
    }

    public enum MoveMode {
        Velocity,
        Teleport,
        Both
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSpear = settings.createGroup("Spear");
    private final SettingGroup sgRise = settings.createGroup("Rise");
    private final SettingGroup sgDive = settings.createGroup("Dive");
    private final SettingGroup sgAttack = settings.createGroup("Attack");
    private final SettingGroup sgLimit = settings.createGroup("Anti-Cheat");

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to attack.")
        .onlyAttackable()
        .defaultValue(EntityType.PLAYER)
        .build());

    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("priority")
        .description("How to pick the target.")
        .defaultValue(SortPriority.LowestDistance)
        .build());

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("Target search range.")
        .min(1.0)
        .sliderRange(1.0, 256.0)
        .defaultValue(120.0)
        .build());

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Do not target friends.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> ignoreCreative = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-creative")
        .description("Do not target creative or spectator players.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> autoSwitch = sgSpear.add(new BoolSetting.Builder()
        .name("auto-switch")
        .description("Swap the spear into the main hand automatically.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> silent = sgSpear.add(new BoolSetting.Builder()
        .name("silent")
        .description("Restore the previous hotbar slot when the module stops.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> forceCharge = sgSpear.add(new BoolSetting.Builder()
        .name("force-charge")
        .description("Hold the use key and force the spear charge for as long as the module runs.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> chargeInteract = sgSpear.add(new BoolSetting.Builder()
        .name("charge-interact")
        .description("Also send an item use every tick, this is what keeps the charge alive on most servers.")
        .defaultValue(true)
        .visible(forceCharge::get)
        .build());

    private final Setting<Boolean> forcePitch = sgSpear.add(new BoolSetting.Builder()
        .name("force-pitch")
        .description("Force the pitch down to 90 degrees while charging.")
        .defaultValue(true)
        .visible(forceCharge::get)
        .build());

    private final Setting<MoveMode> moveMode = sgRise.add(new EnumSetting.Builder<MoveMode>()
        .name("move-mode")
        .description("Velocity is smooth and much less likely to be pulled back, Teleport is the raw vclip.")
        .defaultValue(MoveMode.Both)
        .build());

    private final Setting<Double> riseSpeed = sgRise.add(new DoubleSetting.Builder()
        .name("rise-speed")
        .description("Upward velocity per tick while rising.")
        .min(0.1)
        .sliderRange(0.1, 6.0)
        .defaultValue(1.2)
        .build());

    private final Setting<Double> clipStep = sgRise.add(new DoubleSetting.Builder()
        .name("clip-step")
        .description("Vertical distance of a single vclip step.")
        .min(0.5)
        .sliderRange(0.5, 16.0)
        .defaultValue(2.0)
        .visible(() -> moveMode.get() != MoveMode.Velocity)
        .build());

    private final Setting<Integer> clipInterval = sgRise.add(new IntSetting.Builder()
        .name("clip-interval")
        .description("Ticks between two vclip steps. Higher values look much less suspicious.")
        .min(1)
        .sliderRange(1, 20)
        .defaultValue(3)
        .visible(() -> moveMode.get() != MoveMode.Velocity)
        .build());

    private final Setting<Integer> clipBudget = sgRise.add(new IntSetting.Builder()
        .name("clip-budget")
        .description("Maximum vclip steps before the module lets the server catch up. 0 for unlimited.")
        .min(0)
        .sliderRange(0, 40)
        .defaultValue(6)
        .visible(() -> moveMode.get() != MoveMode.Velocity)
        .build());

    private final Setting<Integer> clipSettle = sgRise.add(new IntSetting.Builder()
        .name("clip-settle")
        .description("Ticks of no teleporting after the budget runs out, to avoid the pull-back.")
        .min(0)
        .sliderRange(0, 40)
        .defaultValue(6)
        .visible(() -> moveMode.get() != MoveMode.Velocity)
        .build());

    private final Setting<Double> height = sgRise.add(new DoubleSetting.Builder()
        .name("height")
        .description("Minimum height above the target before the dive starts.")
        .min(1.0)
        .sliderRange(1.0, 24.0)
        .defaultValue(4.0)
        .build());

    private final Setting<Double> alignRange = sgRise.add(new DoubleSetting.Builder()
        .name("align-range")
        .description("Horizontal distance to the target that counts as being above it.")
        .min(0.1)
        .sliderRange(0.1, 8.0)
        .defaultValue(2.0)
        .build());

    private final Setting<Double> positionHeight = sgDive.add(new DoubleSetting.Builder()
        .name("position-height")
        .description("Height above the target used by the position step right before the dive.")
        .min(0.5)
        .sliderRange(0.5, 16.0)
        .defaultValue(3.0)
        .build());

    private final Setting<Double> diveDistance = sgDive.add(new DoubleSetting.Builder()
        .name("dive-distance")
        .description("How far the dive travels down from the position height. Larger values pierce through the hitbox.")
        .min(0.0)
        .sliderRange(0.0, 12.0)
        .defaultValue(3.0)
        .build());

    private final Setting<Double> stopAbove = sgDive.add(new DoubleSetting.Builder()
        .name("stop-above")
        .description("Height above the target's feet that counts as low enough to attack.")
        .min(0.0)
        .sliderRange(0.0, 4.0)
        .defaultValue(0.6)
        .build());

    private final Setting<Double> attackLowOffset = sgDive.add(new DoubleSetting.Builder()
        .name("attack-low-offset")
        .description("Extra height above the aim point that still counts as a valid dive attack.")
        .min(0.0)
        .sliderRange(0.0, 4.0)
        .defaultValue(0.35)
        .build());

    private final Setting<Double> diveSpeed = sgDive.add(new DoubleSetting.Builder()
        .name("dive-speed")
        .description("Velocity per tick while diving.")
        .min(0.1)
        .sliderRange(0.1, 8.0)
        .defaultValue(1.6)
        .build());

    private final Setting<Boolean> keepDiving = sgDive.add(new BoolSetting.Builder()
        .name("keep-diving")
        .description("Keep pushing into the target while attacking instead of freezing in place.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> pullBack = sgDive.add(new BoolSetting.Builder()
        .name("pull-back")
        .description("Return above the target after each pass. Off rises straight from where the dive ended.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> boxCenter = sgAttack.add(new BoolSetting.Builder()
        .name("box-center")
        .description("Use the real bounding box center, this is what makes small hitboxes connect.")
        .defaultValue(true)
        .build());

    private final Setting<Double> extraHeight = sgAttack.add(new DoubleSetting.Builder()
        .name("extra-height")
        .description("Extra height added to the aim point, offsets the spear reach when aiming low.")
        .min(0.0)
        .sliderRange(0.0, 3.0)
        .defaultValue(0.0)
        .build());

    private final Setting<Double> reach = sgAttack.add(new DoubleSetting.Builder()
        .name("reach")
        .description("Distance at which attack packets are sent.")
        .min(0.5)
        .sliderRange(0.5, 8.0)
        .defaultValue(2.2)
        .build());

    private final Setting<Double> reachExtra = sgAttack.add(new DoubleSetting.Builder()
        .name("reach-extra")
        .description("Tolerance added to the reach, useful for entities with a tiny hitbox.")
        .min(0.0)
        .sliderRange(0.0, 6.0)
        .defaultValue(1.0)
        .build());

    private final Setting<Boolean> attackInMove = sgAttack.add(new BoolSetting.Builder()
        .name("attack-in-move")
        .description("Send the attack in the same tick as the movement, so the server sees the hit at that spot.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> restoreAfterAttack = sgAttack.add(new BoolSetting.Builder()
        .name("restore-after-attack")
        .description("Restore the previous position right after the attack to avoid the server rejecting it.")
        .defaultValue(false)
        .build());

    private final Setting<Integer> attackTimes = sgAttack.add(new IntSetting.Builder()
        .name("attack-times")
        .description("Attack packets sent per pass.")
        .min(1)
        .sliderRange(1, 10)
        .defaultValue(2)
        .build());

    private final Setting<Integer> attackDelay = sgAttack.add(new IntSetting.Builder()
        .name("attack-delay")
        .description("Ticks between two attack passes.")
        .min(0)
        .sliderRange(0, 40)
        .defaultValue(1)
        .build());

    private final Setting<Integer> attackHold = sgAttack.add(new IntSetting.Builder()
        .name("attack-hold")
        .description("Extra ticks attack packets keep being sent while still inside the reach.")
        .min(0)
        .sliderRange(0, 20)
        .defaultValue(2)
        .build());

    private final Setting<Boolean> rotation = sgAttack.add(new BoolSetting.Builder()
        .name("rotation")
        .description("Let InteractUtils rotate the head to the target while attacking.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> swing = sgAttack.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swing the hand while attacking.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> tpsSync = sgAttack.add(new BoolSetting.Builder()
        .name("tps-sync")
        .description("Synchronize the attack delay with the server TPS.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxPasses = sgAttack.add(new IntSetting.Builder()
        .name("max-passes")
        .description("Passes per target before giving up, 0 for unlimited.")
        .min(0)
        .sliderRange(0, 64)
        .defaultValue(0)
        .build());

    private final Setting<Boolean> freezeProtection = sgLimit.add(new BoolSetting.Builder()
        .name("pull-back-protection")
        .description("Stop moving for a moment whenever the server teleports us back.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> pullBackCooldown = sgLimit.add(new IntSetting.Builder()
        .name("pull-back-cooldown")
        .description("Ticks of stillness after a server position correction.")
        .min(0)
        .sliderRange(0, 40)
        .defaultValue(10)
        .visible(freezeProtection::get)
        .build());

    private final Setting<Integer> positionCooldown = sgLimit.add(new IntSetting.Builder()
        .name("position-cooldown")
        .description("Ticks between two position steps.")
        .min(0)
        .sliderRange(0, 20)
        .defaultValue(1)
        .visible(() -> moveMode.get() != MoveMode.Velocity)
        .build());

    private Phase phase = Phase.Idle;
    private Entity target;
    private int timer;
    private int clipTimer;
    private int clipCount;
    private int settleTimer;
    private int holdTimer;
    private int passes;
    private int correctionTimer;
    private boolean holding;
    private boolean switched;
    private int previousSlot = -1;

    public SpearAura() {
        super(AddonTemplate.ATTACK, "SpearAura", "VClip above the target and pierce it with a charged spear in a loop.");
    }

    @Override
    public void onActivate() {
        phase = Phase.Idle;
        target = null;
        timer = 0;
        clipTimer = 0;
        clipCount = 0;
        settleTimer = 0;
        holdTimer = 0;
        passes = 0;
        correctionTimer = 0;
        holding = false;
        switched = false;
        previousSlot = -1;
    }

    @Override
    public void onDeactivate() {
        stopCharge();
        release();
        target = null;
        phase = Phase.Idle;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!freezeProtection.get()) return;
        if (!(event.packet instanceof PlayerPositionLookS2CPacket)) return;

        correctionTimer = pullBackCooldown.get();
        clipCount = 0;
        settleTimer = 0;
        applyVelocity(Vec3d.ZERO);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null) return;

        if (timer > 0) timer--;
        if (clipTimer > 0) clipTimer--;
        if (settleTimer > 0) settleTimer--;
        if (correctionTimer > 0) correctionTimer--;
        if (holdTimer > 0) holdTimer--;

        if (phase != Phase.Idle) {
            if (!isValidTarget(target)) {
                stopCharge();
                release();
                target = null;
                phase = Phase.Idle;
                return;
            }

            maintainCharge();
        }

        switch (phase) {
            case Idle -> handleIdle();
            case Acquire -> handleAcquire();
            case Rise -> handleRise();
            case Position -> handlePosition();
            case Dive -> handleDive();
            case Recover -> handleRecover();
            default -> {
            }
        }
    }

    private void handleIdle() {
        Entity found = findTarget();
        if (found == null) return;

        target = found;
        passes = 0;
        clipCount = 0;
        settleTimer = 0;
        phase = Phase.Acquire;
    }

    private void handleAcquire() {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        int slot = findSpearSlot();
        if (slot == -1) {
            fail("No spear found in the inventory.");
            return;
        }

        if (autoSwitch.get()) {
            if (previousSlot == -1) previousSlot = player.getInventory().getSelectedSlot();
            if (slot != player.getInventory().getSelectedSlot()) {
                InvUtils.swap(slot, false);
                switched = true;
                return;
            }
        } else if (slot != player.getInventory().getSelectedSlot()) {
            fail("Hold the spear to use this module, or enable auto-switch.");
            return;
        }

        maintainCharge();
        clipCount = 0;
        settleTimer = 0;
        phase = Phase.Rise;
    }

    private void handleRise() {
        ClientPlayerEntity player = mc.player;
        Entity t = target;
        if (player == null || t == null) return;

        if (correctionTimer > 0) return;

        Vec3d aim = aimPoint(t);
        Vec3d playerPos = player.getEntityPos();
        double destY = t.getY() + height.get();
        double dx = aim.x - playerPos.x;
        double dz = aim.z - playerPos.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        aimAt(aim);

        boolean highEnough = playerPos.y >= destY - 0.5;
        boolean aligned = horizontal <= alignRange.get();

        if (highEnough && aligned) {
            clipCount = 0;
            settleTimer = 0;
            phase = Phase.Position;
            return;
        }

        if (moveMode.get() != MoveMode.Teleport) {
            double stepH = Math.min(horizontal, riseSpeed.get());
            double moveX = horizontal <= 0.001 ? 0.0 : dx / horizontal * stepH;
            double moveZ = horizontal <= 0.001 ? 0.0 : dz / horizontal * stepH;
            double moveY = highEnough ? 0.0 : Math.min(riseSpeed.get(), destY - playerPos.y);

            applyVelocity(new Vec3d(moveX, moveY, moveZ));
        }

        if (moveMode.get() == MoveMode.Velocity) return;

        if (settleTimer > 0) return;
        if (clipBudget.get() > 0 && clipCount >= clipBudget.get()) {
            settleTimer = clipSettle.get();
            clipCount = 0;
            return;
        }
        if (clipTimer > 0) return;

        double stepY = highEnough ? 0.0 : Math.min(clipStep.get(), destY - playerPos.y);
        double stepH = Math.min(horizontal, clipStep.get());
        double moveX = horizontal <= 0.001 ? 0.0 : dx / horizontal * stepH;
        double moveZ = horizontal <= 0.001 ? 0.0 : dz / horizontal * stepH;

        moveTo(new Vec3d(playerPos.x + moveX, playerPos.y + stepY, playerPos.z + moveZ));
        clipCount++;
        clipTimer = clipInterval.get();
    }

    private void handlePosition() {
        ClientPlayerEntity player = mc.player;
        Entity t = target;
        if (player == null || t == null) return;

        if (correctionTimer > 0) return;

        Vec3d aim = aimPoint(t);
        aimAt(aim);

        Vec3d playerPos = player.getEntityPos();
        double destY = t.getY() + positionHeight.get();
        double horizontal = Math.sqrt(sq(aim.x - playerPos.x) + sq(aim.z - playerPos.z));

        if (horizontal <= alignRange.get() * 0.5 && Math.abs(playerPos.y - destY) <= 1.0) {
            holdTimer = 0;
            phase = Phase.Dive;
            return;
        }

        if (clipTimer > 0) return;

        moveTo(new Vec3d(aim.x, destY, aim.z));
        clipTimer = positionCooldown.get();
    }

    private void handleDive() {
        ClientPlayerEntity player = mc.player;
        Entity t = target;
        if (player == null || t == null) return;

        Vec3d aim = aimPoint(t);
        Vec3d playerPos = player.getEntityPos();

        aimAt(aim);

        double startY = t.getY() + positionHeight.get();
        double stopY = startY - diveDistance.get();
        double distance = playerPos.distanceTo(aim);
        boolean bottomedOut = playerPos.y <= stopY + 0.05;
        boolean lowEnough = playerPos.y <= aim.y + attackLowOffset.get()
            || playerPos.y <= t.getY() + stopAbove.get();

        if (keepDiving.get() || distance > reach.get()) {
            double wanted = Math.max(0.0, Math.min(diveSpeed.get(), playerPos.y - stopY));
            applyVelocity(new Vec3d(0.0, -wanted, 0.0));
        } else {
            applyVelocity(Vec3d.ZERO);
        }

        if (distance <= reach.get() + reachExtra.get() && (lowEnough || bottomedOut)) {
            attack(t, aim);
            holdTimer = attackHold.get();
            passes++;
            timer = currentDelay();
            phase = Phase.Recover;
            return;
        }

        if (playerPos.y < stopY - 0.5) {
            applyVelocity(Vec3d.ZERO);
            phase = Phase.Position;
        }
    }

    private void attack(Entity t, Vec3d aim) {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        Vec3d before = player.getEntityPos();

        if (attackInMove.get()) {
            Vec3d here = new Vec3d(aim.x, player.getY(), aim.z);
            player.setPosition(here.x, here.y, here.z);
            TpUtils.INSTANCE.sendMovePacket(here, false, null);
        }

        for (int i = 0; i < attackTimes.get(); i++) {
            InteractUtils.INSTANCE.attack(t, rotation.get(), swing.get());
        }

        if (restoreAfterAttack.get()) {
            player.setPosition(before.x, before.y, before.z);
            TpUtils.INSTANCE.sendMovePacket(before, false, null);
        }
    }

    private void handleRecover() {
        ClientPlayerEntity player = mc.player;
        Entity t = target;
        if (player == null || t == null) return;

        if (timer > 0) return;

        if (maxPasses.get() > 0 && passes >= maxPasses.get()) {
            stopCharge();
            release();
            target = null;
            phase = Phase.Idle;
            return;
        }

        if (pullBack.get()) {
            Vec3d aim = aimPoint(t);
            moveTo(new Vec3d(aim.x, t.getY() + positionHeight.get(), aim.z));
        }

        player.fallDistance = 0;
        player.setVelocity(Vec3d.ZERO);
        player.velocityDirty = true;
        clipCount = 0;
        settleTimer = 0;
        phase = Phase.Rise;
    }

    private Vec3d aimPoint(Entity t) {
        if (boxCenter.get()) {
            return t.getBoundingBox().getCenter().add(0.0, extraHeight.get(), 0.0);
        }

        return new Vec3d(t.getX(), t.getY() + t.getHeight() * 0.5 + extraHeight.get(), t.getZ());
    }

    private void applyVelocity(Vec3d velocity) {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        player.setVelocity(velocity);
        player.velocityDirty = true;
    }

    private void moveTo(Vec3d dest) {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        player.setPosition(dest.x, dest.y, dest.z);
        player.fallDistance = 0;
        player.setOnGround(false);
        TpUtils.INSTANCE.sendMovePacket(dest, false, null);
    }

    private void maintainCharge() {
        if (!forceCharge.get()) return;

        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        if (!mc.options.useKey.isPressed()) {
            mc.options.useKey.setPressed(true);
            holding = true;
        }

        if (forcePitch.get()) {
            player.setPitch(90.0f);
            sendRotation(player.getYaw(), 90.0f);
        }

        if (chargeInteract.get() && mc.interactionManager != null) {
            mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
        }
    }

    private void stopCharge() {
        if (holding) {
            mc.options.useKey.setPressed(false);
            holding = false;
        }
    }

    private void release() {
        if (switched && silent.get() && previousSlot >= 0) {
            InvUtils.swap(previousSlot, false);
        }
        switched = false;
        previousSlot = -1;
    }

    private void fail(String message) {
        error(message);
        stopCharge();
        release();
        toggle();
    }

    private void aimAt(Vec3d aim) {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        double dx = aim.x - player.getX();
        double dy = aim.y - player.getEyeY();
        double dz = aim.z - player.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, horizontal));

        player.setYaw(yaw);
        player.setPitch(pitch);
        player.setHeadYaw(yaw);
        player.setBodyYaw(yaw);
        sendRotation(yaw, pitch);
    }

    private void sendRotation(float yaw, float pitch) {
        ClientPlayerEntity player = mc.player;
        if (player == null || player.networkHandler == null) return;

        player.networkHandler.sendPacket(
            new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround(), false));
    }

    private int currentDelay() {
        int delay = attackDelay.get();
        if (tpsSync.get()) delay += (int) Math.max(0.0, 20.0 - TickRate.INSTANCE.getTickRate());
        return delay;
    }

    private int findSpearSlot() {
        ClientPlayerEntity player = mc.player;
        if (player == null) return -1;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (isSpear(stack.getItem())) return i;
        }
        return -1;
    }

    private boolean isSpear(Item item) {
        return item.toString().toLowerCase().contains("spear");
    }

    private Entity findTarget() {
        List<Entity> candidates = new ArrayList<>();
        TargetUtils.getList(candidates, this::targetCheck, priority.get(), 1);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private boolean targetCheck(Entity entity) {
        if (entity == null || mc.player == null) return false;
        if (!entities.get().contains(entity.getType())) return false;
        if (entity == mc.player) return false;
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) return false;
        if (living.getHealth() <= 0.0F) return false;

        if (entity instanceof PlayerEntity other) {
            if (ignoreCreative.get() && (other.isCreative() || other.isSpectator())) return false;
            if (ignoreFriends.get() && !Friends.get().shouldAttack(other)) return false;
        }

        if (targetRange.get() > 0.0 && entity.squaredDistanceTo(mc.player) > targetRange.get() * targetRange.get()) return false;

        return true;
    }

    private boolean isValidTarget(Entity entity) {
        return targetCheck(entity) && entity.isAlive();
    }

    private static double sq(double value) {
        return value * value;
    }

    @Override
    public String getInfoString() {
        if (target == null) return phase.name();

        ClientPlayerEntity player = mc.player;
        if (player == null) return phase.name();

        double above = player.getY() - target.getY();
        double distance = player.getEntityPos().distanceTo(aimPoint(target));
        return String.format("%s %s up=%.1f d=%.1f", EntityUtils.getName(target), phase.name(), above, distance);
    }

    public Entity getTarget() {
        return target;
    }
}