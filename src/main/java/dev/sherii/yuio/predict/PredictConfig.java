package dev.sherii.yuio.predict;

public final class PredictConfig {
    public static final int MIN_CAPACITY = 8;
    public static final int MAX_CAPACITY = 64;

    public static volatile int sampleCapacity = 24;
    public static volatile double tickMillis = 50.0;
    public static volatile boolean tickCompensation = true;
    public static volatile double minTickScale = 0.5;
    public static volatile double maxTickScale = 3.0;
    public static volatile boolean packetSampling = true;
    public static volatile boolean tickSampling = true;
    public static volatile int stillSamples = 3;

    public static volatile double staticSpeed = 0.008;
    public static volatile double teleportDistance = 3.0;
    public static volatile int teleportCooldown = 10;
    public static volatile boolean clearOnTeleport = true;
    public static volatile double smoothing = 0.35;
    public static volatile int minSamples = 3;
    public static volatile int residualWindow = 6;

    public static volatile boolean orbitEnabled = true;
    public static volatile double orbitMinSpeed = 0.04;
    public static volatile double orbitMinOmega = 0.01;
    public static volatile int orbitMinStreak = 4;
    public static volatile double orbitMaxRadius = 60.0;
    public static volatile double orbitScore = 0.6;

    public static volatile double stalenessDecay = 0.35;
    public static volatile double minConfidence = 0.0;

    public static volatile boolean latencyCompensation = true;
    public static volatile double pingScale = 1.0;
    public static volatile double pipelineTicks = 1.0;
    public static volatile double actionTicks = 1.0;
    public static volatile double minLead = 1.0;
    public static volatile double maxLead = 12.0;

    public static volatile boolean learning = true;
    public static volatile double learningRate = 0.08;
    public static volatile double maxBias = 8.0;
    public static volatile double maxError = 8.0;
    public static volatile double hitRadius = 1.2;

    public static volatile boolean debug = false;
    public static volatile int maxIdleTicks = 100;
    public static volatile int maxTrackedEntities = 128;

    private PredictConfig() {
    }

    public static int capacity() {
        int value = sampleCapacity;
        if (value < MIN_CAPACITY) return MIN_CAPACITY;
        if (value > MAX_CAPACITY) return MAX_CAPACITY;
        return value;
    }

    public static double tickNanos() {
        double value = tickMillis;
        return (value < 1.0 ? 1.0 : value) * 1.0E6;
    }

    public static void restoreDefaults() {
        sampleCapacity = 24;
        tickMillis = 50.0;
        tickCompensation = true;
        minTickScale = 0.5;
        maxTickScale = 3.0;
        packetSampling = true;
        tickSampling = true;
        stillSamples = 3;

        staticSpeed = 0.008;
        teleportDistance = 3.0;
        teleportCooldown = 10;
        clearOnTeleport = true;
        smoothing = 0.35;
        minSamples = 3;
        residualWindow = 6;

        orbitEnabled = true;
        orbitMinSpeed = 0.04;
        orbitMinOmega = 0.01;
        orbitMinStreak = 4;
        orbitMaxRadius = 60.0;
        orbitScore = 0.6;

        stalenessDecay = 0.35;
        minConfidence = 0.0;

        latencyCompensation = true;
        pingScale = 1.0;
        pipelineTicks = 1.0;
        actionTicks = 1.0;
        minLead = 1.0;
        maxLead = 12.0;

        learning = true;
        learningRate = 0.08;
        maxBias = 8.0;
        maxError = 8.0;
        hitRadius = 1.2;

        debug = false;
        maxIdleTicks = 100;
        maxTrackedEntities = 128;
    }
}
