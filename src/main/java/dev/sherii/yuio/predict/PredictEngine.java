package dev.sherii.yuio.predict;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPosition;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class PredictEngine {
    public static final PredictEngine INSTANCE = new PredictEngine();

    private static final Logger LOG = LoggerFactory.getLogger("yuio-predict");

    private final Map<Integer, TrackedEntity> trackers = new ConcurrentHashMap<>();
    private final Map<String, PredictProfile> profiles = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Pending> pending = new ConcurrentLinkedQueue<>();

    private int tick;
    private long lastTickNanos;

    static {
        MeteorClient.EVENT_BUS.subscribe(PredictEngine.class);
    }

    private PredictEngine() {
    }

    public static void init() {
        INSTANCE.lastTickNanos = 0L;
    }

    @EventHandler(priority = 500)
    private static void onReceive(PacketEvent.Receive event) {
        INSTANCE.handlePacket(event.packet);
    }

    @EventHandler(priority = 500)
    private static void onTick(TickEvent.Post event) {
        INSTANCE.handleTick();
    }

    private void handlePacket(Packet<?> packet) {
        if (!PredictConfig.packetSampling) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        World world = mc.world;
        if (world == null || packet == null) return;

        if (packet instanceof EntityS2CPacket move) {
            if (!move.isPositionChanged()) return;
            Entity entity = move.getEntity(world);
            if (entity == null || entity.isLogicalSideForUpdatingMovement()) return;
            Vec3d next = entity.getTrackedPosition().withDelta(move.getDeltaX(), move.getDeltaY(), move.getDeltaZ());
            this.sample(entity, next, 0L, null);
            return;
        }

        if (packet instanceof EntityPositionS2CPacket full) {
            Entity entity = world.getEntityById(full.entityId());
            if (entity == null || entity.isLogicalSideForUpdatingMovement()) return;
            EntityPosition change = full.change();
            if (change == null) return;
            this.sample(entity, change.position(), 0L, change.deltaMovement());
            return;
        }

        if (packet instanceof EntityPositionSyncS2CPacket sync) {
            Entity entity = world.getEntityById(sync.id());
            if (entity == null || entity.isLogicalSideForUpdatingMovement()) return;
            EntityPosition values = sync.values();
            if (values == null) return;
            this.sample(entity, values.position(), 0L, values.deltaMovement());
        }
    }

    private void handleTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        World world = mc.world;
        if (world == null) {
            this.trackers.clear();
            this.pending.clear();
            this.lastTickNanos = 0L;
            return;
        }

        long now = System.nanoTime();
        double expected = PredictConfig.tickMillis;
        double dt = this.lastTickNanos == 0L ? expected : (now - this.lastTickNanos) / 1.0E6;
        this.lastTickNanos = now;
        this.tick++;

        double perTick = 1.0;
        if (PredictConfig.tickCompensation) {
            perTick = clamp(dt / (expected < 1.0 ? 1.0 : expected), PredictConfig.minTickScale, PredictConfig.maxTickScale);
        }

        if (PredictConfig.tickSampling) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player == mc.player) continue;
                Vec3d velocity = player.getVelocity().multiply(perTick);
                this.sample(player, player.getEntityPos(), now, velocity.lengthSquared() > 1.0E-8 ? velocity : null);
            }
        }

        int maxIdle = Math.max(5, PredictConfig.maxIdleTicks);
        Iterator<Map.Entry<Integer, TrackedEntity>> iterator = this.trackers.entrySet().iterator();
        while (iterator.hasNext()) {
            TrackedEntity tracked = iterator.next().getValue();
            tracked.age();
            if (tracked.idleTicks() > maxIdle) iterator.remove();
        }

        this.resolvePending();
    }

    private void sample(Entity entity, Vec3d pos, long nanos, Vec3d serverVelocity) {
        if (entity == null || pos == null) return;
        long time = nanos != 0L ? nanos : System.nanoTime();
        this.tracker(entity).sample(pos.x, pos.y, pos.z, time, serverVelocity);
    }

    public TrackedEntity tracker(Entity entity) {
        int id = entity.getId();
        TrackedEntity existing = this.trackers.get(id);
        if (existing != null) return existing;

        int cap = Math.max(1, PredictConfig.maxTrackedEntities);
        if (this.trackers.size() >= cap) this.evictOldest();

        UUID uuid = entity.getUuid();
        TrackedEntity created = new TrackedEntity(id, uuid, entity.getName().getString());
        TrackedEntity previous = this.trackers.putIfAbsent(id, created);
        return previous != null ? previous : created;
    }

    private void evictOldest() {
        Integer worstKey = null;
        int worstIdle = -1;
        for (Map.Entry<Integer, TrackedEntity> entry : this.trackers.entrySet()) {
            int idle = entry.getValue().idleTicks();
            if (idle > worstIdle) {
                worstIdle = idle;
                worstKey = entry.getKey();
            }
        }
        if (worstKey != null) this.trackers.remove(worstKey);
    }

    public PredictProfile profile(Entity entity) {
        if (entity == null) return null;
        String name = entity.getName().getString();
        PredictProfile existing = this.profiles.get(name);
        if (existing != null) return existing;
        PredictProfile created = new PredictProfile(name);
        PredictProfile previous = this.profiles.putIfAbsent(name, created);
        return previous != null ? previous : created;
    }

    public Prediction predict(Entity entity, double ticksAhead) {
        if (entity == null) return null;
        TrackedEntity tracked = this.tracker(entity);
        if (tracked.samples() < 2) this.sample(entity, entity.getEntityPos(), System.nanoTime(), null);

        PredictProfile profile = this.profile(entity);
        double bias = profile == null ? 0.0 : profile.bias();
        double scale = profile == null ? 1.0 : profile.scale();
        Vec3d pos = tracked.predict(entity, ticksAhead, bias, scale);
        Vec3d last = tracked.last();
        Box box = entity.getBoundingBox().offset(pos.x - last.x, pos.y - last.y, pos.z - last.z);

        if (profile != null && ticksAhead > 0.15 && tracked.enqueueTick() != this.tick) {
            tracked.enqueueTick(this.tick);
            this.pending.add(new Pending(entity.getId(), last, pos, ticksAhead, this.tick + (int) Math.ceil(ticksAhead), profile));
            if (PredictConfig.debug) {
                LOG.info("[yuio] predict {} mode={} lead={} conf={} var={} speed={} bias={}",
                    entity.getName().getString(), tracked.mode(), round(ticksAhead), round(tracked.confidence()),
                    round(tracked.variance()), round(tracked.speed()), round(bias));
            }
        }

        return new Prediction(pos, tracked.velocity(), box, tracked.mode(), tracked.confidence(), tracked.variance(), ticksAhead, tracked.teleporting());
    }

    public Prediction predict(Entity entity, int ticksAhead) {
        return this.predict(entity, (double) ticksAhead);
    }

    public double leadTicks(Entity entity, double startup, double action) {
        PredictProfile profile = this.profile(entity);
        double bias = profile == null ? 0.0 : profile.bias();

        double latency = 0.0;
        if (PredictConfig.latencyCompensation) {
            latency = (this.myPing() + this.ping(entity)) / 100.0 * PredictConfig.pingScale;
        }

        double lead = startup + action + latency + PredictConfig.pipelineTicks + bias;
        return clamp(lead, PredictConfig.minLead, PredictConfig.maxLead);
    }

    public List<Vec3d> candidates(Entity entity, double ticksAhead, int rings) {
        List<Vec3d> out = new ArrayList<>();
        Prediction prediction = this.predict(entity, ticksAhead);
        if (prediction == null) return out;
        Vec3d center = prediction.aim();
        out.add(center);
        if (rings <= 0) return out;

        double sigma = Math.max(0.05, prediction.variance());
        Vec3d velocity = prediction.velocity();
        Vec3d forward = velocity.horizontalLengthSquared() > 1.0E-6
            ? new Vec3d(velocity.x, 0.0, velocity.z).normalize()
            : new Vec3d(0.0, 0.0, 1.0);
        Vec3d side = new Vec3d(-forward.z, 0.0, forward.x);

        for (int ring = 1; ring <= rings; ring++) {
            double r = sigma * ring;
            out.add(center.add(forward.multiply(r)));
            out.add(center.add(forward.multiply(-r)));
            out.add(center.add(side.multiply(r)));
            out.add(center.add(side.multiply(-r)));
        }
        return out;
    }

    public Box sweptBox(Entity entity, Vec3d from, Vec3d to) {
        if (entity == null || from == null || to == null) return null;
        Box box = entity.getBoundingBox();
        Box a = box.offset(from.x - entity.getX(), from.y - entity.getY(), from.z - entity.getZ());
        Box b = box.offset(to.x - entity.getX(), to.y - entity.getY(), to.z - entity.getZ());
        return a.union(b);
    }

    public static boolean segmentHit(Vec3d from, Vec3d to, Box box) {
        if (from == null || to == null || box == null) return false;
        double[] origin = {from.x, from.y, from.z};
        double[] delta = {to.x - from.x, to.y - from.y, to.z - from.z};
        double[] lo = {box.minX, box.minY, box.minZ};
        double[] hi = {box.maxX, box.maxY, box.maxZ};
        double t0 = 0.0;
        double t1 = 1.0;

        for (int i = 0; i < 3; i++) {
            if (Math.abs(delta[i]) < 1.0E-12) {
                if (origin[i] < lo[i] || origin[i] > hi[i]) return false;
                continue;
            }
            double a = (lo[i] - origin[i]) / delta[i];
            double b = (hi[i] - origin[i]) / delta[i];
            if (a > b) {
                double swap = a;
                a = b;
                b = swap;
            }
            if (a > t0) t0 = a;
            if (b < t1) t1 = b;
            if (t0 > t1) return false;
        }
        return true;
    }

    public Vec3d step(Entity entity, Vec3d from, Vec3d velocity, double ticks) {
        MinecraftClient mc = MinecraftClient.getInstance();
        World world = mc.world;
        if (entity == null || world == null || from == null || velocity == null) return from;
        Vec3d pos = from;
        Vec3d vel = velocity;
        int steps = (int) Math.max(1.0, Math.ceil(ticks));

        for (int i = 0; i < steps; i++) {
            Box box = entity.getBoundingBox().offset(pos.x - entity.getX(), pos.y - entity.getY(), pos.z - entity.getZ());
            List<VoxelShape> shapes = new ArrayList<>();
            for (VoxelShape shape : world.getBlockCollisions(entity, box)) shapes.add(shape);
            Vec3d moved = Entity.adjustMovementForCollisions(entity, vel, box, world, shapes);
            if (moved.lengthSquared() < 1.0E-12) break;
            pos = pos.add(moved);
            vel = moved;
        }
        return pos;
    }

    public int myPing() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        if (handler == null || mc.player == null) return 0;
        PlayerListEntry entry = handler.getPlayerListEntry(mc.player.getUuid());
        return entry == null ? 0 : entry.getLatency();
    }

    public int ping(Entity entity) {
        if (!(entity instanceof PlayerEntity player)) return 0;
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        if (handler == null) return 0;
        PlayerListEntry entry = handler.getPlayerListEntry(player.getUuid());
        return entry == null ? 0 : entry.getLatency();
    }

    public void registerHit(Entity entity) {
        PredictProfile profile = this.profile(entity);
        if (profile != null) profile.registerHit();
    }

    public void registerMiss(Entity entity) {
        PredictProfile profile = this.profile(entity);
        if (profile != null) profile.registerMiss();
    }

    public double hitRate(Entity entity) {
        PredictProfile profile = this.profile(entity);
        return profile == null ? 0.0 : profile.hitRate();
    }

    public int trackers() {
        return this.trackers.size();
    }

    public int profiles() {
        return this.profiles.size();
    }

    public int tick() {
        return this.tick;
    }

    public void reset() {
        this.trackers.clear();
        this.pending.clear();
        this.lastTickNanos = 0L;
    }

    public void resetAll() {
        this.reset();
        this.profiles.clear();
    }

    private void resolvePending() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) {
            this.pending.clear();
            return;
        }

        while (true) {
            Pending head = this.pending.peek();
            if (head == null || head.dueTick() > this.tick) break;
            this.pending.poll();

            Entity entity = mc.world.getEntityById(head.entityId());
            if (entity == null) continue;

            Vec3d actual = entity.getEntityPos();
            Vec3d base = head.base();
            double dx = actual.x - base.x;
            double dz = actual.z - base.z;
            double wx = head.predicted().x - base.x;
            double wz = head.predicted().z - base.z;
            double wl = Math.sqrt(wx * wx + wz * wz);

            if (wl > 0.02) {
                double lead = (dx * wx + dz * wz) / wl;
                head.profile().learn(lead * head.ticks() / wl - head.ticks());
            }

            if (actual.distanceTo(head.predicted()) <= PredictConfig.hitRadius) {
                head.profile().registerHit();
            } else {
                head.profile().registerMiss();
            }
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double clamp(double value, double min, double max) {
        if (min > max) return value;
        return value < min ? min : (value > max ? max : value);
    }

    private record Pending(int entityId, Vec3d base, Vec3d predicted, double ticks, int dueTick, PredictProfile profile) {
    }
}
