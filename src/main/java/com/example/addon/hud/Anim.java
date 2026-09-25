package com.example.addon.hud;

/** 指数平滑动画。 */
public class Anim {
    private double value, target;
    private double speed;

    public Anim(double value, double speed) {
        this.value = this.target = value;
        this.speed = speed;
    }

    public void set(double target) {
        this.target = target;
    }

    /** 直接跳到某个值, 不走动画。 */
    public void reset(double value) {
        this.value = this.target = value;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public void update(double delta) {
        value += (target - value) * Math.min(1, delta * speed);
        if (Math.abs(target - value) < 0.001) value = target;
    }

    public double get() {
        return value;
    }

    public double getTarget() {
        return target;
    }
}