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
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class LacrymiraAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Set<EntityType<?>>> targetTypes = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("target-type").description("Entities to attack.").onlyAttackable().defaultValue(EntityType.PLAYER).build());
    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("target-priority").description("Entity sorting priority.").defaultValue(SortPriority.LowestDistance).build());
    private final Setting<Integer> maxTarget = sgGeneral.add(new IntSetting.Builder()
        .name("max-target").description("Maximum number of targets.").defaultValue(10).min(1).sliderMax(25).build());
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range").description("Max distance of a target.").defaultValue(50).min(1).sliderMax(200).build());
    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay").description("Ticks between attacks.").defaultValue(1).min(1).sliderMax(10).build());
    private final Setting<SwitchMode> switchMode = sgGeneral.add(new EnumSetting.Builder<SwitchMode>()
        .name("switch-mode").description("Weapon switching method.").defaultValue(SwitchMode.Silent).build());
    private final Setting<Boolean> swingHand = sgGeneral.add(new BoolSetting.Builder()
        .name("swing-hand").description("Whether to swing hand when attacking.").defaultValue(false).build());
    private final Setting<Boolean> breach = sgGeneral.add(new BoolSetting.Builder()
        .name("breach").description("Enable breach armor breaking.").defaultValue(true).build());
    private final Setting<Integer> ignoreCount = sgGeneral.add(new IntSetting.Builder()
        .name("ignore-count").description("Ignore targets with armor durability above this threshold.").defaultValue(0).min(0).max(4).visible(breach::get).build());

    // 动态 Breach VClip 列表
    private final Setting<List<String>> breachVclip = sgGeneral.add(new StringListSetting.Builder()
        .name("breach-vclip")
        .description("List of breach VClip heights.")
        .defaultValue(List.of("20", "60", "120"))
        .visible(breach::get)
        .build());

    private final Setting<Double> breachMoveDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("breach-move-distance").description("Max distance per packet in breach mode.").defaultValue(128).min(1).sliderMax(128).visible(breach::get).build());

    // 动态 Miss VClip 列表
    private final Setting<List<String>> missVclip = sgGeneral.add(new StringListSetting.Builder()
        .name("miss-vclip")
        .description("List of miss VClip heights.")
        .defaultValue(List.of("10", "15", "20", "25", "30"))
        .build());

    private final Setting<Double> missMoveDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("miss-move-distance").description("Max distance per packet in miss mode.").defaultValue(10).min(1).sliderMax(128).build());

    private List<Entity> targets = new ArrayList<>();
    private int timer;

    public LacrymiraAura() {
        super(AddonTemplate.MAIN, "lacrymira-aura", "plagiarism Acid....");
    }

    @Override
    public void onDeactivate() {
        targets.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        if (timer > 0) { timer--; return; }

        updateTargets();
        if (targets.isEmpty()) return;

        FindItemResult mace = InvUtils.find(Items.MACE);
        if (!mace.found()) return;

        Entity target = targets.get(0);
        if (mc.player.distanceTo(target) > range.get()) return;

        if (isPlayerWithArmor(target) && breach.get()) performBreach(target, mace);
        else performMiss(target, mace);

        timer = delay.get();
    }

    private void updateTargets() {
        List<Entity> list = new ArrayList<>();
        TargetUtils.getList(list, this::targetCheck, priority.get(), maxTarget.get());
        targets = list;
    }

    private boolean targetCheck(Entity entity) {
        if (!targetTypes.get().contains(entity.getType())) return false;
        if (entity == mc.player) return false;
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) return false;
        if (entity instanceof PlayerEntity player) {
            if (player.isCreative() || player.isSpectator()) return false;
            if (!Friends.get().shouldAttack(player)) return false;
        }
        return entity.distanceTo(mc.player) <= range.get();
    }

    private boolean isPlayerWithArmor(Entity entity) {
        if (!(entity instanceof PlayerEntity player)) return false;
        int total = 0;
        for (int slot : new int[]{39, 38, 37, 36}) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!stack.isEmpty()) total += stack.getMaxDamage() - stack.getDamage();
        }
        return total > ignoreCount.get();
    }

    private void performBreach(Entity target, FindItemResult mace) {
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        int prevSlot = mc.player.getInventory().getSelectedSlot();
        if (switchMode.get() == SwitchMode.Silent) InvUtils.swap(mace.slot(), false);
        else if (switchMode.get() == SwitchMode.Normal) mc.player.getInventory().setSelectedSlot(mace.slot());

        for (double h : parseHeights(breachVclip.get())) {
            Vec3d above = new Vec3d(target.getX(), target.getY() + h, target.getZ());
            doTp(playerPos, above, breachMoveDistance.get());
            mc.interactionManager.attackEntity(mc.player, target);
            if (swingHand.get()) mc.player.swingHand(Hand.MAIN_HAND);
            mc.interactionManager.attackEntity(mc.player, target);
            doTp(above, playerPos, breachMoveDistance.get());
        }

        if (switchMode.get() == SwitchMode.Silent) InvUtils.swapBack();
        else if (switchMode.get() == SwitchMode.Normal) mc.player.getInventory().setSelectedSlot(prevSlot);
    }

    private void performMiss(Entity target, FindItemResult mace) {
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        int prevSlot = mc.player.getInventory().getSelectedSlot();
        if (switchMode.get() == SwitchMode.Silent) InvUtils.swap(mace.slot(), false);
        else if (switchMode.get() == SwitchMode.Normal) mc.player.getInventory().setSelectedSlot(mace.slot());

        for (double h : parseHeights(missVclip.get())) {
            Vec3d missPos = new Vec3d(target.getX(), target.getY() + h, target.getZ());
            doTp(playerPos, missPos, missMoveDistance.get());
            mc.interactionManager.attackEntity(mc.player, target);
            if (swingHand.get()) mc.player.swingHand(Hand.MAIN_HAND);
            mc.interactionManager.attackEntity(mc.player, target);
            doTp(missPos, playerPos, missMoveDistance.get());
        }

        if (switchMode.get() == SwitchMode.Silent) InvUtils.swapBack();
        else if (switchMode.get() == SwitchMode.Normal) mc.player.getInventory().setSelectedSlot(prevSlot);
    }

    private List<Double> parseHeights(List<String> input) {
        List<Double> heights = new ArrayList<>();
        for (String s : input) {
            try { heights.add(Double.parseDouble(s.trim())); } catch (NumberFormatException ignored) {}
        }
        return heights;
    }

    private void doTp(Vec3d from, Vec3d to, double moveDistance) {
        double dist = from.distanceTo(to);
        if (dist <= moveDistance) {
            mc.player.setPosition(to.x, to.y, to.z);
            sendMovePacket(to);
            return;
        }
        Vec3d dir = to.subtract(from).normalize();
        int segments = (int) Math.ceil(dist / moveDistance);
        Vec3d current = from;
        for (int i = 0; i < segments; i++) {
            current = current.add(dir.multiply(moveDistance));
            if (i == segments - 1) current = to;
            mc.player.setPosition(current.x, current.y, current.z);
            sendMovePacket(current);
        }
    }

    private void sendMovePacket(Vec3d pos) {
        if (mc.player.networkHandler != null) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                pos.x, pos.y, pos.z, mc.player.isOnGround(), true));
        }
    }

    @Override
    public String getInfoString() {
        return targets.isEmpty() ? "None" : targets.get(0).getName().getString();
    }

    public enum SwitchMode { Normal, None, Silent, Inventory }
}
