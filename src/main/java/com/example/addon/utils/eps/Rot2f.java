package com.example.addon.utils.eps;

public class Rot2f {

    private float yaw;
    private float pitch;

    public Rot2f() {
        this(0.0f, 0.0f);
    }

    public Rot2f(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public void set(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    @Override
    public String toString() {
        return "Rot2f(yaw=" + yaw + ", pitch=" + pitch + ")";
    }
}
