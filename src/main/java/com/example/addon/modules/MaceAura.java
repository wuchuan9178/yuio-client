package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MaceAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Set<EntityType<?>>> targetTypes = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("target-type").description("Entities to attack.").onlyAttackable().defaultValue(EntityType.PLAYER).build());

    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("target-priority").description("Entity sorting priority.").defaultValue(SortPriority.LowestDistance).build());

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range").description("Max target distance.").defaultValue(10).min(1).sliderMax(20).build());

    // 动态 VClip 列表：可自由添加/删除
    private final Setting<List<String>> vClip = sgGeneral.add(new StringListSetting.Builder()
        .name("v-clip")
        .description("List of VClip heights.")
        .defaultValue(List.of("10", "15", "20"))
        .build());

    private final Setting<Boolean> swingHand = sgGeneral.add(new BoolSetting.Builder()
        .name("swing-hand").description("Whether to swing hand when attacking.").defaultValue(false).build());

    private final Setting<Boolean> useMace = sgGeneral.add(new BoolSetting.Builder()
        .name("use-mace").description("Use mace for attack.").defaultValue(true).build());

    private final Setting<Boolean> ignoreFriend = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friend").description("Ignore friends.").defaultValue(false).build());

    private final Setting<Boolean> useCooldown = sgGeneral.add(new BoolSetting.Builder()
        .name("use-cooldown").description("Use item cooldown to determine attack timing.").defaultValue(true).build());

    private final Setting<Double> cooldownBaseTime = sgGeneral.add(new DoubleSetting.Builder()
        .name("cooldown-base-time").description("Minimum attack cooldown progress.").defaultValue(0.75).min(0.1).max(1.0).sliderMin(0.1).sliderMax(1.0).visible(useCooldown::get).build());

    private final Setting<Integer> attackDelay = sgGeneral.add(new IntSetting.Builder()
        .name("attack-delay").description("Attack delay in ms when not using cooldown.").defaultValue(50).min(1).sliderMax(2000).visible(() -> !useCooldown.get()).build());

    private Entity target;
    private long lastAttackTime;

    public MaceAura() {
        super(AddonTemplate.ATTACK, "mace-aura", "Auto mace crit aura");
    }

    @Override
    public void onDeactivate() {
        target = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.isSpectator() || mc.player.isSleeping() || mc.player.isDead()) return;

        updateTarget();
        if (target == null) return;
        if (!isReadyToAttack()) return;

        if (useMace.get()) performMaceAttack(target);
        else performNormalAttack(target);
        lastAttackTime = System.currentTimeMillis();
    }

    private void updateTarget() {
        List<Entity> list = new ArrayList<>();
        TargetUtils.getList(list, this::isValidTarget, priority.get(), 1);
        target = list.isEmpty() ? null : list.get(0);
    }

    private boolean isValidTarget(Entity entity) {
        if (!targetTypes.get().contains(entity.getType())) return false;
        if (entity == mc.player) return false;
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) return false;
        if (entity instanceof PlayerEntity player) {
            if (player.isCreative() || player.isSpectator()) return false;
            if (ignoreFriend.get() && !Friends.get().shouldAttack(player)) return false;
        }
        return entity.distanceTo(mc.player) <= range.get();
    }

    private boolean isReadyToAttack() {
        if (useCooldown.get()) return mc.player.getAttackCooldownProgress(0.5f) >= cooldownBaseTime.get();
        return System.currentTimeMillis() - lastAttackTime >= attackDelay.get();
    }

    private void performMaceAttack(Entity target) {
        FindItemResult mace = InvUtils.findInHotbar(Items.MACE);
        if (!mace.found()) return;

        int prevSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(mace.slot());
        try {
            Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            for (double h : parseHeights()) {
                if (!target.isAlive()) break;
                Vec3d above = new Vec3d(target.getX(), target.getY() + h, target.getZ());
                smoothTeleport(playerPos, above, 1.0);
                Vec3d attackPos = new Vec3d(target.getX(), target.getY() + 2.5, target.getZ());
                smoothTeleport(above, attackPos, 1.0);
                mc.interactionManager.attackEntity(mc.player, target);
                if (swingHand.get()) mc.player.swingHand(Hand.MAIN_HAND);
                smoothTeleport(attackPos, playerPos, 1.0);
            }
        } finally {
            mc.player.getInventory().setSelectedSlot(prevSlot);
        }
    }

    private void performNormalAttack(Entity target) {
        mc.interactionManager.attackEntity(mc.player, target);
        if (swingHand.get()) mc.player.swingHand(Hand.MAIN_HAND);
    }

    private List<Double> parseHeights() {
        List<Double> heights = new ArrayList<>();
        for (String s : vClip.get()) {
            try { heights.add(Double.parseDouble(s.trim())); } catch (NumberFormatException ignored) {}
        }
        return heights;
    }

    private void smoothTeleport(Vec3d from, Vec3d to, double step) {
        double dist = from.distanceTo(to);
        if (dist <= step) {
            mc.player.setPosition(to.x, to.y, to.z);
            sendMovePacket(to);
            return;
        }
        Vec3d dir = to.subtract(from).normalize();
        int segments = (int) Math.ceil(dist / step);
        Vec3d current = from;
        for (int i = 0; i < segments; i++) {
            current = current.add(dir.multiply(step));
            if (i == segments - 1) current = to;
            mc.player.setPosition(current.x, current.y, current.z);
            sendMovePacket(current);
        }
    }

    private void sendMovePacket(Vec3d pos) {
        if (mc.player.networkHandler != null) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                pos.x, pos.y, pos.z, mc.player.isOnGround(), false));
        }
    }

    @Override
    public String getInfoString() {
        return target != null ? target.getName().getString() : null;
    }
}
