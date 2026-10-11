package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import dev.sherii.yuio.predict.PredictEngine;
import dev.sherii.yuio.predict.Prediction;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class Tracking extends Module {
    public enum Mode { RandomXZ, Precision }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode").description("Movement mode.").defaultValue(Mode.RandomXZ).build());

    private final Setting<Double> randomSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("random-xz-speed").description("Speed for RandomXZ mode.").defaultValue(1.0).min(0.1).sliderRange(0.1, 15.0)
        .visible(() -> mode.get() == Mode.RandomXZ).build());

    private final Setting<Double> precisionSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("precision-xz-speed").description("Speed for Precision mode.").defaultValue(1.0).min(0.1).sliderRange(0.1, 15.0)
        .visible(() -> mode.get() == Mode.Precision).build());

    private final Setting<Double> closeDistanceThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("close-distance-threshold").description("Close distance threshold.").defaultValue(0.2).min(0.1).sliderRange(0.1, 2.0)
        .visible(() -> mode.get() == Mode.Precision).build());

    private final Setting<Double> minDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-distance").description("Minimum random distance.").defaultValue(2.0).sliderRange(1.0, 5.0)
        .visible(() -> mode.get() == Mode.RandomXZ).build());

    private final Setting<Double> maxDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-distance").description("Maximum random distance.").defaultValue(4.0).sliderRange(1.0, 5.0)
        .visible(() -> mode.get() == Mode.RandomXZ).build());

    private final Setting<SortPriority> sortPriority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("priority").description("Target priority.").defaultValue(SortPriority.LowestDistance).build());

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range").description("Max target range.").defaultValue(100.0).min(0.1).sliderRange(0.1, 500.0).build());

    private final Setting<Double> ySpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("y-speed").description("Vertical speed.").defaultValue(2.0).min(0.1).sliderRange(0.1, 30.0).build());

    private final Setting<Double> xOffset = sgGeneral.add(new DoubleSetting.Builder()
        .name("x-offset").description("X offset.").defaultValue(0.0).sliderRange(-5.0, 5.0).build());

    private final Setting<Double> yOffset = sgGeneral.add(new DoubleSetting.Builder()
        .name("y-offset").description("Y offset.").defaultValue(2.0).sliderRange(-5.0, 5.0).build());

    private final Setting<Double> zOffset = sgGeneral.add(new DoubleSetting.Builder()
        .name("z-offset").description("Z offset.").defaultValue(0.0).sliderRange(-5.0, 5.0).build());

    private final Setting<Boolean> predict = sgGeneral.add(new BoolSetting.Builder()
        .name("predict").description("Predict target position.").defaultValue(false).build());

    private final Setting<Integer> predictTicks = sgGeneral.add(new IntSetting.Builder()
        .name("predict-ticks").description("Ticks to predict.").defaultValue(4).min(1).sliderRange(1, 20)
        .visible(predict::get).build());

    private final Setting<Boolean> wallMode = sgGeneral.add(new BoolSetting.Builder()
        .name("wall-mode").description("Auto VClip through walls when blocked.").defaultValue(false).build());

    private final Setting<Boolean> pathMode = sgGeneral.add(new BoolSetting.Builder()
        .name("path-mode").description("Render and follow a path.").defaultValue(false).build());

    private final Setting<Boolean> useTimer = sgGeneral.add(new BoolSetting.Builder()
        .name("use-timer").description("Use Timer override while active.").defaultValue(false).build());

    private final Setting<Double> timerValue = sgGeneral.add(new DoubleSetting.Builder()
        .name("timer-value").description("Timer speed.").defaultValue(1.0).sliderRange(1.0, 20.0)
        .visible(useTimer::get).build());

    private final Setting<Double> maxMoveDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-move-distance").description("Max distance per teleport packet.").defaultValue(10.0).sliderRange(0.0, 20.0)
        .visible(useTimer::get).build());

    private final Setting<Boolean> pauseWhileUsing = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-while-using").description("Pause when using an item.").defaultValue(false).build());

    private final Setting<Double> tp = sgGeneral.add(new DoubleSetting.Builder()
        .name("tp").description("Teleport distance for path mode.").defaultValue(50.0).min(0.0).sliderRange(0.0, 300.0).build());

    private final Setting<Integer> loops = sgGeneral.add(new IntSetting.Builder()
        .name("loops").description("Path loops.").defaultValue(1000).min(1).sliderRange(1, 2000).build());

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").description("Render shape mode.").defaultValue(ShapeMode.Both).build());

    private final Setting<SettingColor> sideColor = sgGeneral.add(new ColorSetting.Builder()
        .name("side-color").description("Side color.").defaultValue(new SettingColor(255, 255, 255, 150)).build());

    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("line-color").description("Line color.").defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final Setting<Boolean> ignoreBots = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-bots").description("Ignore bots.").defaultValue(false).build());

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends").description("Ignore friends.").defaultValue(false).build());

    private PlayerEntity target;
    private List<Vec3d> lastPath = new ArrayList<>();

    public Tracking() {
        super(AddonTemplate.ASSIST, "TPBot", "Auto trace target");
    }

    @Override
    public void onDeactivate() {
        if (useTimer.get()) {
            Modules.get().get(Timer.class).setOverride(1.0);
        }
        target = null;
        lastPath.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (minDistance.get() > maxDistance.get()) {
            minDistance.set(maxDistance.get());
        }

        target = TargetUtils.getPlayerTarget(targetRange.get(), sortPriority.get());
        if (target == null) return;

        if (mc.player.isUsingItem() && pauseWhileUsing.get()) return;

        if (useTimer.get()) {
            Modules.get().get(Timer.class).setOverride(timerValue.get());
        } else {
            Modules.get().get(Timer.class).setOverride(1.0);
        }

        if (ignoreBots.get() && isBot(target)) return;

        // 预测目标位置
        Vec3d predictedPos = predict.get() ? predictPos(target, predictTicks.get()) : new Vec3d(target.getX(), target.getY(), target.getZ());

        // 如果墙模式开启且视线被阻挡，则尝试 VClip 脱困
        if (wallMode.get() && !mc.player.canSee(target)) {
            bypassWall(target);
        }

        if (mode.get() == Mode.Precision) {
            if (pathMode.get()) {
                lastPath = computePath(predictedPos, loops.get(), tp.get());
                for (Vec3d point : lastPath) {
                    if (wallMode.get() && !mc.player.canSee(target)) {
                        bypassWall(target);
                    }
                    moveTowards(point, precisionSpeed.get(), closeDistanceThreshold.get());
                }
            } else {
                moveTowards(predictedPos.add(xOffset.get(), yOffset.get(), zOffset.get()),
                    precisionSpeed.get(), closeDistanceThreshold.get());
            }
        } else {
            // RandomXZ
            double wrap = Math.atan2(mc.player.getZ() - predictedPos.z, mc.player.getX() - predictedPos.x);
            wrap += randomSpeed.get() / Math.sqrt(mc.player.squaredDistanceTo(predictedPos.x, predictedPos.y, predictedPos.z));
            double targetX = predictedPos.x + randomOffset() * Math.cos(wrap);
            double targetZ = predictedPos.z + randomOffset() * Math.cos(wrap);
            moveTowards(new Vec3d(targetX, predictedPos.y, targetZ), randomSpeed.get(), 0);
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (target == null || lastPath == null || !pathMode.get()) return;
        for (Vec3d pos : lastPath) {
            event.renderer.box(pos.x, pos.y, pos.z, pos.x + 0.3, pos.y + 0.3, pos.z + 0.3, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }

    /**
     * 动态VClip脱困
     */
    private void bypassWall(PlayerEntity target) {
        if (mc.player.canSee(target)) return;

        double step = 2.0;          // 初始步长
        int attempts = 0;
        final int maxAttempts = 10; // 最大尝试次数，防止过度传送

        while (!mc.player.canSee(target) && attempts < maxAttempts) {
            Vec3d direction = new Vec3d(target.getX() - mc.player.getX(), 0, target.getZ() - mc.player.getZ()).normalize();
            Vec3d newPos = new Vec3d(mc.player.getX() + direction.x * step,
                mc.player.getY(),
                mc.player.getZ() + direction.z * step);

            mc.player.setPosition(newPos.x, newPos.y, newPos.z);
            sendMovePacket(newPos);

            if (!mc.player.canSee(target)) {
                step = Math.min(step * 1.5, 20.0);
            }
            attempts++;
        }
    }

    private void moveTowards(Vec3d targetPos, double speed, double closeThreshold) {
        double dx = targetPos.x - mc.player.getX();
        double dy = targetPos.y - mc.player.getY();
        double dz = targetPos.z - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        double velX = speed * -Math.sin(Math.toRadians(wrapDS(targetPos.x, targetPos.z)));
        double velZ = speed * Math.cos(Math.toRadians(wrapDS(targetPos.x, targetPos.z)));
        double velY = 0;
        if (Math.abs(dy) > 0.1) {
            velY = Math.min(ySpeed.get(), Math.abs(dy)) * Math.signum(dy);
        }

        if (dist < closeThreshold) {
            velX *= dist / closeThreshold;
            velZ *= dist / closeThreshold;
        }
        mc.player.setVelocity(velX, velY, velZ);
    }

    private boolean isBot(PlayerEntity player) {
        String name = player.getName().getString().toLowerCase();
        return name.contains("bot") || name.contains("fake");
    }

    private Vec3d predictPos(PlayerEntity player, int ticks) {
        Prediction prediction = PredictEngine.INSTANCE.predict(player, ticks);
        return prediction != null && prediction.pos() != null ? prediction.pos() : player.getEntityPos();
    }

    private List<Vec3d> computePath(Vec3d target, int loops, double tpDist) {
        List<Vec3d> path = new ArrayList<>();
        Vec3d start = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        double dist = start.distanceTo(target);
        int steps = Math.min(loops, (int) (dist / tpDist) + 1);
        for (int i = 0; i <= steps; i++) {
            path.add(start.lerp(target, (double) i / steps));
        }
        return path;
    }

    private double randomOffset() {
        return Math.random() * (maxDistance.get() - minDistance.get()) + minDistance.get();
    }

    private double wrapDS(double x, double z) {
        double diffX = x - mc.player.getX();
        double diffZ = z - mc.player.getZ();
        return Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0;
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
