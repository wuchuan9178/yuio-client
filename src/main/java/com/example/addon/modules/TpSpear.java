package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import dev.sherii.yuio.predict.PredictEngine;
import dev.sherii.yuio.predict.Prediction;
import dev.sherii.yuio.utils.FindUtils;
import dev.sherii.yuio.utils.PacketUtils;
import dev.sherii.yuio.utils.TpUtils;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientTickEndC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Spear TP rush attack — 1 tick judgment window.
 *
 * 移植自 PuerHx 的 TpSpear.kt（object TpSpear : PHXModule）。行为逐条对齐：
 * 落点搜索（overshoot / render-limit / searchOrder）、分段移动包、回原位包、
 * server-tick-delay 状态机、ClientTickEnd 与 PlayerMove 的取消。
 *
 * 唯一的结构性改动：原版的 Predict 分组（约 45 个 setting + PredictSetting.PredictMode
 * 的十种模式，全部依赖 PuerHx 的 PredictUtils）改成接本项目的预判引擎
 * {@link PredictEngine}（dev.sherii.yuio.predict），只保留 5 种取点方式。
 */
public class TpSpear extends Module {
    private final SettingGroup sgSpear = settings.createGroup("Spear");
    private final SettingGroup sgPredict = settings.createGroup("Predict");

    // ---- Spear ----

    private final Setting<Double> spearDistance = sgSpear.add(new DoubleSetting.Builder()
        .name("spear-distance").description("Dash distance past target.")
        .min(1.0).sliderRange(1.0, 100.0).defaultValue(50.0).build());

    // 兼容保留：不再参与攻击逻辑。TP 落点由“目标距离 + 越过量”决定，始终贴着目标，
    // 无论该值设多少，攻击范围只由 spear-distance 决定，目标也不会被甩出渲染范围。
    private final Setting<Double> spearMaxTp = sgSpear.add(new DoubleSetting.Builder()
        .name("spear-max-tp").description("兼容保留, 已不再限制攻击/TP 距离, 可忽略。")
        .min(1.0).sliderRange(1.0, 200.0).defaultValue(100.0).build());

    private final Setting<Integer> delay = sgSpear.add(new IntSetting.Builder()
        .name("server-tick-delay").description("Server tick delay before resync.")
        .min(0).sliderMax(20).defaultValue(2).build());

    private final Setting<Double> stepSize = sgSpear.add(new DoubleSetting.Builder()
        .name("spear-step-size")
        .description("单次移动包的最大位移（格）。调大 = 每步移动更远、中间包更少，冲刺更快更接近瞬移；调小 = 分步更多、更慢。默认 10 与原版一致。注意：单包位移过大可能被部分服务器判为异常位移。")
        .min(1.0).sliderMax(200.0).defaultValue(10.0).build());

    private final Setting<Double> renderLimit = sgSpear.add(new DoubleSetting.Builder()
        .name("spear-render-limit")
        .description("TP 落点与目标的最大距离（格）。落点太远时，TP 瞬间目标会脱出渲染/模拟距离而消失（目标脱渲染）。0 = 自动使用客户端视距；调大可攻击更远目标，但可能让目标脱渲染。")
        .min(0.0).sliderMax(512.0).defaultValue(0.0).build());

    // ---- Predict（yuio 自己的预判引擎）----

    private final Setting<Boolean> predict = sgPredict.add(new BoolSetting.Builder()
        .name("predict")
        .description("启用目标预判（用 yuio 自己的预判引擎 dev.sherii.yuio.predict，替代 PuerHx 的 PredictUtils）。")
        .defaultValue(false).build());

    private final Setting<PredictMode> predictMode = sgPredict.add(new EnumSetting.Builder<PredictMode>()
        .name("mode")
        .description("取点方式：Engine = 引擎自动提前量（启动延迟 + 动作延迟 + ping + 学习偏置）；Ticks = 固定 ticks；Ping = 按延迟换算 ticks；Swept = 当前与预判位置的扫描盒中心；Multi = 多环候选取质心。")
        .defaultValue(PredictMode.Engine).visible(predict::get).build());

    private final Setting<Double> startupTicks = sgPredict.add(new DoubleSetting.Builder()
        .name("startup-ticks").description("Own pipeline latency in ticks.")
        .defaultValue(1.0).min(0.0).sliderRange(0.0, 6.0).visible(predict::get).build());

    private final Setting<Double> actionTicks = sgPredict.add(new DoubleSetting.Builder()
        .name("action-ticks").description("Ticks the action itself needs (the spear thrust).")
        .defaultValue(1.0).min(0.0).sliderRange(0.0, 6.0).visible(predict::get).build());

    private final Setting<Double> minLead = sgPredict.add(new DoubleSetting.Builder()
        .name("min-lead").description("Lower bound of the lead in ticks.")
        .defaultValue(1.0).min(0.0).sliderRange(0.0, 10.0).visible(predict::get).build());

    private final Setting<Double> maxLead = sgPredict.add(new DoubleSetting.Builder()
        .name("max-lead").description("Upper bound of the lead in ticks.")
        .defaultValue(12.0).min(1.0).sliderRange(1.0, 40.0).visible(predict::get).build());

    private final Setting<Integer> predictTicks = sgPredict.add(new IntSetting.Builder()
        .name("predict-ticks").description("Ticks ahead for the Ticks mode.")
        .defaultValue(4).min(1).max(20).sliderRange(1, 20)
        .visible(() -> predict.get() && predictMode.get() == PredictMode.Ticks).build());

    private final Setting<Integer> rings = sgPredict.add(new IntSetting.Builder()
        .name("rings").description("Number of sigma rings sampled around the prediction (Multi mode).")
        .defaultValue(1).min(0).sliderMax(3)
        .visible(() -> predict.get() && predictMode.get() == PredictMode.Multi).build());

    private final Setting<Integer> maxPoints = sgPredict.add(new IntSetting.Builder()
        .name("max-points").description("Maximum candidate stations averaged into the aim point (Multi mode).")
        .defaultValue(8).min(1).sliderMax(20)
        .visible(() -> predict.get() && predictMode.get() == PredictMode.Multi).build());

    private final Setting<Double> heightOffset = sgPredict.add(new DoubleSetting.Builder()
        .name("height-offset").description("Vertical offset from the target hitbox center.")
        .defaultValue(0.0).min(-1.0).max(1.0).sliderRange(-1.0, 1.0).visible(predict::get).build());

    private final Setting<Boolean> resetOnTeleport = sgPredict.add(new BoolSetting.Builder()
        .name("reset-on-teleport").description("Clear the motion history when the target vclips.")
        .defaultValue(true).visible(predict::get).build());

    // ---- constants (与 Kotlin 原版一致) ----

    private static final double OVERSHOOT = 5.0;
    private static final double ATTACK_RANGE = 3.0;
    private static final int MAX_STEPS = 320;
    private static final double MIN_SEGMENT = 0.5;
    private static final double SEARCH_STEP = 2.0;
    private static final double[] SEARCH_ORDER = buildSearchOrder();

    private static double[] buildSearchOrder() {
        double[] order = new double[19];
        for (int i = 1; i <= 9; i++) {
            order[i * 2 - 1] = i;
            order[i * 2] = -i;
        }
        return order;
    }

    // ---- state ----

    public int currentWaitBackTick = 0;
    private int pendingBackTick = 0;
    private MoveEntry pendingBackposEntry;
    private float playerPitch;
    private float playerYaw;
    private boolean hasPlayerRotation;
    private int spearSlot = -1;
    private int originalSlot = -1;

    private record MoveEntry(Vec3d pos, Float yaw, Float pitch, boolean onGround) {
    }

    private record Aim(float pitch, float yaw) {
    }

    public enum PredictMode {
        Engine,
        Ticks,
        Ping,
        Swept,
        Multi
    }

    public TpSpear() {
        super(AddonTemplate.ATTACK, "tp-spear", "Spear TP rush attack — 1 tick judgment window.");
    }

    // ---- lifecycle ----

    @Override
    public void onActivate() {
        currentWaitBackTick = 0;
        pendingBackTick = 0;
        pendingBackposEntry = null;
        hasPlayerRotation = false;
        spearSlot = -1;
        originalSlot = -1;
        refreshSpearSlot();
        // 原版在这里向 PredictUtils 注册运动历史请求器；本项目的 PredictEngine 自己每 tick
        // 采样所有实体（PredictEngine.onTick），不需要注册。
    }

    @Override
    public void onDeactivate() {
        currentWaitBackTick = 0;
        pendingBackTick = 0;
        pendingBackposEntry = null;
        hasPlayerRotation = false;
        mc.options.useKey.setPressed(false);
        if (originalSlot >= 0 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
            PacketUtils.INSTANCE.sendPacket(new UpdateSelectedSlotC2SPacket(originalSlot));
        }
        originalSlot = -1;
        spearSlot = -1;
    }

    // ---- events ----

    @EventHandler
    private void onTick(TickEvent.Post event) {
        PlayerEntity player = mc.player;
        if (player == null) return;

        if (!holdingSpear()) {
            refreshSpearSlot();
            return;
        }

        if (currentWaitBackTick > 0) {
            currentWaitBackTick--;
            if (currentWaitBackTick > delay.get()) currentWaitBackTick = delay.get();
            if (currentWaitBackTick > 0) {
                player.setVelocity(Vec3d.ZERO);
                player.setOnGround(false);
            }
            if (currentWaitBackTick == 1) {
                player.fallDistance = 0.0;
                if (hasPlayerRotation) {
                    TpUtils.INSTANCE.sendMovePacket(playerYaw, playerPitch, player.isOnGround(), 0);
                }
                hasPlayerRotation = false;
            }
            return;
        }

        if (pendingBackTick > 0) {
            pendingBackTick--;
            if (pendingBackTick == 0) {
                if (pendingBackposEntry != null) sendMoveEntryPacket(pendingBackposEntry);
                currentWaitBackTick = delay.get() + 1;
                pendingBackposEntry = null;
            }
            return;
        }

        spearAttack();
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof ClientTickEndC2SPacket && currentWaitBackTick > 1) {
            event.cancel();
        }
        if (event.packet instanceof PlayerMoveC2SPacket && currentWaitBackTick > 0 && pendingBackTick <= 0) {
            event.cancel();
        }
    }

    // ---- spearAttack ----

    private boolean spearAttack() {
        PlayerEntity player = mc.player;
        if (player == null) return false;

        spearSlot = findSpearInHotbar();

        if (spearDistance.get() < 0) {
            currentWaitBackTick = 0;
            return false;
        }

        Entity target = FindUtils.INSTANCE.findTarget(
            Set.of(EntityType.PLAYER),
            true,
            true,
            other -> player.getEntityPos().distanceTo(other.getEntityPos()) <= spearDistance.get()
                && isSpearable(other)
        );

        if (target == null) {
            currentWaitBackTick = 0;
            return false;
        }

        Vec3d playerPos = player.getEntityPos();
        playerPitch = player.getPitch();
        playerYaw = player.getYaw();
        hasPlayerRotation = true;

        // Full 3D direction from player eye to target eye — keeps the vertical component
        // so enemies at different heights can be attacked. 预判由 Predict 分组控制。
        Vec3d targetPos = predictTargetPos(target);
        Vec3d direction = targetPos.add(0.0, target.getEyeHeight(target.getPose()), 0.0)
            .subtract(player.getEyePos());
        double distance = direction.length();
        Vec3d directionNorm = direction.normalize();

        // 落点在玩家背后（远离目标）：TP 回原位时产生朝目标的高速位移，
        // 从而触发动能攻击判定 —— 同一平面静止目标也能打出伤害。
        Vec3d tpDirection = directionNorm.multiply(-1.0);
        Vec3d horizontalLine = getVerticalWithSameXZ(directionNorm);

        List<Vec3d> to = findValidTpPosition(playerPos, tpDirection, horizontalLine, spearDistance.get(), distance);

        if (to != null) {
            Aim aim = rotationToVec2f(directionNorm);

            for (int i = 0; i < to.size() - 1; i++) {
                sendMoveEntryPacket(new MoveEntry(to.get(i), null, null, false));
            }
            Vec3d targetTpPos = to.get(to.size() - 1);
            sendMoveEntryPacket(new MoveEntry(targetTpPos, aim.yaw(), aim.pitch(), false));

            pendingBackposEntry = new MoveEntry(playerPos, aim.yaw(), aim.pitch(), false);
            pendingBackTick = 1;

            // Restore visual rotation（原版：setPitch/setYaw 回到动作前的朝向）
            player.setPitch(playerPitch);
            player.setYaw(playerYaw);
            return true;
        }

        // 目标在 spear-distance 内但找不到合法落点：通常是目标贴墙/站在建筑里，
        // 或玩家背后 search 范围内被方块挡住。保持原版文案。
        ChatUtils.errorPrefix("TpSpear", "Too far to reach target");
        currentWaitBackTick = 0;
        return true;
    }

    private Vec3d getVerticalWithSameXZ(Vec3d direction) {
        Vec3d cross = direction.crossProduct(new Vec3d(0.0, 1.0, 0.0));
        return cross.lengthSquared() < 1.0E-6 ? new Vec3d(1.0, 0.0, 0.0) : cross.normalize();
    }

    private Aim rotationToVec2f(Vec3d direction) {
        double h = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        float pitch = (float) Math.toDegrees(Math.atan2(-direction.y, h));
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        return new Aim(pitch, yaw);
    }

    private List<Vec3d> findValidTpPosition(Vec3d currentPlayerPos, Vec3d tpDirection, Vec3d expandDirection,
                                            double distance, double minDistance) {
        // 渲染距离兜底：落点与目标的距离 = search + minDistance，超过渲染/模拟距离时目标会脱渲染。
        // 因此把 search 限制在 renderLimit - minDistance 内；spear-render-limit = 0 时自动取视距。
        // 目标过远（minDistance >= renderLimit / 2）时：显式指定 renderLimit 则返回 null（报 Too far），
        // 自动模式则回退到原逻辑，不破坏“打远目标”的能力。
        boolean explicitRenderLimit = renderLimit.get() > 0.0;
        double limit = explicitRenderLimit ? renderLimit.get() : mc.options.getClampedViewDistance() * 16.0;
        double search = (explicitRenderLimit || limit - minDistance > minDistance)
            ? Math.min(distance, Math.min(minDistance + OVERSHOOT, limit - minDistance))
            : Math.min(distance, minDistance + OVERSHOOT);

        while (search > minDistance) {
            for (double i : SEARCH_ORDER) {
                Vec3d searchTpPos = currentPlayerPos
                    .add(tpDirection.multiply(search))
                    .add(expandDirection.multiply(i));

                // 只用搜索半径 distance 兜底，挡掉横向偏移过大的候选。
                if (searchTpPos.squaredDistanceTo(currentPlayerPos) > distance * distance) break;

                if (!TpUtils.INSTANCE.isPlayerSafe(BlockPos.ofFloored(searchTpPos))) continue;

                List<Vec3d> sequence = isValidTpLocation(currentPlayerPos, searchTpPos);
                if (sequence != null) return sequence;
            }
            search -= SEARCH_STEP;
        }
        return null;
    }

    private List<Vec3d> isValidTpLocation(Vec3d playerLocation, Vec3d tpLocation) {
        Vec3d currentSimulateMovement = playerLocation.subtract(tpLocation);

        if (!TpUtils.INSTANCE.isPathMiss(tpLocation, tpLocation.add(currentSimulateMovement))) return null;

        List<Vec3d> listTo = generateTpSequence(playerLocation, tpLocation);
        return listTo.isEmpty() ? null : listTo;
    }

    private List<Vec3d> generateTpSequence(Vec3d src, Vec3d dst) {
        double totalDist = src.distanceTo(dst);
        if (totalDist < MIN_SEGMENT) return List.of(dst);

        // 单步位移由 spear-step-size 决定：调大 → 中间包更少 → 冲刺更快更接近瞬移
        int stepCount = Math.min(MAX_STEPS, Math.max(1, (int) Math.ceil(totalDist / stepSize.get())));
        List<Vec3d> result = new ArrayList<>(stepCount);
        for (int i = 1; i <= stepCount; i++) {
            double t = (double) i / stepCount;
            result.add(new Vec3d(
                src.x + (dst.x - src.x) * t,
                src.y + (dst.y - src.y) * t,
                src.z + (dst.z - src.z) * t
            ));
        }
        return result;
    }

    // ---- packet ----

    private void sendMoveEntryPacket(MoveEntry entry) {
        if (mc.player == null) return;

        PlayerMoveC2SPacket packet;
        if (entry.yaw() != null && entry.pitch() != null) {
            packet = new PlayerMoveC2SPacket.Full(
                entry.pos().x, entry.pos().y, entry.pos().z,
                entry.yaw(), entry.pitch(), entry.onGround(), false);
        } else {
            packet = new PlayerMoveC2SPacket.PositionAndOnGround(
                entry.pos().x, entry.pos().y, entry.pos().z, entry.onGround(), false);
        }
        PacketUtils.INSTANCE.sendPacket(packet);
    }

    // ---- spear / trident ----

    private boolean isSpearable(Entity entity) {
        PlayerEntity player = mc.player;
        if (player == null) return false;

        if (player.getMainHandStack().getItem() != Items.TRIDENT) {
            refreshSpearSlot();
        }

        // 与原版一致：太近（近战攻击范围内）不扎。
        if (player.getEyePos().squaredDistanceTo(entity.getEyePos()) <= ATTACK_RANGE * ATTACK_RANGE) return false;

        if (mc.world == null) return false;
        HitResult hit = mc.world.raycast(new RaycastContext(
            player.getEyePos(), entity.getEyePos(),
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            player
        ));

        return hit == null || hit.getType() == HitResult.Type.MISS;
    }

    private boolean isSpearStack(ItemStack stack) {
        if (stack.isEmpty()) return false;

        String name = stack.getName().getString();
        String lower = name.toLowerCase();
        if (lower.contains("spear") || lower.contains("trident") || name.contains("矛")) return true;
        return stack.getItem() == Items.TRIDENT;
    }

    private boolean holdingSpear() {
        PlayerEntity player = mc.player;
        return player != null && isSpearStack(player.getMainHandStack());
    }

    private int findSpearInHotbar() {
        PlayerEntity player = mc.player;
        if (player == null) return -1;
        for (int i = 0; i <= 8; i++) {
            if (isSpearStack(player.getInventory().getStack(i))) return i;
        }
        return -1;
    }

    private boolean refreshSpearSlot() {
        PlayerEntity player = mc.player;
        if (player == null) return false;

        spearSlot = findSpearInHotbar();
        if (spearSlot < 0) return false;

        if (originalSlot < 0) originalSlot = player.getInventory().getSelectedSlot();
        if (originalSlot != spearSlot) {
            player.getInventory().setSelectedSlot(spearSlot);
            PacketUtils.INSTANCE.sendPacket(new UpdateSelectedSlotC2SPacket(spearSlot));
        }
        mc.options.useKey.setPressed(true);
        return true;
    }

    // ---- predict (yuio 引擎) ----

    /** 按 Predict 开关 / 模式计算目标预判后的位置（返回的是目标实体原点，eye height 在外面加）。 */
    private Vec3d predictTargetPos(Entity entity) {
        if (!predict.get()) return entity.getEntityPos();

        double lead = switch (predictMode.get()) {
            case Ticks -> Math.max(0.0, predictTicks.get());
            case Ping -> pingTicks();
            default -> leadTicks(entity);
        };

        Prediction prediction = PredictEngine.INSTANCE.predict(entity, lead);
        if (prediction == null) return entity.getEntityPos();

        if (prediction.teleport() && resetOnTeleport.get()) {
            PredictEngine.INSTANCE.tracker(entity).reset();
        }

        Vec3d pos = prediction.aim(heightOffset.get());

        if (predictMode.get() == PredictMode.Swept) {
            Box swept = PredictEngine.INSTANCE.sweptBox(entity, entity.getEntityPos(), pos);
            if (swept != null) pos = swept.getCenter();
        } else if (predictMode.get() == PredictMode.Multi) {
            List<Vec3d> candidates = PredictEngine.INSTANCE.candidates(entity, lead, rings.get());
            if (!candidates.isEmpty()) {
                int limit = Math.min(candidates.size(), Math.max(1, maxPoints.get()));
                Vec3d sum = Vec3d.ZERO;
                for (int i = 0; i < limit; i++) sum = sum.add(candidates.get(i));
                pos = sum.multiply(1.0 / limit);
            }
        }
        return pos;
    }

    private double leadTicks(Entity entity) {
        double lead = PredictEngine.INSTANCE.leadTicks(entity, startupTicks.get(), actionTicks.get());
        return Math.max(minLead.get(), Math.min(maxLead.get(), lead));
    }

    private double pingTicks() {
        return Math.max(1.0, (PredictEngine.INSTANCE.myPing() + 1) / 25.0);
    }
}
