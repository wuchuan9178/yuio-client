package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import dev.sherii.yuio.AModule;
import dev.sherii.yuio.settings.DoubleListSetting;
import dev.sherii.yuio.utils.ChunkBuffer;
import dev.sherii.yuio.utils.FindUtils;
import dev.sherii.yuio.utils.InteractUtils;
import dev.sherii.yuio.utils.PacketUtils;
import dev.sherii.yuio.utils.SwapUtils;
import dev.sherii.yuio.utils.TpUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.PlayerMoveC2SPacketAccessor;
import meteordevelopment.meteorclient.mixininterface.IPlayerMoveC2SPacket;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class MaceMissAurav2 extends AModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgHelper = settings.createGroup("Helper");
    private final SettingGroup sgMace = settings.createGroup("Mace");
    private final SettingGroup sgSpeed = settings.createGroup("Attack Speed");

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to attack.")
        .onlyAttackable()
        .defaultValue(EntityType.PLAYER)
        .build());

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> noGround = sgGeneral.add(new BoolSetting.Builder()
        .name("no-ground")
        .description("Force every movement packet to report not on ground so the server never sees the landing and no fall damage is applied.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> rotation = sgGeneral.add(new BoolSetting.Builder()
        .name("rotation")
        .description("Attacking rotation your head to target.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Attacking swing your hand.")
        .defaultValue(true)
        .build());

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Range is 0.0, then infinity range.")
        .min(0.0)
        .sliderRange(0.0, 256.0)
        .defaultValue(200.0)
        .build());

    private final Setting<List<String>> targetNames = sgGeneral.add(new StringListSetting.Builder()
        .name("target-names")
        .description("Prioritized player names list. Attack these first, nearest among them.")
        .defaultValue(List.of())
        .build());

    private final Setting<Double> drySearchRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("dry-search-range")
        .description("Radius searched around a target that is in water. Nothing dry in range and the hit gets skipped.")
        .min(0.0)
        .sliderRange(0.0, 10.0)
        .defaultValue(6.0)
        .build());

    private final Setting<Boolean> autoSwap = sgHelper.add(new BoolSetting.Builder()
        .name("auto-swap")
        .description("Auto swap item to attack target.")
        .defaultValue(true)
        .build());

    private final Setting<Item> swapItem = sgHelper.add(new ItemSetting.Builder()
        .name("swap-item")
        .description("Auto swap it item to attack target.")
        .defaultValue(Items.NETHERITE_SWORD)
        .build());

    private final Setting<Double> safeRange = sgHelper.add(new DoubleSetting.Builder()
        .name("safe-range")
        .min(0.0)
        .sliderRange(0.0, 5.0)
        .defaultValue(3.0)
        .build());

    private final Setting<Boolean> directTp = sgHelper.add(new BoolSetting.Builder()
        .name("direct-tp")
        .description("On: one tp straight to the attack spot. Off: the old multi step path with the detours.")
        .defaultValue(false)
        .build());

    private final Setting<Double> waterSurfaceMaxY = sgHelper.add(new DoubleSetting.Builder()
        .name("water-surface-max-y")
        .description("Both in water: how far up we scan for somewhere dry to climb out on first.")
        .min(0.0)
        .sliderRange(0.0, 320.0)
        .defaultValue(320.0)
        .build());

    private final Setting<Boolean> tpsSync = sgHelper.add(new BoolSetting.Builder()
        .name("tps-sync")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> noBack = sgHelper.add(new BoolSetting.Builder()
        .name("no-back")
        .description("Do not teleport back after attacking.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> useMace = sgMace.add(new BoolSetting.Builder()
        .name("use-mace")
        .description("Use mace")
        .defaultValue(true)
        .build());

    private final Setting<Double> safeHeight = sgMace.add(new DoubleSetting.Builder()
        .name("safe-height")
        .min(0.0)
        .sliderRange(0.0, 256.0)
        .defaultValue(60.0)
        .build());

    private final Setting<List<Double>> missClip = sgMace.add(new DoubleListSetting.Builder()
        .name("miss-clip")
        .min(0.0)
        .sliderRange(0.0, 1000.0)
        .defaultValue(20.0, 40.0, 60.0, 80.0)
        .visible(useMace::get)
        .build());

    private final Setting<Integer> attackTimes = sgSpeed.add(new IntSetting.Builder()
        .name("attack-times")
        .description("Once attack target times.")
        .min(0)
        .sliderRange(1, 128)
        .defaultValue(1)
        .build());

    private final Setting<Integer> attackDelay = sgSpeed.add(new IntSetting.Builder()
        .name("attack-delay")
        .description("How wait the delay(tick) to attack")
        .min(0)
        .sliderRange(0, 128)
        .defaultValue(1)
        .build());

    private Entity target;
    private int tickTimer;
    private boolean isSending;
    private String dryCacheKey;
    private Vec3d dryCacheValue;
    private boolean dryCacheValid;

    public MaceMissAurav2() {
        super(AddonTemplate.ATTACK, "MaceMissAurav2", "skid by puerhx(iaa)");
    }

    public Set<EntityType<?>> getEntities() {
        return entities.get();
    }

    private boolean isFullyDry(Entity entity) {
        return !entity.isTouchingWater() && !entity.isSubmergedInWater();
    }

    private boolean isDryBlock(BlockPos pos) {
        for (int i = 0; i <= 2; i++) {
            BlockPos checkPos = pos.add(0, i, 0);
            BlockState state = ChunkBuffer.INSTANCE.getBlockState(checkPos);
            if (state == null) return false;
            if (!state.getFluidState().isEmpty()) return false;
            if (!TpUtils.INSTANCE.isPlayerSafe(checkPos)) return false;
        }
        return true;
    }

    private Vec3d findDrySpace(Entity target, Vec3d targetCenter, double radius) {
        int centerX = (int) Math.floor(targetCenter.x);
        int centerY = (int) Math.floor(targetCenter.y);
        int centerZ = (int) Math.floor(targetCenter.z);

        if (targetCenter.y + 64.0 <= 6.0) {
            return new Vec3d(centerX + 0.5, -64.5, centerZ + 0.5);
        }

        int targetId = target != null ? target.getId() : 0;
        String key = targetId + ":" + centerX + ":" + centerY + ":" + centerZ + ":" + radius;
        if (dryCacheValid && key.equals(dryCacheKey)) return dryCacheValue;

        int range = (int) Math.ceil(radius);
        Vec3d[] bestPos = new Vec3d[]{null};
        double[] bestDist = new double[]{radius};

        class DryCheck {
            void check(int dx, int dy, int dz) {
                BlockPos blockPos = new BlockPos(centerX + dx, centerY + dy, centerZ + dz);
                if (!isDryBlock(blockPos)) return;
                Vec3d standPos = new Vec3d(centerX + dx + 0.5, centerY + dy + 0.5, centerZ + dz + 0.5);
                double dist = standPos.distanceTo(targetCenter);
                if (dist < bestDist[0]) {
                    bestDist[0] = dist;
                    bestPos[0] = standPos;
                }
            }
        }

        DryCheck checkDry = new DryCheck();

        for (int r = 0; r <= range; r++) {
            for (int dx = -r; dx <= r; dx++) {
                int ax = Math.abs(dx);
                for (int dz = -r; dz <= r; dz++) {
                    int az = Math.abs(dz);
                    if (ax == r || az == r) {
                        for (int dy = -r; dy <= r; dy++) checkDry.check(dx, dy, dz);
                    } else {
                        checkDry.check(dx, r, dz);
                        checkDry.check(dx, -r, dz);
                    }
                }
            }
            if (bestPos[0] != null && bestDist[0] <= (double) r) break;
        }

        dryCacheKey = key;
        dryCacheValue = bestPos[0];
        dryCacheValid = true;

        return bestPos[0];
    }

    @EventHandler(priority = -200)
    private void onTickPost(TickEvent.Post event) {
        dryCacheValid = false;

        ClientPlayerEntity player = mc.player;
        if (player == null) return;
        Vec3d playerPos = player.getEntityPos();

        List<Entity> allCandidates = new ArrayList<>();
        if (mc.world != null) {
            for (Entity entity : mc.world.getEntities()) {
                if (entities.get().contains(entity.getType())
                    && entity.isAlive()
                    && entity != player
                    && (range.get() <= 0.0 || InteractUtils.INSTANCE.isInRange(playerPos, entity.getBoundingBox(), range.get()))) {
                    allCandidates.add(entity);
                }
            }
        }

        Set<String> nameSet = new HashSet<>();
        for (String name : targetNames.get()) nameSet.add(name.toLowerCase(Locale.ROOT));

        List<Entity> priorityCandidates = new ArrayList<>();
        for (Entity entity : allCandidates) {
            Text displayName = entity.getDisplayName();
            if (displayName != null && nameSet.contains(displayName.getString().toLowerCase(Locale.ROOT))) {
                priorityCandidates.add(entity);
            }
        }

        Entity t;
        if (!priorityCandidates.isEmpty()) {
            Entity nearest = null;
            double nearestDist = Double.MAX_VALUE;
            for (Entity entity : priorityCandidates) {
                double dist = playerPos.distanceTo(entity.getEntityPos());
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = entity;
                }
            }
            t = nearest;
        } else {
            t = FindUtils.INSTANCE.findTarget(entities.get(), ignoreFriends.get(), true, entity -> {
                if (entity == player) return false;
                Box entityBox = entity.getBoundingBox();
                if (range.get() <= 0.0) return true;
                return InteractUtils.INSTANCE.isInRange(playerPos, entityBox, range.get());
            });
        }

        target = t;
        if (t == null) {
            tickTimer = attackDelay.get();
            return;
        }

        double delay = tpsSync.get() ? attackDelay.get() + 20.0 - TickRate.INSTANCE.getTickRate() : attackDelay.get();
        if (tickTimer < (int) delay) {
            tickTimer++;
            return;
        }
        tickTimer = 0;

        if (!isFullyDry(t) && findDrySpace(t, t.getEntityPos(), drySearchRange.get()) == null) {
            return;
        }

        for (int i = 0; i < attackTimes.get(); i++) {
            InteractUtils.INSTANCE.attack(t, rotation.get(), swing.get());
        }
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        Packet<?> packet = event.packet;
        if (!(packet instanceof PlayerInteractEntityC2SPacket)) return;
        if (!InteractUtils.INSTANCE.isAttack((PlayerInteractEntityC2SPacket) packet)) return;
        if (isSending) return;
        isSending = true;

        Entity t = target;
        if (t == null) {
            isSending = false;
            return;
        }

        ClientPlayerEntity player = mc.player;
        if (player == null) {
            isSending = false;
            return;
        }

        Vec3d playerPos = player.getEntityPos();
        Vec3d targetPosRaw = t.getEntityPos();

        boolean playerInWater = !isFullyDry(player);
        final Vec3d finalTargetPos;
        if (!isFullyDry(t)) {
            Vec3d drySpace = findDrySpace(t, targetPosRaw, drySearchRange.get());
            if (drySpace == null) {
                event.cancel();
                isSending = false;
                return;
            }
            finalTargetPos = drySpace;
        } else {
            Vec3d safePos = targetPosRaw;
            BlockPos blockTargetPos = BlockPos.ofFloored(safePos);
            if (!isDryBlock(blockTargetPos)) {
                Vec3d found = FindUtils.INSTANCE.radiusBlockIter(blockTargetPos, safeRange.get(), (bp, bs) -> {
                    if (!isDryBlock(bp)) return null;
                    return new Vec3d(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
                });
                if (found != null) safePos = found;
            }
            finalTargetPos = safePos;
        }

        if (!directTp.get() && playerInWater && !isFullyDry(t)) {
            ClientWorld world = mc.world;
            int px = (int) Math.floor(playerPos.x);
            int pz = (int) Math.floor(playerPos.z);
            int surfaceY = (int) Math.floor(playerPos.y);
            int scanLimit = Math.min((int) waterSurfaceMaxY.get().doubleValue(), (world != null ? world.getHeight() : 320) - 1);
            if (world != null && surfaceY <= scanLimit) {
                for (int y = surfaceY; y <= scanLimit; y++) {
                    BlockPos checkPos = new BlockPos(px, y, pz);
                    BlockState state = ChunkBuffer.INSTANCE.getBlockState(checkPos);
                    if (state == null) break;
                    if (state.getFluidState().isEmpty() && TpUtils.INSTANCE.isPlayerSafe(checkPos)) {
                        surfaceY = y;
                        break;
                    }
                }
            }
            Vec3d upPos = new Vec3d(playerPos.x, (double) surfaceY, playerPos.z);
            TpUtils.INSTANCE.tp(playerPos, upPos, false);
            playerPos = upPos;
        }

        if (directTp.get()) {
            TpUtils.INSTANCE.tp(playerPos, finalTargetPos, false);
        } else {
            Vec3d intermediatePos = new Vec3d(finalTargetPos.x, playerPos.y, finalTargetPos.z);
            BlockPos intermediateBlockPos = BlockPos.ofFloored(intermediatePos);

            if (!TpUtils.INSTANCE.isPlayerSafe(intermediateBlockPos)) {
                double safeY = finalTargetPos.y;
                double maxY = 320.0;
                while (safeY < maxY) {
                    BlockPos checkBlock = new BlockPos((int) Math.floor(playerPos.x), (int) Math.floor(safeY), (int) Math.floor(playerPos.z));
                    if (TpUtils.INSTANCE.isPlayerSafe(checkBlock)) break;
                    safeY += 1.0;
                }
                Vec3d upPos = new Vec3d(playerPos.x, safeY, playerPos.z);
                TpUtils.INSTANCE.tp(playerPos, upPos, false);

                Vec3d xzTargetAtSafeY = new Vec3d(finalTargetPos.x, safeY, finalTargetPos.z);
                BlockPos xzTargetBlock = BlockPos.ofFloored(xzTargetAtSafeY);
                if (!TpUtils.INSTANCE.isPlayerSafe(xzTargetBlock)) {
                    double dx = finalTargetPos.x - playerPos.x;
                    double dz = finalTargetPos.z - playerPos.z;
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist > 0.01) {
                        double normX = dx / dist;
                        double normZ = dz / dist;
                        double beforeX = finalTargetPos.x - normX;
                        double beforeZ = finalTargetPos.z - normZ;
                        Vec3d beforeUp = new Vec3d(beforeX, safeY, beforeZ);
                        TpUtils.INSTANCE.tp(upPos, beforeUp, false);
                        Vec3d beforeDown = new Vec3d(beforeX, finalTargetPos.y, beforeZ);
                        TpUtils.INSTANCE.tp(beforeUp, beforeDown, false);
                        TpUtils.INSTANCE.tp(beforeDown, finalTargetPos, false);
                    } else {
                        TpUtils.INSTANCE.tp(upPos, finalTargetPos, false);
                    }
                } else {
                    TpUtils.INSTANCE.tp(upPos, finalTargetPos, false);
                }
            } else {
                TpUtils.INSTANCE.tp(playerPos, intermediatePos, false);
                TpUtils.INSTANCE.tp(intermediatePos, finalTargetPos, false);
            }
        }

        FindItemResult item = InvUtils.find(swapItem.get());
        FindItemResult mace = InvUtils.find(Items.MACE);
        boolean isCanSwap = item.found() && autoSwap.get();
        if (isCanSwap) SwapUtils.INSTANCE.swap(item.slot(), true);
        PacketUtils.INSTANCE.sendPacket(packet);
        if (isCanSwap) SwapUtils.INSTANCE.swapBack();

        if (mace.found() && useMace.get()) {
            SwapUtils.INSTANCE.swap(mace.slot(), true);
            for (double clip : missClip.get()) {
                Vec3d newPos = finalTargetPos.add(0.0, clip, 0.0);
                Double height = FindUtils.INSTANCE.findVClip(newPos, safeHeight.get());
                ((IVec3d) newPos).meteor$setY(height != null ? height : newPos.y);
                TpUtils.INSTANCE.tp(finalTargetPos, newPos, false);
                TpUtils.INSTANCE.tp(newPos, finalTargetPos, false);
                PacketUtils.INSTANCE.sendPacket(packet);
            }
            SwapUtils.INSTANCE.swapBack();
        }

        if (!noBack.get()) {
            TpUtils.INSTANCE.tp(finalTargetPos, playerPos, false);
        }

        event.cancel();
        isSending = false;
    }

    @EventHandler(priority = EventPriority.LOWEST - 666)
    private void onNoGroundSend(PacketEvent.Send event) {
        if (!noGround.get()) return;

        Packet<?> packet = event.packet;
        if (!(packet instanceof PlayerMoveC2SPacket)) return;

        PlayerMoveC2SPacket move = (PlayerMoveC2SPacket) packet;
        if (((IPlayerMoveC2SPacket) move).meteor$getTag() == 1337) return;

        ((PlayerMoveC2SPacketAccessor) move).meteor$setOnGround(false);
    }

    @Override
    public void onActivate() {
        target = null;
        tickTimer = attackDelay.get();
        isSending = false;
        dryCacheValid = false;
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();
        if (noGround.get() && mc.player != null) {
            TpUtils.INSTANCE.sendMovePacket(mc.player.getX(), mc.player.getY() + 1.0E-4, mc.player.getZ(), false, null);
            TpUtils.INSTANCE.sendMovePacket(mc.player.getX(), mc.player.getY(), mc.player.getZ(), true, null);
        }
    }

    @Override
    public String getInfoString() {
        Entity t = target;
        if (t == null) return null;
        String suffix = isFullyDry(t) ? "" : " (water)";
        return EntityUtils.getName(t) + suffix;
    }
}
