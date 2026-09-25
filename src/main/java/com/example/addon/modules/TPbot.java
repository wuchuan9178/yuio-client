package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TPbot extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgMovement = settings.createGroup("Movement");

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities").description("Entities to target.").onlyAttackable().defaultValue(EntityType.PLAYER).build());
    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("priority").description("How to select the target.").defaultValue(SortPriority.ClosestAngle).build());
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range").description("Maximum targeting range.").defaultValue(300).min(1).sliderMax(500).build());
    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay").description("Ticks between movement updates.").defaultValue(2).min(1).sliderMax(20).build());
    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends").description("Do not target friends.").defaultValue(true).build());
    private final Setting<Boolean> ignoreWalls = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-walls").description("Move through walls.").defaultValue(true).build());

    private final Setting<Double> vclipDistance = sgMovement.add(new DoubleSetting.Builder()
        .name("vclip-distance").description("Distance of each movement step.").defaultValue(10).min(1).sliderMax(50).build());
    private final Setting<Double> teleportStopDistance = sgMovement.add(new DoubleSetting.Builder()
        .name("teleport-stop-distance").description("Stop when closer than this distance.").defaultValue(2.5).min(1).sliderMax(10).build());

    private Entity target;
    private int timer;
    private Vec3d lastSentPos;

    public TPbot() {
        super(AddonTemplate.MAIN, "TP-bot-V2", "auto tracking");
    }

    @Override
    public void onDeactivate() {
        target = null;
        lastSentPos = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        updateTarget();
        if (target == null) return;

        if (timer > 0) {
            timer--;
            return;
        }

        timer = delay.get();
        teleportMove();
    }

    private void updateTarget() {
        List<Entity> list = new ArrayList<>();
        TargetUtils.getList(list, this::isValidEntity, priority.get(), 1);
        target = list.isEmpty() ? null : list.get(0);
    }

    private boolean isValidEntity(Entity entity) {
        if (!entities.get().contains(entity.getType())) return false;
        if (entity == mc.player || !entity.isAlive()) return false;
        if (entity instanceof PlayerEntity p && ignoreFriends.get() && Friends.get().isFriend(p)) return false;
        if (!ignoreWalls.get() && !PlayerUtils.canSeeEntity(entity)) return false;
        return entity.distanceTo(mc.player) <= range.get();
    }

    private void teleportMove() {
        Vec3d p = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Vec3d t = new Vec3d(target.getX(), target.getY(), target.getZ());
        double dx = t.x - p.x, dz = t.z - p.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        if (distXZ > teleportStopDistance.get()) {
            double step = Math.min(vclipDistance.get(), distXZ);
            teleport(new Vec3d(p.x + dx / distXZ * step, p.y, p.z + dz / distXZ * step));
        } else if (Math.abs(t.y - p.y) > 0.5) {
            double dy = Math.signum(t.y - p.y) * Math.min(vclipDistance.get(), Math.abs(t.y - p.y));
            teleport(new Vec3d(p.x, p.y + dy, p.z));
        }
    }

    private void teleport(Vec3d pos) {
        while (collides(pos) && pos.y < mc.player.getY() + 10) pos = pos.add(0, 1, 0);
        mc.player.setPosition(pos);
        sendPositionPacket(pos);
    }

    private boolean collides(Vec3d pos) {
        BlockPos bp = BlockPos.ofFloored(pos);
        return !mc.world.getBlockState(bp).isAir() || !mc.world.getBlockState(bp.up()).isAir();
    }

    private void sendPositionPacket(Vec3d pos) {
        if (mc.player.networkHandler == null) return;
        if (lastSentPos != null && lastSentPos.squaredDistanceTo(pos) < 0.0001) return;
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            pos.x, pos.y, pos.z, false, false));
        lastSentPos = pos;
    }

    @Override
    public String getInfoString() {
        return target != null ? EntityUtils.getName(target) : null;
    }
}
