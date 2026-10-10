package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import dev.sherii.yuio.AModule;
import dev.sherii.yuio.utils.FindUtils;
import dev.sherii.yuio.utils.InteractUtils;
import dev.sherii.yuio.utils.SwapUtils;
import dev.sherii.yuio.utils.TpUtils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Set;

public class AutoElytraMace extends AModule {
    public enum State {
        Idle,
        Target,
        GlideToAbove,
        Offset,
        Dive,
        Approach,
        Drop,
        MaceSetup,
        Attack,
        Launch,
        Cooldown
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFlight = settings.createGroup("Flight");
    private final SettingGroup sgDive = settings.createGroup("Dive");
    private final SettingGroup sgAttack = settings.createGroup("Attack");

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to attack.")
        .onlyAttackable()
        .defaultValue(EntityType.PLAYER)
        .build());

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Do not target friends.")
        .defaultValue(true)
        .build());

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Target search range, 0 for unlimited.")
        .min(0.0)
        .sliderRange(0.0, 256.0)
        .defaultValue(120.0)
        .build());

    private final Setting<List<String>> targetNames = sgGeneral.add(new StringListSetting.Builder()
        .name("target-names")
        .description("Prioritized player names, nearest among them is attacked first.")
        .defaultValue(List.of())
        .build());

    private final Setting<Boolean> onlyInAir = sgGeneral.add(new BoolSetting.Builder()
        .name("only-in-air")
        .description("Only work while the player is not on the ground.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> autoElytra = sgFlight.add(new BoolSetting.Builder()
        .name("auto-elytra")
        .description("Move the elytra into the chest slot automatically.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> autoReplenish = sgFlight.add(new BoolSetting.Builder()
        .name("auto-replenish")
        .description("Allow moving items from the inventory into the hotbar and the armor slots.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> useWindCharge = sgFlight.add(new BoolSetting.Builder()
        .name("use-wind-charge")
        .description("Prefer wind charges for the boost, they work without gliding.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> useFirework = sgFlight.add(new BoolSetting.Builder()
        .name("use-firework")
        .description("Use fireworks for the boost, they need the elytra and gliding.")
        .defaultValue(true)
        .build());

    private final Setting<Item> fireworkItem = sgFlight.add(new ItemSetting.Builder()
        .name("firework-item")
        .description("Item used as the firework boost.")
        .defaultValue(Items.FIREWORK_ROCKET)
        .visible(useFirework::get)
        .build());

    private final Setting<Double> aboveHeight = sgFlight.add(new DoubleSetting.Builder()
        .name("above-height")
        .description("Height above the target used as the flight destination.")
        .min(1.0)
        .sliderRange(1.0, 64.0)
        .defaultValue(14.0)
        .build());

    private final Setting<Double> horizontalArrive = sgFlight.add(new DoubleSetting.Builder()
        .name("horizontal-arrive")
        .description("Horizontal distance to the destination that counts as arrived.")
        .min(0.1)
        .sliderRange(0.1, 10.0)
        .defaultValue(1.5)
        .build());

    private final Setting<Double> arriveTolerance = sgFlight.add(new DoubleSetting.Builder()
        .name("arrive-tolerance")
        .description("Vertical tolerance when checking the flight destination.")
        .min(0.5)
        .sliderRange(0.5, 8.0)
        .defaultValue(2.0)
        .build());

    private final Setting<Double> climbStep = sgFlight.add(new DoubleSetting.Builder()
        .name("climb-step")
        .description("Distance moved per teleport step, keep it small to stay under the anti cheat.")
        .min(1.0)
        .sliderRange(1.0, 32.0)
        .defaultValue(6.0)
        .build());

    private final Setting<Integer> boostInterval = sgFlight.add(new IntSetting.Builder()
        .name("boost-interval")
        .description("Ticks between two flight boosts.")
        .min(1)
        .sliderRange(1, 60)
        .defaultValue(20)
        .build());

    private final Setting<Double> offset = sgDive.add(new DoubleSetting.Builder()
        .name("offset")
        .description("Sideways distance kept from the target before diving.")
        .min(0.0)
        .sliderRange(0.0, 8.0)
        .defaultValue(2.0)
        .build());

    private final Setting<Double> diveAhead = sgDive.add(new DoubleSetting.Builder()
        .name("dive-ahead")
        .description("Distance to the target at which the dive state is entered.")
        .min(0.5)
        .sliderRange(0.5, 24.0)
        .defaultValue(8.0)
        .build());

    private final Setting<Double> fallHeight = sgDive.add(new DoubleSetting.Builder()
        .name("fall-height")
        .description("Height above the target the drop starts from, the mace needs this falling distance.")
        .min(1.0)
        .sliderRange(1.0, 20.0)
        .defaultValue(3.0)
        .build());

    private final Setting<Integer> dropDelay = sgDive.add(new IntSetting.Builder()
        .name("drop-delay")
        .description("Ticks waited while falling before the chestplate is swapped and the attack lands.")
        .min(0)
        .sliderRange(0, 20)
        .defaultValue(2)
        .build());

    private final Setting<Boolean> swapChestplate = sgAttack.add(new BoolSetting.Builder()
        .name("swap-chestplate")
        .description("Equip a chestplate before attacking to reset the mace attack.")
        .defaultValue(true)
        .build());

    private final Setting<Item> chestplate = sgAttack.add(new ItemSetting.Builder()
        .name("chestplate")
        .description("Chestplate equipped before attacking.")
        .defaultValue(Items.NETHERITE_CHESTPLATE)
        .visible(swapChestplate::get)
        .build());

    private final Setting<Integer> attackDelay = sgAttack.add(new IntSetting.Builder()
        .name("attack-delay")
        .description("Ticks between two attack cycles.")
        .min(0)
        .sliderRange(0, 60)
        .defaultValue(4)
        .build());

    private final Setting<Integer> attackTimes = sgAttack.add(new IntSetting.Builder()
        .name("attack-times")
        .description("Attack packets sent per cycle.")
        .min(1)
        .sliderRange(1, 16)
        .defaultValue(1)
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

    private final Setting<Integer> retry = sgAttack.add(new IntSetting.Builder()
        .name("retry")
        .description("Attack cycles per target before giving up, 0 for unlimited.")
        .min(0)
        .sliderRange(0, 16)
        .defaultValue(3)
        .build());

    private State state = State.Idle;
    private Entity target;
    private int timer;
    private int retries;
    private int boostTimer;
    private boolean maceSwapped;
    private boolean boostSwapped;
    private boolean armorSwapped;

    public AutoElytraMace() {
        super(AddonTemplate.ATTACK, "AutoElytraMace", "Elytra flight, dive and mace smash the target.");
    }

    @Override
    public void onActivate() {
        state = State.Idle;
        target = null;
        timer = 0;
        retries = 0;
        boostTimer = 0;
        maceSwapped = false;
        boostSwapped = false;
        armorSwapped = false;
    }

    @Override
    public void onDeactivate() {
        restore(true);
        state = State.Idle;
        target = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null) return;

        if (timer > 0) timer--;
        if (boostTimer > 0) boostTimer--;

        if (onlyInAir.get() && player.isOnGround() && state == State.Idle) return;

        if (state != State.Idle && !isValidTarget(target)) {
            restore(true);
            target = null;
            state = State.Idle;
            return;
        }

        switch (state) {
            case Idle -> handleIdle();
            case Target -> handleTarget();
            case GlideToAbove -> handleGlideToAbove();
            case Offset -> handleOffset();
            case Dive -> handleDive();
            case Approach -> handleApproach();
            case Drop -> handleDrop();
            case MaceSetup -> handleMaceSetup();
            case Attack -> handleAttack();
            case Launch -> handleLaunch();
            case Cooldown -> handleCooldown();
            default -> {
            }
        }
    }

    private void handleIdle() {
        Entity found = findTarget();
        if (found == null) return;

        target = found;
        retries = 0;
        state = State.Target;
    }

    private void handleTarget() {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        if (!hasItem(Items.ELYTRA)) {
            fail("No elytra found.");
            return;
        }

        if (!hasItem(Items.MACE)) {
            fail("No mace found.");
            return;
        }

        if (useWindCharge.get() && hasItem(Items.WIND_CHARGE)) {
            if (!hasBoost()) {
                fail("No boost item found.");
                return;
            }
        } else if (useFirework.get() && !hasItem(fireworkItem.get())) {
            fail("No " + fireworkItem.get().getName().getString() + " or wind charge found.");
            return;
        }

        if (autoElytra.get() && !equipArmor(Items.ELYTRA)) {
            fail("Could not equip the elytra.");
            return;
        }

        if (player.isOnGround()) {
            player.jump();
            return;
        }

        if (!player.isGliding()) {
            player.startGliding();
            return;
        }

        state = State.GlideToAbove;
    }

    private void handleGlideToAbove() {
        ClientPlayerEntity player = mc.player;
        Entity t = target;
        if (player == null || t == null) return;

        if (!player.isGliding()) {
            state = State.Target;
            return;
        }

        Vec3d playerPos = player.getEntityPos();
        Vec3d targetPos = t.getEntityPos();
        double destY = targetPos.y + aboveHeight.get();
        double dx = targetPos.x - playerPos.x;
        double dz = targetPos.z - playerPos.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        lookAt(targetPos.x, destY, targetPos.z);

        if (horizontal <= horizontalArrive.get() && playerPos.y >= destY - arriveTolerance.get()) {
            state = State.Offset;
            return;
        }

        if (boostTimer <= 0) boost();

        double stepH = Math.min(horizontal, climbStep.get());
        double moveX = horizontal <= 0.001 ? 0.0 : dx / horizontal * stepH;
        double moveZ = horizontal <= 0.001 ? 0.0 : dz / horizontal * stepH;
        double destPlayerY = Math.min(playerPos.y + climbStep.get(), destY);

        TpUtils.INSTANCE.tp(playerPos, new Vec3d(playerPos.x + moveX, destPlayerY, playerPos.z + moveZ), false);
    }

    private void handleOffset() {
        ClientPlayerEntity player = mc.player;
        Entity t = target;
        if (player == null || t == null) return;

        if (!player.isGliding()) {
            state = State.Target;
            return;
        }

        Vec3d playerPos = player.getEntityPos();
        Vec3d targetPos = t.getEntityPos();
        Vec3d want = offsetPos(playerPos, targetPos);
        double distance = Math.sqrt(sq(want.x - playerPos.x) + sq(want.z - playerPos.z));

        lookAt(targetPos.x, targetPos.y + t.getHeight() * 0.5, targetPos.z);

        if (distance <= 0.6 || offset.get() <= 0.0) {
            state = State.Dive;
            return;
        }

        TpUtils.INSTANCE.tp(playerPos, new Vec3d(want.x, playerPos.y, want.z), false);
    }

    private void handleDive() {        ClientPlayerEntity player = mc.player;
        Entity t = target;
        if (player == null || t == null) return;

        if (!player.isGliding()) {
            state = State.Target;
            return;
        }

        Vec3d playerPos = player.getEntityPos();
        Vec3d targetPos = t.getEntityPos();
        double centerY = targetPos.y + t.getHeight() * 0.5;
        double horizontal = Math.sqrt(sq(targetPos.x - playerPos.x) + sq(targetPos.z - playerPos.z));

        lookAt(targetPos.x, centerY, targetPos.z);

        if (horizontal <= diveAhead.get()) {
            state = State.Approach;
            return;
        }

        double step = Math.min(climbStep.get(), Math.max(1.0, horizontal - diveAhead.get()));
        double dx = targetPos.x - playerPos.x;
        double dz = targetPos.z - playerPos.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        Vec3d dest = length <= 0.001
            ? playerPos
            : new Vec3d(playerPos.x + dx / length * step, playerPos.y, playerPos.z + dz / length * step);

        TpUtils.INSTANCE.tp(playerPos, dest, false);
    }

    private void handleApproach() {
        ClientPlayerEntity player = mc.player;
        Entity t = target;
        if (player == null || t == null) return;

        Vec3d playerPos = player.getEntityPos();
        Vec3d targetPos = t.getEntityPos();
        double centerY = targetPos.y + t.getHeight() * 0.5;
        double startY = targetPos.y + fallHeight.get();

        lookAt(targetPos.x, centerY, targetPos.z);

        TpUtils.INSTANCE.tp(playerPos, new Vec3d(targetPos.x, startY, targetPos.z), false);
        timer = 0;
        state = State.Drop;
    }

    private void handleDrop() {
        ClientPlayerEntity player = mc.player;
        Entity t = target;
        if (player == null || t == null) return;

        if (timer > 0) return;

        if (swapChestplate.get()) {
            Item armor = chestplate.get();
            if (armor != null && player.getEquippedStack(EquipmentSlot.CHEST).getItem() != armor) {
                if (!equipArmor(armor)) {
                    fail("No chestplate found, check the chestplate setting.");
                    return;
                }
                armorSwapped = true;
                return;
            }
        }

        Vec3d playerPos = player.getEntityPos();
        Vec3d targetPos = t.getEntityPos();
        double dropY = targetPos.y;
        if (!TpUtils.INSTANCE.isPlayerSafe(net.minecraft.util.math.BlockPos.ofFloored(targetPos.x, dropY, targetPos.z))) {
            dropY += 1.0;
        }

        TpUtils.INSTANCE.tp(playerPos, new Vec3d(targetPos.x, dropY, targetPos.z), false);
        timer = dropDelay.get();
        state = State.MaceSetup;
    }

    private void handleMaceSetup() {
        ClientPlayerEntity player = mc.player;
        Entity t = target;
        if (player == null || t == null) return;

        if (timer > 0) return;

        FindItemResult mace = InvUtils.findInHotbar(Items.MACE);
        if (!mace.found()) {
            if (!autoReplenish.get() || !replenish(Items.MACE)) {
                fail("No mace in the hotbar.");
                return;
            }
            mace = InvUtils.findInHotbar(Items.MACE);
            if (!mace.found()) return;
        }

        if (!mace.isMainHand()) {
            SwapUtils.INSTANCE.swap(mace.slot(), true);
            maceSwapped = true;
        }

        state = State.Attack;
    }

    private void handleAttack() {
        ClientPlayerEntity player = mc.player;
        Entity t = target;
        if (player == null || t == null) {
            state = State.Launch;
            return;
        }

        if (timer > 0) return;

        if (!InteractUtils.INSTANCE.isInRange(player.getEntityPos(), t.getBoundingBox(), 4.5)) {
            retries++;
            if (retry.get() > 0 && retries > retry.get()) {
                restore(true);
                target = null;
                state = State.Idle;
                return;
            }
            state = State.Approach;
            return;
        }

        timer = currentDelay();
        retries++;

        for (int i = 0; i < attackTimes.get(); i++) {
            InteractUtils.INSTANCE.attack(t, rotation.get(), swing.get());
        }

        state = State.Launch;
    }

    private void handleLaunch() {
        ClientPlayerEntity player = mc.player;
        if (player == null) {
            state = State.Cooldown;
            return;
        }

        if (maceSwapped) {
            SwapUtils.INSTANCE.swapBack();
            maceSwapped = false;
        }

        if (useWindCharge.get()) {
            FindItemResult wind = findBoost(Items.WIND_CHARGE);
            if (wind.found()) {
                launchWith(wind.slot());
                state = State.Cooldown;
                return;
            }
        }

        if (useFirework.get()) {
            if (autoElytra.get() && !equipArmor(Items.ELYTRA)) {
                fail("Could not equip the elytra.");
                return;
            }

            if (!player.isGliding()) {
                if (player.isOnGround()) player.jump();
                else player.startGliding();
                return;
            }

            FindItemResult firework = findBoost(fireworkItem.get());
            if (firework.found()) {
                launchWith(firework.slot());
                state = State.Cooldown;
                return;
            }
        }

        Vec3d playerPos = player.getEntityPos();
        TpUtils.INSTANCE.tp(playerPos, new Vec3d(playerPos.x, playerPos.y + climbStep.get(), playerPos.z), false);
        state = State.Cooldown;
    }

    private void handleCooldown() {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        if (timer > 0) return;

        if (retry.get() > 0 && retries > retry.get()) {
            restore(true);
            target = null;
            state = State.Idle;
            return;
        }

        if (player.isGliding()) {
            state = State.GlideToAbove;
            return;
        }

        if (!hasItem(Items.ELYTRA)) {
            fail("No elytra found.");
            return;
        }

        if (autoElytra.get() && !equipArmor(Items.ELYTRA)) {
            fail("Could not equip the elytra.");
            return;
        }

        if (!player.isGliding()) {
            if (player.isOnGround()) player.jump();
            else player.startGliding();
            return;
        }

        state = State.GlideToAbove;
    }

    private void restore(boolean armor) {
        if (maceSwapped) {
            SwapUtils.INSTANCE.swapBack();
            maceSwapped = false;
        }

        if (boostSwapped) {
            SwapUtils.INSTANCE.swapBack();
            boostSwapped = false;
        }

        if (armor && armorSwapped) {
            armorSwapped = false;
            if (autoElytra.get()) equipArmor(Items.ELYTRA);
        }
    }

    private void fail(String message) {
        error(message);
        restore(true);
        toggle();
    }

    private int currentDelay() {
        int delay = attackDelay.get();
        if (tpsSync.get()) delay += (int) Math.max(0.0, 20.0 - TickRate.INSTANCE.getTickRate());
        return delay;
    }

    private Vec3d offsetPos(Vec3d playerPos, Vec3d targetPos) {
        double dx = playerPos.x - targetPos.x;
        double dz = playerPos.z - targetPos.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        double distance = offset.get();

        if (length <= 0.001) return new Vec3d(targetPos.x + distance, playerPos.y, targetPos.z);

        return new Vec3d(targetPos.x + dx / length * distance, playerPos.y, targetPos.z + dz / length * distance);
    }

    private void lookAt(double x, double y, double z) {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        double dx = x - player.getX();
        double dy = y - player.getEyeY();
        double dz = z - player.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, horizontal));

        player.setYaw(yaw);
        player.setPitch(pitch);
        player.setHeadYaw(yaw);
        player.setBodyYaw(yaw);
    }

    private void boost() {
        if (useWindCharge.get()) {
            FindItemResult wind = findBoost(Items.WIND_CHARGE);
            if (wind.found()) {
                launchWith(wind.slot());
                return;
            }
        }

        if (!useFirework.get()) return;

        ClientPlayerEntity player = mc.player;
        if (player == null || !player.isGliding()) return;

        FindItemResult firework = findBoost(fireworkItem.get());
        if (!firework.found()) return;

        launchWith(firework.slot());
    }

    private FindItemResult findBoost(Item item) {
        FindItemResult result = InvUtils.findInHotbar(item);
        if (result.found()) return result;
        if (!autoReplenish.get()) return result;

        replenish(item);
        return InvUtils.findInHotbar(item);
    }

    private void launchWith(int slot) {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        if (boostSwapped) {
            SwapUtils.INSTANCE.swapBack();
            boostSwapped = false;
        }

        SwapUtils.INSTANCE.swap(slot, true);
        boostSwapped = true;
        mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
        player.swingHand(Hand.MAIN_HAND);
        SwapUtils.INSTANCE.swapBack();
        boostSwapped = false;

        boostTimer = boostInterval.get();
    }

    private boolean hasBoost() {
        if (useWindCharge.get() && hasItem(Items.WIND_CHARGE)) return true;
        return useFirework.get() && hasItem(fireworkItem.get());
    }

    private boolean equipArmor(Item item) {
        ClientPlayerEntity player = mc.player;
        if (player == null) return false;
        if (player.getEquippedStack(EquipmentSlot.CHEST).getItem() == item) return true;
        if (!autoReplenish.get()) return false;

        FindItemResult result = InvUtils.find(item);
        if (!result.found()) return false;

        if (result.isArmor() || result.isHotbar()) {
            InvUtils.move().fromId(result.slot()).toArmor(2);
            return true;
        }

        int empty = emptyHotbarSlot();
        if (empty < 0) return false;

        InvUtils.move().fromId(result.slot()).toHotbar(empty);
        InvUtils.move().fromId(empty).toArmor(2);
        return true;
    }

    private boolean replenish(Item item) {
        ClientPlayerEntity player = mc.player;
        if (player == null) return false;
        if (InvUtils.findInHotbar(item).found()) return true;

        int empty = emptyHotbarSlot();
        if (empty < 0) return false;

        FindItemResult result = InvUtils.find(item);
        if (!result.found()) return false;
        if (result.isArmor()) return false;

        InvUtils.move().fromId(result.slot()).toHotbar(empty);
        return true;
    }

    private int emptyHotbarSlot() {
        ClientPlayerEntity player = mc.player;
        if (player == null) return -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) return i;
        }
        return -1;
    }

    private boolean hasItem(Item item) {
        return InvUtils.find(item).found();
    }

    private Entity findTarget() {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null) return null;

        Vec3d playerPos = player.getEntityPos();
        List<String> names = targetNames.get();

        if (!names.isEmpty()) {
            Entity nearest = null;
            double nearestDistance = Double.MAX_VALUE;

            for (Entity entity : mc.world.getEntities()) {
                if (!isValidTarget(entity)) continue;
                if (!names.contains(entity.getName().getString())) continue;

                double distance = playerPos.distanceTo(entity.getEntityPos());
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = entity;
                }
            }

            if (nearest != null) return nearest;
        }

        return FindUtils.INSTANCE.findTarget(entities.get(), ignoreFriends.get(), true, this::isValidTarget);
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == null || mc.player == null) return false;
        if (entity == mc.player) return false;
        if (!entity.isAlive()) return false;
        if (!entities.get().contains(entity.getType())) return false;
        if (entity instanceof LivingEntity living && living.getHealth() <= 0.0F) return false;
        if (range.get() > 0.0
            && !InteractUtils.INSTANCE.isInRange(mc.player.getEntityPos(), entity.getBoundingBox(), range.get())) return false;
        if (ignoreFriends.get() && entity instanceof PlayerEntity other && !Friends.get().shouldAttack(other)) return false;

        return true;
    }

    private static double sq(double value) {
        return value * value;
    }

    @Override
    public String getInfoString() {
        if (target == null) return state.name();
        return EntityUtils.getName(target) + " " + state.name();
    }
}