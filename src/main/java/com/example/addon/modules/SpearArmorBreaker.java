package com.example.addon.modules;

import com.example.addon.AddonTemplate;
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
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SpearArmorBreaker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTarget = settings.createGroup("Target");
    private final SettingGroup sgCharge = settings.createGroup("Charge");

    private final Setting<Set<EntityType<?>>> targetTypes = sgTarget.add(new EntityTypeListSetting.Builder()
        .name("target-type").description("Entities to attack.")
        .onlyAttackable().defaultValue(EntityType.PLAYER).build());
    private final Setting<SortPriority> priority = sgTarget.add(new EnumSetting.Builder<SortPriority>()
        .name("priority").description("Target sorting priority.").defaultValue(SortPriority.LowestDistance).build());
    private final Setting<Double> range = sgTarget.add(new DoubleSetting.Builder()
        .name("range").description("Max target distance.").defaultValue(6).min(1).sliderMax(20).build());
    private final Setting<Boolean> targetPlayers = sgTarget.add(new BoolSetting.Builder()
        .name("target-players").description("Target players.").defaultValue(true).build());
    private final Setting<Boolean> targetMobs = sgTarget.add(new BoolSetting.Builder()
        .name("target-mobs").description("Target mobs.").defaultValue(false).build());
    private final Setting<Boolean> ignoreFriends = sgTarget.add(new BoolSetting.Builder()
        .name("ignore-friends").description("Ignore friends.").defaultValue(true).build());

    private final Setting<Double> speed = sgCharge.add(new DoubleSetting.Builder()
        .name("speed").description("Charge speed multiplier.").defaultValue(1.0).min(0.1).sliderMax(3).build());
    private final Setting<Double> passDistance = sgCharge.add(new DoubleSetting.Builder()
        .name("pass-distance").description("Distance to target before considering pass-through.").defaultValue(1.0).min(0.5).sliderMax(3).build());
    private final Setting<Double> turnDistance = sgCharge.add(new DoubleSetting.Builder()
        .name("turn-distance").description("Distance away before turning back to target.").defaultValue(20.0).min(5).sliderMax(40).build());
    private final Setting<Double> armorThreshold = sgCharge.add(new DoubleSetting.Builder()
        .name("armor-threshold").description("Min armor durability before disabling.").defaultValue(20).min(0).sliderMax(100).build());
    private final Setting<SwitchMode> switchMode = sgGeneral.add(new EnumSetting.Builder<SwitchMode>()
        .name("switch-mode").description("Weapon switch method.").defaultValue(SwitchMode.Silent).build());

    private Entity target;
    private int spearSlot = -1;
    private boolean weaponSwitched = false;
    private boolean movingForward = true;
    private double lastDirX, lastDirZ;
    private boolean charging = false;

    public SpearArmorBreaker() {
        super(AddonTemplate.MAIN, "spear breaker", "Spear pop pop pop!");
    }

    @Override
    public void onActivate() {
        target = null;
        movingForward = true;
        weaponSwitched = false;
        charging = false;
    }

    @Override
    public void onDeactivate() {
        if (charging) {
            mc.options.useKey.setPressed(false);
        }
        if (weaponSwitched && switchMode.get() != SwitchMode.None) {
            if (switchMode.get() == SwitchMode.Silent) InvUtils.swapBack();
            else if (switchMode.get() == SwitchMode.Normal) setSelectedSlot(spearSlot);
        }
        target = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        updateTarget();
        if (target == null) {
            if (charging) mc.options.useKey.setPressed(false);
            charging = false;
            movingForward = true;
            return;
        }

        if (target instanceof PlayerEntity player && getArmorDurability(player) <= armorThreshold.get()) {
            target = null;
            if (charging) mc.options.useKey.setPressed(false);
            charging = false;
            movingForward = true;
            return;
        }

        if (!weaponSwitched) {
            spearSlot = findSpearSlot();
            if (spearSlot == -1) return;
            if (switchMode.get() == SwitchMode.Silent) InvUtils.swap(spearSlot, false);
            else if (switchMode.get() == SwitchMode.Normal) setSelectedSlot(spearSlot);
            weaponSwitched = true;
        }

        if (!mc.options.useKey.isPressed()) {
            mc.options.useKey.setPressed(true);
            charging = true;
        }
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Vec3d targetPos = new Vec3d(target.getX(), target.getY(), target.getZ());
        double dist = horizontalDistance(playerPos, targetPos);
        double dirX = targetPos.x - playerPos.x;
        double dirZ = targetPos.z - playerPos.z;
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len > 0.001) {
            dirX /= len;
            dirZ /= len;
        } else {
            dirX = 0;
            dirZ = 0;
        }

        if (movingForward) {
            if (dist <= passDistance.get()) {
                movingForward = false;
                // Store direction for continuing through (same as current approach dir)
                lastDirX = dirX;
                lastDirZ = dirZ;
            }
        } else {
            if (dist >= turnDistance.get()) {
                movingForward = true;
            }
        }
        double moveX, moveZ;
        if (movingForward) {
            moveX = dirX;
            moveZ = dirZ;
        } else {
            moveX = lastDirX;
            moveZ = lastDirZ;
        }
        mc.player.setPosition(mc.player.getX(), target.getY() + 0.5, mc.player.getZ());
        mc.player.fallDistance = 0;

        mc.player.setVelocity(moveX * speed.get(), 0, moveZ * speed.get());
    }

    private int findSpearSlot() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem().toString().toLowerCase().contains("spear")) {
                return i;
            }
        }
        return -1;
    }

    private void setSelectedSlot(int slot) {
        try {
            Field field = mc.player.getInventory().getClass().getDeclaredField("selectedSlot");
            field.setAccessible(true);
            field.setInt(mc.player.getInventory(), slot);
        } catch (Exception ignored) {}
    }

    private void updateTarget() {
        List<Entity> candidates = new ArrayList<>();
        TargetUtils.getList(candidates, this::targetCheck, priority.get(), 1);
        target = candidates.isEmpty() ? null : candidates.get(0);
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

    private int getArmorDurability(PlayerEntity player) {
        int total = 0;
        for (int slot : new int[]{39, 38, 37, 36}) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!stack.isEmpty()) total += stack.getMaxDamage() - stack.getDamage();
        }
        return total;
    }

    private double horizontalDistance(Vec3d a, Vec3d b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void aimAtTarget(Vec3d targetPos) {
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Vec3d diff = targetPos.subtract(playerPos);
        float yaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
        float pitch = 0.0f;

        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, mc.player.isOnGround(), false));
    }

    public enum SwitchMode { Normal, None, Silent }
}
