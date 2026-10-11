package dev.sherii.yuio.predict;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public record Prediction(Vec3d pos, Vec3d velocity, Box box, MotionMode mode, double confidence, double variance, double ticks, boolean teleport) {
    public Vec3d aim() {
        return this.box == null ? this.pos : this.box.getCenter();
    }

    public Vec3d aim(double heightOffset) {
        Vec3d center = this.aim();
        return heightOffset == 0.0 ? center : center.add(0.0, heightOffset, 0.0);
    }

    public boolean known() {
        return this.mode != MotionMode.Unknown;
    }

    public Vec3d error(Vec3d actual) {
        return actual.subtract(this.pos);
    }

    public Box body() {
        return this.box;
    }
}
