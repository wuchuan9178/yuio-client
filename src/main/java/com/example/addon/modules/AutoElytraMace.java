package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.events.KeyboardInputEvent;
import com.example.addon.utils.ForcedFriends;
import dev.sherii.yuio.AModule;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class AutoElytraMace extends AModule {
    public static AutoElytraMace INSTANCE;

    public enum Mode { Follow, Mace }

    public enum TargetAction { AFK, SLOW_SPEED, TOWARDS, ESCAPING, CIRCLING }

    private static final int STATE_NONE = 0;
    private static final int STATE_PULL_UP = 1;
    private static final int STATE_FOLLOW = 2;
    private static final int STATE_WAIT_ATTACK = 3;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgMace = settings.createGroup("Mace");
    private final SettingGroup sgAngle = settings.createGroup("Angle");

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode").description("Bot behaviour.").defaultValue(Mode.Mace).build());

    private final Setting<Integer> targetRange = sgGeneral.add(new IntSetting.Builder()
        .name("target-range").description("Search range.").defaultValue(80).min(8).sliderMax(256).build());

    private final Setting<Boolean> playerOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("player-only").description("Only target players.").defaultValue(true).build());

    private final Setting<Boolean> dynamicTarget = sgGeneral.add(new BoolSetting.Builder()
        .name("dynamic-target").description("Re-search target every tick.").defaultValue(false).build());

    private final Setting<Boolean> onlyWhenNoWasd = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-no-wasd").description("Pause control while wasd is pressed.").defaultValue(true).build());

    private final Setting<Boolean> autoControl = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-control").description("Take over rotation and forward input.").defaultValue(true).build());

    private final Setting<Boolean> followFriend = sgGeneral.add(new BoolSetting.Builder()
        .name("follow-friend").description("Allow friends as follow target.").defaultValue(false).build());

    private final Setting<Boolean> autoTakeoff = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-takeoff").description("Start gliding while falling.").defaultValue(true).build());

    private final Setting<Double> combatRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("combat-range").description("Attack range.").defaultValue(10).min(1).sliderMax(20).build());

    private final Setting<Boolean> render = sgGeneral.add(new BoolSetting.Builder()
        .name("render").description("Render target prediction.").defaultValue(true).build());

    private final Setting<Double> maceHeight = sgMace.add(new DoubleSetting.Builder()
        .name("mace-height").description("Height above target to pull up.").defaultValue(10).min(1).sliderMax(30).build());

    private final Setting<Integer> maceRemainPullUpTick = sgMace.add(new IntSetting.Builder()
        .name("mace-remain-pullup-tick").description("Extra pull up ticks.").defaultValue(20).min(0).sliderMax(100).build());

    private final Setting<Double> maceYLevelWeight = sgMace.add(new DoubleSetting.Builder()
        .name("mace-y-level-weight").description("Target y lerp.").defaultValue(0).min(0).sliderMax(1).build());

    private final Setting<Double> followOnGroundHeight = sgMace.add(new DoubleSetting.Builder()
        .name("follow-on-ground-height").description("Extra height when target on ground.").defaultValue(2).min(0).sliderMax(10).build());

    private final Setting<Double> maceMinFallDistance = sgMace.add(new DoubleSetting.Builder()
        .name("mace-min-fall-distance").description("Min fall distance to use mace.").defaultValue(1.5).min(0).sliderMax(10).build());

    private final Setting<Boolean> maceUsePredictor = sgMace.add(new BoolSetting.Builder()
        .name("mace-use-predictor").description("Predict target position.").defaultValue(true).build());

    private final Setting<Boolean> maceAttackUseSimple = sgMace.add(new BoolSetting.Builder()
        .name("mace-attack-use-simple").description("Allow simple attack without mace.").defaultValue(true).build());

    private final Setting<Boolean> appendPostAttack = sgMace.add(new BoolSetting.Builder()
        .name("append-post-attack").description("Attack at end of player tick.").defaultValue(true).build());

    private final Setting<Boolean> angleOptimizePullUp = sgAngle.add(new BoolSetting.Builder()
        .name("angle-optimize-pullup").description("Optimize climbing angle.").defaultValue(true).build());

    private final Setting<Boolean> angleOptimizeFollow = sgAngle.add(new BoolSetting.Builder()
        .name("angle-optimize-follow").description("Optimize diving angle.").defaultValue(true).build());

    private final Color sideColor = new Color(255, 25, 25, 60);
    private final Color lineColor = new Color(255, 25, 25, 255);

    private final Follower follower = new Follower();
    private final MaceAura maceAura = new MaceAura();

    private Entity target;
    private AbstractBehaviour currentBehaviour;
    private TargetAction currentAction = TargetAction.SLOW_SPEED;
    private boolean currentInCombatRange;
    private boolean currentOnGround;
    private boolean heightLimitEnvironment;

    private final Deque<Vec3d> knownPositions = new ArrayDeque<>();
    private final Deque<Integer> knownTicks = new ArrayDeque<>();

    private int tick;
    private double fallDistance;
    private double lastFallDistance;
    private double maxHeightInAttack;
    private boolean attackFlag;
    private int lastMaceAttackTick;
    private int lastMaceAttackSuccessTick;
    private boolean currentTargetUpFly;

    public AutoElytraMace() {
        super(AddonTemplate.ATTACK, "AutoElytraMace", "skid? by:slm");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        tick = 0;
        target = null;
        currentBehaviour = null;
        currentAction = TargetAction.SLOW_SPEED;
        knownPositions.clear();
        knownTicks.clear();
        fallDistance = 0;
        lastFallDistance = 0;
        attackFlag = false;
        lastMaceAttackTick = 0;
        lastMaceAttackSuccessTick = 0;
    }

    @Override
    public void onDeactivate() {
        if (currentBehaviour != null) currentBehaviour.onDisable();
        currentBehaviour = null;
        target = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.isSpectator() || mc.player.isDead()) return;

        tick++;
        fallDistance = mc.player.fallDistance;
        heightLimitEnvironment = mc.world.getDimension().hasCeiling()
            && mc.player.getY() < mc.world.getBottomY() + mc.world.getDimension().logicalHeight() - 64;

        refreshTarget();
        updateTargetAction();
        if (target != null) rememberPosition();

        AbstractBehaviour wanted = mode.get() == Mode.Follow ? follower : maceAura;
        if (wanted != currentBehaviour) {
            if (currentBehaviour != null) currentBehaviour.onDisable();
            currentBehaviour = wanted;
            currentBehaviour.onEnable();
        }

        currentBehaviour.movementDirection = new ElytraMotion(
            pitchYawToRotation(mc.player.getPitch(), mc.player.getYaw()), 0, 10.0D);
        currentBehaviour.onUpdate();

        // 空中下坠时自动开鞘翅
        if (autoTakeoff.get() && !mc.player.isGliding() && !mc.player.isOnGround() && mc.player.getVelocity().y < -0.1) {
            mc.player.startGliding();
        }
        lastFallDistance = fallDistance;
    }

    private void refreshTarget() {
        if (target != null && (!isValidTarget(target) || target.squaredDistanceTo(mc.player) > (double) targetRange.get() * targetRange.get())) {
            target = null;
        }
        if (target == null || dynamicTarget.get()) {
            if (currentBehaviour == null) currentBehaviour = follower;
            currentBehaviour.searchTarget();
        }
    }

    private void updateTargetAction() {
        currentInCombatRange = target != null && isWithinAttackRange(mc.player.getEntityPos(), target, combatRange.get());
        currentOnGround = target != null && (target.isOnGround() || isSupported(target));

        if (target == null || currentOnGround || !(target instanceof PlayerEntity)) {
            currentAction = TargetAction.SLOW_SPEED;
            return;
        }

        List<Vec3d> positions = new ArrayList<>(knownPositions);
        if (positions.size() < 2) {
            currentAction = TargetAction.TOWARDS;
            return;
        }

        int oldestTick = knownTicks.peekFirst() == null ? 0 : knownTicks.peekFirst();
        if (tick - oldestTick > 20) {
            currentAction = TargetAction.AFK;
            return;
        }

        Vec3d pos0 = positions.get(0);
        Vec3d pos1 = positions.get(1);
        double dist01 = pos0.squaredDistanceTo(pos1);
        if (dist01 < 1e-6) {
            currentAction = TargetAction.AFK;
            return;
        }
        if (positions.size() < 3) {
            currentAction = TargetAction.TOWARDS;
            return;
        }

        Vec3d pos2 = positions.get(2);
        double dist12 = pos1.squaredDistanceTo(pos2);
        if (dist01 < 0.75 * 0.75 && dist12 < 0.75 * 0.75) {
            currentAction = TargetAction.SLOW_SPEED;
            return;
        }

        Vec3d ab = pos1.subtract(pos0);
        Vec3d bc = pos2.subtract(pos1);
        double dot = ab.dotProduct(bc) / (ab.length() * bc.length() + 1e-9);
        double angle = Math.toDegrees(Math.acos(MathHelper.clamp(dot, -1, 1)));
        if (angle < 60) {
            Vec3d toPlayer = mc.player.getEntityPos().subtract(pos1);
            currentAction = bc.dotProduct(toPlayer) > 0 ? TargetAction.TOWARDS : TargetAction.ESCAPING;
        } else {
            currentAction = TargetAction.CIRCLING;
        }
    }

    private void rememberPosition() {
        knownPositions.addLast(target.getEntityPos());
        knownTicks.addLast(tick);
        while (knownPositions.size() > 5) {
            knownPositions.removeFirst();
            knownTicks.removeFirst();
        }
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == mc.player || !entity.isAlive() || entity.isRemoved()) return false;
        if (entity.squaredDistanceTo(mc.player) > (double) targetRange.get() * targetRange.get()) return false;
        if (!(entity instanceof LivingEntity)) return false;
        if (entity instanceof PlayerEntity player) {
            if (player.isCreative() || player.isSpectator()) return false;
            if (ForcedFriends.protects(player)) return false;
            if (followFriend.get() && Friends.get().shouldAttack(player)) return false;
            return playerOnly.get() || Friends.get().shouldAttack(player);
        }
        return !playerOnly.get();
    }

    private boolean isSupported(Entity entity) {
        Box box = entity.getBoundingBox().offset(0, -0.1, 0);
        return !mc.world.isSpaceEmpty(entity, box);
    }

    private boolean isWithinAttackRange(Vec3d pos, Entity entity, double range) {
        return entity != null && pos.squaredDistanceTo(entity.getBoundingBox().getCenter()) <= range * range;
    }

    private Vec3d predictTarget() {
        if (target == null) return null;
        return target.getEntityPos().add(target.getVelocity().multiply(10));
    }

    private boolean isFallFlying() {
        return mc.player.isGliding() || mc.player.getAbilities().flying;
    }

    private boolean canUseMace() {
        return mc.player.getMainHandStack().isOf(Items.MACE) || mc.player.getOffHandStack().isOf(Items.MACE)
            || InvUtils.findInHotbar(Items.MACE).found();
    }

    private float attackCooldown() {
        return mc.player.getAttackCooldownProgress(0.5f);
    }

    private double horizontal(Vec3d vec) {
        return Math.hypot(vec.x, vec.z);
    }

    private void lookAt(Entity entity) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d center = entity.getBoundingBox().getCenter();
        double dx = center.x - eye.x;
        double dy = center.y - eye.y;
        double dz = center.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        mc.player.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        mc.player.setPitch((float) -Math.toDegrees(Math.atan2(dy, horizontal)));
    }

    private static Vec3d pitchYawToRotation(float pitch, float yaw) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRad);
        return new Vec3d(-Math.sin(yawRad) * cosPitch, -Math.sin(pitchRad), Math.cos(yawRad) * cosPitch);
    }

    private static float rotationToPitch(Vec3d rotation) {
        return (float) -Math.toDegrees(Math.asin(rotation.y / Math.max(1e-9, rotation.length())));
    }

    private static float rotationToYaw(Vec3d rotation) {
        return (float) Math.toDegrees(Math.atan2(-rotation.x, rotation.z));
    }

    // 45°爬升时水平分量最大
    private Vec2f calculateBestPullupSpeed(float pitch, float yaw) {
        double sin = Math.abs(Math.sin(Math.toRadians(yaw)));
        double cos = Math.abs(Math.cos(Math.toRadians(yaw)));
        double max = Math.max(sin, cos);
        if (max < 1e-6) return new Vec2f(pitch, yaw);
        double critical = 0.5 / max;
        if (critical >= 1) return new Vec2f(pitch, yaw);
        float newPitch = (float) -Math.toDegrees(Math.acos(critical));
        return new Vec2f(Math.min(newPitch, pitch), yaw);
    }

    private Vec2f calculateBestDownForwardSpeed(float pitch, float yaw, boolean natural, boolean downward) {
        float bestPitch = 30.5f;
        if (downward && pitch > 75) return new Vec2f(pitch, yaw);
        if (natural && pitch < bestPitch) return new Vec2f(pitch, yaw);
        return new Vec2f(bestPitch, yaw);
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (!autoControl.get() || currentBehaviour == null || mc.player == null) return;
        if (onlyWhenNoWasd.get() && (event.getForward() != 0 || event.getStrafe() != 0)) return;
        if (currentBehaviour.movementDirection.speed() > 0.01) {
            event.setForward(1);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || target == null || mc.player == null) return;
        Vec3d pos = maceUsePredictor.get() ? predictTarget() : target.getEntityPos();
        if (pos == null) return;
        event.renderer.box(new Box(pos.x - 0.4, pos.y, pos.z - 0.4, pos.x + 0.4, pos.y + 1.8, pos.z + 0.4),
            sideColor, lineColor, ShapeMode.Both, 0);
    }

    @Override
    public String getInfoString() {
        return target != null ? target.getName().getString() : null;
    }

    public static class ElytraMotion {
        private Vec3d rotation;
        private double speed;
        private final double maxSpeed;

        public ElytraMotion(Vec3d rotation, double speed, double maxSpeed) {
            this.rotation = rotation;
            this.speed = speed;
            this.maxSpeed = maxSpeed;
        }

        public Vec3d rotation() {
            return rotation;
        }

        public double speed() {
            return speed;
        }

        public void setRotation(Vec3d rotation) {
            if (rotation.lengthSquared() > 1e-9) this.rotation = rotation.normalize();
        }

        public void setSpeed(double speed) {
            this.speed = MathHelper.clamp(speed, 0, maxSpeed);
        }

        public void setMovement(Vec3d movement, double maxSpeed) {
            double length = movement.length();
            if (length > 1e-6) this.rotation = movement.multiply(1.0 / length);
            this.speed = Math.min(length, Math.min(maxSpeed, this.maxSpeed));
        }
    }

    private abstract class AbstractBehaviour {
        ElytraMotion movementDirection = new ElytraMotion(Vec3d.ZERO, 0, 10);

        void setRotation(Vec3d rotation) {
            movementDirection.setRotation(rotation);
            applyToPlayer(rotation);
        }

        void setRotation(float pitch, float yaw) {
            setRotation(pitchYawToRotation(pitch, yaw));
        }

        void setMovement(Vec3d movement, double maxSpeed) {
            movementDirection.setMovement(movement, maxSpeed);
            applyToPlayer(movementDirection.rotation());
        }

        void setSpeed(double speed) {
            movementDirection.setSpeed(speed);
        }

        private void applyToPlayer(Vec3d rotation) {
            if (!autoControl.get() || rotation.lengthSquared() < 1e-9) return;
            mc.player.setYaw(rotationToYaw(rotation));
            mc.player.setPitch(rotationToPitch(rotation));
        }

        void searchTarget() {
            target = findTarget();
        }

        void onUpdate() {
        }

        void onHit() {
        }

        void onEnable() {
        }

        void onDisable() {
        }
    }

    private class Follower extends AbstractBehaviour {
        @Override
        void onUpdate() {
            if (target == null) {
                setSpeed(0);
                return;
            }
            setMovement(target.getEntityPos().add(0, target.getHeight() * 0.5, 0).subtract(mc.player.getEntityPos()), 10);
        }
    }

    private class MaceAura extends AbstractBehaviour {
        private final StateMachine machine;
        private int startPullUpTick;
        private int startWaitAttack;

        MaceAura() {
            machine = new StateMachine(STATE_NONE, this::onCondition,
                this::onStateNone, this::onStatePullUp, this::onStateFollow, this::onStateWaitAttack);
        }

        @Override
        void onEnable() {
            machine.setState(STATE_NONE);
            startPullUpTick = 0;
            startWaitAttack = 0;
        }

        private int onCondition(StateMachine stateMachine, int state) {
            if (target == null || !target.isAlive()) {
                setSpeed(0);
                stateMachine.markForEndState();
                return STATE_NONE;
            }
            return state;
        }

        private int onStateNone(StateMachine stateMachine) {
            if (target != null && target.isAlive()) {
                stateMachine.markForEndState();
                if (fallDistance > 4 && mc.player.getY() > target.getY() + 4) {
                    return STATE_FOLLOW;
                }
                return STATE_PULL_UP;
            }
            setSpeed(0);
            stateMachine.markForEndState();
            return STATE_NONE;
        }

        // 爬到目标头顶
        private int onStatePullUp(StateMachine stateMachine) {
            Vec3d predictor = maceUsePredictor.get() ? predictTarget() : null;
            double pullupHeight = maceHeight.get();
            Vec3d base = predictor != null ? predictor : mc.player.getEntityPos();
            double thresholdY = base.y;
            double targetY = thresholdY + pullupHeight;
            boolean mayFollow = mc.player.getY() >= targetY
                || (mc.player.getY() > thresholdY && startPullUpTick != 0 && tick > maceRemainPullUpTick.get() + startPullUpTick);
            setTargetToPlayerUpper(base, pullupHeight);
            stateMachine.markForEndState();
            return mayFollow ? STATE_FOLLOW : STATE_PULL_UP;
        }

        private int onStateFollow(StateMachine stateMachine) {
            if (fallDistance < 1e-6 && lastFallDistance > 1e-6) {
                stateMachine.markForEndState();
                return STATE_PULL_UP;
            }
            Vec3d predictor = maceUsePredictor.get() ? predictTarget() : mc.player.getEntityPos();
            if (predictor == null) {
                stateMachine.markForEndState();
                return STATE_PULL_UP;
            }

            Vec3d delta = predictor.subtract(mc.player.getEntityPos());
            double horizontal = horizontal(delta);
            currentTargetUpFly = mc.player.getY() + Math.min(horizontal, 10) < predictor.y;

            boolean mayAttack = shouldAttackSimple() || shouldAttackMace();
            boolean inRange = isWithinAttackRange(mc.player.getEntityPos(), target, combatRange.get());
            if (mayAttack && inRange) {
                setTargetToPlayer(predictor);
                scheduleAttack();
                stateMachine.markForEndState();
                return movementDirection.rotation().y >= 0 ? STATE_PULL_UP : STATE_FOLLOW;
            }

            setTargetToPlayer(predictor);
            if (movementDirection.rotation().y >= 0) {
                stateMachine.markForEndState();
                return STATE_PULL_UP;
            }
            stateMachine.markForEndState();
            return STATE_FOLLOW;
        }

        private int onStateWaitAttack(StateMachine stateMachine) {
            if (currentAction != TargetAction.AFK && currentAction != TargetAction.SLOW_SPEED) {
                stateMachine.markForEndState();
                return STATE_PULL_UP;
            }
            startWaitAttack++;
            if (startWaitAttack > 1) {
                stateMachine.markForEndState();
                return currentOnGround ? STATE_NONE : STATE_FOLLOW;
            }
            Vec3d predicted = maceUsePredictor.get() ? predictTarget() : null;
            Vec3d targetPos = predicted != null ? predicted.withAxis(Direction.Axis.Y, target.getY()) : target.getEntityPos();
            setTargetToPlayer(targetPos);
            if (movementDirection.rotation().y >= 0) {
                stateMachine.markForEndState();
                return STATE_PULL_UP;
            }
            stateMachine.markForEndState();
            return STATE_WAIT_ATTACK;
        }

        @Override
        void onUpdate() {
            if (!isFallFlying()) {
                setSpeed(0);
                machine.setState(STATE_NONE);
                return;
            }
            maxHeightInAttack = Math.max(maxHeightInAttack, mc.player.getY());
            machine.step();
            if (attackFlag && target != null && target.isAlive()) {
                doAttack();
            }
            attackFlag = false;
            maxHeightInAttack = mc.player.getY();
        }

        private void scheduleAttack() {
            attackFlag = true;
        }

        private boolean shouldAttackSimple() {
            if (!maceAttackUseSimple.get()) return false;
            return attackCooldown() > 0.95 && canUseMace();
        }

        private boolean shouldAttackMace() {
            if (lastMaceAttackSuccessTick >= tick) return false;
            return fallDistance >= maceMinFallDistance.get() && canUseMace() && attackCooldown() > 0.9;
        }

        private boolean canAttackMace() {
            return lastMaceAttackSuccessTick < tick && canUseMace() && fallDistance >= 1.5;
        }

        // 下落够高才砸 mace
        private void doAttack() {
            if (target == null) return;
            boolean useMace = shouldAttackMace() && canAttackMace();
            lookAt(target);
            FindItemResult mace = InvUtils.findInHotbar(Items.MACE);
            int prevSlot = mc.player.getInventory().getSelectedSlot();
            if (useMace && mace.found()) {
                mc.player.getInventory().setSelectedSlot(mace.slot());
            }
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
            if (useMace && mace.found()) {
                mc.player.getInventory().setSelectedSlot(prevSlot);
            }
            lastMaceAttackTick = tick;
        }

        private void setTargetToPlayerUpper(Vec3d predictor, double height) {
            Vec3d playerPos = mc.player.getEntityPos();
            Vec3d targetPos = new Vec3d(predictor.x, predictor.y + height, predictor.z);
            if (heightLimitEnvironment) {
                double ceiling = mc.world.getBottomY() + mc.world.getDimension().logicalHeight() - 30;
                if (targetPos.y > ceiling) targetPos = targetPos.withAxis(Direction.Axis.Y, ceiling);
            }
            Vec3d movement = targetPos.subtract(playerPos);
            if (movement.length() < 5) {
                movement = movement.length() < 1e-6 ? new Vec3d(0, 1, 0) : movement.normalize().multiply(5);
            }
            setMovement(movement, 10);
            if (angleOptimizePullUp.get() && movementDirection.rotation().y >= 0) {
                Vec3d rotation = movementDirection.rotation();
                rotation = rotation.withAxis(Direction.Axis.Y, Math.max(Math.abs(rotation.x), Math.abs(rotation.z)));
                setRotation(rotation);
                Vec2f best = calculateBestPullupSpeed(rotationToPitch(movementDirection.rotation()), rotationToYaw(movementDirection.rotation()));
                setRotation(best.x, best.y);
            }
        }

        private void setTargetToPlayer(Vec3d targetPos) {
            if (currentOnGround) targetPos = targetPos.add(0, followOnGroundHeight.get(), 0);
            double weight = maceYLevelWeight.get();
            if (weight > 0) {
                targetPos = new Vec3d(targetPos.x,
                    mc.player.getY() + (targetPos.y - mc.player.getY()) * weight, targetPos.z);
            }
            Vec3d movement = targetPos.subtract(mc.player.getEntityPos());
            if (movement.lengthSquared() < 1e-6) movement = new Vec3d(0, -1, 0);
            setMovement(movement, 10);
            Vec3d rotation = movementDirection.rotation();
            if (rotation.y >= 0) return;
            if (angleOptimizeFollow.get()) {
                Vec2f best = calculateBestDownForwardSpeed(rotationToPitch(rotation), rotationToYaw(rotation), true, false);
                setRotation(best.x, best.y);
            }
        }
    }

    private Entity findTarget() {
        List<Entity> list = new ArrayList<>();
        TargetUtils.getList(list, this::isValidTarget, SortPriority.LowestDistance, 1);
        return list.isEmpty() ? null : list.get(0);
    }

    private static class StateMachine {
        interface StateAction {
            int step(StateMachine machine);
        }

        interface StateUpdater {
            int update(StateMachine machine, int state);
        }

        private int state;
        private final StateAction[] actions;
        private final StateUpdater updater;
        private boolean currentEnd;
        private boolean stepping;

        StateMachine(int initState, StateUpdater updater, StateAction... actions) {
            this.state = initState;
            this.updater = updater;
            this.actions = actions;
        }

        int state() {
            return state;
        }

        void setState(int state) {
            if (stepping) throw new IllegalStateException("Cannot set state while stepping");
            setStateInternal(state);
        }

        private void setStateInternal(int state) {
            if (state >= 0 && state < actions.length) this.state = state;
        }

        void markForEndState() {
            currentEnd = true;
        }

        void step() {
            if (stepping) return;
            stepping = true;
            currentEnd = false;
            setStateInternal(updater.update(this, state));
            for (int i = 0; i < actions.length && !currentEnd; i++) {
                setStateInternal(actions[state].step(this));
            }
            stepping = false;
        }
    }
}
