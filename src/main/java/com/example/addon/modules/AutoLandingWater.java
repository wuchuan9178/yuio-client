package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;

public class AutoLandingWater extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlace = settings.createGroup("Place");
    private final SettingGroup sgPickup = settings.createGroup("Pickup");

    private final Setting<Double> minFall = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-fall-distance")
        .description("Minimum fall distance before the module activates.")
        .defaultValue(8.0)
        .min(3.0)
        .sliderRange(3.0, 60.0)
        .build());

    private final Setting<Integer> placeBuffer = sgGeneral.add(new IntSetting.Builder()
        .name("place-buffer-ticks")
        .description("Ticks of buffer when calculating placement time. Higher = safer.")
        .defaultValue(3)
        .min(1)
        .sliderRange(1, 8)
        .build());

    private final Setting<Boolean> smartTiming = sgGeneral.add(new BoolSetting.Builder()
        .name("smart-timing")
        .description("Also require at least one free block of headroom above the landing spot.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> autoPickup = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-pickup")
        .description("Automatically pick the water back up after landing.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> rotate = sgPlace.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Look straight down before placing, otherwise the water lands on a side face.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> rotateBack = sgPlace.add(new BoolSetting.Builder()
        .name("rotate-back")
        .description("Send the original angles back to the server after placing and picking up.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> verifyPlace = sgPlace.add(new BoolSetting.Builder()
        .name("verify-place")
        .description("Check that the water really appeared, retry on the next tick if it did not.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> placeRetry = sgPlace.add(new IntSetting.Builder()
        .name("place-retry")
        .description("Placement attempts before giving up.")
        .min(1)
        .sliderRange(1, 6)
        .defaultValue(3)
        .visible(verifyPlace::get)
        .build());

    private final Setting<Boolean> avoidLava = sgPlace.add(new BoolSetting.Builder()
        .name("avoid-lava")
        .description("Skip landing spots whose floor is lava.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> fillHole = sgPlace.add(new BoolSetting.Builder()
        .name("fill-hole")
        .description("Place the water one block above the floor when the floor is not solid enough.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> swapBack = sgPlace.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Restore the hotbar slot after placing.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> pickupDelay = sgPickup.add(new IntSetting.Builder()
        .name("pickup-delay")
        .description("Ticks to wait after landing before picking up the water.")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 10)
        .build());

    private final Setting<Integer> pickupRange = sgPickup.add(new IntSetting.Builder()
        .name("pickup-range")
        .description("Radius searched for our own water source.")
        .min(1)
        .sliderRange(1, 4)
        .defaultValue(2)
        .visible(autoPickup::get)
        .build());

    private final Setting<Integer> pickupRetry = sgPickup.add(new IntSetting.Builder()
        .name("pickup-retry")
        .description("Immediate pickup attempts before pausing and searching again.")
        .min(1)
        .sliderRange(1, 8)
        .defaultValue(4)
        .visible(autoPickup::get)
        .build());

    private final Setting<Integer> pickupTimeout = sgPickup.add(new IntSetting.Builder()
        .name("pickup-timeout")
        .description("Ticks to keep trying to pick the water up before giving up.")
        .min(5)
        .sliderRange(5, 100)
        .defaultValue(30)
        .visible(autoPickup::get)
        .build());

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug")
        .description("Log the state machine, raycast and slot decisions.")
        .defaultValue(true)
        .build());

    private enum State {
        Idle,
        Falling,
        PickingUp
    }

    private State state = State.Idle;
    private int timer;
    private int retries;
    private int fallingTicks;
    private int pickupTicks;
    private boolean bucketInOffhand;
    private boolean holdingBucket;
    private BlockPos waterPos;
    private BlockPos placeExpect;
    private Vec3d waterCenter;
    private boolean rotated;
    private float savedYaw;
    private float savedPitch;

    public AutoLandingWater() {
        super(AddonTemplate.ASSIST, "auto-landing-water",
            "Places water under you when falling from a high place.");
    }

    @Override
    public void onActivate() {
        reset();
    }

    @Override
    public void onDeactivate() {
        if (holdingBucket && swapBack.get()) InvUtils.swapBack();
        holdingBucket = false;
        restoreRotation();
        reset();
    }

    private void reset() {
        state = State.Idle;
        timer = 0;
        retries = 0;
        fallingTicks = 0;
        pickupTicks = 0;
        bucketInOffhand = false;
        waterPos = null;
        placeExpect = null;
        waterCenter = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        switch (state) {
            case Idle -> tickIdle();
            case Falling -> tickFalling();
            case PickingUp -> tickPickingUp();
        }
    }

    private void tickIdle() {
        if (mc.player.isOnGround()) return;
        if (mc.player.isTouchingWater()) return;
        if (mc.player.isGliding()) return;
        if (mc.player.getAbilities().flying) return;
        if (mc.player.fallDistance < minFall.get()) return;

        double speed = Math.abs(mc.player.getVelocity().y);
        if (speed <= 0.01) return;

        BlockHitResult ground = groundRaycast(mc.player.getEntityPos(), RaycastContext.ShapeType.COLLIDER);
        if (ground == null) {
            logDebug("idle: ground raycast MISSED fall=%.2f y=%.2f", mc.player.fallDistance, mc.player.getY());
            return;
        }

        double groundDist = mc.player.getY() - ground.getPos().y;
        double safeDist = Math.max(2.0, speed * placeBuffer.get() + 0.5);
        logDebug("idle: ground=%s side=%s groundDist=%.2f safeDist=%.2f fall=%.2f vy=%.3f",
            ground.getBlockPos(), ground.getSide(), groundDist, safeDist, mc.player.fallDistance, speed);
        if (groundDist > safeDist) return;
        if (!groundDistValid(ground)) {
            logDebug("idle: groundDistValid=false (lava=%s smart=%s)", containsLava(ground.getBlockPos().up()),
                smartTiming.get());
            return;
        }

        FindItemResult bucket = ensureWaterBucket();
        logDebug("idle: bucket found=%s slot=%d offhand=%s mainHand=%s selected=%d stack=%s",
            bucket.found(), bucket.slot(), bucket.isOffhand(), bucket.isMainHand(),
            mc.player.getInventory().getSelectedSlot(),
            mc.player.getInventory().getStack(mc.player.getInventory().getSelectedSlot()));
        if (!bucket.found()) {
            error("No water bucket in the inventory.");
            toggle();
            return;
        }

        bucketInOffhand = bucket.isOffhand();
        holdingBucket = false;

        if (!bucketInOffhand && !bucket.isMainHand()) {
            boolean swapped = InvUtils.swap(bucket.slot(), true);
            holdingBucket = true;
            logDebug("idle: swap to slot %d -> %s, now selected=%d stack=%s", bucket.slot(), swapped,
                mc.player.getInventory().getSelectedSlot(),
                mc.player.getInventory().getStack(mc.player.getInventory().getSelectedSlot()));
        }

        savedYaw = mc.player.getYaw();
        savedPitch = mc.player.getPitch();
        rotated = false;

        logDebug("idle: pitch=%.1f", mc.player.getPitch());
        ActionResult result = interactBucket(center(ground.getBlockPos()));
        logDebug("idle: interacted, hand=%s stackInHand=%s", bucketInOffhand ? "OFF" : "MAIN",
            mc.player.getStackInHand(bucketInOffhand ? Hand.OFF_HAND : Hand.MAIN_HAND));

        if (!result.isAccepted()) {
            waterPos = ground.getBlockPos().up();
            waterCenter = new Vec3d(waterPos.getX() + 0.5, waterPos.getY() + 0.5, waterPos.getZ() + 0.5);
        }

        placeExpect = waterPos;
        logDebug("idle: expect water at %s", waterPos);

        state = State.Falling;
        fallingTicks = 0;
        retries = 0;
        timer = 0;
    }

    private void tickFalling() {
        fallingTicks++;

        if (mc.player.isOnGround() || mc.player.isTouchingWater()) {
            logDebug("falling: landed onGround=%s inWater=%s ticks=%d expectWater=%s actualWater=%s",
                mc.player.isOnGround(), mc.player.isTouchingWater(), fallingTicks, placeExpect,
                isWaterAt(placeExpect));
            restoreRotation();
            if (swapBack.get()) releaseBucket();
            state = State.PickingUp;
            timer = pickupDelay.get();
            retries = 0;
            pickupTicks = 0;
            return;
        }

        if (verifyPlace.get() && fallingTicks > 2 && !waterUnderPlayer()) {
            retries++;
            logDebug("falling: no water under player at %s, retry %d/%d expectWater=%s", mc.player.getBlockPos(),
                retries, placeRetry.get(), placeExpect);
            if (retries <= placeRetry.get()) {
                FindItemResult bucket = ensureWaterBucket();
                if (bucket.found()) {
                    if (!bucket.isOffhand() && !bucket.isMainHand()) {
                        InvUtils.swap(bucket.slot(), true);
                        holdingBucket = true;
                    }
                    BlockPos below = mc.player.getBlockPos().down();
                    logDebug("falling: retry place below=%s selected=%d stack=%s", below,
                        mc.player.getInventory().getSelectedSlot(),
                        mc.player.getInventory().getStack(mc.player.getInventory().getSelectedSlot()));
                    waterPos = null;
                    ActionResult result = interactBucket(center(below));
                    if (!result.isAccepted()) {
                        waterPos = below.up();
                        waterCenter = new Vec3d(waterPos.getX() + 0.5, waterPos.getY() + 0.5, waterPos.getZ() + 0.5);
                    }
                    placeExpect = waterPos;
                    fallingTicks = 0;
                    return;
                }
                logDebug("falling: retry found no water bucket");
            }
        }

        if (fallingTicks > 200) {
            logDebug("falling: timeout after 200 ticks, giving up");
            restoreRotation();
            releaseBucket();
            reset();
        }
    }

    private void tickPickingUp() {
        if (!autoPickup.get()) {
            restoreRotation();
            releaseBucket();
            reset();
            return;
        }

        if (timer > 0) {
            timer--;
            return;
        }

        if (mc.player.isTouchingWater() || mc.player.isOnGround()) lookDown();

        pickupTicks++;

        BlockPos source = trackedWaterSource();
        if (source == null) source = findWaterSource();

        logDebug("pickup: trackedWater=%s isWater=%s source=%s ticks=%d retries=%d", waterPos,
            isWaterAt(waterPos), source, pickupTicks, retries);
        if (source == null) {
            restoreRotation();
            releaseBucket();
            reset();
            return;
        }

        FindItemResult empty = ensureEmptyBucket();
        logDebug("pickup: emptyBucket found=%s slot=%d offhand=%s mainHand=%s selected=%d stack=%s",
            empty.found(), empty.slot(), empty.isOffhand(), empty.isMainHand(),
            mc.player.getInventory().getSelectedSlot(),
            mc.player.getInventory().getStack(mc.player.getInventory().getSelectedSlot()));
        if (!empty.found()) {
            restoreRotation();
            releaseBucket();
            reset();
            return;
        }

        bucketInOffhand = empty.isOffhand();
        if (!bucketInOffhand && !empty.isMainHand()) {
            boolean swapped = InvUtils.swap(empty.slot(), true);
            holdingBucket = true;
            logDebug("pickup: swap to slot %d -> %s, now selected=%d stack=%s", empty.slot(), swapped,
                mc.player.getInventory().getSelectedSlot(),
                mc.player.getInventory().getStack(mc.player.getInventory().getSelectedSlot()));
        }

        interactBucket(center(source));

        retries++;
        if (pickupTicks >= pickupTimeout.get()) {
            logDebug("pickup: timeout after %d ticks, waterLeft=%s", pickupTicks, isWaterAt(source));
            restoreRotation();
            releaseBucket();
            reset();
            return;
        }

        BlockPos again = trackedWaterSource();
        if (again == null) again = findWaterSource();
        if (again != null) {
            logDebug("pickup: water still at %s, retry %d/%d", again, retries, pickupRetry.get());
            timer = 1;
            return;
        }

        logDebug("pickup: done, retries=%d", retries);
        restoreRotation();
        releaseBucket();
        reset();
    }

    private BlockPos trackedWaterSource() {
        BlockPos tracked = waterPos;

        if (tracked == null && waterCenter != null) {
            tracked = BlockPos.ofFloored(waterCenter);
        }

        if (tracked == null) return null;
        if (!isWaterAt(tracked)) return null;

        return withinPickupRange(tracked) ? tracked : null;
    }

    private boolean withinPickupRange(BlockPos pos) {
        if (mc.player == null) return false;

        double reach = pickupRange.get() + 4.0;
        return center(pos).squaredDistanceTo(mc.player.getEyePos()) <= reach * reach;
    }

    private void logDebug(String format, Object... args) {
        if (!debug.get()) return;

        AddonTemplate.LOG.info("[yuio-water] " + String.format(format, args));
    }

    private void lookDown() {
        lookAt(null);
    }

    private void lookAt(Vec3d target) {
        if (mc.player == null) return;
        if (!rotate.get()) return;

        if (!rotated) {
            savedYaw = mc.player.getYaw();
            savedPitch = mc.player.getPitch();
            rotated = true;
        }

        float yaw = mc.player.getYaw();
        float pitch = 90.0f;

        if (target != null) {
            Vec3d eyes = mc.player.getEyePos();
            Vec3d dir = target.subtract(eyes);
            double horizontal = Math.sqrt(dir.x * dir.x + dir.z * dir.z);

            if (horizontal > 0.05) {
                yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
                pitch = (float) -Math.toDegrees(Math.atan2(dir.y, horizontal));
                pitch = MathHelper.clamp(pitch, -90.0f, 90.0f);
            }
        }

        mc.player.setPitch(pitch);
        sendRotation(yaw, pitch);
    }

    private void restoreCamera() {
        if (mc.player == null) return;
        if (!rotated) return;

        mc.player.setPitch(savedPitch);
    }

    private void restoreRotation() {
        if (mc.player == null) {
            rotated = false;
            return;
        }

        restoreCamera();

        if (rotateBack.get()) sendRotation(savedYaw, savedPitch);
        rotated = false;    }

    private void sendRotation(float yaw, float pitch) {
        if (mc.player == null || mc.player.networkHandler == null) return;

        mc.player.networkHandler.sendPacket(
            new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, mc.player.isOnGround(), false));
    }

    private Hand bucketHand() {
        return bucketInOffhand ? Hand.OFF_HAND : Hand.MAIN_HAND;
    }

    private ActionResult interactBucket(Vec3d target) {
        if (mc.player == null || mc.interactionManager == null) return ActionResult.FAIL;

        Hand hand = bucketHand();
        lookAt(target);

        ActionResult result = mc.interactionManager.interactItem(mc.player, hand);

        if (result.isAccepted()) {
            waterPos = BlockPos.ofFloored(target);
            waterCenter = new Vec3d(waterPos.getX() + 0.5, waterPos.getY() + 0.5, waterPos.getZ() + 0.5);
        }

        restoreCamera();

        logDebug("interact result=%s accepted=%s hand=%s target=%s water=%s", result, result.isAccepted(),
            hand, target, waterPos);
        return result;
    }

    private void releaseBucket() {
        if (holdingBucket && swapBack.get()) InvUtils.swapBack();
        holdingBucket = false;
    }

    private boolean groundDistValid(BlockHitResult ground) {
        BlockPos hitPos = ground.getBlockPos();
        BlockPos landing = hitPos.up();

        if (avoidLava.get() && containsLava(landing)) return false;
        if (!smartTiming.get()) return true;

        BlockPos above = landing.up();
        return isPassable(landing) || isPassable(above);
    }

    private boolean containsLava(BlockPos pos) {
        if (mc.world == null) return false;

        FluidState fluid = mc.world.getFluidState(pos);
        return !fluid.isEmpty() && fluid.isIn(net.minecraft.registry.tag.FluidTags.LAVA);
    }

    private boolean isPassable(BlockPos pos) {
        if (mc.world == null) return false;

        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) return true;
        if (!state.getFluidState().isEmpty()) return true;
        return state.getCollisionShape(mc.world, pos).isEmpty();
    }

    private boolean isWaterAt(BlockPos pos) {
        if (pos == null || mc.world == null) return false;

        FluidState fluid = mc.world.getFluidState(pos);
        return !fluid.isEmpty() && fluid.isIn(net.minecraft.registry.tag.FluidTags.WATER);
    }

    private boolean waterUnderPlayer() {
        if (mc.player == null || mc.world == null) return false;
        BlockPos feet = mc.player.getBlockPos();
        for (int dy = 0; dy >= -3; dy--) {
            if (isWaterAt(feet.add(0, dy, 0))) return true;
        }
        return false;
    }

    private BlockHitResult groundRaycast(Vec3d from, RaycastContext.ShapeType shapeType) {
        return raycast(new RaycastContext(
            from,
            from.add(0, -60, 0),
            shapeType,
            RaycastContext.FluidHandling.NONE,
            mc.player
        ));
    }

    private BlockHitResult raycastPlacement() {
        return raycast(mc.player.getEntityPos(), mc.player.getRotationVector(),
            RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE);
    }

    private BlockHitResult raycast(Vec3d from, Vec3d direction, RaycastContext.ShapeType shapeType,
                                   RaycastContext.FluidHandling fluids) {
        return raycast(new RaycastContext(
            from,
            from.add(direction.multiply(60.0)),
            shapeType,
            fluids,
            mc.player
        ));
    }

    private BlockHitResult raycast(RaycastContext context) {
        BlockHitResult hit = mc.world.raycast(context);
        return hit != null && hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    private Vec3d center(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private BlockPos findWaterSource() {
        if (mc.world == null || mc.player == null) return null;

        int radius = pickupRange.get();
        BlockPos feet = mc.player.getBlockPos();
        List<BlockPos> candidates = new ArrayList<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= 1; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = feet.add(dx, dy, dz);
                    FluidState fluid = mc.world.getFluidState(pos);
                    if (fluid.isEmpty()) continue;
                    if (!fluid.isIn(net.minecraft.registry.tag.FluidTags.WATER)) continue;
                    if (!fluid.isStill()) continue;

                    candidates.add(pos);
                }
            }
        }

        if (candidates.isEmpty()) return null;

        BlockPos best = candidates.get(0);
        double bestDistance = Double.MAX_VALUE;

        for (BlockPos pos : candidates) {
            double distance = center(pos).squaredDistanceTo(mc.player.getEntityPos());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos;
            }
        }

        return best;
    }

    private FindItemResult ensureWaterBucket() {
        return ensureItem(Items.WATER_BUCKET);
    }

    private FindItemResult ensureEmptyBucket() {
        return ensureItem(Items.BUCKET);
    }

    private FindItemResult ensureItem(Item item) {
        FindItemResult result = InvUtils.findInHotbar(item);
        if (result.found()) return result;

        result = InvUtils.find(item);
        if (!result.found()) return result;

        int empty = emptyHotbarSlot();
        if (empty < 0) return new FindItemResult(-1, 0);

        InvUtils.move().fromId(result.slot()).toHotbar(empty);
        return InvUtils.findInHotbar(item);
    }

    private int emptyHotbarSlot() {
        if (mc.player == null) return -1;

        int selected = mc.player.getInventory().getSelectedSlot();
        for (int i = 0; i < 9; i++) {
            if (i == selected) continue;
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }

        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }

        return -1;
    }

    @Override
    public String getInfoString() {
        if (state == State.Idle) return null;
        if (mc.player == null) return state.name();

        return String.format("%s fall=%.1f", state.name(), mc.player.fallDistance);
    }
}