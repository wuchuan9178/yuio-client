package dev.sherii.yuio.predict;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public final class TrackedEntity {
    public static final int CAPACITY = PredictConfig.MAX_CAPACITY;

    private final int id;
    private final UUID uuid;
    private final String name;

    private final double[] sx = new double[CAPACITY];
    private final double[] sy = new double[CAPACITY];
    private final double[] sz = new double[CAPACITY];
    private final double[] st = new double[CAPACITY];
    private int size;
    private int cursor;

    private Vec3d last = Vec3d.ZERO;
    private boolean hasLast;
    private Vec3d linear = Vec3d.ZERO;
    private Vec3d smoothed = Vec3d.ZERO;
    private boolean hasSmoothed;
    private Vec3d velocity = Vec3d.ZERO;
    private Vec3d seedVelocity = Vec3d.ZERO;

    private double fit;
    private double variance;
    private double speed;
    private MotionMode mode = MotionMode.Unknown;

    private long lastSeenNanos;
    private int idleTicks;
    private int teleportAge = 1_000_000;
    private boolean teleported;

    private double omega;
    private double centerX;
    private double centerZ;
    private double radius;
    private double orbitScore;
    private int headingStreak;

    private int enqueueTick = -1;
    private int still;

    public TrackedEntity(int id, UUID uuid, String name) {
        this.id = id;
        this.uuid = uuid;
        this.name = name;
    }

    public int id() {
        return this.id;
    }

    public UUID uuid() {
        return this.uuid;
    }

    public String name() {
        return this.name;
    }

    public MotionMode mode() {
        return this.mode;
    }

    public Vec3d velocity() {
        return this.velocity;
    }

    public Vec3d last() {
        return this.last;
    }

    public double speed() {
        return this.speed;
    }

    public double variance() {
        return this.variance;
    }

    public double fit() {
        return this.fit;
    }

    public double radius() {
        return this.radius;
    }

    public double omega() {
        return this.omega;
    }

    public int samples() {
        return this.size;
    }

    public int idleTicks() {
        return this.idleTicks;
    }

    public long lastSeenNanos() {
        return this.lastSeenNanos;
    }

    public boolean teleporting() {
        return this.teleported && this.teleportAge < PredictConfig.teleportCooldown;
    }

    public int enqueueTick() {
        return this.enqueueTick;
    }

    public void enqueueTick(int tick) {
        this.enqueueTick = tick;
    }

    private static int limit() {
        return PredictConfig.capacity();
    }

    public void age() {
        this.idleTicks++;
        if (this.teleportAge < 1_000_000) this.teleportAge++;
    }

    public void sample(double x, double y, double z, long nanos, Vec3d serverVelocity) {
        this.lastSeenNanos = nanos;

        if (this.hasLast && this.last.squaredDistanceTo(x, y, z) < 1.0E-12) {
            this.idleTicks = 0;
            this.still++;
            if (this.still >= PredictConfig.stillSamples && this.mode == MotionMode.Unknown) {
                this.mode = MotionMode.Static;
                this.velocity = Vec3d.ZERO;
                this.speed = 0.0;
                this.fit = 1.0;
            }
            return;
        }
        this.still = 0;

        if (this.hasLast && this.last.distanceTo(new Vec3d(x, y, z)) > PredictConfig.teleportDistance) {
            if (PredictConfig.clearOnTeleport) this.reset();
            this.teleported = true;
            this.teleportAge = 0;
        } else if (this.teleported && this.teleportAge > PredictConfig.teleportCooldown) {
            this.teleported = false;
        }

        if (serverVelocity != null && serverVelocity.lengthSquared() > 1.0E-8 && this.size < 2) {
            this.seedVelocity = serverVelocity;
        }

        this.push(x, y, z, nanos);
        this.last = new Vec3d(x, y, z);
        this.hasLast = true;
        this.idleTicks = 0;
        this.recompute();
    }

    public void reset() {
        this.size = 0;
        this.cursor = 0;
        this.hasLast = false;
        this.hasSmoothed = false;
        this.smoothed = Vec3d.ZERO;
        this.linear = Vec3d.ZERO;
        this.velocity = Vec3d.ZERO;
        this.speed = 0.0;
        this.fit = 0.0;
        this.variance = 0.0;
        this.omega = 0.0;
        this.radius = 0.0;
        this.orbitScore = 0.0;
        this.headingStreak = 0;
        this.still = 0;
        this.mode = MotionMode.Unknown;
    }

    private void push(double x, double y, double z, long nanos) {
        int idx = this.cursor;
        this.sx[idx] = x;
        this.sy[idx] = y;
        this.sz[idx] = z;
        this.st[idx] = nanos / PredictConfig.tickNanos();
        this.cursor = (idx + 1) % CAPACITY;
        this.size = Math.min(this.size + 1, limit());
    }

    private int index(int fromOldest) {
        return (this.cursor - this.size + fromOldest + CAPACITY * 2) % CAPACITY;
    }

    private void recompute() {
        int n = this.size;
        if (n < 2) {
            this.mode = MotionMode.Unknown;
            this.velocity = this.seedVelocity;
            this.speed = this.seedVelocity.horizontalLength();
            this.fit = 0.0;
            this.variance = 0.0;
            return;
        }

        int start = this.index(0);
        double t0 = this.st[start];
        double sumT = 0.0;
        double sumX = 0.0;
        double sumY = 0.0;
        double sumZ = 0.0;
        double sumTT = 0.0;
        double sumTX = 0.0;
        double sumTY = 0.0;
        double sumTZ = 0.0;

        for (int i = 0; i < n; i++) {
            int k = (start + i) % CAPACITY;
            double t = this.st[k] - t0;
            sumT += t;
            sumX += this.sx[k];
            sumY += this.sy[k];
            sumZ += this.sz[k];
            sumTT += t * t;
            sumTX += t * this.sx[k];
            sumTY += t * this.sy[k];
            sumTZ += t * this.sz[k];
        }

        double denom = n * sumTT - sumT * sumT;
        double vx = 0.0;
        double vy = 0.0;
        double vz = 0.0;
        double ax = sumX / n;
        double az = sumZ / n;

        if (Math.abs(denom) > 1.0E-9) {
            vx = (n * sumTX - sumT * sumX) / denom;
            vy = (n * sumTY - sumT * sumY) / denom;
            vz = (n * sumTZ - sumT * sumZ) / denom;
            ax = (sumX - vx * sumT) / n;
            az = (sumZ - vz * sumT) / n;
        }

        double meanX = sumX / n;
        double meanZ = sumZ / n;
        double ssRes = 0.0;
        double ssTot = 0.0;
        double resWindow = 0.0;
        int resCount = 0;
        int window = Math.max(2, PredictConfig.residualWindow);

        for (int i = 0; i < n; i++) {
            int k = (start + i) % CAPACITY;
            double t = this.st[k] - t0;
            double dx = this.sx[k] - (ax + vx * t);
            double dz = this.sz[k] - (az + vz * t);
            double r = dx * dx + dz * dz;
            ssRes += r;
            ssTot += sq(this.sx[k] - meanX) + sq(this.sz[k] - meanZ);
            if (i >= n - window) {
                resWindow += r;
                resCount++;
            }
        }

        double tot = ssTot;
        if (tot < 1.0E-9) {
            this.fit = (vx * vx + vz * vz) < 1.0E-9 ? 1.0 : 0.0;
        } else {
            this.fit = clamp(1.0 - ssRes / tot, 0.0, 1.0);
        }
        this.variance = resCount > 0 ? Math.sqrt(resWindow / (resCount * 2.0)) : 0.0;

        int lastIdx = this.index(n - 1);
        int prevIdx = this.index(n - 2);
        double dt = this.st[lastIdx] - this.st[prevIdx];
        if (dt > 1.0E-6) {
            Vec3d inst = new Vec3d(
                (this.sx[lastIdx] - this.sx[prevIdx]) / dt,
                (this.sy[lastIdx] - this.sy[prevIdx]) / dt,
                (this.sz[lastIdx] - this.sz[prevIdx]) / dt
            );
            double alpha = clamp(PredictConfig.smoothing, 0.0, 1.0);
            this.smoothed = this.hasSmoothed ? this.smoothed.multiply(1.0 - alpha).add(inst.multiply(alpha)) : inst;
            this.hasSmoothed = true;
        }

        this.linear = new Vec3d(vx, vy, vz);
        this.velocity = this.linear.multiply(this.fit).add(this.smoothed.multiply(1.0 - this.fit));
        this.speed = this.velocity.horizontalLength();

        this.fitOrbit(n, start, vx, vz, t0);
        this.selectMode(n);
    }

    private void fitOrbit(int n, int start, double vx, double vz, double t0) {
        this.omega = 0.0;
        this.radius = 0.0;
        this.orbitScore = 0.0;
        this.headingStreak = 0;
        if (!PredictConfig.orbitEnabled) return;

        double hs = Math.hypot(vx, vz);
        if (hs <= PredictConfig.orbitMinSpeed || n < 4) return;

        double lastHeading = Double.NaN;
        int sign = 0;
        int streak = 0;
        double sum = 0.0;
        int count = 0;

        for (int i = 1; i < n; i++) {
            int a = (start + i - 1) % CAPACITY;
            int b = (start + i) % CAPACITY;
            double dx = this.sx[b] - this.sx[a];
            double dz = this.sz[b] - this.sz[a];
            if (dx * dx + dz * dz < 1.0E-8) continue;
            double h = Math.atan2(dz, dx);
            if (!Double.isNaN(lastHeading)) {
                double dh = wrap(h - lastHeading);
                int s = dh > 1.0E-4 ? 1 : (dh < -1.0E-4 ? -1 : 0);
                if (s == 0) {
                    streak = 0;
                } else {
                    if (s == sign) {
                        streak++;
                    } else {
                        sign = s;
                        streak = 1;
                    }
                    sum += dh;
                    count++;
                }
            }
            lastHeading = h;
        }

        this.headingStreak = streak;
        int minStreak = Math.max(2, PredictConfig.orbitMinStreak);
        if (count < minStreak || streak < minStreak) return;

        double span = this.st[this.index(n - 1)] - t0;
        double dtAvg = span / Math.max(1, n - 1);
        if (dtAvg <= 1.0E-6) return;

        double perTick = (sum / count) / dtAvg;
        if (Math.abs(perTick) < PredictConfig.orbitMinOmega) return;
        double r = hs / Math.abs(perTick);
        if (r > PredictConfig.orbitMaxRadius) return;

        double theta = Math.atan2(vz, vx);
        double sgn = Math.signum(perTick);
        int lastIdx = this.index(n - 1);

        this.omega = perTick;
        this.radius = r;
        this.centerX = this.sx[lastIdx] - Math.sin(theta) * sgn * r;
        this.centerZ = this.sz[lastIdx] + Math.cos(theta) * sgn * r;
        this.orbitScore = Math.min(1.0, streak / (minStreak * 2.0));
    }

    private void selectMode(int n) {
        if (this.teleporting()) {
            this.mode = MotionMode.Teleport;
            return;
        }
        double rest = PredictConfig.staticSpeed;
        if (this.speed < rest && Math.abs(this.velocity.y) < rest) {
            this.mode = MotionMode.Static;
            return;
        }
        if (this.orbitScore >= PredictConfig.orbitScore) {
            this.mode = MotionMode.Orbit;
            return;
        }
        this.mode = n >= Math.max(2, PredictConfig.minSamples) ? MotionMode.Linear : MotionMode.Unknown;
    }

    public Vec3d predict(Entity entity, double ticksAhead, double bias, double scale) {
        Vec3d base = this.hasLast ? this.last : (entity == null ? Vec3d.ZERO : entity.getEntityPos());
        double t = ticksAhead + bias;
        if (t <= 0.0) return base;
        if (this.mode == MotionMode.Unknown || this.size < 2) return base;
        if (this.mode == MotionMode.Static || this.mode == MotionMode.Teleport) return base;

        Vec3d v = this.velocity.multiply(scale);

        if (this.mode == MotionMode.Orbit && Math.abs(this.omega) > PredictConfig.orbitMinOmega) {
            double a = this.omega * t;
            double relX = base.x - this.centerX;
            double relZ = base.z - this.centerZ;
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            double nx = this.centerX + relX * cos - relZ * sin;
            double nz = this.centerZ + relX * sin + relZ * cos;
            return new Vec3d(nx, base.y + v.y * t, nz);
        }

        return new Vec3d(base.x + v.x * t, base.y + v.y * t, base.z + v.z * t);
    }

    public double confidence() {
        if (this.mode == MotionMode.Teleport) return 0.0;

        double base = this.mode == MotionMode.Unknown ? 0.0 : clamp(this.fit, 0.0, 1.0);
        if (this.mode == MotionMode.Orbit) base = Math.max(base, clamp(this.orbitScore, 0.0, 1.0));
        if (this.mode == MotionMode.Static) base = 1.0;

        double decay = PredictConfig.stalenessDecay;
        double staleness = this.idleTicks > 2 ? 1.0 / (1.0 + (this.idleTicks - 2) * decay) : 1.0;
        double value = base * staleness;
        double floor = PredictConfig.minConfidence;
        return value < floor ? floor : value;
    }

    private static double wrap(double value) {
        double v = value;
        while (v > Math.PI) v -= Math.PI * 2.0;
        while (v < -Math.PI) v += Math.PI * 2.0;
        return v;
    }

    private static double sq(double value) {
        return value * value;
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }
}
