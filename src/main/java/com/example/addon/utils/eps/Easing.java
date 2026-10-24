package com.example.addon.utils.eps;

public final class Easing {

    private Easing() {
    }

    public static float easeInOutExpo(float x) {
        if (x <= 0.0f) return 0.0f;
        if (x >= 1.0f) return 1.0f;
        if (x < 0.5f) {
            return (float) (Math.pow(2.0, 20.0 * x - 10.0) / 2.0);
        }
        return (float) ((2.0 - Math.pow(2.0, -20.0 * x + 10.0)) / 2.0);
    }
}
