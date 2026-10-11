package dev.sherii.yuio.predict;

public final class PredictProfile {
    private static final double MIN_SCALE = 0.25;
    private static final double MAX_SCALE = 4.0;

    private final String name;
    private double bias;
    private double scale = 1.0;
    private int hits;
    private int misses;
    private int samples;

    public PredictProfile(String name) {
        this.name = name;
    }

    public String name() {
        return this.name;
    }

    public double bias() {
        return this.bias;
    }

    public void bias(double value) {
        double max = PredictConfig.maxBias;
        this.bias = clamp(value, -max, max);
    }

    public double scale() {
        return this.scale;
    }

    public void scale(double value) {
        this.scale = clamp(value, MIN_SCALE, MAX_SCALE);
    }

    public int hits() {
        return this.hits;
    }

    public int misses() {
        return this.misses;
    }

    public int samples() {
        return this.samples;
    }

    public double hitRate() {
        int total = this.hits + this.misses;
        return total == 0 ? 0.0 : (double) this.hits / (double) total;
    }

    public void learn(double errorTicks) {
        if (!PredictConfig.learning) return;
        if (Double.isNaN(errorTicks) || Double.isInfinite(errorTicks)) return;

        double maxError = PredictConfig.maxError;
        double max = PredictConfig.maxBias;
        double rate = PredictConfig.learningRate;
        this.bias = clamp(this.bias + rate * clamp(errorTicks, -maxError, maxError), -max, max);
        this.samples++;
    }

    public void registerHit() {
        this.hits++;
    }

    public void registerMiss() {
        this.misses++;
    }

    public void reset() {
        this.bias = 0.0;
        this.scale = 1.0;
        this.hits = 0;
        this.misses = 0;
        this.samples = 0;
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }
}
